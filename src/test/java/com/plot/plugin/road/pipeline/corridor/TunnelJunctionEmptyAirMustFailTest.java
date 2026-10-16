package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelJunctionEmptyAirMustFailTest {

    @Test
    void twoPortalSeedsWithNoTunnelAirReportsFailure() {
        BlockPos seedA = new BlockPos(-4, 64, 0);
        BlockPos seedB = new BlockPos(4, 64, 0);
        JunctionValidationManifest manifest = tunnelManifest(seedA, seedB);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            new RoadGenerationResult(0),
            PlacementRecordColumnIndex.build(new RoadGenerationResult(0)),
            IdentityCoordinateService.INSTANCE);

        assertTrue(report.disconnectedTunnelComponents() > 0);
        assertEquals(2, report.disconnectedTunnelComponents());
    }

    @Test
    void twoDisconnectedAirPocketsStillFail() {
        BlockPos seedA = new BlockPos(-4, 64, 0);
        BlockPos seedB = new BlockPos(4, 64, 0);
        JunctionValidationManifest manifest = tunnelManifest(seedA, seedB);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        placeTunnelAir(aggregate, seedA);
        placeTunnelAir(aggregate, seedB);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(1, report.disconnectedTunnelComponents());
    }

    @Test
    void connectedAirBetweenSeedsPasses() {
        BlockPos seedA = new BlockPos(-4, 64, 0);
        BlockPos seedB = new BlockPos(4, 64, 0);
        JunctionValidationManifest manifest = tunnelManifest(seedA, seedB);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        for (int x = -4; x <= 4; x++) {
            placeTunnelAir(aggregate, new BlockPos(x, 64, 0));
        }

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(0, report.disconnectedTunnelComponents());
    }

    @Test
    void airConnectivityValidatorEmptyVolumeWithTwoSeedsFails() {
        TunnelJunctionAirConnectivityValidator.Result result =
            TunnelJunctionAirConnectivityValidator.validate(
                java.util.Set.of(),
                List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        assertEquals(2, result.disconnectedComponentCount());
        assertEquals(0, result.reachableSeedCount());
    }

    private static JunctionValidationManifest tunnelManifest(BlockPos seedA, BlockPos seedB) {
        return new JunctionValidationManifest(
            "tunnel-node",
            List.of(),
            List.of(),
            List.of(
                new TunnelPortalSeed("edge-a", seedA),
                new TunnelPortalSeed("edge-b", seedB)),
            List.of(
                new Vec2d(-8, -8),
                new Vec2d(8, -8),
                new Vec2d(8, 8),
                new Vec2d(-8, 8)),
            64,
            null,
            64,
            64);
    }

    private static void placeTunnelAir(RoadGenerationResult aggregate, BlockPos pos) {
        aggregate.recordPlacementOverride(pos, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        aggregate.tunnelBlocks.add(pos);
    }
}
