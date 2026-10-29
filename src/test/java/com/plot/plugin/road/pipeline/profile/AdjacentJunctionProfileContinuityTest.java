package com.plot.plugin.road.pipeline.profile;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjacentJunctionProfileContinuityTest {

    @Test
    void softAdjacentJunctionsDoNotLeaveBlockingCliffOnSteppedTerrain() {
        RoadNetwork network = twoAdjacentTJunctions();
        RoadSystemConfig config = new RoadSystemConfig("adj-soft");
        config.setMaxSlope(12.0f);
        for (Road road : network.getRoads().values()) {
            road.setMaxSlope(12.0f);
        }

        TerrainSampler stepped = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return point.x < 20 ? 80 : 40;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };

        RoadNetworkGenerator generator = new RoadNetworkGenerator(new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance()));
        RoadNetworkGenerator.PreviewResult preview = generator.generatePreview(network, stepped);

        List<RoadNode> junctions = preview.profileNetwork().getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .toList();
        assertTrue(junctions.size() >= 2, "expected two junctions");

        Integer y0 = preview.nodeElevations().get(junctions.get(0).getId());
        Integer y1 = preview.nodeElevations().get(junctions.get(1).getId());
        assertTrue(y0 != null && y1 != null);
        // Soft continuity must pull the pair into a slope-feasible band for ~40 blocks @ 12%.
        assertTrue(Math.abs(y0 - y1) <= 6,
            "soft adjacent junctions must not cliff, y=" + y0 + "," + y1);

        assertTrue(preview.networkResult().getAdjacentJunctionProfileViolations().stream()
                .noneMatch(AdjacentJunctionProfileContinuity.Violation::blocking),
            "blocking adjacent-junction cliffs must be absent after continuity");

        assertTrue(connectorRoadBandIsSmooth(preview),
            "connector ROAD placements between junctions must not jump more than 2 blocks between neighbors");
    }

    @Test
    void conflictingHardLocksAreReportedWithoutBeingSilentlyMoved() {
        RoadNetwork network = twoAdjacentTJunctions();
        RoadNode left = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3 && node.getPosition().x < 20)
            .findFirst()
            .orElseThrow();
        RoadNode right = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3 && node.getPosition().x > 20)
            .findFirst()
            .orElseThrow();
        left.setManualElevation(80.0);
        right.setManualElevation(40.0);

        RoadSystemConfig config = new RoadSystemConfig("adj-hard");
        config.setMaxSlope(8.0f);
        var violations = AdjacentJunctionProfileContinuity.find(
            network,
            Map.of(left.getId(), 80, right.getId(), 40),
            config);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().noneMatch(AdjacentJunctionProfileContinuity.Violation::blocking),
            "hard lock cliffs warn but do not silently rewrite anchors");
    }

    private static boolean connectorRoadBandIsSmooth(RoadNetworkGenerator.PreviewResult preview) {
        int previousY = Integer.MIN_VALUE;
        int previousX = Integer.MIN_VALUE;
        for (int x = 2; x <= 38; x++) {
            Integer y = null;
            for (var edgeResult : preview.edgeResults().values()) {
                for (var entry : edgeResult.placementRecords.entrySet()) {
                    var pos = entry.getKey();
                    if (pos.getX() != x || Math.abs(pos.getZ()) > 2) {
                        continue;
                    }
                    if (edgeResult.placementLayers.get(pos) != RoadSolidLayer.ROAD) {
                        continue;
                    }
                    if ("minecraft:air".equals(entry.getValue().newBlockId)) {
                        continue;
                    }
                    y = pos.getY();
                    break;
                }
                if (y != null) {
                    break;
                }
            }
            if (y == null) {
                continue;
            }
            if (previousY != Integer.MIN_VALUE && previousX == x - 1) {
                if (Math.abs(y - previousY) > 2) {
                    return false;
                }
            }
            previousY = y;
            previousX = x;
        }
        return previousY != Integer.MIN_VALUE;
    }

    private static RoadNetwork twoAdjacentTJunctions() {
        RoadNetwork network = new RoadNetwork();
        Road main = network.createRoad("main");
        Road spurA = network.createRoad("spurA");
        Road spurB = network.createRoad("spurB");

        RoadNode j1 = network.createNode(new Vec2d(0, 0));
        RoadNode j2 = network.createNode(new Vec2d(40, 0));
        RoadNode west = network.createNode(new Vec2d(-24, 0));
        RoadNode east = network.createNode(new Vec2d(64, 0));
        RoadNode north1 = network.createNode(new Vec2d(0, 24));
        RoadNode north2 = network.createNode(new Vec2d(40, 24));

        network.createEdge(west.getId(), j1.getId(),
            List.of(west.getPosition(), j1.getPosition()), main.getId());
        network.createEdge(j1.getId(), j2.getId(),
            List.of(j1.getPosition(), j2.getPosition()), main.getId());
        network.createEdge(j2.getId(), east.getId(),
            List.of(j2.getPosition(), east.getPosition()), main.getId());
        network.createEdge(j1.getId(), north1.getId(),
            List.of(j1.getPosition(), north1.getPosition()), spurA.getId());
        network.createEdge(j2.getId(), north2.getId(),
            List.of(j2.getPosition(), north2.getPosition()), spurB.getId());
        return network;
    }
}
