package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics.TerrainAdaptationPreset;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.road.profile.WaterCrossingChartMarker;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for long-bridge pier planning across irregular multi-segment centerlines.
 */
class LongBridgeMultiSegmentIntegrationTest {

    private static final double RIVER_START = 15.0;
    private static final double RIVER_END = 80.0;

    @Test
    void longBridgePierSpacingRemainsUniformThroughStandalonePipeline() {
        RoadSystemConfig config = longBridgeConfig();
        RoadGenerator generator = generator(config);
        TerrainSampler terrain = wideRiverTerrain();

        RoadGenerationResult result = generator.generateFromPathPoints(
            irregularCenterline(100.0),
            terrain);

        WaterCrossingChartMarker crossing = requireLongBridgeMarker(result);
        assertTrue(result.bridgeLength > 0.0, "long bridge crossing must produce bridge construction");
        assertTrue(result.constructionTypes.contains(RoadConstructionType.BRIDGE),
            "profile long bridge must map to BRIDGE construction");
        assertUniformInteriorPierSpacing(result, crossing.startStation(), crossing.endStation());
    }

    @Test
    void longBridgeNetworkPreviewKeepsUniformPierSpacingAcrossEdgeSegments() {
        RoadSystemConfig config = longBridgeConfig();
        RoadGenerator generator = generator(config);
        RoadNetwork network = longBridgeNetwork();
        TerrainSampler terrain = wideRiverTerrain();

        RoadNetworkGenerator.PreviewResult preview =
            new RoadNetworkGenerator(generator).generatePreview(network, terrain);
        RoadGenerationResult result = preview.edgeResults().values().iterator().next();

        WaterCrossingChartMarker crossing = requireLongBridgeMarker(result);
        assertFalse(preview.edgeResults().isEmpty());
        assertUniformInteriorPierSpacing(result, crossing.startStation(), crossing.endStation());
    }

    private static RoadSystemConfig longBridgeConfig() {
        RoadSystemConfig config = new RoadSystemConfig("long-bridge-integration");
        config.setRoadWidth(5);
        config.setIncludeSidewalk(false);
        config.setIncludeShoulder(false);
        config.setTerrainStyle(com.plot.plugin.road.terrain.RoadTerrainStyle.BALANCED);
        config.setPathSampleDistance(2.0);
        return config;
    }

