package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EarthworkTerrainTask;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.plot.plugin.earthwork.EarthworkTestFixtures.DIRT;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.generateLegacy;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.levelPadRegion;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.rectangleTerrain;
import static com.plot.plugin.earthwork.EarthworkTestFixtures.solidColumnSampler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 六种玩家地形任务的合成与（部分）落地回归：保证任务入口与 {@link DesignTerrainComposer} 一致。
 */
class EarthworkTerrainTaskGenerationTest {

    private static final List<Vec2d> PAD = List.of(
        new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));

    @Test
    void flattenTaskLevelsSiteToManualTarget() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("pad", PAD);
        EarthworkTerrainTask.FLATTEN.applyTo(zone);
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(68);
        site.addZone(zone);

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        DesignTerrainGrid grid = compose(site, terrain);

        assertEquals(68, grid.get(5, 5).targetY());
        assertEquals(68, grid.get(0, 0).targetY());
    }

    @Test
    void flattenTaskPipelinePlacesFillOnLowTerrain() {
        TerrainSnapshot terrain = rectangleTerrain(0, 3, 0, 3, 64);
        GradingRegion region = levelPadRegion(0, 3, 0, 3, 65, false);
        EarthworkGenerationResult result = generateLegacy(
            region, terrain, solidColumnSampler(terrain, EarthworkTestFixtures.STONE));
        assertEquals(16L, result.volumeReport.geometricFillVolume());
        assertTrue(result.placementRecords.size() > 0);
    }

    @Test
    void slopeTaskSinglePlaneVariesAlongFallDirection() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("slope", PAD);
        EarthworkTerrainTask.SLOPE.applyTo(zone);
        zone.getRegion().setSurfaceMode(GradingSurfaceMode.SINGLE_SLOPE_PLANE);
        zone.getRegion().setSlopeDirectionDegrees(0.0);
        zone.getRegion().setSlopePitchRatio(4);
        zone.getRegion().setSlopeAnchorCanvas(new Vec2d(0, 0));
        zone.getRegion().setSlopeAnchorElevation(64);
        zone.getRegion().setAutoBalance(false);
        site.addZone(zone);

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        DesignTerrainGrid grid = compose(site, terrain);

        int atOrigin = grid.get(0, 0).targetY();
        int alongX = grid.get(4, 0).targetY();
        int offAxis = grid.get(0, 8).targetY();
        assertEquals(64, atOrigin);
        assertEquals(65, alongX);
        assertEquals(64, offAxis);
    }

    @Test
    void followTerrainMatchExistingKeepsGroundPlusOffset() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("fit", PAD);
        EarthworkTerrainTask.FOLLOW_TERRAIN.applyTo(zone);
        EarthworkTerrainTask.applySurfaceMode(zone, GradingSurfaceMode.MATCH_EXISTING);
        zone.getDesignSurface().setVerticalOffset(2);
        site.addZone(zone);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(2.5, 2.5), 2, 2, 61),
            new TerrainSnapshot.Column(new Vec2d(8.5, 8.5), 8, 8, 67)));
        DesignTerrainGrid grid = compose(site, terrain);

        assertEquals(63, grid.get(2, 2).targetY());
        assertEquals(69, grid.get(8, 8).targetY());
    }

    @Test
    void followTerrainBestFitPlaneTrendsWithSamples() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("fit", PAD);
        EarthworkTerrainTask.FOLLOW_TERRAIN.applyTo(zone);
        EarthworkTerrainTask.applySurfaceMode(zone, GradingSurfaceMode.BEST_FIT_PLANE);
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setFitSlopeBalanceCutFill(false);
        site.addZone(zone);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(1.5, 5.5), 1, 5, 60),
            new TerrainSnapshot.Column(new Vec2d(5.5, 5.5), 5, 5, 64),
            new TerrainSnapshot.Column(new Vec2d(9.5, 5.5), 9, 5, 68)));
        DesignTerrainGrid grid = compose(site, terrain);

        int low = grid.get(1, 5).targetY();
        int mid = grid.get(5, 5).targetY();
        int high = grid.get(9, 5).targetY();
        assertTrue(low < mid && mid < high, () -> "low=" + low + " mid=" + mid + " high=" + high);
        assertNotEquals(low, high);
    }

    @Test
    void buildingPadTaskHoldsConstantElevation() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("building", PAD);
        EarthworkTerrainTask.BUILDING_PAD.applyTo(zone);
        zone.getDesignSurface().setElevation(74);
        site.addZone(zone);

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        DesignTerrainGrid grid = compose(site, terrain);
        assertEquals(74, grid.get(5, 5).targetY());
    }

    @Test
    void excavateTaskLowersCenterRelativeToRim() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("pit", PAD);
        EarthworkTerrainTask.EXCAVATE.applyTo(zone);
        zone.getDesignSurface().setBottomElevation(58);
        zone.getDesignSurface().setWorkingMarginBlocks(2);
        zone.getDesignSurface().setSlopePitchRatio(1);
        zone.syncDesignSurfaceToRegion();
        site.addZone(zone);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5.5, 5.5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(0.5, 0.5), 0, 0, 70)));
        DesignTerrainGrid grid = compose(site, terrain);

        int center = grid.get(5, 5).targetY();
        int corner = grid.get(0, 0).targetY();
        assertEquals(58, center);
        assertTrue(corner > center, () -> "corner=" + corner + " center=" + center);
    }

    @Test
    void roadConnectionTaskSamplesLinkedRoadElevation() {
        EarthworkSite site = siteWithBoundary();
        GradingZone zone = new GradingZone("road", PAD);
        EarthworkTerrainTask.ROAD_CONNECTION.applyTo(zone);
        zone.setRoadEdgeRef("edge-1");
        site.addZone(zone);

        TerrainSnapshot terrain = rectangleTerrain(0, 9, 0, 9, 64);
        DesignTerrainGrid grid = DesignTerrainComposer.compose(
            site,
            terrain,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BuildingFootprintLookup.NONE,
            (RoadSurfaceLookup) (edgeId, point) -> "edge-1".equals(edgeId) ? 71 : null).grid();

        assertEquals(71, grid.get(5, 5).targetY());
    }

    private static EarthworkSite siteWithBoundary() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(PAD);
        return site;
    }

    private static DesignTerrainGrid compose(EarthworkSite site, TerrainSnapshot terrain) {
        return DesignTerrainComposer.compose(
            site, terrain, com.plot.test.world.IdentityCoordinateService.INSTANCE).grid();
    }
}
