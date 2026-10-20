package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.vertical.PointOfVerticalIntersection;
import com.plot.plugin.road.vertical.RoadVerticalAlignment;
import com.plot.plugin.road.vertical.RoadVerticalMode;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossingPavementContinuityTest {
    @ParameterizedTest
    @CsvSource({"false, 80, 12, 0, false", "true, 80, 12, 0, false", "false, 64, 12, 0, false", "true, 64, 12, 0, false", "true, 80, 6, 0, false", "true, 72, 6, 0, false", "true, 68, 6, 0, false", "true, 80, 6, 30, false", "true, 80, 6, 45, false", "false, 80, 12, 30, false", "true, 80, 6, 0, true", "true, 80, 6, 30, true", "false, -1, 12, 0, false"})
    void bothRoadsHaveContinuousPavementAndShouldersThroughTheJunction(boolean separated, int terrainY, int clearance, int angle, boolean sloped) {
        RoadSystemConfig config = new RoadSystemConfig("crossing-pavement");
        config.setRoadWidth(7);
        config.setIncludeShoulder(true);
        config.setShoulderWidth(2);
        config.setIncludeSidewalk(false);
        config.setMaxSlope(100);
        RoadNetwork network = new RoadNetwork();
        Road ns = network.createRoad("ns");
        Road ew = network.createRoad("ew");
        var center = network.createNode(new Vec2d(0, 0));
        center.setManualElevation(64.0);
        var north = network.createNode(new Vec2d(0, 40));
        var south = network.createNode(new Vec2d(0, -40));
        var east = network.createNode(new Vec2d(40, 0));
        var west = network.createNode(new Vec2d(-40, 0));
        network.createEdge(south.getId(), center.getId(), List.of(south.getPosition(), center.getPosition()), ns.getId());
        network.createEdge(center.getId(), north.getId(), List.of(center.getPosition(), north.getPosition()), ns.getId());
        network.createEdge(west.getId(), center.getId(), List.of(west.getPosition(), center.getPosition()), ew.getId());
        network.createEdge(center.getId(), east.getId(), List.of(center.getPosition(), east.getPosition()), ew.getId());
        int upperY = separated ? 64 + clearance : 64;
        for (Road road : List.of(ns, ew)) {
            int y = road == ns ? 64 : upperY;
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
                PointOfVerticalIntersection.of(0, y), PointOfVerticalIntersection.of(80, y))));
        }
        if (sloped) ew.setVerticalAlignment(new RoadVerticalAlignment(List.of(
            PointOfVerticalIntersection.of(0, upperY - 6), PointOfVerticalIntersection.of(40, upperY),
            PointOfVerticalIntersection.of(80, upperY + 6))));
        if (separated) network.setNodeGradeSeparation(center.getId(), true, ew.getId(), (double) clearance);
        double radians = Math.toRadians(angle);
        // Build the same junction away from the block grid axes.
        if (angle != 0) {
            for (var node : network.getNodes().values()) node.setPosition(rotate(node.getPosition(), radians));
            for (var edge : network.getEdges().values()) edge.setCenterlinePoints(edge.getCenterlinePoints().stream()
                .map(point -> rotate(point, radians)).toList());
        }
        RoadGenerator generator = new RoadGenerator(config, IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        networkGenerator.setActiveManualProfileRoadIds(Set.of(ns.getId(), ew.getId()));
        com.plot.core.terrain.TerrainSampler terrain = terrainY >= 0 ? new FlatTerrainSampler(terrainY)
            : new com.plot.core.terrain.TerrainSampler() {
                private int ground(double x, double z) { return Math.abs(x) >= 5 && Math.abs(z) <= 3 ? 50 : 80; }
                public int sampleSurfaceY(Vec2d point) { return ground(point.x, point.y); }
                public boolean isSolidBlock(int x, int y, int z) { return y <= ground(x, z); }
            };
        var preview = networkGenerator.generatePreview(network, terrain);
        RoadGenerationResult result = preview.aggregate();
        List<BlockPos> missing = new ArrayList<>();
        for (int along = -24; along <= 24; along++) {
            for (int lateral = (angle == 0 ? -5 : -4); lateral <= (angle == 0 ? 5 : 4); lateral++) {
                for (BlockPos pos : List.of(new BlockPos(lateral, 64, along), new BlockPos(along, upperY, lateral))) {
                    if (sloped && pos.getY() == upperY) continue;
                    pos = rotatedBlock(pos, radians);
                    var record = result.placementRecords.get(pos);
                    if (record == null || "minecraft:air".equals(record.newBlockId)
                            || !Set.of(com.plot.plugin.road.solid.RoadSolidLayer.ROAD,
                                com.plot.plugin.road.solid.RoadSolidLayer.SHOULDER,
                                com.plot.plugin.road.solid.RoadSolidLayer.MARKING).contains(result.placementLayers.get(pos))) {
                        missing.add(pos);
                    }
                }
            }
        }
        if (sloped) {
            for (var edge : network.getEdges().values()) {
                if (!ew.getId().equals(edge.getRoadId())) continue;
                for (var entry : preview.edgeResults().get(edge.getId()).placementRecords.entrySet()) {
                    var pos = entry.getKey();
                    var layer = preview.edgeResults().get(edge.getId()).placementLayers.get(pos);
                    if (Math.abs(pos.getX()) > 12 || Math.abs(pos.getZ()) > 12
                            || (layer != com.plot.plugin.road.solid.RoadSolidLayer.ROAD
                                && layer != com.plot.plugin.road.solid.RoadSolidLayer.SHOULDER)) continue;
                    var finalRecord = result.placementRecords.get(pos);
                    if (finalRecord == null || "minecraft:air".equals(finalRecord.newBlockId)) missing.add(pos);
                }
            }
        }
        if (sloped) {
            var upperSurfaces = new java.util.HashMap<ColumnKey, BlockPos>();
            for (var edge : network.getEdges().values()) {
                if (!ew.getId().equals(edge.getRoadId())) continue;
                var edgeResult = preview.edgeResults().get(edge.getId());
                edgeResult.placementRecords.forEach((pos, record) -> {
                    if (Math.abs(pos.getX()) <= 12 && Math.abs(pos.getZ()) <= 12
                            && edgeResult.placementLayers.get(pos) == com.plot.plugin.road.solid.RoadSolidLayer.ROAD) {
                        upperSurfaces.merge(new ColumnKey(pos.getX(), pos.getZ()), pos,
                            (left, right) -> left.getY() >= right.getY() ? left : right);
                    }
                });
            }
            for (var surface : upperSurfaces.values()) {
                for (int dy = 1; dy <= 2; dy++) {
                    var pos = surface.up(dy);
                    var record = result.placementRecords.get(pos);
                    assertTrue(record != null ? "minecraft:air".equals(record.newBlockId)
                        : !terrain.isSolidBlock(pos.getX(), pos.getY(), pos.getZ()),
                        "terrain left above sloping upper road: " + pos);
                }
            }
        }
        List<BlockPos> obstructions = new ArrayList<>();
        for (int along = -12; along <= 12; along++) {
            for (int lateral = -3; lateral <= 3; lateral++) {
                for (int dy = 1; dy <= (angle == 0 ? 4 : 3); dy++) {
                    for (var pos : List.of(new BlockPos(lateral, 64 + dy, along),
                            new BlockPos(along, upperY + dy, lateral))) {
                        if (sloped) continue;
                        pos = rotatedBlock(pos, radians);
                        var record = result.placementRecords.get(pos);
                        if (record != null ? !"minecraft:air".equals(record.newBlockId) : terrain.isSolidBlock(pos.getX(), pos.getY(), pos.getZ())) {
                            obstructions.add(pos);
                        }
                    }
                }
            }
        }
        assertTrue(obstructions.isEmpty() && missing.isEmpty(), "missing=" + missing.stream().limit(12).map(pos -> pos + "=" + result.placementLayers.get(pos) + ":" + (result.placementRecords.get(pos) != null ? result.placementRecords.get(pos).newBlockId : "terrain")).toList()
            + " headroom=" + obstructions.stream().limit(20).map(pos -> pos + "=" + result.placementLayers.get(pos) + ":" + (result.placementRecords.get(pos) != null ? result.placementRecords.get(pos).newBlockId : "terrain")).toList());
        assertTrue(missing.isEmpty(), "missing pavement/shoulder cells: " + missing.size() + " "
            + missing.stream().limit(20).map(pos -> pos + "=" + result.placementLayers.get(pos)
                + ":" + (result.placementRecords.get(pos) != null
                    ? result.placementRecords.get(pos).newBlockId : "missing")
                + " edges=" + preview.edgeResults().values().stream().map(edge ->
                    edge.placementLayers.get(pos) + ":" + (edge.placementRecords.get(pos) != null
                        ? edge.placementRecords.get(pos).newBlockId : "missing")).toList()).toList());
    }

    private static Vec2d rotate(Vec2d point, double angle) {
        return new Vec2d(point.x * Math.cos(angle) - point.y * Math.sin(angle),
            point.x * Math.sin(angle) + point.y * Math.cos(angle));
    }

    private static BlockPos rotatedBlock(BlockPos pos, double angle) {
        Vec2d point = rotate(new Vec2d(pos.getX(), pos.getZ()), angle);
        return new BlockPos((int) Math.round(point.x), pos.getY(), (int) Math.round(point.y));
    }
}
