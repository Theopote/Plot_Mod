package com.plot.plugin.earthwork.volume;

import com.plot.plugin.earthwork.grading.ZoneOverlapAnalyzer;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.solver.ProjectGlobalBalanceAggregator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 项目级土方报告：几何挖填合计、分区明细与重叠摘要（无材料运输台账）。
 */
public final class EarthworkProjectReport {
    public static final EarthworkProjectReport EMPTY = new EarthworkProjectReport(
        0L, 0L,
        EarthworkVolumeReport.empty(),
        Map.of(),
        List.of(),
        CompositionPolicy.BALANCE_SCOPE_SITE,
        0,
        Map.of(),
        Map.of(),
        0);

    private final long totalCut;
    private final long totalFill;
    private final EarthworkVolumeReport volumeReport;
    private final Map<String, EarthworkVolumeReport> byZone;
    private final List<OverlapConflict> overlaps;
    private final String balanceScope;
    private final int siteWideVerticalOffset;
    private final Map<String, Integer> zoneVerticalOffsets;
    private final Map<String, ProjectGlobalBalanceAggregator.SiteBalanceSnapshot> bySite;
    private final int sitesWithVolume;

    public EarthworkProjectReport(
            long totalCut,
            long totalFill,
            EarthworkVolumeReport volumeReport,
            Map<String, EarthworkVolumeReport> byZone,
            List<OverlapConflict> overlaps,
            String balanceScope,
            int siteWideVerticalOffset,
            Map<String, Integer> zoneVerticalOffsets,
            Map<String, ProjectGlobalBalanceAggregator.SiteBalanceSnapshot> bySite,
            int sitesWithVolume) {
        this.totalCut = Math.max(0L, totalCut);
        this.totalFill = Math.max(0L, totalFill);
        this.volumeReport = volumeReport != null ? volumeReport : EarthworkVolumeReport.empty();
        this.byZone = byZone != null ? Map.copyOf(byZone) : Map.of();
        this.overlaps = overlaps != null ? List.copyOf(overlaps) : List.of();
        this.balanceScope = balanceScope != null ? balanceScope : CompositionPolicy.BALANCE_SCOPE_SITE;
        this.siteWideVerticalOffset = siteWideVerticalOffset;
        this.zoneVerticalOffsets = zoneVerticalOffsets != null ? Map.copyOf(zoneVerticalOffsets) : Map.of();
        this.bySite = bySite != null ? Map.copyOf(bySite) : Map.of();
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

    public EarthworkVolumeReport volumeReport() {
        return volumeReport;
    }

    public Map<String, EarthworkVolumeReport> byZone() {
        return byZone;
    }

    public List<OverlapConflict> overlaps() {
        return overlaps;
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
            return buildFromProject(null, site, siteReport, 0, Map.of());
        }

        public static EarthworkProjectReport buildFromProject(
                EarthworkProject project,
                EarthworkSite activeSite,
                SiteEarthworkReport activeSiteReport) {
            return buildFromProject(project, activeSite, activeSiteReport, 0, Map.of());
        }

        public static EarthworkProjectReport buildFromProject(
                EarthworkProject project,
                EarthworkSite activeSite,
                SiteEarthworkReport activeSiteReport,
                int siteWideVerticalOffset,
                Map<String, Integer> zoneVerticalOffsets) {
            if (activeSiteReport == null) {
                return empty();
            }
            EarthworkProjectReport active = buildActiveSiteReport(
                activeSite, activeSiteReport, siteWideVerticalOffset, zoneVerticalOffsets);
            String freshSiteId = activeSite != null ? activeSite.getId() : null;
            ProjectGlobalBalanceAggregator.AggregatedBalance global =
                ProjectGlobalBalanceAggregator.aggregate(
                    project,
                    freshSiteId,
                    activeSiteReport,
                    siteWideVerticalOffset,
                    zoneVerticalOffsets);
            if (project == null || project.getSiteCount() <= 1) {
                return withGlobalBalance(active, global);
            }
            return new EarthworkProjectReport(
                global.totalCut(),
                global.totalFill(),
                active.volumeReport(),
                active.byZone(),
                active.overlaps(),
                active.balanceScope(),
                active.siteWideVerticalOffset(),
                active.zoneVerticalOffsets(),
                global.bySite(),
                global.sitesWithVolume());
        }

        public static EarthworkProjectReport buildFromSingleZone(
                EarthworkProject project,
                EarthworkSite site,
                String zoneId,
                EarthworkVolumeReport zoneReport) {
            return buildFromSingleZone(project, site, zoneId, zoneReport, 0, Map.of());
        }

        public static EarthworkProjectReport buildFromSingleZone(
                EarthworkProject project,
                EarthworkSite site,
                String zoneId,
                EarthworkVolumeReport zoneReport,
                int siteWideVerticalOffset,
                Map<String, Integer> zoneVerticalOffsets) {
            if (zoneReport == null) {
                return empty();
            }
            Map<String, EarthworkVolumeReport> byZone = zoneId != null && !zoneId.isBlank()
                ? Map.of(zoneId, zoneReport)
                : Collections.emptyMap();
            SiteEarthworkReport siteReport = new SiteEarthworkReport(zoneReport, byZone);
            return buildFromProject(
                project, site, siteReport, siteWideVerticalOffset, zoneVerticalOffsets);
        }

        private static EarthworkProjectReport buildActiveSiteReport(
                EarthworkSite site,
                SiteEarthworkReport siteReport,
                int siteWideVerticalOffset,
                Map<String, Integer> zoneVerticalOffsets) {
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
            return new EarthworkProjectReport(
                totals.geometricCutVolume(),
                totals.geometricFillVolume(),
                totals,
                byZone,
                overlaps,
                policy.getBalanceScope(),
                siteWideVerticalOffset,
                zoneVerticalOffsets != null ? zoneVerticalOffsets : Map.of(),
                Map.of(),
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
                active.volumeReport(),
                active.byZone(),
                active.overlaps(),
                active.balanceScope(),
                active.siteWideVerticalOffset(),
                active.zoneVerticalOffsets(),
                global.bySite(),
                global.sitesWithVolume());
        }
    }
}
