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
 * 调配量以<strong>压实填方</strong>计量，与 {@link EarthworkVolumeReport#compactedFillSurplus()} /
 * {@link EarthworkVolumeReport#compactedFillDeficit()} 一致。台账匹配用 {@code double}；
 * {@link Transfer#volume()} 为离散 long（当前 Minecraft 方块≈1 m³ 代理）。亚单位量经
 * {@code Math.round} 可能变为 0——体素尺度可接受；若日后改为连续现实 m³ 或 1 block≠1 m³，
 * 应让 Transfer 改用 double。
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

    /** 场外外运合计（压实填方；离散 long）。 */
    public long externalExportVolume() {
        return volumeTo(EXPORT);
    }

    /** 场外外借合计（压实填方；离散 long）。 */
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
            double surplus = report.compactedFillSurplus();
            double deficit = report.compactedFillDeficit();
            if (surplus > 0.0) {
                sources.add(new ZoneLedger(zoneId, surplus, classes.cutClass(), classes.fillClass()));
            } else if (deficit > 0.0) {
                sinks.add(new ZoneLedger(zoneId, deficit, classes.cutClass(), classes.fillClass()));
            }
        }
        sources.sort(Comparator.comparingDouble(ZoneLedger::remaining).reversed());
        sinks.sort(Comparator.comparingDouble(ZoneLedger::remaining).reversed());

        List<Transfer> transfers = new ArrayList<>();
        for (ZoneLedger source : sources) {
            if (source.remaining() <= 0.0) {
                continue;
            }
            if (source.cutClass().isExportOnlySpoil()) {
                continue;
            }
            for (ZoneLedger sink : sinks) {
                if (source.remaining() <= 0.0) {
                    break;
                }
                if (sink.remaining() <= 0.0) {
                    continue;
                }
                MaterialCompatibility compatibility = EarthMaterialCompatibilityMatrix.compatibility(
                    source.cutClass(),
                    sink.fillClass());
                if (!compatibility.allowsTransfer()) {
                    continue;
                }
                double amount = Math.min(source.remaining(), sink.remaining());
                long reported = Math.round(amount);
                if (reported <= 0L) {
                    // 亚单位在离散 Transfer 中丢弃；台账仍扣减，避免反复匹配同一碎量。
                    source.consume(amount);
                    sink.consume(amount);
                    continue;
                }
                transfers.add(new Transfer(
                    source.zoneId(),
                    sink.zoneId(),
                    reported,
                    source.cutClass(),
                    compatibility));
                source.consume(amount);
                sink.consume(amount);
            }
        }
        for (ZoneLedger source : sources) {
            long reported = Math.round(source.remaining());
            if (reported > 0L) {
                transfers.add(new Transfer(
                    source.zoneId(),
                    EXPORT,
                    reported,
                    source.cutClass(),
                    MaterialCompatibility.FORBIDDEN));
            }
        }
        for (ZoneLedger sink : sinks) {
            long reported = Math.round(sink.remaining());
            if (reported > 0L) {
                transfers.add(new Transfer(
                    IMPORT,
                    sink.zoneId(),
                    reported,
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

    /**
     * @param volume 离散压实填方量（方块代理）。显示名由 UI 按 zoneId 解析，不在此冗余存储。
     */
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

        public boolean isExport() {
            return EXPORT.equals(destinationZoneId);
        }

        public boolean isImport() {
            return IMPORT.equals(sourceZoneId);
        }
    }

    /** 匹配台账：余量保持 double，直到写出 {@link Transfer} 再 round。 */
    private static final class ZoneLedger {
        private final String zoneId;
        private final EarthMaterialClass cutClass;
        private final EarthMaterialClass fillClass;
        private double remaining;

        private ZoneLedger(
                String zoneId,
                double amount,
                EarthMaterialClass cutClass,
                EarthMaterialClass fillClass) {
            this.zoneId = zoneId;
            this.cutClass = cutClass != null ? cutClass : EarthMaterialClass.UNKNOWN;
            this.fillClass = fillClass != null ? fillClass : EarthMaterialClass.COMMON_FILL;
            this.remaining = Math.max(0.0, amount);
        }

        private String zoneId() {
            return zoneId;
        }

        private EarthMaterialClass cutClass() {
            return cutClass;
        }

        private EarthMaterialClass fillClass() {
            return fillClass;
        }

        private double remaining() {
            return remaining;
        }

        private void consume(double amount) {
            remaining = Math.max(0.0, remaining - amount);
        }
    }
}
