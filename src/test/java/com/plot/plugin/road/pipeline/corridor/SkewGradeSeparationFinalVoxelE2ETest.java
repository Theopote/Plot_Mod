package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadJunctionGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skew grade-separated crossing: upper pavement, upper underside, lower pavement, underpass clearance.
 */
class SkewGradeSeparationFinalVoxelE2ETest {

    @Test
    void skewedGradeSepKeepsUpperDeckUndersideAndClearsLowerShaft() {
        RoadSystemConfig config = new RoadSystemConfig("skew-gs-e2e");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCrossingClearance(6.0);
        config.setMaxSlope(100.0f);

        RoadNetwork network = skewedGradeSeparatedCross(Math.toRadians(30));
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        TerrainSampler terrain = hillTerrain(64, 78);
        RoadNetworkGenerator.PreviewResult preview = networkGenerator.generatePreview(network, terrain);
        RoadGenerationResult aggregate = preview.aggregate();
        assertNotNull(aggregate);

        Integer deckY = RoadJunctionGenerator.resolveElevatedDeckY(
            junction, 64, network, config);
        assertNotNull(deckY);
        assertTrue(deckY > 64, "elevated deck must sit above underpass");

        int upperRoad = 0;
        int upperUndersideIntact = 0;
        int lowerRoad = 0;
        int lowerShaftAir = 0;
        int upperUndersideCleared = 0;

        for (var entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX()) > 10 || Math.abs(pos.getZ()) > 10) {
                continue;
            }
            BlockRecord record = entry.getValue();
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (record == null) {
                continue;
            }
            boolean air = "minecraft:air".equals(record.newBlockId);
            if (pos.getY() == deckY && !air
                    && (layer == RoadSolidLayer.ROAD || layer == RoadSolidLayer.BRIDGE)) {
                upperRoad++;
            }
            if (pos.getY() == deckY - 1 && !air
                    && (layer == RoadSolidLayer.BRIDGE || layer == RoadSolidLayer.SUBGRADE
                        || layer == RoadSolidLayer.ROAD)) {
                upperUndersideIntact++;
            }
            if (pos.getY() == deckY - 1 && air
                    && aggregate.placementMaterialPriorities.getOrDefault(
                        pos, RoadSolidMaterialPriority.DEFAULT) == RoadSolidMaterialPriority.CLEARANCE) {
                upperUndersideCleared++;
            }
            if (pos.getY() == 64 && !air
                    && (layer == RoadSolidLayer.ROAD || layer == RoadSolidLayer.SHOULDER
                        || layer == RoadSolidLayer.MARKING)) {
                lowerRoad++;
            }
            if (pos.getY() > 64 && pos.getY() < deckY && air) {
                lowerShaftAir++;
            }
        }

        assertTrue(upperRoad > 0, "skew GS must stamp elevated ROAD/BRIDGE near junction");
        assertTrue(lowerRoad > 0, "skew GS must stamp underpass ROAD near junction");
        assertTrue(lowerShaftAir > 0, "underpass shaft must clear air up toward the deck");
        assertTrue(upperUndersideIntact > 0,
            "elevated underside (deckY-1) must exist; intact=" + upperUndersideIntact);
        assertTrue(upperUndersideCleared == 0,
            "CLEARANCE must not punch elevated underside; cleared=" + upperUndersideCleared
                + " intact=" + upperUndersideIntact);
        assertTrue(upperUndersideIntact * 2 >= upperRoad,
            "most elevated pavement columns should keep continuous underside; "
                + "pavement=" + upperRoad + " underside=" + upperUndersideIntact);
    }

    private static RoadNetwork skewedGradeSeparatedCross(double radians) {
        RoadNetwork network = new RoadNetwork();
        Road underpass = network.createRoad("underpass");
        Road overpass = network.createRoad("overpass");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 48));
        RoadNode south = network.createNode(new Vec2d(0, -48));
        RoadNode east = network.createNode(new Vec2d(48, 0));
        RoadNode west = network.createNode(new Vec2d(-48, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), underpass.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), underpass.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), overpass.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), overpass.getId());
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, overpass.getId(), 6.0));
        for (var node : network.getNodes().values()) {
            node.setPosition(rotate(node.getPosition(), radians));
        }
        for (var edge : network.getEdges().values()) {
            edge.setCenterlinePoints(edge.getCenterlinePoints().stream()
                .map(p -> rotate(p, radians))
                .toList());
        }
        return network;
    }

    private static Vec2d rotate(Vec2d point, double angle) {
        return new Vec2d(
            point.x * Math.cos(angle) - point.y * Math.sin(angle),
            point.x * Math.sin(angle) + point.y * Math.cos(angle));
    }

    private static TerrainSampler hillTerrain(int floorY, int hillY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return Math.hypot(point.x, point.y) < 8 ? hillY : floorY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= sampleSurfaceY(new Vec2d(x, z));
            }
        };
    }
}
