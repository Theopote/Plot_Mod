package com.plot.plugin.earthwork.solver;

import com.plot.core.material.EarthMaterialClassLookup;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.ProjectMaterialBalance;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目级土方平衡汇总：合并多场地挖填量，并生成跨场地调配矩阵。
 * <p>
 * 材料数字分三层：场地毛缺量/余量、跨场地内部调配、场外净进出口（均为压实填方）。
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
            ProjectMaterialBalance materialBalance,
            Map<String, SiteBalanceSnapshot> bySite,
            EarthworkAllocationMatrix crossSiteAllocationMatrix,
            int sitesWithVolume) {

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
        double grossImportDemand = 0.0;
        double grossExportSurplus = 0.0;
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
            grossImportDemand += volumes.compactedFillDeficit();
            grossExportSurplus += volumes.compactedFillSurplus();
        }

        EarthworkAllocationMatrix projectMatrix = matrixInputs.isEmpty()
            ? EarthworkAllocationMatrix.EMPTY
            : EarthworkAllocationMatrix.fromZoneReports(matrixInputs, projectMaterialLookup(project));
        EarthworkAllocationMatrix crossSite = matrixInputs.size() >= 2
            ? projectMatrix
            : EarthworkAllocationMatrix.EMPTY;

        ProjectMaterialBalance materialBalance = new ProjectMaterialBalance(
            grossImportDemand,
            grossExportSurplus,
            projectMatrix.internalTransferVolume(),
            projectMatrix.externalImportVolume(),
            projectMatrix.externalExportVolume());

        return new AggregatedBalance(
            totalCut,
            totalFill,
            reusableCut,
            materialBalance,
            Map.copyOf(bySite),
            crossSite,
            bySite.size());
    }

    private static EarthMaterialClassLookup projectMaterialLookup(EarthworkProject project) {
        if (project == null) {
            return EarthMaterialClassLookup.UNKNOWN;
        }
        return id -> {
            EarthworkSite site = project.getSite(id);
            if (site != null) {
                return new EarthMaterialClassLookup.Classes(
                    site.getCutMaterialClass(),
                    site.getFillMaterialClass());
            }
            for (EarthworkSite candidate : project.getSites().values()) {
                if (candidate == null) {
                    continue;
                }
                GradingZone zone = candidate.getZone(id);
                if (zone != null) {
                    return new EarthMaterialClassLookup.Classes(
                        zone.getCutMaterialClass(),
                        zone.getFillMaterialClass());
                }
            }
            return EarthMaterialClassLookup.Classes.DEFAULT;
        };
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
            ProjectMaterialBalance.EMPTY,
            Map.of(),
            EarthworkAllocationMatrix.EMPTY,
            0);
    }
}
