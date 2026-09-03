package com.plot.plugin.earthwork.solver;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;

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
 * 本矩阵<strong>不修改</strong>设计标高；仅描述既定 Design Terrain 上的土方怎么搬。
 * 竖向设计优化见 {@link EarthworkOptimizationSolver}（Mode B）。
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

    /**
     * 根据各分区材料感知挖填量，生成贪心调配方案（压实填方余量 → 缺量 → 余方外运/缺方外借）。
     */
    public static EarthworkAllocationMatrix fromZoneReports(
            Map<String, EarthworkVolumeReport> byZone,
            EarthworkSite site) {
        if (byZone == null || byZone.isEmpty()) {
            return EMPTY;
        }
        List<ZoneLedger> sources = new ArrayList<>();
        List<ZoneLedger> sinks = new ArrayList<>();
        for (Map.Entry<String, EarthworkVolumeReport> entry : byZone.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String zoneId = entry.getKey();
            EarthworkVolumeReport report = entry.getValue();
            long surplus = Math.round(report.compactedFillSurplus());
            long deficit = Math.round(report.compactedFillDeficit());
            if (surplus > 0L) {
                sources.add(new ZoneLedger(zoneId, resolveZoneName(site, zoneId), surplus));
            } else if (deficit > 0L) {
                sinks.add(new ZoneLedger(zoneId, resolveZoneName(site, zoneId), deficit));
            }
        }
        sources.sort(Comparator.comparingLong(ZoneLedger::remaining).reversed());
        sinks.sort(Comparator.comparingLong(ZoneLedger::remaining).reversed());

        List<Transfer> transfers = new ArrayList<>();
        int sourceIndex = 0;
        int sinkIndex = 0;
        while (sourceIndex < sources.size() && sinkIndex < sinks.size()) {
            ZoneLedger source = sources.get(sourceIndex);
            ZoneLedger sink = sinks.get(sinkIndex);
            long amount = Math.min(source.remaining(), sink.remaining());
            if (amount <= 0L) {
                break;
            }
            transfers.add(new Transfer(source.zoneId(), sink.zoneId(), amount));
            source.consume(amount);
            sink.consume(amount);
            if (source.remaining() == 0L) {
                sourceIndex++;
            }
            if (sink.remaining() == 0L) {
                sinkIndex++;
            }
        }
        for (ZoneLedger source : sources) {
            if (source.remaining() > 0L) {
                transfers.add(new Transfer(source.zoneId(), EXPORT, source.remaining()));
            }
        }
        for (ZoneLedger sink : sinks) {
            if (sink.remaining() > 0L) {
                transfers.add(new Transfer(IMPORT, sink.zoneId(), sink.remaining()));
            }
        }
        return new EarthworkAllocationMatrix(transfers);
    }

    private static String resolveZoneName(EarthworkSite site, String zoneId) {
        if (site == null || zoneId == null) {
            return zoneId != null ? zoneId : "";
        }
        GradingZone zone = site.getZone(zoneId);
        if (zone == null) {
            return zoneId;
        }
        String name = zone.getName();
        return name != null && !name.isBlank() ? name : zoneId;
    }

    public record Transfer(String sourceZoneId, String destinationZoneId, long volume) {
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
        private long remaining;

        private ZoneLedger(String zoneId, String zoneName, long amount) {
            this.zoneId = zoneId;
            this.zoneName = zoneName;
            this.remaining = Math.max(0L, amount);
        }

        private String zoneId() {
            return zoneId;
        }

        private String zoneName() {
            return zoneName;
        }

        private long remaining() {
            return remaining;
        }

        private void consume(long amount) {
            remaining = Math.max(0L, remaining - amount);
        }
    }
}
