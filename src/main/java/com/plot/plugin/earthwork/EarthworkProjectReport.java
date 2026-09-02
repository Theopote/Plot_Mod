package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 项目级土方平衡报告：全场挖填合计、材料调配与分区重叠摘要。
 */
public final class EarthworkProjectReport {
    public static final EarthworkProjectReport EMPTY = new EarthworkProjectReport(
        0L, 0L, 0.0, 0.0, 0.0,
        EarthworkVolumeReport.empty(),
        Map.of(),
        List.of(),
        EarthworkAllocationMatrix.EMPTY,
        CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);

    private final long totalCut;
    private final long totalFill;
    private final double reusableCut;
    private final double importRequired;
    private final double exportRequired;
    private final EarthworkVolumeReport volumeReport;
    private final Map<String, EarthworkVolumeReport> byZone;
    private final List<OverlapConflict> overlaps;
    private final EarthworkAllocationMatrix allocationMatrix;
    private final String balanceScope;

    public EarthworkProjectReport(
            long totalCut,
            long totalFill,
            double reusableCut,
            double importRequired,
            double exportRequired,
            EarthworkVolumeReport volumeReport,
            Map<String, EarthworkVolumeReport> byZone,
            List<OverlapConflict> overlaps,
            EarthworkAllocationMatrix allocationMatrix,
            String balanceScope) {
        this.totalCut = Math.max(0L, totalCut);
        this.totalFill = Math.max(0L, totalFill);
        this.reusableCut = Math.max(0.0, reusableCut);
        this.importRequired = Math.max(0.0, importRequired);
        this.exportRequired = Math.max(0.0, exportRequired);
        this.volumeReport = volumeReport != null ? volumeReport : EarthworkVolumeReport.empty();
        this.byZone = byZone != null ? Map.copyOf(byZone) : Map.of();
        this.overlaps = overlaps != null ? List.copyOf(overlaps) : List.of();
        this.allocationMatrix = allocationMatrix != null ? allocationMatrix : EarthworkAllocationMatrix.EMPTY;
        this.balanceScope = balanceScope != null ? balanceScope : CompositionPolicy.BALANCE_SCOPE_SITE_WIDE;
    }

    public static EarthworkProjectReport empty() {
        return EMPTY;
    }

    public long totalCut() {
        return totalCut;
    }

    public long totalFill() {
        return totalFill;
    }

    public long netVolume() {
        return totalCut - totalFill;
    }

    public double reusableCut() {
        return reusableCut;
    }

    public double importRequired() {
        return importRequired;
    }

    public double exportRequired() {
        return exportRequired;
    }

    public EarthworkVolumeReport volumeReport() {
        return volumeReport;
    }

    public Map<String, EarthworkVolumeReport> byZone() {
        return byZone;
    }

    public List<OverlapConflict> overlaps() {
        return overlaps;
    }

    public EarthworkAllocationMatrix allocationMatrix() {
        return allocationMatrix;
    }

    public String balanceScope() {
        return balanceScope;
    }

    public boolean hasZoneBreakdown() {
        return byZone.size() > 1;
    }

    public record OverlapConflict(
            String zoneIdA,
            String zoneNameA,
            int priorityA,
            String zoneIdB,
            String zoneNameB,
            int priorityB,
            int overlapCells,
            String overlapResolution,
            String winnerZoneId,
            String winnerZoneName) {

        public static OverlapConflict from(
                ZoneOverlapAnalyzer.ZoneOverlap overlap,
                CompositionPolicy policy) {
            CompositionPolicy safePolicy = policy != null ? policy : CompositionPolicy.DEFAULT;
            String winnerId = overlap.resolveWinner(safePolicy);
            return new OverlapConflict(
                overlap.zoneIdA(),
                overlap.zoneNameA(),
                overlap.priorityA(),
                overlap.zoneIdB(),
                overlap.zoneNameB(),
                overlap.priorityB(),
                overlap.overlapCells(),
                safePolicy.getOverlapResolution(),
                winnerId,
                winnerId.equals(overlap.zoneIdA()) ? overlap.zoneNameA() : overlap.zoneNameB());
        }
    }

    public static final class Builder {
        private Builder() {
        }

        public static EarthworkProjectReport build(
                EarthworkSite site,
                SiteEarthworkReport siteReport) {
            if (siteReport == null) {
                return empty();
            }
            CompositionPolicy policy = site != null
                ? site.getCompositionPolicy()
                : CompositionPolicy.DEFAULT;
            EarthworkVolumeReport totals = siteReport.totals();
            Map<String, EarthworkVolumeReport> byZone = siteReport.byZone();
            List<OverlapConflict> overlaps = site != null
                ? ZoneOverlapAnalyzer.findOverlaps(site).stream()
                    .map(overlap -> OverlapConflict.from(overlap, policy))
                    .toList()
                : List.of();
            EarthworkAllocationMatrix matrix = EarthworkAllocationMatrix.fromZoneReports(byZone, site);
            return new EarthworkProjectReport(
                totals.geometricCutVolume(),
                totals.geometricFillVolume(),
                totals.reusableCutVolume(),
                totals.importVolume(),
                totals.exportVolume(),
                totals,
                byZone,
                overlaps,
                matrix,
                policy.getBalanceScope());
        }

        public static EarthworkProjectReport buildFromSingleZone(
                EarthworkSite site,
                String zoneId,
                EarthworkVolumeReport zoneReport) {
            if (zoneReport == null) {
                return empty();
            }
            Map<String, EarthworkVolumeReport> byZone = zoneId != null && !zoneId.isBlank()
                ? Map.of(zoneId, zoneReport)
                : Collections.emptyMap();
            SiteEarthworkReport siteReport = new SiteEarthworkReport(zoneReport, byZone);
            return build(site, siteReport);
        }
    }
}
