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
    @CsvSource({"false, 80", "true, 80", "false, 64", "true, 64"})
    void bothRoadsHaveContinuousPavementAndShouldersThroughTheJunction(boolean separated, int terrainY) {
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
        int upperY = separated ? 76 : 64;
        for (Road road : List.of(ns, ew)) {
            int y = road == ns ? 64 : upperY;
            road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            road.setVerticalAlignment(new RoadVerticalAlignment(List.of(
                PointOfVerticalIntersection.of(0, y), PointOfVerticalIntersection.of(80, y))));
        }
        if (separated) network.setNodeGradeSeparation(center.getId(), true, ew.getId(), 12.0);
        RoadGenerator generator = new RoadGenerator(config, IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        networkGenerator.setActiveManualProfileRoadIds(Set.of(ns.getId(), ew.getId()));
        var preview = networkGenerator.generatePreview(network, new FlatTerrainSampler(terrainY));
        RoadGenerationResult result = preview.aggregate();
        List<BlockPos> missing = new ArrayList<>();
        for (int along = -24; along <= 24; along++) {
            for (int lateral = -5; lateral <= 5; lateral++) {
                for (BlockPos pos : List.of(new BlockPos(lateral, 64, along), new BlockPos(along, upperY, lateral))) {
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
        List<BlockPos> obstructions = new ArrayList<>();
        for (int along = -12; along <= 12; along++) {
            for (int lateral = -3; lateral <= 3; lateral++) {
                for (int dy = 1; dy <= 4; dy++) {
                    for (var pos : List.of(new BlockPos(lateral, 64 + dy, along),
                            new BlockPos(along, upperY + dy, lateral))) {
                        var record = result.placementRecords.get(pos);
                        if (record != null ? !"minecraft:air".equals(record.newBlockId) : pos.getY() <= terrainY) {
                            obstructions.add(pos);
                        }
                    }
                }
            }
        }
        assertTrue(obstructions.isEmpty(), "uncleared crossing headroom: " + obstructions.stream().limit(20).toList());
        assertTrue(missing.isEmpty(), "missing pavement/shoulder cells: " + missing.size() + " "
            + missing.stream().limit(20).map(pos -> pos + "=" + result.placementLayers.get(pos)
                + ":" + (result.placementRecords.get(pos) != null
                    ? result.placementRecords.get(pos).newBlockId : "missing")
                + " edges=" + preview.edgeResults().values().stream().map(edge ->
                    edge.placementLayers.get(pos) + ":" + (edge.placementRecords.get(pos) != null
                        ? edge.placementRecords.get(pos).newBlockId : "missing")).toList()).toList());
    }
}
