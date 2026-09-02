package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.validation.EarthworkValidationReport;
import com.plot.plugin.earthwork.validation.EarthworkValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkValidatorTest {

    @Test
    void rejectsRegionWithInsufficientOutline() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(List.of(new Vec2d(0, 0), new Vec2d(1, 0)));
        project.addRegion(region);

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, region);

        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item -> "plugin.earthwork.validation.region_outline_insufficient".equals(item.messageKey())));
    }

    @Test
    void rejectsManualElevationWhenAutoBalanceDisabled() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(rectangleOutline(0, 3, 0, 3));
        region.setAutoBalance(false);
        region.setManualTargetElevation(null);
        project.addRegion(region);

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, region);

        assertTrue(report.blocksPreview());
    }

    @Test
    void warnsOnCollinearThreePointPlane() {
        EarthworkProject project = new EarthworkProject();
        GradingRegion region = new GradingRegion(rectangleOutline(0, 3, 0, 3));
        region.setSurfaceMode(GradingSurfaceMode.THREE_POINT_PLANE);
        region.setThreePointControl(0, new Vec2d(0, 0), 64);
        region.setThreePointControl(1, new Vec2d(1, 0), 65);
        region.setThreePointControl(2, new Vec2d(2, 0), 66);
        project.addRegion(region);

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, region);

        assertFalse(report.blocksPreview());
        assertTrue(report.warnings().stream()
            .anyMatch(item -> "plugin.earthwork.validation.three_point_collinear".equals(item.messageKey())));
    }

    @Test
    void rejectsSiteWithoutEnabledZones() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        site.recomputeSiteBoundaryFromZones();
        GradingRegion region = new GradingRegion(rectangleOutline(0, 3, 0, 3));
        project.addRegion(region);
        site.removeZone(region.getId());

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, region);

        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item -> "plugin.earthwork.validation.no_enabled_zones".equals(item.messageKey())));
    }

    @Test
    void warnsWhenZonesOverlap() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone zoneA = new GradingZone("a", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        GradingZone zoneB = new GradingZone("b", RegionGeometry.of(rectangleOutline(3, 8, 3, 8)));
        site.addZone(zoneA);
        site.addZone(zoneB);
        site.recomputeSiteBoundaryFromZones();
        GradingRegion preview = zoneA.getRegion();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, preview);

        assertFalse(report.blocksPreview());
        assertTrue(report.warnings().stream()
            .anyMatch(item -> "plugin.earthwork.validation.zone_overlap_detected".equals(item.messageKey())));
    }
}
