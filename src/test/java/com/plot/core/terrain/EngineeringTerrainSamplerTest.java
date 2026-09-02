package com.plot.core.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineeringTerrainSamplerTest {

    @Test
    void nullWorldReturnsDefaultElevation() {
        assertEquals(
            EngineeringTerrainService.DEFAULT_GROUND_ELEVATION,
            EngineeringTerrainSampler.sampleGroundSurface(null, 0, 0));
        assertEquals(
            EngineeringTerrainService.DEFAULT_GROUND_ELEVATION,
            EngineeringTerrainSampler.sampleRawSurface(null, 0, 0));
    }

    @Test
    void defaultGroundElevationMatchesService() {
        assertEquals(
            EngineeringTerrainService.DEFAULT_GROUND_ELEVATION,
            EngineeringTerrainSampler.defaultGroundElevation());
    }
}
