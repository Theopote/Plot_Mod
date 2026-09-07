package com.plot.core.terrain;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(service.isClearableNaturalDecoration(0, 64, 0));
        assertFalse(service.isChunkLoaded(0, 0));
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
