package com.plot.core.terrain;

import org.junit.jupiter.api.Test;

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
}
