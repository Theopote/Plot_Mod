package com.plot.plugin.earthwork.volume;

import com.plot.plugin.earthwork.grading.ZoneOverlapAnalyzer;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.solver.ProjectGlobalBalanceAggregator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 项目级土方平衡报告：全场挖填合计、材料调配与分区重叠摘要。
 */
public final class EarthworkProjectReport {
    public static final EarthworkProjectReport EMPTY = new EarthworkProjectReport(
        0L, 0L, 0.0,
        ProjectMaterialBalance.EMPTY,
        EarthworkVolumeReport.empty(),
        Map.of(),
        List.of(),
        EarthworkAllocationMatrix.EMPTY,
        CompositionPolicy.BALANCE_SCOPE_SITE,
        0,
        Map.of(),
        Map.of(),
        EarthworkAllocationMatrix.EMPTY,
        0);

    private final long totalCut;
    private final long totalFill;
    private final double reusableCut;
    private final ProjectMaterialBalance materialBalance;
    private final EarthworkVolumeReport volumeReport;
    private final Map<String, EarthworkVolumeReport> byZone;
    private final List<OverlapConflict> overlaps;
    private final EarthworkAllocationMatrix allocationMatrix;
    private final String balanceScope;
    private final int siteWideVerticalOffset;
    private final Map<String, Integer> zoneVerticalOffsets;
    private final Map<String, ProjectGlobalBalanceAggregator.SiteBalanceSnapshot> bySite;
    private final EarthworkAllocationMatrix crossSiteAllocationMatrix;
    private final int sitesWithVolume;

