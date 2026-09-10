package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerPoleLayoutUtilsWorldScaleTest {

    private static final ICoordinateService FOUR_BLOCKS_PER_CANVAS_UNIT =
        SnapshotCoordinateService.uniformScale(4.0);

    @Test
    void poleSpacingUsesProjectedWorldDistance() {
        List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));

        List<PowerPoleSite> canvasUnits = PowerPoleLayoutUtils.computePoleSites(
            path, 5.0, 50.0, IdentityCoordinateService.INSTANCE);
        List<PowerPoleSite> worldBlocks = PowerPoleLayoutUtils.computePoleSites(
            path, 5.0, 50.0, FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(3, canvasUnits.size(), "100 canvas units / 50 block spacing at 1:1 projection");
        assertEquals(9, worldBlocks.size(), "400 world blocks / 50 block spacing with 4x projection");
    }

    @Test
    void footprintEstimateUsesProjection() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(100, 0)));
        line.setMaxPoleSpacing(50.0);

        assertEquals(3, line.estimatePoleCount(IdentityCoordinateService.INSTANCE));
        assertEquals(9, line.estimatePoleCount(FOUR_BLOCKS_PER_CANVAS_UNIT));
        assertEquals(400.0, line.computeWorldPathLength(FOUR_BLOCKS_PER_CANVAS_UNIT), 1e-6);
    }

    @Test
    void poleSpacingIsScaleInvariantInWorldBlocks() {
        List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));
        double spacingBlocks = 20.0;

        List<PowerPoleSite> nearView = PowerPoleLayoutUtils.computePoleSites(
            path, 5.0, spacingBlocks, IdentityCoordinateService.INSTANCE);
        List<PowerPoleSite> farView = PowerPoleLayoutUtils.computePoleSites(
            path, 5.0, spacingBlocks, FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(6, nearView.size());
        assertEquals(21, farView.size());
        assertTypicalSpan(nearView, spacingBlocks);
        assertTypicalSpan(farView, spacingBlocks);
    }

    @Test
    void towerSelectionSpanUsesWorldStationingNotCanvasDistance() {
        List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(30, 0));
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(
            path, 5.0, 200.0, FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(2, sites.size());
        double canvasSpan = sites.get(0).getPlanPosition().distance(sites.get(1).getPlanPosition());
        double worldSpan = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(0), sites.get(1));

        assertEquals(30.0, canvasSpan, 1e-6);
        assertEquals(120.0, worldSpan, 1e-6);
    }

    private static void assertTypicalSpan(List<PowerPoleSite> sites, double expectedBlocks) {
        for (int i = 0; i < sites.size() - 1; i++) {
            double span = PowerPoleLayoutUtils.worldSpanBlocks(sites.get(i), sites.get(i + 1));
            assertEquals(expectedBlocks, span, 1.0);
        }
    }
}
