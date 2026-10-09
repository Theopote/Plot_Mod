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
    void addDeduplicatesRoundedGridCellInsteadOfExactFloat() {
        RoadSolidModel model = new RoadSolidModel();

        assertTrue(model.add(new Vec2d(0.1, 0.0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        assertFalse(model.add(new Vec2d(0.4, 0.0), 64, RoadSolidLayer.ROAD, "minecraft:dirt"));
        assertEquals(1, model.count(RoadSolidLayer.ROAD));
        assertTrue(model.add(new Vec2d(0.4, 0.0), 64, RoadSolidLayer.TUNNEL, "minecraft:air"));
        assertEquals(1, model.count(RoadSolidLayer.TUNNEL));
    }

    @Test
    void rasterizerMapsPlanPointToBlockPos() {
        BlockPos pos = RoadVoxelRasterizer.toBlockPos(new Vec2d(4.2, 9.8), 65, null);
        assertEquals(4, pos.getX());
        assertEquals(65, pos.getY());
        assertEquals(10, pos.getZ());
    }

    @Test
    void flushEdgeSolidsPopulatesResultBucketsAndPlacementRecords() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadSolidModel solids = new RoadSolidModel();
        solids.add(new Vec2d(1, 2), 64, RoadSolidLayer.ROAD, "minecraft:stone");
        solids.add(new Vec2d(3, 2), 64, RoadSolidLayer.SIDEWALK, "minecraft:oak_planks");
        solids.add(new Vec2d(2, 2), 64, RoadSolidLayer.MARKING, "minecraft:white_concrete");

        RoadVoxelRasterizer.flushEdgeSolids(result, solids, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());

        assertEquals(2, result.roadBlocks.size());
        assertEquals(1, result.sidewalkBlocks.size());
        assertEquals(3, result.placementRecords.size());
        assertEquals(0, result.droppedSolidCount);
    }

    @Test
    void droppedCountIsTrackedWhenAtCapacityWithoutOverflowHandler() {
        RoadSolidModel solids = new RoadSolidModel(4);
        for (int i = 0; i < 4; i++) {
            assertTrue(solids.add(new Vec2d(i, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        }
        assertTrue(solids.isAtCapacity());
        assertFalse(solids.add(new Vec2d(999, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        assertFalse(solids.add(new Vec2d(998, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        assertEquals(2, solids.getDroppedDueToLimit());
        assertEquals(0, solids.getOverflowFlushCount());

        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadSolidModel emptyWithDrops = new RoadSolidModel();
        emptyWithDrops.addAll(solids);
        assertEquals(2, emptyWithDrops.getDroppedDueToLimit());
        RoadVoxelRasterizer.flushEdgeSolids(
            result, emptyWithDrops, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        assertEquals(2, result.droppedSolidCount);
    }

    @Test
    void overflowHandlerFlushesChunksInsteadOfDropping() {
        RoadSolidModel solids = new RoadSolidModel(4);
        RoadGenerationResult result = new RoadGenerationResult(0);
        solids.setOverflowHandler(model -> {
            RoadVoxelRasterizer.flushEdgeSolids(
                result, model, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
            model.clear();
        });

        for (int i = 0; i < 10; i++) {
            assertTrue(solids.add(new Vec2d(i, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        }
        assertEquals(0, solids.getDroppedDueToLimit());
        assertEquals(2, solids.getOverflowFlushCount());
        assertEquals(2, solids.primitives().size());

        RoadVoxelRasterizer.flushEdgeSolids(
            result, solids, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        assertEquals(0, result.droppedSolidCount);
        assertEquals(10, result.placementRecords.size());
    }
}
