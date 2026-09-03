package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy;
import com.plot.plugin.earthwork.validation.EarthworkValidationReport;
import com.plot.plugin.earthwork.validation.EarthworkValidator;
import com.plot.plugin.building.model.BuildingFootprint;
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

    @Test
    void excavationPitMissingBuildingReferenceIsError() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone pit = new GradingZone("pit", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        pit.setBuildingFootprintRef("");
        site.addZone(pit);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, pit.getRegion());

        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item -> "plugin.earthwork.validation.excavation_pit_no_reference".equals(item.messageKey())));
    }

    @Test
    void excavationPitUnresolvedBuildingReferenceIsError() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone pit = new GradingZone("pit", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        pit.setBuildingFootprintRef("missing-b1");
        site.addZone(pit);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(
            project, pit.getRegion(), BuildingFootprintLookup.NONE);

        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.excavation_pit_unresolved_reference".equals(item.messageKey())));
    }

    @Test
    void excavationPitResolvedBuildingReferencePasses() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone pit = new GradingZone("pit", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        pit.setBuildingFootprintRef("b1");
        site.addZone(pit);
        site.recomputeSiteBoundaryFromZones();

        BuildingFootprint footprint = new BuildingFootprint("b1", pit.getOuterPoints(), false);
        BuildingFootprintLookup lookup = id -> "b1".equals(id) ? footprint : null;

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(
            project, pit.getRegion(), lookup);

        assertFalse(report.errors().stream()
            .anyMatch(item -> item.messageKey().contains("excavation_pit")));
    }

    @Test
    void warnsWhenSiteBalanceCanAdjustUnlockedBuildingPad() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        CompositionPolicy policy = site.getCompositionPolicy();
        policy.setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        policy.setBalanceMethod(CompositionPolicy.BALANCE_METHOD_UNIFORM);

        GradingZone pad = new GradingZone("pad", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.getDesignSurface().setElevation(70);
        pad.setVerticalAdjustmentPolicy(VerticalAdjustmentPolicy.adjustable(4, 1.0f));
        site.addZone(pad);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, pad.getRegion());

        assertFalse(report.blocksPreview());
        assertTrue(report.warnings().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.site_balance_may_adjust_building_pad".equals(item.messageKey())));
    }

    @Test
    void lockedBuildingPadDoesNotWarnUnderSiteBalance() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        CompositionPolicy policy = site.getCompositionPolicy();
        policy.setBalanceScope(CompositionPolicy.BALANCE_SCOPE_SITE_WIDE);
        policy.setBalanceMethod(CompositionPolicy.BALANCE_METHOD_EARTHWORK_OPTIMIZATION);

        GradingZone pad = new GradingZone("pad", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.getDesignSurface().setElevation(70);
        // default BUILDING_PAD policy is LOCKED
        site.addZone(pad);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, pad.getRegion());

        assertFalse(report.warnings().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.site_balance_may_adjust_building_pad".equals(item.messageKey())));
    }
}
