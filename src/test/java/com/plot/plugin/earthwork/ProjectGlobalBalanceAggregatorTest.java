package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.solver.EarthworkAllocationMatrix;
import com.plot.plugin.earthwork.solver.ProjectGlobalBalanceAggregator;
import com.plot.plugin.earthwork.volume.EarthworkProjectReport;
import com.plot.plugin.earthwork.volume.EarthworkVolumeReport;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.core.material.MaterialConversionModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectGlobalBalanceAggregatorTest {

    @Test
    void aggregatesMultipleSitesWithCrossSiteAllocation() {
        EarthworkProject project = new EarthworkProject();

        EarthworkSite cutSite = new EarthworkSite("site-cut");
        cutSite.setName("Cut Site");
        cutSite.setLastReport(volume(10_000L, 0L));

        EarthworkSite fillSite = new EarthworkSite("site-fill");
        fillSite.setName("Fill Site");
        fillSite.setLastReport(volume(0L, 7_000L));

        project.addSite(cutSite);
        project.addSite(fillSite);

        ProjectGlobalBalanceAggregator.AggregatedBalance balance =
            ProjectGlobalBalanceAggregator.aggregate(project);

        assertEquals(10_000L, balance.totalCut());
        assertEquals(7_000L, balance.totalFill());
        assertEquals(2, balance.sitesWithVolume());
        assertEquals(7_000L, transfer(balance.crossSiteAllocationMatrix(), "site-cut", "site-fill"));
        assertEquals(3_000L, transfer(balance.crossSiteAllocationMatrix(), "site-cut", EarthworkAllocationMatrix.EXPORT));
    }

    @Test
    void freshPreviewOverridesCachedSiteTotals() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = new EarthworkSite("site-a");
        site.setName("Site A");
        site.setLastReport(volume(100L, 50L));
        project.addSite(site);

        SiteEarthworkReport fresh = new SiteEarthworkReport(
            volume(500L, 200L),
            Map.of());

        ProjectGlobalBalanceAggregator.AggregatedBalance balance =
            ProjectGlobalBalanceAggregator.aggregate(project, "site-a", fresh);

        assertEquals(500L, balance.totalCut());
        assertEquals(200L, balance.totalFill());
        assertTrue(balance.bySite().get("site-a").previewFresh());
    }

    @Test
    void projectReportUsesGlobalTotalsForMultiSiteProject() {
        EarthworkProject project = new EarthworkProject();

        EarthworkSite cutSite = new EarthworkSite("site-cut");
        cutSite.setName("Cut Site");
        cutSite.setLastReport(volume(4_000L, 0L));

        EarthworkSite fillSite = new EarthworkSite("site-fill");
        fillSite.setName("Fill Site");
        fillSite.setLastReport(volume(0L, 2_500L));

        project.addSite(cutSite);
        project.addSite(fillSite);
        project.setActiveSiteId("site-cut");

        SiteEarthworkReport activeReport = new SiteEarthworkReport(
            volume(4_000L, 0L),
            Map.of("zone-a", volume(4_000L, 0L)));

        EarthworkProjectReport report = EarthworkProjectReport.Builder.buildFromProject(
            project, cutSite, activeReport);

        assertEquals(4_000L, report.totalCut());
        assertEquals(2_500L, report.totalFill());
        assertTrue(report.hasCrossSiteBreakdown());
        assertEquals(2, report.sitesWithVolume());
        assertEquals(2_500L, transfer(report.crossSiteAllocationMatrix(), "site-cut", "site-fill"));
    }

    private static long transfer(EarthworkAllocationMatrix matrix, String source, String destination) {
        return matrix.transfers().stream()
            .filter(t -> t.sourceZoneId().equals(source) && t.destinationZoneId().equals(destination))
            .mapToLong(EarthworkAllocationMatrix.Transfer::volume)
            .sum();
    }

    private static EarthworkVolumeReport volume(long cut, long fill) {
        return EarthworkVolumeReport.fromMetrics(cut, fill, MaterialConversionModel.DEFAULT, 0L, 0L);
    }
}
