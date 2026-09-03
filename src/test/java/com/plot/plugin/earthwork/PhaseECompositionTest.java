package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.BuildingFootprintLookup;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.RoadCorridorBaker;
import com.plot.plugin.earthwork.design.RoadSurfaceLookup;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.BakedElevationGrid;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseECompositionTest {

    @Test
    void roadCorridorBakeStoresGridAndSwitchesToTerrainFit() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone corridor = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        corridor.setType(GradingZoneType.ROAD_CORRIDOR);
        corridor.setRoadEdgeRef("edge-main");
        site.addZone(corridor);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65),
            new TerrainSnapshot.Column(new Vec2d(2, 2), 2, 2, 62)
        ));

        RoadSurfaceLookup lookup = (edgeId, planPoint) -> 70;
        int bakedCount = RoadCorridorBaker.bake(corridor, terrain, lookup);

        assertEquals(2, bakedCount);
        assertEquals(GradingZoneType.TERRAIN_FIT, corridor.getType());
        assertEquals(DesignSurfaceElevationSource.BAKED_ROAD, corridor.getDesignSurface().getElevationSource());
        assertEquals(70, corridor.getDesignSurface().getBakedElevationGrid().get(5, 5));
        assertEquals(
            com.plot.plugin.earthwork.model.VerticalAdjustmentPolicy.Mode.LOCKED,
            corridor.getVerticalAdjustmentPolicy().getMode());
        assertFalse(corridor.isAutoAdjustElevation());
    }

    @Test
    void bakedGridUsedByComposerInsteadOfLiveRoadLookup() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));

        GradingZone zone = new GradingZone(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 10),
            new Vec2d(0, 10)
        ));
        zone.setType(GradingZoneType.TERRAIN_FIT);
        BakedElevationGrid grid = new BakedElevationGrid();
        grid.put(5, 5, 82);
        zone.getDesignSurface().setBakedElevationGrid(grid);
        zone.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BAKED_ROAD);
        zone.setAutoAdjustElevation(false);
        site.addZone(zone);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65)
        ));

        RoadSurfaceLookup liveLookup = (edgeId, planPoint) -> 40;
        DesignTerrainCell cell = DesignTerrainComposer.compose(
            site, terrain, null, BuildingFootprintLookup.NONE, liveLookup).grid().get(5, 5);
        assertEquals(82, cell.targetY(), () -> "targetY=" + cell.targetY());
    }

    @Test
    void bakedGridFallsBackToNearestSample() {
        BakedElevationGrid grid = new BakedElevationGrid();
        grid.put(5, 5, 70);
        assertEquals(70, grid.evaluateAt(5, 5, 64));
        assertEquals(70, grid.evaluateAt(6, 5, 64));
        grid.put(6, 5, 72);
        assertEquals(72, grid.evaluateAt(6, 5, 64));
    }
}
