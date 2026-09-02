package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.design.DesignTerrainComposer;
import com.plot.plugin.earthwork.grading.DesignTerrainGrid;
import com.plot.plugin.earthwork.grading.SlopeDaylightSolver;
import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.model.DesignSurface;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.EdgeTreatment;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.model.ZoneEdgeSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeDaylightSolverTest {

    @Test
    void findsCutDaylightOnFlatGround() {
        int daylight = SlopeDaylightSolver.findDaylightDistance(
            64,
            SlopeDaylightSolver.SlopeMode.CUT,
            1.0,
            0,
            offset -> 70,
            16);
        assertEquals(6, daylight);
    }

    @Test
    void findsFillDaylightOnSlopedGround() {
        IntUnaryOperator risingGround = offset -> 64 + offset;
        int daylight = SlopeDaylightSolver.findDaylightDistance(
            70,
            SlopeDaylightSolver.SlopeMode.FILL,
            2.0,
            0,
            risingGround,
            32);
        assertEquals(4, daylight);
    }

    @Test
    void flatGroundAtToeReturnsZeroDaylightForFill() {
        int daylight = SlopeDaylightSolver.findDaylightDistance(
            70,
            SlopeDaylightSolver.SlopeMode.FILL,
            2.0,
            0,
            offset -> 70,
            32);
        assertEquals(0, daylight);
    }

    @Test
    void beyondDaylightKeepsExistingGround() {
        IntUnaryOperator dippingGround = offset -> 70 - offset;
        int targetOnSlope = SlopeDaylightSolver.resolveExteriorTargetY(
            64,
            68,
            2.0,
            SlopeDaylightSolver.SlopeMode.CUT,
            1.0,
            0,
            dippingGround,
            8);
        int targetBeyondDaylight = SlopeDaylightSolver.resolveExteriorTargetY(
            64,
            65,
            5.0,
            SlopeDaylightSolver.SlopeMode.CUT,
            1.0,
            0,
            dippingGround,
            8);

        assertEquals(66, targetOnSlope);
        assertEquals(65, targetBeyondDaylight);
    }

    @Test
    void benchProfileDelaysDaylightOnFlatGround() {
        int continuous = SlopeDaylightSolver.findDaylightDistance(
            64,
            SlopeDaylightSolver.SlopeMode.CUT,
            1.0,
            0,
            offset -> 70,
            16);
        int benched = SlopeDaylightSolver.findDaylightDistance(
            64,
            SlopeDaylightSolver.SlopeMode.CUT,
            1.0,
            2,
            offset -> 70,
            16);
        assertTrue(benched >= continuous);
    }

    @Test
    void composerStopsSlopeBeyondEarlyDaylight() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(List.of(
            new Vec2d(0, 0), new Vec2d(12, 0), new Vec2d(12, 12), new Vec2d(0, 12)));

        GradingZone pad = new GradingZone("pad", List.of(
            new Vec2d(4, 4), new Vec2d(8, 4), new Vec2d(8, 8), new Vec2d(4, 8)));
        pad.getDesignSurface().setManualTargetElevation(64);
        pad.getDesignSurface().setAutoBalance(false);
        pad.syncDesignSurfaceToRegion();
        ZoneEdgeSettings edge = pad.getEdgeSettings();
        edge.setDefaultTreatment(EdgeTreatment.CUT_FILL_SLOPE);
        edge.setCutSlopePitchRatio(1);
        edge.setMaximumReachBlocks(8);
        site.addZone(pad);

        TerrainSnapshot terrain = TerrainSnapshot.forColumns(List.of(
            new TerrainSnapshot.Column(new Vec2d(5, 5), 5, 5, 70),
            new TerrainSnapshot.Column(new Vec2d(5, 1.5), 5, 1, 69),
            new TerrainSnapshot.Column(new Vec2d(5, 0.5), 5, 0, 68),
            new TerrainSnapshot.Column(new Vec2d(5, -0.5), 5, -1, 65)));

        DesignTerrainGrid grid = DesignTerrainComposer.compose(site, terrain, null).grid();
        assertEquals(64, grid.get(5, 5).targetY());
        assertTrue(grid.get(5, 1).targetY() > 64);
        assertEquals(65, grid.get(5, -1).targetY());
    }
}
