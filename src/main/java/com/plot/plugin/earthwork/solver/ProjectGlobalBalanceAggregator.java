package com.plot.plugin.earthwork.solver;

import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目级土方平衡汇总：合并多场地挖填量，并生成跨场地调配矩阵。
 * <p>
 * 项目级 {@code importRequired} / {@code exportRequired} 取自跨场地调配后的
 * <strong>场外</strong>进出口（压实填方），不是各场地 import/export 的简单相加。
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
            double reusableCut,
            double importRequired,
            double exportRequired,
            Map<String, SiteBalanceSnapshot> bySite,
            EarthworkAllocationMatrix crossSiteAllocationMatrix,
            int sitesWithVolume) {
    }

    private ProjectGlobalBalanceAggregator() {
    }

    public static AggregatedBalance aggregate(EarthworkProject project) {
        return aggregate(project, null, null);
    }

    /**
     * @param freshSiteId     本次预览/生成的场地 id（优先使用 {@code freshSiteReport}）
     * @param freshSiteReport 当前场地最新方量；其它场地使用各自 {@link EarthworkSite#getLastReport()}
     */
    public static AggregatedBalance aggregate(
            EarthworkProject project,
            String freshSiteId,
            SiteEarthworkReport freshSiteReport) {
        if (project == null || project.getSiteCount() == 0) {
            return empty();
        }

        long totalCut = 0L;
        long totalFill = 0L;
        double reusableCut = 0.0;
        Map<String, SiteBalanceSnapshot> bySite = new LinkedHashMap<>();
        Map<String, EarthworkVolumeReport> matrixInputs = new LinkedHashMap<>();

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
                site.getLastSiteWideVerticalOffset(),
                site.getLastZoneVerticalOffsets(),
                fresh));
            matrixInputs.put(site.getId(), volumes);

            totalCut += volumes.geometricCutVolume();
            totalFill += volumes.geometricFillVolume();
            reusableCut += volumes.reusableCutVolume();
        }

        // 先做跨场地（或多场地）调配，再读场外进出口；禁止对各场地 import/export 求和。
        EarthworkAllocationMatrix projectMatrix = matrixInputs.isEmpty()
            ? EarthworkAllocationMatrix.EMPTY
            : EarthworkAllocationMatrix.fromZoneReports(matrixInputs, null);
        EarthworkAllocationMatrix crossSite = matrixInputs.size() >= 2
            ? projectMatrix
            : EarthworkAllocationMatrix.EMPTY;

        return new AggregatedBalance(
            totalCut,
            totalFill,
            reusableCut,
            projectMatrix.externalImportVolume(),
            projectMatrix.externalExportVolume(),
            Map.copyOf(bySite),
            crossSite,
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
        return new AggregatedBalance(
            0L,
            0L,
            0.0,
            0.0,
            0.0,
            Map.of(),
            EarthworkAllocationMatrix.EMPTY,
            0);
    }
}
