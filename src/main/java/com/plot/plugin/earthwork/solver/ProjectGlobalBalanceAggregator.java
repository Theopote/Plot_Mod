package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目级土方汇总：合并多场地几何挖填量（无材料调配矩阵）。
 */
public final class ProjectGlobalBalanceAggregator {

    public record SiteBalanceSnapshot(
            String siteId,
            String siteName,
            EarthworkVolumeReport volumes,
            String balanceScope,
            int siteWideVerticalOffset,
            Map<String, Integer> zoneVerticalOffsets,
            boolean previewFresh) {
    }

    public record AggregatedBalance(
            long totalCut,
            long totalFill,
            Map<String, SiteBalanceSnapshot> bySite,
            int sitesWithVolume) {
        public long netVolume() {
            return totalCut - totalFill;
        }
    }

    private ProjectGlobalBalanceAggregator() {
    }

    public static AggregatedBalance aggregate(EarthworkProject project) {
        return aggregate(project, null, null, 0, Map.of());
    }

    /**
     * @param freshSiteId     本次预览/生成的场地 id（优先使用 {@code freshSiteReport}）
     * @param freshSiteReport 当前场地最新方量；其它场地使用各自 {@link EarthworkSite#getLastReport()}
     */
    public static AggregatedBalance aggregate(
            EarthworkProject project,
            String freshSiteId,
            SiteEarthworkReport freshSiteReport) {
        return aggregate(project, freshSiteId, freshSiteReport, 0, Map.of());
    }

    /**
     * @param freshSiteWideOffset 本次预览求解的统一 ΔY（仅挂到 fresh 场地快照）
     * @param freshZoneOffsets    本次预览求解的分区 ΔY
     */
    public static AggregatedBalance aggregate(
            EarthworkProject project,
            String freshSiteId,
            SiteEarthworkReport freshSiteReport,
            int freshSiteWideOffset,
            Map<String, Integer> freshZoneOffsets) {
        if (project == null || project.getSiteCount() == 0) {
            return empty();
        }

        long totalCut = 0L;
        long totalFill = 0L;
        Map<String, SiteBalanceSnapshot> bySite = new LinkedHashMap<>();
        Map<String, Integer> safeFreshZones =
            freshZoneOffsets != null ? Map.copyOf(freshZoneOffsets) : Map.of();

        for (EarthworkSite site : project.getSites().values()) {
            if (site == null) {
                continue;
            }
            boolean fresh = freshSiteId != null && freshSiteId.equals(site.getId());
            EarthworkVolumeReport volumes = resolveSiteVolumes(site, fresh, freshSiteReport);
            if (!volumes.hasGeometricVolume()) {
                continue;
            }

            bySite.put(site.getId(), new SiteBalanceSnapshot(
                site.getId(),
                site.getName(),
                volumes,
                site.getCompositionPolicy().getBalanceScope(),
                fresh ? freshSiteWideOffset : 0,
                fresh ? safeFreshZones : Map.of(),
                fresh));

            totalCut += volumes.geometricCutVolume();
            totalFill += volumes.geometricFillVolume();
        }

        return new AggregatedBalance(
            totalCut,
            totalFill,
            Map.copyOf(bySite),
            bySite.size());
    }

    private static EarthworkVolumeReport resolveSiteVolumes(
            EarthworkSite site,
            boolean fresh,
            SiteEarthworkReport freshSiteReport) {
        if (fresh && freshSiteReport != null) {
            return freshSiteReport.totals();
        }
        return site.getLastReport();
    }

    private static AggregatedBalance empty() {
        return new AggregatedBalance(0L, 0L, Map.of(), 0);
    }
}
