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
        assertEquals(10, pos.getZ());
    }

    @Test
    void flushEdgeSolidsPopulatesResultBucketsAndPlacementRecords() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadSolidModel solids = new RoadSolidModel();
        solids.add(new Vec2d(1, 2), 64, RoadSolidLayer.ROAD, "minecraft:stone");
        solids.add(new Vec2d(3, 2), 64, RoadSolidLayer.SIDEWALK, "minecraft:oak_planks");
        solids.add(new Vec2d(2, 2), 64, RoadSolidLayer.MARKING, "minecraft:white_concrete");

        RoadVoxelRasterizer.flushEdgeSolids(result, solids, null);

        assertEquals(2, result.roadBlocks.size());
        assertEquals(1, result.sidewalkBlocks.size());
        assertEquals(3, result.placementRecords.size());
        assertEquals(0, result.droppedSolidCount);
    }

    @Test
    void droppedCountIsTrackedWhenAtCapacity() {
        RoadSolidModel solids = new RoadSolidModel();
        // 填满硬顶后继续添加应计入丢弃数（不 flush 全量，避免测试过慢）
        for (int i = 0; i < 100_000; i++) {
            assertTrue(solids.add(new Vec2d(i, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        }
        assertTrue(solids.isAtCapacity());
        assertFalse(solids.add(new Vec2d(999_999, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        assertFalse(solids.add(new Vec2d(999_998, 0), 64, RoadSolidLayer.ROAD, "minecraft:stone"));
        assertEquals(2, solids.getDroppedDueToLimit());

        RoadGenerationResult result = new RoadGenerationResult(0);
        // 仅验证丢弃计数传播：构造一个小模型并手动设置场景
        RoadSolidModel tiny = new RoadSolidModel();
        tiny.add(new Vec2d(1, 1), 64, RoadSolidLayer.ROAD, "minecraft:stone");
        // 通过 addAll 合并带丢弃计数的模型
        RoadSolidModel emptyWithDrops = new RoadSolidModel();
        // 模拟：将 solids 的 dropped 合并
        emptyWithDrops.addAll(solids);
        assertTrue(emptyWithDrops.getDroppedDueToLimit() >= 2);
        RoadVoxelRasterizer.flushEdgeSolids(result, emptyWithDrops, null);
        assertTrue(result.droppedSolidCount >= 2);
    }
}
