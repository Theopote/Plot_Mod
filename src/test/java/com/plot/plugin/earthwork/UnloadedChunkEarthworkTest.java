package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainCell;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.solver.EarthworkElevationVolumeCurve;
import com.plot.plugin.earthwork.solver.EarthworkOptimizationSolver;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.plugin.earthwork.volume.SiteEarthworkReport;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnloadedChunkEarthworkTest {

    @Test
    void unloadedCellsDoNotParticipateInEarthworkOrVolumes() {
        DesignTerrainCell loaded = new DesignTerrainCell(0, 0, new Vec2d(0.5, 0.5), 70, true);
        loaded.setTargetY(64);
        loaded.setZoneId("pad");
        DesignTerrainCell unloaded = new DesignTerrainCell(1, 0, new Vec2d(1.5, 0.5), 64, false);
        unloaded.setTargetY(64);
        unloaded.setZoneId("pad");

        assertTrue(loaded.participatesInEarthwork());
        assertFalse(unloaded.participatesInEarthwork());

        DesignTerrainGrid grid = new DesignTerrainGrid();
        grid.put(0, 0, loaded);
        grid.put(1, 0, unloaded);
        SiteEarthworkReport report = EarthworkOptimizationSolver.collectZoneVolumes(grid);
        assertEquals(6L, report.totals().geometricCutVolume());
        assertEquals(0L, report.totals().geometricFillVolume());
    }

    @Test
    void composerIgnoresUnloadedColumnsForBalance() {
        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5.5, 5.5), 5, 5, 70, "minecraft:grass_block", true),
            new TerrainSnapshot.Column(new Vec2d(6.5, 5.5), 6, 5, 64, "", false),
            new TerrainSnapshot.Column(new Vec2d(5.5, 6.5), 5, 6, 66, "minecraft:grass_block", true),
            new TerrainSnapshot.Column(new Vec2d(6.5, 6.5), 6, 6, 68, "minecraft:grass_block", true)
        ));
        EarthworkSite site = new EarthworkSite("site");
        site.setSiteBoundary(List.of(
            new Vec2d(5, 5), new Vec2d(7, 5), new Vec2d(7, 7), new Vec2d(5, 7)));
        site.getCompositionPolicy().setOptimizationMode(
            com.plot.plugin.earthwork.model.OptimizationMode.UNIFORM_VERTICAL_SHIFT);
        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(5, 5), new Vec2d(7, 5), new Vec2d(7, 7), new Vec2d(5, 7)));
        pad.getRegion().setAutoBalance(true);
        pad.setAutoAdjustElevation(true, 16);
        site.addZone(pad);

        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, terrain, IdentityCoordinateService.INSTANCE);
        DesignTerrainCell unloaded = composed.grid().get(6, 5);
        assertFalse(unloaded.chunkLoaded());
        assertFalse(unloaded.participatesInEarthwork());
        assertFalse(composed.slopeCoupledCurve().isEmpty());
    }

    @Test
    void slopeCoupledCurveAttachedWhenProvided() {
        DesignTerrainGrid grid = new DesignTerrainGrid();
        DesignTerrainCell cell = new DesignTerrainCell(0, 0, new Vec2d(0.5, 0.5), 70, true);
        cell.setTargetY(64);
        cell.setZoneId("pad");
        grid.put(0, 0, cell);
        grid.finalizeStats();

        EarthworkElevationVolumeCurve curve = EarthworkElevationVolumeCurve.fromSlopeCoupledSamples(
            List.of(
                new EarthworkElevationVolumeCurve.Sample(62, 8, 0),
                new EarthworkElevationVolumeCurve.Sample(64, 6, 0),
                new EarthworkElevationVolumeCurve.Sample(66, 4, 2)
            ),
            64,
            grid);
        assertFalse(curve.isEmpty());
        assertEquals(3, curve.samples().size());
    }
}