    public EarthworkProjectReport(
            long totalCut,
            long totalFill,
            double reusableCut,
            ProjectMaterialBalance materialBalance,
            EarthworkVolumeReport volumeReport,
            Map<String, EarthworkVolumeReport> byZone,
            List<OverlapConflict> overlaps,
            EarthworkAllocationMatrix allocationMatrix,
            String balanceScope,
            int siteWideVerticalOffset,
            Map<String, Integer> zoneVerticalOffsets,
            Map<String, ProjectGlobalBalanceAggregator.SiteBalanceSnapshot> bySite,
            EarthworkAllocationMatrix crossSiteAllocationMatrix,
            int sitesWithVolume) {
        this.totalCut = Math.max(0L, totalCut);
        this.totalFill = Math.max(0L, totalFill);
        this.reusableCut = Math.max(0.0, reusableCut);
        this.materialBalance = materialBalance != null ? materialBalance : ProjectMaterialBalance.EMPTY;
        this.volumeReport = volumeReport != null ? volumeReport : EarthworkVolumeReport.empty();
        this.byZone = byZone != null ? Map.copyOf(byZone) : Map.of();
        this.overlaps = overlaps != null ? List.copyOf(overlaps) : List.of();
        this.allocationMatrix = allocationMatrix != null ? allocationMatrix : EarthworkAllocationMatrix.EMPTY;
        this.balanceScope = balanceScope != null ? balanceScope : CompositionPolicy.BALANCE_SCOPE_SITE;
        this.siteWideVerticalOffset = siteWideVerticalOffset;
        this.zoneVerticalOffsets = zoneVerticalOffsets != null ? Map.copyOf(zoneVerticalOffsets) : Map.of();
        this.bySite = bySite != null ? Map.copyOf(bySite) : Map.of();
        this.crossSiteAllocationMatrix = crossSiteAllocationMatrix != null
            ? crossSiteAllocationMatrix
            : EarthworkAllocationMatrix.EMPTY;
        this.sitesWithVolume = Math.max(0, sitesWithVolume);
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

    public ProjectMaterialBalance materialBalance() {
        return materialBalance;
    }

    public double grossImportDemand() {
        return materialBalance.grossImportDemand();
    }

    public double grossExportSurplus() {
        return materialBalance.grossExportSurplus();
    }

    public double internalTransferVolume() {
        return materialBalance.internalTransferVolume();
    }

    public double externalImportRequired() {
        return materialBalance.externalImportRequired();
    }

    public double externalExportRequired() {
        return materialBalance.externalExportRequired();
    }

    /**
     * @deprecated 使用 {@link #externalImportRequired()}
     */
    @Deprecated
    public double importRequired() {
        return externalImportRequired();
    }

    /**
     * @deprecated 使用 {@link #externalExportRequired()}
     */
    @Deprecated
    public double exportRequired() {
        return externalExportRequired();
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

    public int siteWideVerticalOffset() {
        return siteWideVerticalOffset;
    }

    public Map<String, Integer> zoneVerticalOffsets() {
        return zoneVerticalOffsets;
    }

    public Map<String, ProjectGlobalBalanceAggregator.SiteBalanceSnapshot> bySite() {
        return bySite;
    }

    public EarthworkAllocationMatrix crossSiteAllocationMatrix() {
        return crossSiteAllocationMatrix;
    }

    public int sitesWithVolume() {
        return sitesWithVolume;
    }

    public boolean hasZoneVerticalOffsets() {
        return !zoneVerticalOffsets.isEmpty();
    }

    public boolean hasZoneBreakdown() {
        return byZone.size() > 1;
    }

    public boolean hasCrossSiteBreakdown() {
        return sitesWithVolume > 1;
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
            return buildFromProject(null, site, siteReport);
        }

        public static EarthworkProjectReport buildFromProject(
                EarthworkProject project,
                EarthworkSite activeSite,
                SiteEarthworkReport activeSiteReport) {
            if (activeSiteReport == null) {
                return empty();
            }
            EarthworkProjectReport active = buildActiveSiteReport(activeSite, activeSiteReport);
            if (project == null || project.getSiteCount() <= 1) {
                ProjectGlobalBalanceAggregator.AggregatedBalance global =
                    ProjectGlobalBalanceAggregator.aggregate(
                        project,
                        activeSite != null ? activeSite.getId() : null,
                        activeSiteReport);
                return withGlobalBalance(active, global);
            }

            ProjectGlobalBalanceAggregator.AggregatedBalance global =
                ProjectGlobalBalanceAggregator.aggregate(project, activeSite.getId(), activeSiteReport);
            return new EarthworkProjectReport(
                global.totalCut(),
                global.totalFill(),
                global.reusableCut(),
                global.materialBalance(),
                active.volumeReport(),
                active.byZone(),
                active.overlaps(),
                active.allocationMatrix(),
                active.balanceScope(),
                active.siteWideVerticalOffset(),
                active.zoneVerticalOffsets(),
                global.bySite(),
                global.crossSiteAllocationMatrix(),
                global.sitesWithVolume());
        }

        public static EarthworkProjectReport buildFromSingleZone(
                EarthworkProject project,
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
            return buildFromProject(project, site, siteReport);
        }

        private static EarthworkProjectReport buildActiveSiteReport(
                EarthworkSite site,
                SiteEarthworkReport siteReport) {
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
            int siteWideOffset = site != null ? site.getLastSiteWideVerticalOffset() : 0;
            Map<String, Integer> zoneOffsets = site != null
                ? site.getLastZoneVerticalOffsets()
                : Map.of();
            return new EarthworkProjectReport(
                totals.geometricCutVolume(),
                totals.geometricFillVolume(),
                totals.reusableCutVolume(),
                ProjectMaterialBalance.fromSiteVolumes(totals),
                totals,
                byZone,
                overlaps,
                matrix,
                policy.getBalanceScope(),
                siteWideOffset,
                zoneOffsets,
                Map.of(),
                EarthworkAllocationMatrix.EMPTY,
                0);
        }

        private static EarthworkProjectReport withGlobalBalance(
                EarthworkProjectReport active,
                ProjectGlobalBalanceAggregator.AggregatedBalance global) {
            if (global == null || global.sitesWithVolume() == 0) {
                return active;
            }
            return new EarthworkProjectReport(
                global.totalCut(),
                global.totalFill(),
                global.reusableCut(),
                global.materialBalance(),
                active.volumeReport(),
                active.byZone(),
                active.overlaps(),
                active.allocationMatrix(),
                active.balanceScope(),
                active.siteWideVerticalOffset(),
                active.zoneVerticalOffsets(),
                global.bySite(),
                global.crossSiteAllocationMatrix(),
                global.sitesWithVolume());
        }
    }
}
