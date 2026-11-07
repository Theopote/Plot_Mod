package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.TerrainSampler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadGenerator;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 立交材质隔离：最终 BlockPos 上，下层路口不得被更宽上层道路材质覆盖。
 */
class JunctionMaterialPolicyGradeSepVoxelTest {

    @Test
    void widerElevatedRoadMustNotReplaceUnderpassJunctionBlocks() {
        assertGradeSepIsolation(
            /*underpassWidth*/ 5, "minecraft:white_concrete",
            /*overpassWidth*/ 11, "minecraft:oak_planks");
    }

    @Test
    void widerUnderpassKeepsOwnMaterialAgainstNarrowElevated() {
        assertGradeSepIsolation(
            /*underpassWidth*/ 11, "minecraft:white_concrete",
            /*overpassWidth*/ 5, "minecraft:oak_planks");
    }

    private static void assertGradeSepIsolation(
            int underpassWidth,
            String underpassMaterial,
            int overpassWidth,
            String overpassMaterial) {
        RoadSystemConfig config = new RoadSystemConfig("gs-material-voxel");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCrossingClearance(6.0);
        config.setMaxSlope(100.0f);
        config.setSelectedMaterial(underpassMaterial);
        config.setDefaultCornerRadius(2.0f);

        RoadNetwork network = new RoadNetwork();
        Road underpass = network.createRoad("underpass");
        underpass.setWidth(underpassWidth);
        underpass.setMaterial(underpassMaterial);
        Road overpass = network.createRoad("overpass");
        overpass.setWidth(overpassWidth);
        overpass.setMaterial(overpassMaterial);

        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(64.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        RoadNode west = network.createNode(new Vec2d(-40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), underpass.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), underpass.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), overpass.getId());
        network.createEdge(junction.getId(), west.getId(),
            List.of(junction.getPosition(), west.getPosition()), overpass.getId());
        assertTrue(network.setNodeGradeSeparation(junction.getId(), true, overpass.getId(), 6.0));

        RoadGenerator generator = new RoadGenerator(
            config, IdentityCoordinateService.INSTANCE, BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 64;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 64;
            }
        };
        RoadGenerationResult aggregate = networkGenerator.generatePreview(network, terrain).aggregate();
        assertNotNull(aggregate);

        int underpassPlaza = 0;
        int elevatedLeak = 0;
        for (var entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX()) > 6 || Math.abs(pos.getZ()) > 6) {
                continue;
            }
            if (pos.getY() != 64) {
                continue;
            }
            BlockRecord record = entry.getValue();
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (layer != RoadSolidLayer.ROAD && layer != RoadSolidLayer.MARKING) {
                continue;
            }
            underpassPlaza++;
            if (overpassMaterial.equals(record.newBlockId)) {
                elevatedLeak++;
            }
        }
        assertTrue(underpassPlaza > 0, "underpass plaza must stamp ROAD near junction");
        assertFalse(elevatedLeak > 0,
            "elevated material must not appear on underpass plaza Y; leaks=" + elevatedLeak
                + " plaza=" + underpassPlaza);
    }
}
