package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.design.ExcavationPitSurfaceEvaluator;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.DesignSurfaceElevationSource;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.GradingZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseCDesignSurfaceTest {

    @Test
    void buildingPadUsesManualElevation() {
        EarthworkSite site = createSite();
        GradingZone pad = createZone("pad", List.of(
            new Vec2d(2, 2), new Vec2d(8, 2), new Vec2d(8, 8), new Vec2d(2, 8)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.MANUAL);
        pad.getDesignSurface().setElevation(72);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 65)));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(72, grid.get(5, 5).targetY());
    }

    @Test
    void buildingPadResolvesBuildingBaseElevation() {
        EarthworkSite site = createSite();
        GradingZone pad = createZone("pad", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pad.setType(GradingZoneType.BUILDING_PAD);
        pad.setBuildingFootprintRef("b1");
        pad.getDesignSurface().setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 66),
            new TerrainSnapshot.Column(new Vec2d(6, 5), 6, 5, 68)));

        BuildingFootprint footprint = new BuildingFootprint("b1", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)), false);
        footprint.setManualBaseElevation(71);

        DesignTerrainGrid grid = DesignTerrainComposer.compose(
            site, terrain, null, id -> footprint).grid();
        assertEquals(71, grid.get(5, 5).targetY());
    }

    @Test
    void excavationPitResolvesBottomFromBuildingBaseMinusDepth() {
        EarthworkSite site = createSite();
        GradingZone pit = createZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        pit.setBuildingFootprintRef("b1");
        DesignSurface surface = pit.getDesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.BUILDING_BASE_ELEVATION);
        surface.setBasementFloorDepth(5);
        surface.setWorkingMarginBlocks(0);
        surface.setSlopePitchRatio(1);
        site.addZone(pit);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70)));

        BuildingFootprint footprint = new BuildingFootprint("b1", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)), false);
        footprint.setManualBaseElevation(70);

        DesignTerrainGrid grid = DesignTerrainComposer.compose(
            site, terrain, null, id -> footprint).grid();
        assertEquals(65, grid.get(5, 5).targetY());
    }

    @Test
    void pitBottomResolverUsesManualWhenNotLinkedToBuilding() {
        DesignSurface surface = new DesignSurface();
        surface.setElevationSource(DesignSurfaceElevationSource.MANUAL);
        surface.setBottomElevation(48);

        int bottom = com.plot.plugin.earthwork.design.BuildingFootprintResolver.resolvePitBottomElevation(
            null, surface, TerrainSnapshot.empty(), id -> null, 64);
        assertEquals(48, bottom);
    }

    @Test
    void excavationPitRaisesTargetNearWalls() {
        EarthworkSite site = createSite();
        GradingZone pit = createZone("pit", List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        pit.setType(GradingZoneType.EXCAVATION_PIT);
        DesignSurface surface = pit.getDesignSurface();
        surface.setBottomElevation(50);
        surface.setWorkingMarginBlocks(2);
        surface.setSlopePitchRatio(1);
        site.addZone(pit);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(0.5, 0.5), 0, 0, 70)));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(50, grid.get(5, 5).targetY());
        assertTrue(grid.get(0, 0).targetY() > 50);
    }

    @Test
    void pitEvaluatorDistanceToEdge() {
        List<Vec2d> square = List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10));
        int center = ExcavationPitSurfaceEvaluator.evaluateTargetY(
            new Vec2d(5, 5), square, 50, 2, 1);
        int corner = ExcavationPitSurfaceEvaluator.evaluateTargetY(
            new Vec2d(0.5, 0.5), square, 50, 2, 1);
        assertEquals(50, center);
        assertTrue(corner > center);
    }

    private static EarthworkSite createSite() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(10, 0), new Vec2d(10, 10), new Vec2d(0, 10)));
        return site;
    }

    private static GradingZone createZone(String id, List<Vec2d> points) {
        return new GradingZone(id, points);
    }
}
