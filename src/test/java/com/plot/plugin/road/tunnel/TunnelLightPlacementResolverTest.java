package com.plot.plugin.road.tunnel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelLightPlacementResolverTest {

    private static final int ROAD_Y = 64;
    private static final int ENVELOPE = 5;
    private static final int SIDE = 1;
    private static final int CLEAR = 6;
    private static final int LINING = 1;

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void noneModeReturnsEmpty(TunnelShape shape) {
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, TunnelLightingMode.NONE);
        assertTrue(lights.isEmpty(), shape.name());
    }

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void wallBandsProduceSymmetricWallLightsInAir(TunnelShape shape) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, TunnelLightingMode.WALL_BANDS);
        long walls = lights.stream()
            .filter(l -> l.type() == TunnelLightPlacementResolver.LightType.WALL)
            .count();
        assertEquals(0, lights.stream()
            .filter(l -> l.type() == TunnelLightPlacementResolver.LightType.CEILING)
            .count(), shape.name());
        if (walls > 0) {
            assertEquals(2, walls, shape + " wall lights should be left/right pair when present");
            for (var light : lights) {
                assertEquals(TunnelCrossSectionMask.CellKind.AIR, mask.kindAt(light.lateral(), light.y()));
                assertTrue(light.y() > ROAD_Y);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void ceilingBandPlacesCenterCeilingWhenAir(TunnelShape shape) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, false);
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, TunnelLightingMode.CEILING_BAND);
        assertTrue(lights.stream().noneMatch(l -> l.type() == TunnelLightPlacementResolver.LightType.WALL));
        for (var light : lights) {
            assertEquals(TunnelLightPlacementResolver.LightType.CEILING, light.type());
            assertEquals(0, light.lateral());
            assertEquals(TunnelCrossSectionMask.CellKind.AIR, mask.kindAt(light.lateral(), light.y()));
        }
    }

    @ParameterizedTest
    @EnumSource(TunnelShape.class)
    void wallAndCeilingCombinesBoth(TunnelShape shape) {
        List<TunnelLightPlacementResolver.LightPosition> lights = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, TunnelLightingMode.WALL_AND_CEILING);
        List<TunnelLightPlacementResolver.LightPosition> walls = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, TunnelLightingMode.WALL_BANDS);
        List<TunnelLightPlacementResolver.LightPosition> ceiling = TunnelLightPlacementResolver.resolve(
            shape, ENVELOPE, SIDE, CLEAR, LINING, ROAD_Y, TunnelLightingMode.CEILING_BAND);
        assertEquals(walls.size() + ceiling.size(), lights.size(), shape.name());
    }
}
