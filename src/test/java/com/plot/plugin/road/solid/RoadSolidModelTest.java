package com.plot.plugin.road.solid;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSolidModelTest {

    @Test
    void addDeduplicatesSameLayerPointAndElevation() {
        RoadSolidModel model = new RoadSolidModel();
        Vec2d point = new Vec2d(3, 7);

        assertTrue(model.add(point, 64, RoadSolidLayer.MARKING));
        assertFalse(model.add(point, 64, RoadSolidLayer.MARKING));
        assertEquals(1, model.count(RoadSolidLayer.MARKING));
    }

    @Test
    void rasterizerMapsPlanPointToBlockPos() {
        BlockPos pos = RoadVoxelRasterizer.toBlockPos(new Vec2d(4.2, 9.8), 65, null);
        assertEquals(4, pos.getX());
        assertEquals(65, pos.getY());
        assertEquals(9, pos.getZ());
    }
}