    private static RoadGenerator generator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            com.plot.test.world.IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }

    private static RoadNetwork longBridgeNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad("long-bridge");
        road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        road.setTerrainFollowPreset(TerrainFollowPreset.STANDARD);
        road.setMaxSlope(8.0f);
        RoadNode start = network.createNode(new Vec2d(0, 0));
        RoadNode end = network.createNode(new Vec2d(100, 0));
        network.createEdge(
            start.getId(),
            end.getId(),
            irregularCenterline(100.0),
            road.getId());
        return network;
    }

    private static List<Vec2d> irregularCenterline(double endX) {
        return List.of(
            new Vec2d(0, 0),
            new Vec2d(7, 0),
            new Vec2d(25, 0),
            new Vec2d(60, 0),
            new Vec2d(endX, 0));
    }

    private static TerrainSampler wideRiverTerrain() {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                return inRiver(planPoint.x) ? 50 : 68;
            }

            @Override
            public OptionalInt findExposedWaterSurface(Vec2d planPoint) {
                return inRiver(planPoint.x) ? OptionalInt.of(63) : OptionalInt.empty();
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= (inRiver(worldX) ? 50 : 68);
            }

            private boolean inRiver(double x) {
                return x >= RIVER_START && x <= RIVER_END;
            }
        };
    }

    private static WaterCrossingChartMarker requireLongBridgeMarker(RoadGenerationResult result) {
        assertFalse(result.profileWaterCrossingMarkers.isEmpty(),
            "water-aware profile must emit crossing markers");
        WaterCrossingChartMarker crossing = result.profileWaterCrossingMarkers.stream()
            .filter(marker -> marker.strategy() == WaterCrossingStrategy.LONG_BRIDGE)
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected LONG_BRIDGE classification for wide river"));
        assertTrue(crossing.endStation() - crossing.startStation() >= 60.0 - 1e-6,
            "long bridge crossing span must meet threshold");
        return crossing;
    }

    private static void assertUniformInteriorPierSpacing(
            RoadGenerationResult result,
            double crossingStart,
            double crossingEnd) {
        List<Integer> pierCenters = interiorPierCenters(result, crossingStart, crossingEnd);
        assertTrue(pierCenters.size() >= 3,
            "long bridge must place multiple interior piers, got " + pierCenters);

        List<Double> spacings = new ArrayList<>();
        for (int i = 1; i < pierCenters.size(); i++) {
            spacings.add(pierCenters.get(i) - pierCenters.get(i - 1).doubleValue());
        }
        double minSpacing = spacings.stream().min(Comparator.naturalOrder()).orElse(0.0);
        double maxSpacing = spacings.stream().max(Comparator.naturalOrder()).orElse(0.0);
        assertTrue(maxSpacing - minSpacing < 2.0,
            "pier spacing must stay uniform across path segment boundaries, spacings=" + spacings);
        assertTrue(minSpacing >= 10.0,
            "long bridge pier spacing should honor widened spacing, min=" + minSpacing);
    }

    private static List<Integer> interiorPierCenters(
            RoadGenerationResult result,
            double crossingStart,
            double crossingEnd) {
        int deckY = crossingDeckElevation(result, crossingStart, crossingEnd);
        Map<Integer, Integer> minYByColumn = new HashMap<>();
        Map<Integer, Integer> maxYByColumn = new HashMap<>();
        for (BlockPos pos : result.bridgeBlocks) {
            if (pos.getX() + 0.5 < crossingStart || pos.getX() + 0.5 > crossingEnd) {
                continue;
            }
            minYByColumn.merge(pos.getX(), pos.getY(), Math::min);
            maxYByColumn.merge(pos.getX(), pos.getY(), Math::max);
        }
        List<Integer> pierColumns = minYByColumn.keySet().stream()
            .filter(x -> x > crossingStart + 2.0 && x < crossingEnd - 2.0)
            .filter(x -> maxYByColumn.get(x) - minYByColumn.get(x) + 1 >= 5)
            .sorted()
            .toList();
        return pierRuns(pierColumns)
            .stream()
            .filter(run -> run.size() <= 4)
            .map(LongBridgeMultiSegmentIntegrationTest::clusterMedian)
            .toList();
    }

    private static List<List<Integer>> pierRuns(List<Integer> sortedColumns) {
        if (sortedColumns.isEmpty()) {
            return List.of();
        }
        List<List<Integer>> runs = new ArrayList<>();
        List<Integer> run = new ArrayList<>();
        run.add(sortedColumns.getFirst());
        for (int i = 1; i < sortedColumns.size(); i++) {
            int x = sortedColumns.get(i);
            if (x - run.getLast() <= 4) {
                run.add(x);
            } else {
                runs.add(run);
                run = new ArrayList<>();
                run.add(x);
            }
        }
        runs.add(run);
        return runs;
    }

    private static int crossingDeckElevation(
            RoadGenerationResult result,
            double crossingStart,
            double crossingEnd) {
        return result.roadBlocks.stream()
            .filter(pos -> pos.getX() + 0.5 >= crossingStart && pos.getX() + 0.5 <= crossingEnd)
            .mapToInt(BlockPos::getY)
            .max()
            .orElseGet(() -> result.roadBlocks.stream().mapToInt(BlockPos::getY).max().orElse(64));
    }

    private static int clusterMedian(List<Integer> cluster) {
        return cluster.get(cluster.size() / 2);
    }
}
