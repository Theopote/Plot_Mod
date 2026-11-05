package com.plot.plugin.road.profile.terrain;

import com.plot.api.geometry.Vec2d;
import com.plot.core.context.ApplicationContext;
import com.plot.core.context.PluginContext;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadPreviewManager;
import com.plot.plugin.road.manager.RoadProjectStatus;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.profile.RoadProfileChartData;
import com.plot.plugin.road.profile.edit.ProfileEditorWindowSession;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.vertical.VerticalAlignmentGeometry;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
            network,
            road,
            terrain,
            new RoadSystemConfig("terrain-test"),
            "minecraft:overworld",
            IdentityCoordinateService.INSTANCE);

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
            network,
            road,
            unloaded,
            new RoadSystemConfig("unloaded"),
            "minecraft:overworld",
            IdentityCoordinateService.INSTANCE);
        assertTrue(data == null || !data.hasRenderableTerrain());
    }

    @Test
    void longRoadSamplesCoverEntireChainage() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("long");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10000, 0));
        network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10000, 0)), road.getId());

        RoadSystemConfig config = new RoadSystemConfig("long-road");
        config.setPathSampleDistance(1.0);
        List<Double> stations = RoadTerrainProfileSampler.buildStations(
            network, road, RoadTerrainProfileSampler.resolveSpacing(config));

        assertEquals(RoadTerrainProfileSampler.MAX_SAMPLES, stations.size());
        assertEquals(0.0, stations.getFirst(), 1e-9);
        assertEquals(10000.0, stations.getLast(), 1e-9);
        // 均匀覆盖：中点附近必须有采样，不能停在 ~2047
        double mid = stations.get(stations.size() / 2);
        assertTrue(mid > 4000.0, "mid sample should sit past first-quarter chainage, was " + mid);
        double maxGap = 0.0;
        for (int i = 1; i < stations.size(); i++) {
            maxGap = Math.max(maxGap, stations.get(i) - stations.get(i - 1));
        }
        assertTrue(maxGap < 8.0, "uniform spacing should keep gaps under ~5m, was " + maxGap);
    }

    @Test
    void terrainEpochInvalidatesCachedProfile() {
        TerrainProfileKey keyA = new TerrainProfileKey("w", "r1", 1L, 2L, 10L, 1.0);
        TerrainProfileKey keyB = new TerrainProfileKey("w", "r1", 1L, 2L, 11L, 1.0);
        RoadTerrainProfileData data = loadedTerrain(keyA, List.of(0.0, 10.0), List.of(64.0, 66.0));

        RoadTerrainProfileCache cache = new RoadTerrainProfileCache();
        cache.put(data);
        assertTrue(cache.isFresh(keyA));
        assertFalse(cache.isFresh(keyB));
        assertTrue(cache.isStale(keyB));
    }

    @Test
    void worldSwitchInvalidatesTerrainProfile() {
        TerrainProfileKey overworld = new TerrainProfileKey("minecraft:overworld", "r1", 1L, 0L, 1L, 1.0);
        TerrainProfileKey nether = new TerrainProfileKey("minecraft:the_nether", "r1", 1L, 0L, 1L, 1.0);
        RoadTerrainProfileCache cache = new RoadTerrainProfileCache();
        cache.put(loadedTerrain(overworld, List.of(0.0, 20.0), List.of(70.0, 71.0)));
        assertTrue(cache.isFresh(overworld));
        assertFalse(cache.isFresh(nether));
        assertTrue(cache.isStale(nether));
    }

    @Test
    void partialTerrainDoesNotPretendComplete() {
        TerrainProfileKey key = new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0);
        List<Double> stations = List.of(0.0, 1.0, 2.0, 3.0, 4.0);
        List<Double> terrainY = List.of(72.0, Double.NaN, Double.NaN, Double.NaN, 86.0);
        List<Double> waterY = List.of(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        List<Boolean> loaded = List.of(true, false, false, false, true);
        RoadTerrainProfileData data = new RoadTerrainProfileData(
            key, 4.0, stations, terrainY, waterY, loaded);

        assertTrue(data.hasAnyValidSample());
        assertFalse(data.hasDrawableSegment());
        assertFalse(data.hasRenderableTerrain());
        assertFalse(data.fullyLoaded());
        assertTrue(RoadProfileChartData.fromTerrain(data) == null);
    }

    @Test
    void refreshTerrainOverridesStaleFullChartGround() {
        TerrainProfileKey key = new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0);
        RoadTerrainProfileData terrain = loadedTerrain(key, List.of(0.0, 50.0, 100.0), List.of(80.0, 81.0, 82.0));

        RoadProfileChartData staleFull = new RoadProfileChartData(
            "r1",
            100.0,
            List.of(0.0, 50.0, 100.0),
            List.of(60.0, 61.0, 62.0),
            List.of(65.0, 66.0, 67.0),
            List.of(65.0, 66.0, 67.0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of(),
            List.of());

        RoadProfileChartData merged = staleFull.withTerrainGround(terrain);
        assertEquals(80.0, merged.groundElevationAt(0.0), 1e-9);
        assertEquals(82.0, merged.groundElevationAt(100.0), 1e-9);
        assertFalse(merged.hasProfileData());
        assertTrue(merged.hasStaleDesignReference());
        assertEquals(65.0, merged.previewElevationAt(0.0), 1e-9);
        assertTrue(Double.isNaN(merged.buildElevationAt(0.0)));
    }

    @Test
    void cancelledSamplingNeverPublishes() {
        ApplicationContext app = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            app.getAppState(),
            app.getCommandService(),
            app.getEventBus(),
            app.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        RoadPreviewManager manager = new RoadPreviewManager(new RoadProjectStatus(), host);
        TerrainProfileKey key = new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0);
        RoadTerrainProfileSamplingJob job = new RoadTerrainProfileSamplingJob(
            manager,
            key,
            100.0,
            List.of(0.0, 50.0, 100.0),
            List.of(new Vec2d(0, 0), new Vec2d(50, 0), new Vec2d(100, 0)));
        try {
            var field = RoadPreviewManager.class.getDeclaredField("terrainProfileJob");
            field.setAccessible(true);
            field.set(manager, job);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        job.cancel();
        job.tick();
        assertEquals(RoadTerrainProfileSamplingJob.Phase.CANCELLED, job.phase());
        assertTrue(manager.getTerrainProfile(null, "r1") == null);
        assertFalse(manager.isTerrainProfileJobRunning());
    }

    @Test
    void unresolvedStationPointsDoNotThrow() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(10, 0));
        network.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(10, 0)), road.getId());

        // 人为塞入无法落在几何上的桩号：resolvePoints 应允许 null 占位，不抛 NPE。
        List<Double> stations = List.of(0.0, 5.0, 10.0, 1_000_000.0);
        List<Vec2d> points = RoadTerrainProfileSampler.resolvePoints(network, road, stations);
        assertEquals(4, points.size());
        assertNotNull(points.get(0));
        assertNotNull(points.get(1));
        assertNotNull(points.get(2));
        // 超长桩号可能被夹到终点，也可能为 null；关键是不抛异常且列表可被 job 接受。
        RoadTerrainProfileSamplingJob job = new RoadTerrainProfileSamplingJob(
            new RoadPreviewManager(
                new RoadProjectStatus(),
                new PluginContext(
                    ApplicationContext.getInstance().getAppState(),
                    ApplicationContext.getInstance().getCommandService(),
                    ApplicationContext.getInstance().getEventBus(),
                    ApplicationContext.getInstance().getToolManager(),
                    IdentityCoordinateService.INSTANCE,
                    null,
                    null,
                    null)),
            new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0),
            10.0,
            stations,
            points);
        assertEquals(4, job.totalWorkUnits());
    }

    @Test
    void sameWorldUnchangedGeometryPublishesJobResult() {
        RoadPreviewManager manager = terrainManager("test-world");
        RoadNetwork network = straightRoadNetwork(40.0);
        Road road = network.getRoad("r1");
        manager.setLiveNetworkLookup(() -> network);

        TerrainProfileKey key = RoadTerrainProfileSampler.buildKey(
            "test-world", network, road, IdentityCoordinateService.INSTANCE, new RoadSystemConfig("t"));
        RoadTerrainProfileData data = loadedTerrain(
            key, List.of(0.0, 20.0, 40.0), List.of(70.0, 71.0, 72.0));
        RoadTerrainProfileSamplingJob job = attachJob(manager, key, data);
        manager.completeTerrainProfileJob(job, data);

        RoadTerrainProfileData cached = manager.getTerrainProfile(network, "r1");
        assertNotNull(cached);
        assertTrue(manager.isTerrainProfileFresh(network, road));
    }

    @Test
    void sameWorldMovedRoadRejectsCompletedJobResult() {
        RoadPreviewManager manager = terrainManager("test-world");
        RoadNetwork network = straightRoadNetwork(40.0);
        Road road = network.getRoad("r1");
        var edge = network.getEdges().values().iterator().next();
        manager.setLiveNetworkLookup(() -> network);

        TerrainProfileKey startKey = RoadTerrainProfileSampler.buildKey(
            "test-world", network, road, IdentityCoordinateService.INSTANCE, new RoadSystemConfig("t"));
        RoadTerrainProfileData data = loadedTerrain(
            startKey, List.of(0.0, 20.0, 40.0), List.of(70.0, 71.0, 72.0));
        RoadTerrainProfileSamplingJob job = attachJob(manager, startKey, data);

        edge.setCenterlinePoints(List.of(new Vec2d(0, 0), new Vec2d(40, 20)));
        manager.completeTerrainProfileJob(job, data);
        assertTrue(manager.getTerrainProfile(network, "r1") == null);
    }

    @Test
    void worldSwitchRejectsCompletedJobResult() {
        RoadPreviewManager manager = terrainManager("world-a");
        RoadNetwork network = straightRoadNetwork(40.0);
        Road road = network.getRoad("r1");
        manager.setLiveNetworkLookup(() -> network);

        TerrainProfileKey startKey = RoadTerrainProfileSampler.buildKey(
            "world-a", network, road, IdentityCoordinateService.INSTANCE, new RoadSystemConfig("t"));
        RoadTerrainProfileData data = loadedTerrain(
            startKey, List.of(0.0, 20.0, 40.0), List.of(70.0, 71.0, 72.0));
        RoadTerrainProfileSamplingJob job = attachJob(manager, startKey, data);

        manager.setWorldKeyLookup(() -> "world-b");
        manager.completeTerrainProfileJob(job, data);
        assertTrue(manager.getTerrainProfile(network, "r1") == null);
    }

    @Test
    void withTerrainGroundResamplesInLinearTime() {
        List<Double> designStations = new ArrayList<>();
        List<Double> preview = new ArrayList<>();
        for (int i = 0; i <= 2047; i++) {
            designStations.add(i * (10000.0 / 2047.0));
            preview.add(60.0 + i * 0.001);
        }
        RoadProfileChartData full = new RoadProfileChartData(
            "r1",
            10000.0,
            designStations,
            preview,
            preview,
            preview,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of(),
            List.of());
        TerrainProfileKey key = new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0);
        List<Double> terrainStations = new ArrayList<>();
        List<Double> terrainY = new ArrayList<>();
        for (int i = 0; i <= 2047; i++) {
            terrainStations.add(i * (10000.0 / 2047.0));
            terrainY.add(80.0 + i * 0.002);
        }
        RoadTerrainProfileData terrain = loadedTerrain(key, terrainStations, terrainY);
        long start = System.nanoTime();
        RoadProfileChartData merged = full.withTerrainGround(terrain);
        long elapsedNs = System.nanoTime() - start;
        assertNotNull(merged);
        assertEquals(80.0, merged.groundElevationAt(0.0), 1e-6);
        assertEquals(60.0, merged.previewElevationAt(0.0), 1e-6);
        assertTrue(merged.hasStaleDesignReference());
        // 双指针合成应远低于 O(N*M) 量级；给宽裕上限以免 CI 抖动。
        assertTrue(elapsedNs < 50_000_000L, "merge took " + elapsedNs + " ns");
    }

    @Test
    void staleFullChartIsNotUsedAsCurrentTerrain() {
        RoadPreviewManager manager = terrainManager("test-world");
        RoadNetwork network = straightRoadNetwork(40.0);
        manager.setLiveNetworkLookup(() -> network);

        RoadProfileChartData full = new RoadProfileChartData(
            "r1",
            40.0,
            List.of(0.0, 40.0),
            List.of(60.0, 62.0),
            List.of(65.0, 67.0),
            List.of(65.0, 67.0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            true,
            true,
            List.of(),
            List.of(),
            List.of());
        try {
            var field = RoadPreviewManager.class.getDeclaredField("lastRoadProfiles");
            field.setAccessible(true);
            field.set(manager, java.util.Map.of("r1", full));
            var stale = RoadPreviewManager.class.getDeclaredField("previewNeedsRecalc");
            stale.setAccessible(true);
            stale.setBoolean(manager, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        assertTrue(manager.getOverviewChart(network, "r1") == null);
    }

    @Test
    void endpointGapDoesNotBootstrapFromDistantSample() {
        RoadNetwork live = new RoadNetwork();
        Road road = live.createRoad("r1");
        RoadNode a = live.createNode(new Vec2d(0, 0));
        RoadNode b = live.createNode(new Vec2d(100, 0));
        live.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());

        TerrainProfileKey key = new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0);
        // 仅中段有有效点，起终点附近缺失。
        List<Double> stations = List.of(0.0, 50.0, 100.0);
        List<Double> terrainY = List.of(Double.NaN, 74.0, Double.NaN);
        List<Double> waterY = List.of(Double.NaN, Double.NaN, Double.NaN);
        List<Boolean> loaded = List.of(false, true, false);
        // 中段相邻？只有一个有效点 → 不可渲染。补一对中段相邻点。
        stations = List.of(0.0, 48.0, 52.0, 100.0);
        terrainY = List.of(Double.NaN, 74.0, 75.0, Double.NaN);
        waterY = List.of(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        loaded = List.of(false, true, true, false);
        RoadTerrainProfileData terrain = new RoadTerrainProfileData(
            key, 100.0, stations, terrainY, waterY, loaded);
        RoadProfileChartData chart = RoadProfileChartData.fromTerrain(terrain);
        assertNotNull(chart);

        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 0L);
        Road working = session.editingNetwork().getRoad(road.getId());
        assertFalse(session.seedRoadEditorAlignment(
            working, new RoadSystemConfig("bootstrap"), java.util.Map.of(), chart));
        assertFalse(VerticalAlignmentGeometry.isEvaluable(working.getVerticalAlignment()));
    }

    @Test
    void terrainOnlyBootstrapCreatesEditablePvis() {
        RoadNetwork live = new RoadNetwork();
        Road road = live.createRoad("r1");
        RoadNode a = live.createNode(new Vec2d(0, 0));
        RoadNode b = live.createNode(new Vec2d(100, 0));
        live.createEdge(a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(100, 0)), road.getId());
        assertFalse(VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment()));

        TerrainProfileKey key = new TerrainProfileKey("w", "r1", 1L, 0L, 1L, 1.0);
        RoadTerrainProfileData terrain = loadedTerrain(key, List.of(0.0, 50.0, 100.0), List.of(72.0, 74.0, 76.0));
        RoadProfileChartData chart = RoadProfileChartData.fromTerrain(terrain);
        assertNotNull(chart);
        assertTrue(chart.canRenderChart());
        assertFalse(chart.hasProfileData());

        ProfileEditorWindowSession session = ProfileEditorWindowSession.open(live, 0L);
        Road working = session.editingNetwork().getRoad(road.getId());
        assertTrue(session.seedRoadEditorAlignment(
            working, new RoadSystemConfig("bootstrap"), java.util.Map.of(), chart));
        assertTrue(VerticalAlignmentGeometry.isEvaluable(working.getVerticalAlignment()));
        assertEquals(2, working.getVerticalAlignment().getPvis().size());
        assertEquals(72.0, working.getVerticalAlignment().getPvis().get(0).getElevation(), 1e-9);
        assertEquals(76.0, working.getVerticalAlignment().getPvis().get(1).getElevation(), 1e-9);
        assertEquals(ProfileSource.AUTO, working.getEffectiveProfileSource());
        assertFalse(VerticalAlignmentGeometry.isEvaluable(live.getRoad(road.getId()).getVerticalAlignment()));
    }

    private static RoadPreviewManager terrainManager(String worldId) {
        ApplicationContext app = ApplicationContext.getInstance();
        PluginContext host = new PluginContext(
            app.getAppState(),
            app.getCommandService(),
            app.getEventBus(),
            app.getToolManager(),
            IdentityCoordinateService.INSTANCE,
            null,
            null,
            null);
        RoadPreviewManager manager = new RoadPreviewManager(new RoadProjectStatus(), host);
        manager.setWorldKeyLookup(() -> worldId);
        return manager;
    }

    private static RoadNetwork straightRoadNetwork(double length) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("r1");
        RoadNode a = network.createNode(new Vec2d(0, 0));
        RoadNode b = network.createNode(new Vec2d(length, 0));
        network.createEdge(
            a.getId(), b.getId(), List.of(new Vec2d(0, 0), new Vec2d(length, 0)), road.getId());
        return network;
    }

    private static RoadTerrainProfileSamplingJob attachJob(
            RoadPreviewManager manager,
            TerrainProfileKey key,
            RoadTerrainProfileData data) {
        List<Vec2d> points = new ArrayList<>(data.stations().size());
        for (double station : data.stations()) {
            points.add(new Vec2d(station, 0));
        }
        RoadTerrainProfileSamplingJob job = new RoadTerrainProfileSamplingJob(
            manager, key, data.totalStation(), data.stations(), points);
        try {
            var field = RoadPreviewManager.class.getDeclaredField("terrainProfileJob");
            field.setAccessible(true);
            field.set(manager, job);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return job;
    }

    private static RoadTerrainProfileData loadedTerrain(
            TerrainProfileKey key,
            List<Double> stations,
            List<Double> terrainY) {
        List<Double> water = new ArrayList<>(stations.size());
        List<Boolean> loaded = new ArrayList<>(stations.size());
        for (int i = 0; i < stations.size(); i++) {
            water.add(Double.NaN);
            loaded.add(true);
        }
        return new RoadTerrainProfileData(
            key,
            stations.getLast(),
            stations,
            terrainY,
            water,
            loaded);
    }
}
