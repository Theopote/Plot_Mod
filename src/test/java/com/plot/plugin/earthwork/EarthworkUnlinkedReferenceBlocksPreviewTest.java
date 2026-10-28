package com.plot.plugin.earthwork;

import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.BuildingFootprintResolver;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.RoadCorridorSurfaceResolver;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkTerrainTask;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.validation.EarthworkValidationReport;
import com.plot.plugin.earthwork.validation.EarthworkValidator;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 道路衔接 / 建筑跟随 未关联或引用失效时：校验阻断预览，合成 fail-closed。
 */
class EarthworkUnlinkedReferenceBlocksPreviewTest {

    @Test
    void roadConnectionTaskWithoutEdgeBlocksValidatorAndCompose() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone corridor = new GradingZone(
            "corridor", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        EarthworkTerrainTask.ROAD_CONNECTION.applyTo(corridor);
        assertTrue(corridor.getRoadEdgeRef().isBlank());
        site.addZone(corridor);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(
            project, corridor.getRegion(), BuildingFootprintLookup.NONE, RoadSurfaceLookup.NONE);
        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.road_corridor_no_reference".equals(item.messageKey())));

        assertThrows(
            RoadCorridorSurfaceResolver.UnresolvedRoadDesignSurfaceException.class,
            () -> DesignTerrainComposer.compose(
                site,
                rectangleTerrain(0, 5, 0, 5, 65),
                com.plot.test.world.IdentityCoordinateService.INSTANCE,
                BuildingFootprintLookup.NONE,
                RoadSurfaceLookup.NONE));
    }

    @Test
    void buildingPadLinkedWithoutFootprintBlocksValidatorAndCompose() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone pad = new GradingZone(
            "pad", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        EarthworkTerrainTask.BUILDING_PAD.applyTo(pad);
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        site.addZone(pad);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(
            project, pad.getRegion(), BuildingFootprintLookup.NONE);
        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.building_pad_linked_no_reference".equals(item.messageKey())));

        assertThrows(
            BuildingFootprintResolver.UnresolvedBuildingReferenceException.class,
            () -> DesignTerrainComposer.compose(
                site,
                rectangleTerrain(0, 5, 0, 5, 65),
                com.plot.test.world.IdentityCoordinateService.INSTANCE,
                BuildingFootprintLookup.NONE));
    }

    @Test
    void buildingPadUnresolvedFootprintBlocksValidatorAndCompose() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone pad = new GradingZone(
            "pad", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.setBuildingFootprintRef("missing-building");
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        site.addZone(pad);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(
            project, pad.getRegion(), BuildingFootprintLookup.NONE);
        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.building_pad_unresolved_reference".equals(item.messageKey())));

        assertThrows(
            BuildingFootprintResolver.UnresolvedBuildingReferenceException.class,
            () -> DesignTerrainComposer.compose(
                site,
                rectangleTerrain(0, 5, 0, 5, 65),
                com.plot.test.world.IdentityCoordinateService.INSTANCE,
                BuildingFootprintLookup.NONE));
    }

    @Test
    void manualBuildingPadWithoutElevationOnlyWarns() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone pad = new GradingZone(
            "pad", RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        EarthworkTerrainTask.BUILDING_PAD.applyTo(pad);
        site.addZone(pad);
        site.recomputeSiteBoundaryFromZones();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(project, pad.getRegion());
        assertTrue(!report.blocksPreview());
        assertTrue(report.warnings().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.building_pad_no_reference".equals(item.messageKey())));
    }
}
