package com.plot.plugin.earthwork.solver;

import com.plot.core.material.EarthMaterialClass;
import com.plot.core.material.EarthMaterialClassLookup;
import com.plot.core.material.EarthMaterialCompatibilityMatrix;
import com.plot.core.material.MaterialCompatibility;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Mode A — 土方物流调配报告（挖方区 → 填方区 / 场外进出口）。
 * <p>
 * 调配量以<strong>压实填方</strong>（m³）计量，与 {@link EarthworkVolumeReport#compactedFillSurplus()} /
 * {@link EarthworkVolumeReport#compactedFillDeficit()} 一致。
 * <p>
 * 除换算系数外，还按 {@link EarthMaterialClass} 兼容性匹配：岩石/不宜回填等不能进结构填方。
 * 本矩阵<strong>不修改</strong>设计标高；竖向优化见 {@link EarthworkOptimizationSolver}。
 */
public final class EarthworkAllocationMatrix {
    public static final String EXPORT = "__EXPORT__";
    public static final String IMPORT = "__IMPORT__";

    public static final EarthworkAllocationMatrix EMPTY = new EarthworkAllocationMatrix(List.of());

    private final List<Transfer> transfers;

    public EarthworkAllocationMatrix(List<Transfer> transfers) {
        this.transfers = transfers != null ? List.copyOf(transfers) : List.of();
    }

    public List<Transfer> transfers() {
        return transfers;
    }

    public boolean isEmpty() {
        return transfers.isEmpty();
    }

    public long volumeFrom(String sourceZoneId) {
        long total = 0L;
        for (Transfer transfer : transfers) {
            if (transfer.sourceZoneId().equals(sourceZoneId)) {
                total += transfer.volume();
            }
        }
        return total;
    }

    public long volumeTo(String destinationZoneId) {
        long total = 0L;
        for (Transfer transfer : transfers) {
            if (transfer.destinationZoneId().equals(destinationZoneId)) {
                total += transfer.volume();
            }
        }
        return total;
    }

    /** 场外外运合计（压实填方 m³）。 */
    public long externalExportVolume() {
        return volumeTo(EXPORT);
    }

    /** 场外外借合计（压实填方 m³）。 */
    public long externalImportVolume() {
        return volumeFrom(IMPORT);
    }

    /** 场地（或分区）之间的内部调配合计，不含场外进出口。 */
    public long internalTransferVolume() {
        long total = 0L;
        for (Transfer transfer : transfers) {
            if (!transfer.isExport() && !transfer.isImport()) {
                total += transfer.volume();
            }
        }
        return total;
    }

    /**
     * 根据各分区材料换算量 + 材料类别兼容性，生成贪心调配方案。
     * 无场地上下文时按 {@link EarthMaterialClass#UNKNOWN} 宽松兼容（旧行为）。
     */
    public static EarthworkAllocationMatrix fromZoneReports(Map<String, EarthworkVolumeReport> byZone) {
        return fromZoneReports(byZone, EarthMaterialClassLookup.UNKNOWN);
    }

    public static EarthworkAllocationMatrix fromZoneReports(
            Map<String, EarthworkVolumeReport> byZone,
            EarthworkSite site) {
        return fromZoneReports(byZone, siteLookup(site));
    }

    public static EarthworkAllocationMatrix fromZoneReports(
            Map<String, EarthworkVolumeReport> byZone,
            EarthMaterialClassLookup materialLookup) {
        if (byZone == null || byZone.isEmpty()) {
            return EMPTY;
        }
        EarthMaterialClassLookup lookup = materialLookup != null
            ? materialLookup
            : EarthMaterialClassLookup.UNKNOWN;

        List<ZoneLedger> sources = new ArrayList<>();
        List<ZoneLedger> sinks = new ArrayList<>();
        for (Map.Entry<String, EarthworkVolumeReport> entry : byZone.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String zoneId = entry.getKey();
            EarthworkVolumeReport report = entry.getValue();
            EarthMaterialClassLookup.Classes classes = lookup.resolve(zoneId);
            long surplus = Math.round(report.compactedFillSurplus());
            long deficit = Math.round(report.compactedFillDeficit());
            if (surplus > 0L) {
                sources.add(new ZoneLedger(
                    zoneId,
                    resolveDisplayName(zoneId, lookup),
                    surplus,
                    classes.cutClass(),
                    classes.fillClass()));
            } else if (deficit > 0L) {
                sinks.add(new ZoneLedger(
                    zoneId,
                    resolveDisplayName(zoneId, lookup),
                    deficit,
                    classes.cutClass(),
                    classes.fillClass()));
            }
        }
        sources.sort(Comparator.comparingLong(ZoneLedger::remaining).reversed());
        sinks.sort(Comparator.comparingLong(ZoneLedger::remaining).reversed());

        List<Transfer> transfers = new ArrayList<>();
        for (ZoneLedger source : sources) {
            if (source.remaining() <= 0L) {
                continue;
            }
            if (source.cutClass().isExportOnlySpoil()) {
                continue;
            }
            for (ZoneLedger sink : sinks) {
                if (source.remaining() <= 0L) {
                    break;
                }
                if (sink.remaining() <= 0L) {
                    continue;
                }
                MaterialCompatibility compatibility = EarthMaterialCompatibilityMatrix.compatibility(
                    source.cutClass(),
                    sink.fillClass());
                if (!compatibility.allowsTransfer()) {
                    continue;
                }
                long amount = Math.min(source.remaining(), sink.remaining());
                if (amount <= 0L) {
                    continue;
                }
                transfers.add(new Transfer(
                    source.zoneId(),
                    sink.zoneId(),
                    amount,
                    source.cutClass(),
                    compatibility));
                source.consume(amount);
                sink.consume(amount);
            }
        }
        for (ZoneLedger source : sources) {
            if (source.remaining() > 0L) {
                transfers.add(new Transfer(
                    source.zoneId(),
                    EXPORT,
                    source.remaining(),
                    source.cutClass(),
                    MaterialCompatibility.FORBIDDEN));
            }
        }
        for (ZoneLedger sink : sinks) {
            if (sink.remaining() > 0L) {
                transfers.add(new Transfer(
                    IMPORT,
                    sink.zoneId(),
                    sink.remaining(),
                    sink.fillClass(),
                    MaterialCompatibility.FORBIDDEN));
            }
        }
        return new EarthworkAllocationMatrix(transfers);
    }

    private static EarthMaterialClassLookup siteLookup(EarthworkSite site) {
        if (site == null) {
            return EarthMaterialClassLookup.UNKNOWN;
        }
        return id -> {
            GradingZone zone = site.getZone(id);
            if (zone != null) {
                return new EarthMaterialClassLookup.Classes(
                    zone.getCutMaterialClass(),
                    zone.getFillMaterialClass());
            }
            return new EarthMaterialClassLookup.Classes(
                site.getCutMaterialClass(),
                site.getFillMaterialClass());
        };
    }

    private static String resolveDisplayName(String zoneId, EarthMaterialClassLookup lookup) {
        return zoneId != null ? zoneId : "";
    }

    public record Transfer(
            String sourceZoneId,
            String destinationZoneId,
            long volume,
            EarthMaterialClass materialClass,
            MaterialCompatibility compatibility) {

        public Transfer(String sourceZoneId, String destinationZoneId, long volume) {
            this(
                sourceZoneId,
                destinationZoneId,
                volume,
                EarthMaterialClass.UNKNOWN,
                MaterialCompatibility.ALLOWED);
        }

        /** 调配量单位：压实填方 m³。 */
        public boolean isExport() {
            return EXPORT.equals(destinationZoneId);
        }

        public boolean isImport() {
            return IMPORT.equals(sourceZoneId);
        }
    }

    private static final class ZoneLedger {
        private final String zoneId;
        private final String zoneName;
        private final EarthMaterialClass cutClass;
        private final EarthMaterialClass fillClass;
        private long remaining;

        private ZoneLedger(
                String zoneId,
                String zoneName,
                long amount,
                EarthMaterialClass cutClass,
                EarthMaterialClass fillClass) {
            this.zoneId = zoneId;
            this.zoneName = zoneName;
            this.cutClass = cutClass != null ? cutClass : EarthMaterialClass.UNKNOWN;
            this.fillClass = fillClass != null ? fillClass : EarthMaterialClass.COMMON_FILL;
            this.remaining = Math.max(0L, amount);
        }

        private String zoneId() {
            return zoneId;
        }

        private String zoneName() {
            return zoneName;
        }

        private EarthMaterialClass cutClass() {
            return cutClass;
        }

        private EarthMaterialClass fillClass() {
            return fillClass;
        }

        private long remaining() {
            return remaining;
        }

        private void consume(long amount) {
            remaining = Math.max(0L, remaining - amount);
        }
    }
}
