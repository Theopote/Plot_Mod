package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.terrain.FlatTerrainSampler;
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
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end final world-state checks for grade-separated junctions.
 */
class RoadJunctionFinalWorldStateIntegrationTest {

    @Test
    void gradeSeparatedCrossPreviewKeepsDeckAndZeroPostMergeErrors() {
        RoadSystemConfig config = new RoadSystemConfig("final-world-state");
        config.setTerrainStyle(RoadTerrainStyle.SMOOTH);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        config.setDefaultCrossingClearance(6.0);
        config.setMaxSlope(100.0f);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);

        RoadNetwork network = gradeSeparatedCrossNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        TerrainSampler terrain = steppedTerrain(64, 72);
        RoadNetworkGenerator.PreviewResult preview = networkGenerator.generatePreview(network, terrain);
        assertNotNull(preview.aggregate());
        assertFalse(preview.aggregate().placementRecords.isEmpty());

        RoadJunctionGenerator.JunctionBlocks junctionBlocks =
            preview.networkResult().getJunctionResults().get(junction.getId());
        assertNotNull(junctionBlocks, "junction solids must be stored on network result");
        JunctionValidationManifest manifest = junctionBlocks.validationManifest();
        assertNotNull(manifest);
        assertFalse(manifest.surfaceExpectations().isEmpty(), "manifest must capture stamped surface cells");
        assertTrue(manifest.hasGradeSeparation(), "grade-separated node must record elevatedDeckY");

        JunctionConstructionReport report = preview.networkResult().getJunctionReports().get(junction.getId());
        assertNotNull(report);
        assertEquals(0, report.postMergeMissingSurfaceColumns(),
            "post-merge must not false-positive missing surface on blended elevations");
        assertEquals(0, report.postMergeProtectedLosses(),
            "elevated deck must not be reported as protected loss");
        assertEquals(0, report.crossLayerLeaks(),
            "underpass tunnel air must not leak into elevated deck volume");

        for (JunctionSurfaceExpectation expectation : manifest.surfaceExpectations()) {
            BlockPos pos = expectation.position();
            BlockRecord record = preview.aggregate().placementRecords.get(pos);
            assertNotNull(record, "expected surface missing at " + pos);
            assertFalse("minecraft:air".equals(record.newBlockId), "surface must be solid at " + pos);
        }

        for (BlockPos protectedPos : manifest.expectedProtectedBlocks()) {
            BlockRecord record = preview.aggregate().placementRecords.get(protectedPos);
            if (record == null) {
                continue;
            }
            assertFalse(
                "minecraft:air".equals(record.newBlockId)
                    && preview.aggregate().placementLayers.get(protectedPos) == RoadSolidLayer.TUNNEL,
                "protected elevated structure must not be tunnel-air at " + protectedPos);
        }

        assertElevatedDeckContinuousNearOrigin(preview.aggregate(), manifest.elevatedDeckY());
    }

    @Test
    void atGradeTJunctionManifestMatchesAggregateSurface() {
        RoadSystemConfig config = new RoadSystemConfig("final-t-junction");
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);

        RoadGenerator generator = new RoadGenerator(
            config,
            IdentityCoordinateService.INSTANCE,
            BlockProjectionHandler.getInstance());
        RoadNetworkGenerator networkGenerator = new RoadNetworkGenerator(generator);

        RoadNetwork network = tJunctionNetwork();
        RoadNode junction = network.getNodes().values().stream()
            .filter(node -> node.getDegree() >= 3)
            .findFirst()
            .orElseThrow();

        RoadNetworkGenerator.PreviewResult preview =
            networkGenerator.generatePreview(network, new FlatTerrainSampler(68));

        RoadJunctionGenerator.JunctionBlocks junctionBlocks =
            preview.networkResult().getJunctionResults().get(junction.getId());
        assertNotNull(junctionBlocks);
        JunctionValidationManifest manifest = junctionBlocks.validationManifest();
        assertFalse(manifest.surfaceExpectations().isEmpty());

        JunctionConstructionReport report = preview.networkResult().getJunctionReports().get(junction.getId());
        assertNotNull(report);
        assertEquals(0, report.postMergeMissingSurfaceColumns());

        PlacementRecordColumnIndex index = PlacementRecordColumnIndex.build(preview.aggregate());
        JunctionFinalVoxelValidator.PostMergeReport postMerge =
            JunctionFinalVoxelValidator.validateNode(
                manifest,
                preview.aggregate(),
                index,
                IdentityCoordinateService.INSTANCE);
        assertEquals(0, postMerge.missingSurfaceColumns());
        assertEquals(0, postMerge.protectedLosses());
        assertEquals(0, postMerge.crossLayerLeaks());
    }

    private static void assertElevatedDeckContinuousNearOrigin(
            RoadGenerationResult aggregate,
            Integer elevatedDeckY) {
        if (elevatedDeckY == null) {
            return;
        }
        int solidDeckCells = 0;
        int minDeckY = Integer.MAX_VALUE;
        int maxDeckY = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockRecord> entry : aggregate.placementRecords.entrySet()) {
            BlockPos pos = entry.getKey();
            if (Math.abs(pos.getX()) > 6 || Math.abs(pos.getZ()) > 6) {
                continue;
            }
            BlockRecord record = entry.getValue();
            RoadSolidLayer layer = aggregate.placementLayers.get(pos);
            if (record == null || "minecraft:air".equals(record.newBlockId)) {
                continue;
            }
            if (layer != RoadSolidLayer.ROAD && layer != RoadSolidLayer.BRIDGE) {
                continue;
            }
            if (pos.getY() < elevatedDeckY - 1) {
                continue;
            }
            solidDeckCells++;
            minDeckY = Math.min(minDeckY, pos.getY());
            maxDeckY = Math.max(maxDeckY, pos.getY());
        }
        assertTrue(
            solidDeckCells > 0,
            "elevated deck must have solid ROAD/BRIDGE near junction; elevatedDeckY="
                + elevatedDeckY + " seenY=[" + minDeckY + "," + maxDeckY + "]");
        assertTrue(
            maxDeckY - minDeckY <= 2,
            "elevated deck near junction must stay nearly level, got Y span "
                + minDeckY + ".." + maxDeckY);
    }

    private static TerrainSampler steppedTerrain(int underpassY, int overpassY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                // Overpass corridor (east-west) sits higher so clearance stays clear.
                if (Math.abs(point.y) <= 3.0) {
                    return overpassY;
                }
                return underpassY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                Vec2d plan = new Vec2d(x, z);
                return y <= sampleSurfaceY(plan);
            }
        };
    }

    private static RoadNetwork gradeSeparatedCrossNetwork() {
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
        return network;
    }

    private static RoadNetwork tJunctionNetwork() {
        RoadNetwork network = new RoadNetwork();
        Road main = network.createRoad("main");
        Road spur = network.createRoad("spur");
        RoadNode junction = network.createNode(new Vec2d(0, 0));
        junction.setManualElevation(68.0);
        RoadNode north = network.createNode(new Vec2d(0, 40));
        RoadNode south = network.createNode(new Vec2d(0, -40));
        RoadNode east = network.createNode(new Vec2d(40, 0));
        network.createEdge(junction.getId(), north.getId(),
            List.of(junction.getPosition(), north.getPosition()), main.getId());
        network.createEdge(junction.getId(), south.getId(),
            List.of(junction.getPosition(), south.getPosition()), main.getId());
        network.createEdge(junction.getId(), east.getId(),
            List.of(junction.getPosition(), east.getPosition()), spur.getId());
        return network;
    }
}
