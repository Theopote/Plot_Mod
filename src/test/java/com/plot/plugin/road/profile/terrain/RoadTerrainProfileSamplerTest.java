package com.plot.plugin.road.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.vertical.ProfileSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadTerrainProfileSamplerTest {

    @Test
    void samplesCenterlineTerrainWithoutChangingProfileSource() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        road.setProfileSource(ProfileSource.EDITED);
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(40, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(40, 0)), road.getId());

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return 70 + (int) Math.round(planPoint.x / 10.0);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= sampleSurfaceY(new Vec2d(worldX, worldZ));
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return planPoint.x < 30;
            }
        };

        RoadTerrainProfileData data = RoadTerrainProfileSampler.sample(
            network, road, terrain, new RoadSystemConfig("terrain-test"), 1L);

        assertNotNull(data);
        assertTrue(data.hasRenderableTerrain());
        assertFalse(data.fullyLoaded());
        assertEquals(ProfileSource.EDITED, road.getEffectiveProfileSource());

        RoadProfileChartData chart = RoadProfileChartData.fromTerrain(data);
        assertNotNull(chart);
        assertTrue(chart.canRenderChart());
        assertTrue(chart.hasTerrainSeries());
        assertFalse(chart.hasProfileData());
    }

    @Test
    void geometryFingerprintChangesWhenPathMoves() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(20, 0));
        var edge = network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(20, 0)), road.getId());

        long before = RoadTerrainProfileSampler.geometryFingerprint(network, road);
        edge.setCenterlinePoints(List.of(new Vec2d(0, 0), new Vec2d(20, 5)));
        long after = RoadTerrainProfileSampler.geometryFingerprint(network, road);
        assertTrue(before != after);
    }

    @Test
    void unloadedStationsBecomeGapsNotSeaLevel() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        TerrainSampler unloaded = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return TerrainSampler.DEFAULT_SEA_LEVEL;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return false;
            }

            @Override
            public boolean isChunkLoaded(Vec2d planPoint) {
                return false;
            }

            @Override
            public OptionalInt sampleLoadedSurfaceY(Vec2d planPoint) {
                return OptionalInt.empty();
            }
        };

        RoadTerrainProfileData data = RoadTerrainProfileSampler.sample(
            network, road, unloaded, new RoadSystemConfig("unloaded"), 0L);
        // 全程未加载：不可作为可渲染真实地形
        assertTrue(data == null || !data.hasRenderableTerrain());
    }
}
