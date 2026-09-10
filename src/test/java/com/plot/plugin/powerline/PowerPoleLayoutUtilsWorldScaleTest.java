package com.plot.plugin.powerline;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerPoleLayoutUtilsWorldScaleTest {

    private static final ICoordinateService FOUR_BLOCKS_PER_CANVAS_UNIT = new ICoordinateService() {
        @Override
        public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
            return new Vec2d(canvasPos.x * 4.0, canvasPos.y * 4.0);
        }

        @Override
        public WorldViewBounds getMinecraftWorldViewBounds() {
            return new WorldViewBounds(-1.0e9, 1.0e9, -1.0e9, 1.0e9);
        }
    };

    @Test
    void poleSpacingUsesProjectedWorldDistance() {
        List<Vec2d> path = List.of(new Vec2d(0, 0), new Vec2d(100, 0));

        List<PowerPoleSite> canvasUnits = PowerPoleLayoutUtils.computePoleSites(path, 5.0, 50.0);
        List<PowerPoleSite> worldBlocks = PowerPoleLayoutUtils.computePoleSites(
            path, 5.0, 50.0, FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(3, canvasUnits.size(), "100 canvas units / 50 block spacing without projection");
        assertEquals(9, worldBlocks.size(), "400 world blocks / 50 block spacing with 4x projection");
    }

    @Test
    void footprintEstimateUsesProjection() {
        PowerLineFootprint line = new PowerLineFootprint(List.of(new Vec2d(0, 0), new Vec2d(100, 0)));
        line.setMaxPoleSpacing(50.0);

        assertEquals(3, line.estimatePoleCount());
        assertEquals(9, line.estimatePoleCount(FOUR_BLOCKS_PER_CANVAS_UNIT));
        assertEquals(400.0, line.computeWorldPathLength(FOUR_BLOCKS_PER_CANVAS_UNIT), 1e-6);
    }
}
