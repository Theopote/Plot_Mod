package com.plot.plugin.road.pipeline.e2e;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.benchmark.RoadBenchmarkHarness;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E: tight S-curves and crossing arcs project multiple cross-sections onto overlapping
 * world columns; rays must not cross-merge and batter must stay continuous.
 */
class RoadSlopeOverlapCurvedE2ETest {

    @Test
    void sCurveMountainCutKeepsSlopeIntegrityWithoutCrossMerge() {
        RoadNetwork network = sCurveMountainRoad("overlap-s");
        RoadGenerationResult edge = preview(network, slopeConfig(), new FlatTerrainSampler(92));

        assertEquals(0, edge.missingRoadSurfaceCount);
        assertEquals(0, edge.orphanSlopePillarCount);
        assertTrue(edge.cutVolume > 0);
        assertTrue(edge.brokenSlopeSurfaceCount <= 4,
            "S-curve overlap region should not leave many broken batter columns, count="
                + edge.brokenSlopeSurfaceCount);

        int verticalWalls = countVerticalEmbankmentWalls(edge, 8, 24, -8, 8, 70, 88);
        assertTrue(verticalWalls <= 6,
            "overlapping cross-section columns must not form long vertical fill walls, count="
                + verticalWalls);
    }

    @Test
    void crossingArcsOnMountainShareColumnsWithoutOrphanSlopes() {
        RoadNetwork network = crossingArcRoads();
        RoadBenchmarkHarness.PreviewRun run = RoadBenchmarkHarness.generatePreview(
            network, generator(slopeConfig()), new FlatTerrainSampler(90));

        assertEquals(2, run.edgeResults().size(), "expected two crossing arc edges");
        for (RoadGenerationResult edge : run.edgeResults().values()) {
            assertEquals(0, edge.missingRoadSurfaceCount);
            assertEquals(0, edge.orphanSlopePillarCount);
            assertTrue(edge.cutVolume > 0, "each arc should cut high ground");
            assertTrue(edge.brokenSlopeSurfaceCount <= 12,
                "each arc should keep broken batter columns bounded, count="
                    + edge.brokenSlopeSurfaceCount);
        }

        RoadGenerationResult combined = run.aggregate();
        int overlapWalls = countVerticalEmbankmentWalls(combined, 14, 28, 14, 28, 66, 88);
        assertTrue(overlapWalls <= 4,
            "crossing arc overlap region must not accumulate vertical fill walls, count=" + overlapWalls);
    }

    private static RoadNetwork sCurveMountainRoad(String id) {
        RoadNetwork network = new RoadNetwork();
        Road road = network.createRoad(id);
        road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        road.setIncludeSlopeBatter(true);
        road.setMaxSlope(40.0f);
        road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, 64),
            PointOfVerticalIntersection.of(50, 64))));

        List<Vec2d> path = List.of(
            new Vec2d(0, 0),
            new Vec2d(8, 6),
            new Vec2d(16, -2),
            new Vec2d(24, 8),
            new Vec2d(32, 2),
            new Vec2d(40, 0));
        RoadNode start = network.createNode(path.getFirst());
        RoadNode end = network.createNode(path.getLast());
        network.createEdge(start.getId(), end.getId(), path, road.getId());
        return network;
    }

    private static RoadNetwork crossingArcRoads() {
        RoadNetwork network = new RoadNetwork();

        Road roadA = network.createRoad("arc-a");
        roadA.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        roadA.setIncludeSlopeBatter(true);
        roadA.setMaxSlope(40.0f);
        roadA.setVerticalAlignment(flatProfile(64));

        Road roadB = network.createRoad("arc-b");
        roadB.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
        roadB.setIncludeSlopeBatter(true);
        roadB.setMaxSlope(40.0f);
        roadB.setVerticalAlignment(flatProfile(64));

        List<Vec2d> arcA = sampleArc(new Vec2d(10, 22), 14, -Math.PI / 3, Math.PI / 6, 18);
        List<Vec2d> arcB = sampleArc(new Vec2d(22, 10), 14, Math.PI * 2 / 3, Math.PI * 7 / 6, 18);

        RoadNode aStart = network.createNode(arcA.getFirst());
        RoadNode aEnd = network.createNode(arcA.getLast());
        network.createEdge(aStart.getId(), aEnd.getId(), arcA, roadA.getId());

        RoadNode bStart = network.createNode(arcB.getFirst());
        RoadNode bEnd = network.createNode(arcB.getLast());
        network.createEdge(bStart.getId(), bEnd.getId(), arcB, roadB.getId());
        return network;
    }

    private static List<Vec2d> sampleArc(Vec2d center, double radius, double startAngle, double endAngle, int steps) {
        List<Vec2d> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double angle = startAngle + (endAngle - startAngle) * t;
            points.add(new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)));
        }
        return points;
    }

    private static RoadVerticalAlignment flatProfile(int elevation) {
        return new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, elevation),
            PointOfVerticalIntersection.of(80, elevation)));
    }

    private static int countVerticalEmbankmentWalls(
            RoadGenerationResult edge,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minY,
            int maxY) {
        int walls = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isVerticalEmbankmentWall(edge, x, z, minY, maxY)) {
                    walls++;
                }
            }
        }
        return walls;
    }

    private static boolean isVerticalEmbankmentWall(
            RoadGenerationResult edge,
            int x,
            int z,
            int minY,
            int maxY) {
        int run = 0;
        boolean hasBridgePier = false;
        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            RoadSolidLayer layer = edge.placementLayers.get(pos);
            if (layer == RoadSolidLayer.BRIDGE) {
                hasBridgePier = true;
            }
            if (layer == RoadSolidLayer.SHOULDER || layer == RoadSolidLayer.SUBGRADE) {
                run++;
            } else {
                run = 0;
            }
            if (run >= 4) {
                return !hasBridgePier;
            }
        }
        return false;
    }

    private static RoadSystemConfig slopeConfig() {
        RoadSystemConfig config = new RoadSystemConfig("overlap-slope-e2e");
        config.setRoadWidth(5);
        config.setIncludeShoulder(true);
        config.setIncludeSlopeBatter(true);
        config.setFillSlopeRatio(1.0f);
        config.setCutSlopeRatio(1.0f);
        config.setMaxSlope(40.0f);
        return config;
    }

    private static RoadGenerator generator(RoadSystemConfig config) {
        return new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
    }

    private static RoadGenerationResult preview(
            RoadNetwork network,
            RoadSystemConfig config,
            TerrainSampler terrain) {
        RoadBenchmarkHarness.PreviewRun run = RoadBenchmarkHarness.generatePreview(
            network, generator(config), terrain);
        assertEquals(1, run.edgeResults().size());
        return run.edgeResults().values().iterator().next();
    }
}
