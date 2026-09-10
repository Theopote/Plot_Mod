package com.plot.plugin.road.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.EngineeringTerrainBlockRole;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadRoadbedGradingUtils;
import com.plot.plugin.road.solid.RoadSolidModel;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 道路地形语义：Road 仅清除 sampler 标记为 clearable 的方块，并覆盖共享水体语义。
 */
class RoadTerrainSemanticsTest {

    @Test
    void flatTerrainSamplerDoesNotClearDecorationsByDefault() {
        TerrainSampler terrain = new FlatTerrainSampler(64);
        assertFalse(terrain.isRoadClearableDecoration(0, 65, 0));
    }

    @Test
    void clearRoadDecorationsOnlyRemovesWhenSamplerMarksClearable() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler woodedColumn = new TerrainSampler() {
            @Override public int sampleSurfaceY(Vec2d point) { return 64; }
            @Override public int sampleColumnTopY(Vec2d point) { return 70; }
            @Override public boolean isSolidBlock(int x, int y, int z) { return y <= 64; }
            @Override public boolean isRoadClearableDecoration(int x, int y, int z) {
                return y >= 65 && y <= 67;
            }
        };

        RoadRoadbedGradingUtils.clearRoadDecorations(
            solids, new Vec2d(0, 0), new Vec2d(0, 1), 3,
            woodedColumn, new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override public int worldX(Vec2d point) { return (int) Math.round(point.x); }
                @Override public int worldZ(Vec2d point) { return (int) Math.round(point.y); }
            }, 1.0);

        assertEquals(9, solids.primitives().size());
        assertTrue(solids.primitives().stream().allMatch(p -> p.elevation() >= 65 && p.elevation() <= 67));
    }

    @Test
    void tr01PlayerLogColumnNotClearableThroughProductionClearancePath() {
        var fixtures = playerLogColumnFixtures();
        BlockPos log = new BlockPos(0, 64, 0);

        assertFalse(EngineeringTerrainService.isClearableNaturalDecorationAt(
            log, fixtures.isLog(), fixtures.isLeaf(), fixtures.isTerrain(), pos -> false));
        assertFalse(new ProductionClearanceSampler(fixtures).isRoadClearableDecoration(0, 64, 0));
    }

    @Test
    void tr02NaturalTreeStillClearableThroughProductionClearancePath() {
        var fixtures = naturalTreeFixtures();
        BlockPos log = new BlockPos(0, 64, 0);

        assertTrue(EngineeringTerrainService.isClearableNaturalDecorationAt(
            log, fixtures.isLog(), fixtures.isLeaf(), fixtures.isTerrain(), pos -> false));
        assertTrue(new ProductionClearanceSampler(fixtures).isRoadClearableDecoration(0, 64, 0));
        assertTrue(EngineeringTerrainService.isClearableNaturalDecorationAt(
            new BlockPos(1, 66, 0),
            fixtures.isLog(), fixtures.isLeaf(), fixtures.isTerrain(), pos -> false));
    }

    @Test
    void playerLogColumnPreservedWhenProductionSamplerReturnsFalse() {
        RoadSolidModel solids = new RoadSolidModel();
        TerrainSampler productionLike = new ProductionClearanceSampler(playerLogColumnFixtures());

        RoadRoadbedGradingUtils.clearRoadDecorations(
            solids, new Vec2d(0, 0), new Vec2d(0, 1), 3,
            productionLike, new com.plot.plugin.road.RoadTerrainClearanceUtils.BlockColumnResolver() {
                @Override public int worldX(Vec2d point) { return (int) Math.round(point.x); }
                @Override public int worldZ(Vec2d point) { return (int) Math.round(point.y); }
            }, 1.0);

        assertTrue(solids.isEmpty(), "player log column must not be cleared by production sampler");
    }

    @Test
    void undergroundWaterIgnoredByExposedSurfaceScan() {
        assertTrue(EngineeringTerrainService.findExposedWaterInRange(
            80, 80, y -> y == 40).isEmpty());
    }

    @Test
    void surfaceWaterDetectedInExposedRange() {
        var lake = EngineeringTerrainService.findExposedWaterInRange(
            64, 60, y -> y >= 61 && y <= 64);
        assertTrue(lake.isPresent());
        assertEquals(64, lake.getAsInt());
    }

    @Test
    void nullBlockStateIsNotClearableEngineeringTerrain() {
        assertFalse(EngineeringTerrainService.isEngineeringTerrain(null));
        assertFalse(EngineeringTerrainService.isNaturalDecoration(null));
        assertEquals(EngineeringTerrainBlockRole.AIR, EngineeringTerrainService.classifyBlock(null));
    }

    private static ClearanceFixtures playerLogColumnFixtures() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(0, 65, 0),
            BlockPos.asLong(0, 66, 0),
            BlockPos.asLong(0, 67, 0));
        Set<Long> terrain = Set.of(BlockPos.asLong(0, 63, 0));
        return new ClearanceFixtures(logs, Set.of(), terrain);
    }

    private static ClearanceFixtures naturalTreeFixtures() {
        Set<Long> logs = Set.of(
            BlockPos.asLong(0, 64, 0),
            BlockPos.asLong(0, 65, 0),
            BlockPos.asLong(0, 66, 0));
        Set<Long> leaves = Set.of(
            BlockPos.asLong(1, 66, 0),
            BlockPos.asLong(-1, 66, 0),
            BlockPos.asLong(0, 66, 1),
            BlockPos.asLong(0, 66, -1));
        Set<Long> terrain = Set.of(BlockPos.asLong(0, 63, 0));
        return new ClearanceFixtures(logs, leaves, terrain);
    }

    private record ClearanceFixtures(Set<Long> logs, Set<Long> leaves, Set<Long> terrain) {
        Predicate<BlockPos> isLog() {
            return pos -> logs.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }

        Predicate<BlockPos> isLeaf() {
            return pos -> leaves.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }

        Predicate<BlockPos> isTerrain() {
            return pos -> terrain.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /** 与 {@link MinecraftTerrainSampler#isRoadClearableDecoration} 共用 clearance 判定。 */
    private static final class ProductionClearanceSampler implements TerrainSampler {
        private final ClearanceFixtures fixtures;

        private ProductionClearanceSampler(ClearanceFixtures fixtures) {
            this.fixtures = fixtures;
        }

        @Override public int sampleSurfaceY(Vec2d planPoint) { return 64; }
        @Override public int sampleColumnTopY(Vec2d planPoint) { return 68; }
        @Override public boolean isSolidBlock(int worldX, int y, int worldZ) { return y <= 63; }

        @Override
        public boolean isRoadClearableDecoration(int worldX, int y, int worldZ) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            return EngineeringTerrainService.isClearableNaturalDecorationAt(
                pos,
                fixtures.isLog(),
                fixtures.isLeaf(),
                fixtures.isTerrain(),
                ignored -> false);
        }
    }
}
