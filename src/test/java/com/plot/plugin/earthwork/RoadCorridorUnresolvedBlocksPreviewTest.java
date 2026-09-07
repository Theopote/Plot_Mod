package com.plot.plugin.earthwork;

import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.RoadCorridorSurfaceResolver;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.model.EarthworkProject;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelineContext;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.validation.EarthworkValidationReport;
import com.plot.plugin.earthwork.validation.EarthworkValidator;
import org.junit.jupiter.api.Test;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleOutline;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROAD_CORRIDOR 在道路设计面不可解析时必须 fail-closed，禁止回退场地平均标高。
 */
class RoadCorridorUnresolvedBlocksPreviewTest {

    @Test
    void validatorBlocksPreviewWhenRoadDesignSurfaceUnresolved() {
        EarthworkProject project = roadCorridorProject();
        GradingZone corridor = project.getActiveSite().getGradingZones().values().iterator().next();

        EarthworkValidationReport report = EarthworkValidator.analyzePrePreview(
            project,
            corridor.getRegion(),
            BuildingFootprintLookup.NONE,
            RoadSurfaceLookup.NONE);

        assertTrue(report.blocksPreview());
        assertTrue(report.errors().stream()
            .anyMatch(item ->
                "plugin.earthwork.validation.road_corridor_unresolved_design_surface"
                    .equals(item.messageKey())));
    }

    @Test
    void sitePipelineFailsClosedInsteadOfAverageElevationFallback() {
        EarthworkSite site = roadCorridorProject().getActiveSite();
        GradingZone corridor = site.getGradingZones().values().iterator().next();

        assertThrows(
            RoadCorridorSurfaceResolver.UnresolvedRoadDesignSurfaceException.class,
            () -> EarthworkPipelines.create(null).site().execute(
                EarthworkPipelineContext.of(
                    site,
                    null,
                    rectangleTerrain(0, 5, 0, 5, 65),
                    corridor.getRegion(),
                    BuildingFootprintLookup.NONE,
                    RoadSurfaceLookup.NONE)));
    }

    private static EarthworkProject roadCorridorProject() {
        EarthworkProject project = new EarthworkProject();
        EarthworkSite site = project.getActiveSite();
        GradingZone corridor = new GradingZone(
            "corridor",
            RegionGeometry.of(rectangleOutline(0, 5, 0, 5)));
        corridor.setType(GradingZoneType.ROAD_CORRIDOR);
        corridor.setRoadEdgeRef("edge-main");
        site.addZone(corridor);
        site.recomputeSiteBoundaryFromZones();
        return project;
    }
}
