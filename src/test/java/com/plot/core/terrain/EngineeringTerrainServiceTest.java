package com.plot.core.terrain;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringTerrainServiceTest {

    @Test
    void classifiesAirAndFluid() {
        assertEquals(EngineeringTerrainBlockRole.AIR, EngineeringTerrainService.classifyTraits(
            true, false, false, false, false, false));
        assertEquals(EngineeringTerrainBlockRole.FLUID, EngineeringTerrainService.classifyTraits(
            false, true, false, false, false, false));
    }

    @Test
    void classifiesEngineeringTerrainAndNaturalDecoration() {
        assertTrue(EngineeringTerrainService.isEngineeringTerrainTraits(
            false, false, false, false, false, false));
        assertEquals(EngineeringTerrainBlockRole.ENGINEERING_TERRAIN, EngineeringTerrainService.classifyTraits(
            false, false, false, false, false, false));

        assertTrue(EngineeringTerrainService.isNaturalDecorationTraits(false, true, false, false));
        assertFalse(EngineeringTerrainService.isEngineeringTerrainTraits(
            false, false, false, true, false, false));
        assertEquals(EngineeringTerrainBlockRole.NATURAL_DECORATION, EngineeringTerrainService.classifyTraits(
            false, false, false, true, false, false));

        assertTrue(EngineeringTerrainService.isNaturalDecorationTraits(false, false, true, false));
        assertTrue(EngineeringTerrainService.isNaturalDecorationTraits(false, false, false, true));
        assertTrue(EngineeringTerrainService.isNaturalDecorationTraits(true, false, false, false));
    }

    @Test
    void nullWorldReturnsDefaultGroundElevation() {
        EngineeringTerrainService service = EngineeringTerrainService.of(null);
        assertEquals(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION, service.sampleGroundSurface(0, 0));
        assertEquals(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION, service.sampleRawSurface(0, 0));
        assertEquals(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION, service.sampleWaterSurface(0, 0));
        assertTrue(service.findWaterSurface(0, 0).isEmpty());
        assertEquals(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION, service.sampleSolidSurface(0, 0));
        assertFalse(service.isSolidEngineeringBlock(0, 64, 0));
        assertFalse(service.isWireObstruction(0, 64, 0));
        assertFalse(service.isClearableNaturalDecoration(0, 64, 0));
        assertFalse(service.isChunkLoaded(0, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wireVsEngineeringBlockCases")
    void wireObstructionDiffersFromSolidEngineeringBlock(
            String label,
            EngineeringTerrainBlockRole role,
            boolean expectSolidEngineering,
            boolean expectWireObstruction) {
        assertEquals(expectSolidEngineering, expectsSolidEngineeringBlock(role), label);
        assertEquals(expectWireObstruction, expectsWireObstruction(role), label);
        if (expectWireObstruction && !expectSolidEngineering) {
            assertNotEquals(
                expectsSolidEngineeringBlock(role),
                expectsWireObstruction(role),
                label + " should distinguish wire collision from engineering ground");
        }
    }

    @Test
    void naturalDecorationAndStructuresBlockWiresButNotEngineeringGround() {
        assertTrue(expectsWireObstruction(EngineeringTerrainBlockRole.NATURAL_DECORATION));
        assertFalse(expectsSolidEngineeringBlock(EngineeringTerrainBlockRole.NATURAL_DECORATION));

        assertTrue(expectsWireObstruction(EngineeringTerrainBlockRole.OTHER_SOLID));
        assertFalse(expectsSolidEngineeringBlock(EngineeringTerrainBlockRole.OTHER_SOLID));
    }

    @Test
    void onlyEngineeringTerrainCountsAsSolidEngineeringBlock() {
        assertTrue(expectsSolidEngineeringBlock(EngineeringTerrainBlockRole.ENGINEERING_TERRAIN));
        assertTrue(expectsWireObstruction(EngineeringTerrainBlockRole.ENGINEERING_TERRAIN));
    }

    @Test
    void airAndFluidAreNeitherWireNorEngineeringGround() {
        assertFalse(expectsWireObstruction(EngineeringTerrainBlockRole.AIR));
        assertFalse(expectsSolidEngineeringBlock(EngineeringTerrainBlockRole.AIR));
        assertFalse(expectsWireObstruction(EngineeringTerrainBlockRole.FLUID));
        assertFalse(expectsSolidEngineeringBlock(EngineeringTerrainBlockRole.FLUID));
    }

    @Test
    void classifyTraitsMatchesServiceSemantics() {
        EngineeringTerrainBlockRole grass = EngineeringTerrainService.classifyTraits(
            false, false, true, false, false, false);
        assertEquals(EngineeringTerrainBlockRole.NATURAL_DECORATION, grass);
        assertTrue(expectsWireObstruction(grass));
        assertFalse(expectsSolidEngineeringBlock(grass));

        EngineeringTerrainBlockRole dirt = EngineeringTerrainService.classifyTraits(
            false, false, false, false, false, false, true);
        assertEquals(EngineeringTerrainBlockRole.ENGINEERING_TERRAIN, dirt);
        assertTrue(expectsWireObstruction(dirt));
        assertTrue(expectsSolidEngineeringBlock(dirt));

        EngineeringTerrainBlockRole brick = EngineeringTerrainService.classifyTraits(
            false, false, false, false, false, false, false);
        assertEquals(EngineeringTerrainBlockRole.OTHER_SOLID, brick);
        assertTrue(expectsWireObstruction(brick));
        assertFalse(expectsSolidEngineeringBlock(brick));
    }

    static Stream<Arguments> wireVsEngineeringBlockCases() {
        return Stream.of(
            Arguments.of("air", EngineeringTerrainBlockRole.AIR, false, false),
            Arguments.of("fluid", EngineeringTerrainBlockRole.FLUID, false, false),
            Arguments.of("engineering_terrain", EngineeringTerrainBlockRole.ENGINEERING_TERRAIN, true, true),
            Arguments.of("oak_log", EngineeringTerrainBlockRole.NATURAL_DECORATION, false, true),
            Arguments.of("oak_leaves", EngineeringTerrainBlockRole.NATURAL_DECORATION, false, true),
            Arguments.of("short_grass", EngineeringTerrainBlockRole.NATURAL_DECORATION, false, true),
            Arguments.of("brick_wall", EngineeringTerrainBlockRole.OTHER_SOLID, false, true));
    }

    /** Mirrors {@link EngineeringTerrainService#isSolidEngineeringBlock} classification. */
    private static boolean expectsSolidEngineeringBlock(EngineeringTerrainBlockRole role) {
        return role == EngineeringTerrainBlockRole.ENGINEERING_TERRAIN;
    }

    /** Mirrors {@link EngineeringTerrainService#isWireObstruction} classification. */
    private static boolean expectsWireObstruction(EngineeringTerrainBlockRole role) {
        return role != EngineeringTerrainBlockRole.AIR && role != EngineeringTerrainBlockRole.FLUID;
    }

    @Test
    void nullStateIsNotEngineeringTerrain() {
        assertFalse(EngineeringTerrainService.isEngineeringTerrain(null));
        assertFalse(EngineeringTerrainService.isNaturalDecoration(null));
        assertEquals(EngineeringTerrainBlockRole.AIR, EngineeringTerrainService.classifyBlock(null));
    }

    @Test
    void sitePrepNaturalDecorationTraits_grassFlowersLeaves() {
        assertEquals(EngineeringTerrainBlockRole.NATURAL_DECORATION,
            EngineeringTerrainService.classifyTraits(false, false, true, false, false, false));
        assertEquals(EngineeringTerrainBlockRole.NATURAL_DECORATION,
            EngineeringTerrainService.classifyTraits(false, false, false, false, false, true));
        assertEquals(EngineeringTerrainBlockRole.NATURAL_DECORATION,
            EngineeringTerrainService.classifyTraits(false, false, false, false, true, false));
        // 非自然地表材料 → OTHER_SOLID（人工构筑）
        assertEquals(EngineeringTerrainBlockRole.OTHER_SOLID,
            EngineeringTerrainService.classifyTraits(false, false, false, false, false, false, false));
        assertEquals(EngineeringTerrainBlockRole.ENGINEERING_TERRAIN,
            EngineeringTerrainService.classifyTraits(false, false, false, false, false, false, true));
    }

    @Test
    void clearableNaturalDecorationAtTreatsStandaloneLogsAsStructure() {
        Set<Long> logs = Set.of(BlockPos.asLong(0, 64, 0));
        Set<Long> terrain = Set.of(BlockPos.asLong(0, 63, 0));
        Predicate<BlockPos> isLog = pos -> logs.contains(pack(pos));
        Predicate<BlockPos> isLeaf = pos -> pos.equals(new BlockPos(1, 66, 0));
        Predicate<BlockPos> isTerrain = pos -> terrain.contains(pack(pos));

        assertFalse(EngineeringTerrainService.isClearableNaturalDecorationAt(
            new BlockPos(0, 64, 0), isLog, isLeaf, isTerrain, pos -> false));
        assertTrue(EngineeringTerrainService.isClearableNaturalDecorationAt(
            new BlockPos(1, 66, 0), isLog, isLeaf, isTerrain, pos -> false));
    }

    private static long pack(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }

    @Test
    void w09UndergroundWaterIgnoredByExposedSurfaceScan() {
        // surface terrain Y80；地下洞穴水 Y40 → 不应算 footprint 地表水
        assertTrue(EngineeringTerrainService.findExposedWaterInRange(
            80, 80, y -> y == 40).isEmpty());

        // 湖面：ground=60, raw=64，水体在 61..64
        var lake = EngineeringTerrainService.findExposedWaterInRange(
            64, 60, y -> y >= 61 && y <= 64);
        assertTrue(lake.isPresent());
        assertEquals(64, lake.getAsInt());
    }
}
