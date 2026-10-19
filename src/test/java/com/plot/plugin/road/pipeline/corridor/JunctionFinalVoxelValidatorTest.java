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

class JunctionFinalVoxelValidatorTest {

    @Test
    void upperRoadHeadroomIsNotADeckLeak() {
        var deck = new BlockPos(0, 72, 0);
        var manifest = new JunctionValidationManifest("n1", List.of(), List.of(deck), List.of(),
            squareFootprint(), 64, 72, 65, 68);
        var aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(deck, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(deck, RoadSolidLayer.BRIDGE);
        for (int dy = 1; dy <= 4; dy++) {
            var headroom = deck.up(dy);
            aggregate.recordPlacementOverride(headroom, "minecraft:stone", "minecraft:air");
            aggregate.placementLayers.put(headroom, RoadSolidLayer.TUNNEL);
        }
        var report = JunctionFinalVoxelValidator.validateNode(manifest, aggregate,
            PlacementRecordColumnIndex.build(aggregate), IdentityCoordinateService.INSTANCE);
        assertEquals(0, report.protectedLosses());
        assertEquals(0, report.crossLayerLeaks());
    }

    @Test
    void slopedSurfaceExpectationsNotFlaggedWhenPresent() {
        BlockPos surfaceAt72 = new BlockPos(0, 72, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(new JunctionSurfaceExpectation(surfaceAt72, RoadSolidLayer.ROAD)),
            List.of(),
            List.of(),
            squareFootprint(),
            71,
            null,
            0,
            0);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(surfaceAt72, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(surfaceAt72, RoadSolidLayer.ROAD);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(0, report.surfaceSemanticMismatches());
    }

    @Test
    void uniformJunctionYWouldFalsePositiveButManifestPasses() {
        BlockPos surfaceAt72 = new BlockPos(0, 72, 0);
        BlockPos wrongY71 = new BlockPos(0, 71, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(new JunctionSurfaceExpectation(surfaceAt72, RoadSolidLayer.ROAD)),
            List.of(),
            List.of(),
            squareFootprint(),
            71,
            null,
            0,
            0);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(surfaceAt72, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(surfaceAt72, RoadSolidLayer.ROAD);

        PlacementRecordColumnIndex index = PlacementRecordColumnIndex.build(aggregate);
        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest, aggregate, index, IdentityCoordinateService.INSTANCE);
        assertEquals(0, report.missingSurfaceColumns(), "manifest uses actual cellY=72");

        assertTrue(
            aggregate.placementRecords.get(wrongY71) == null
                || "minecraft:air".equals(aggregate.placementRecords.get(wrongY71).newBlockId),
            "looking up uniform junctionY=71 would miss the real surface");
    }

    @Test
    void columnIndexQueriesLocalColumnNotFullScanSemantics() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos near = new BlockPos(0, 64, 0);
        BlockPos far = new BlockPos(500, 64, 0);
        aggregate.recordPlacementOverride(near, "minecraft:air", "minecraft:air");
        aggregate.placementLayers.put(near, RoadSolidLayer.TUNNEL);
        aggregate.tunnelBlocks.add(near);
        aggregate.recordPlacementOverride(far, "minecraft:air", "minecraft:air");
        aggregate.placementLayers.put(far, RoadSolidLayer.TUNNEL);
        aggregate.tunnelBlocks.add(far);

        PlacementRecordColumnIndex index = PlacementRecordColumnIndex.build(aggregate);
        List<BlockPos> column = index.positionsInColumn(0, 0);
        assertEquals(1, column.size());
        assertEquals(near, column.getFirst());
        assertTrue(index.positionsInColumn(500, 0).contains(far));
    }

    @Test
    void crossLayerLeakIgnoresDistantTunnelAir() {
        BlockPos protectedDeck = new BlockPos(0, 72, 0);
        BlockPos distantAir = new BlockPos(200, 80, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(),
            List.of(protectedDeck),
            List.of(),
            squareFootprint(),
            64,
            72,
            60,
            68);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(protectedDeck, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(protectedDeck, RoadSolidLayer.BRIDGE);
        aggregate.recordPlacementOverride(distantAir, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(distantAir, RoadSolidLayer.TUNNEL);
        aggregate.tunnelBlocks.add(distantAir);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(0, report.crossLayerLeaks(), "distant tunnel air must not pollute this node");
    }

    @Test
    void crossLayerLeakDetectsProtectedVolumeIntrusion() {
        BlockPos protectedDeck = new BlockPos(0, 72, 0);
        BlockPos leakAir = new BlockPos(0, 72, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(),
            List.of(protectedDeck),
            List.of(),
            squareFootprint(),
            64,
            72,
            60,
            68);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(leakAir, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(leakAir, RoadSolidLayer.TUNNEL);
        aggregate.tunnelBlocks.add(leakAir);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertTrue(report.crossLayerLeaks() > 0, "TUNNEL air on protected deck must count as leak");
        assertTrue(report.protectedLosses() > 0, "AIR replacing expected protected block is a loss");
    }

    @Test
    void protectedLossDetectedWhenBridgeReplacedByAir() {
        BlockPos deck = new BlockPos(1, 72, 1);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(),
            List.of(deck),
            List.of(),
            squareFootprint(),
            64,
            72,
            60,
            68);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(deck, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(deck, RoadSolidLayer.TUNNEL);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(1, report.protectedLosses());
    }

    @Test
    void floodFillUsesPortalSeedsNotAllAir() {
        BlockPos seedA = new BlockPos(0, 64, 0);
        BlockPos seedB = new BlockPos(10, 64, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(),
            List.of(),
            List.of(
                new TunnelPortalSeed("e1", seedA),
                new TunnelPortalSeed("e2", seedB)),
            largeFootprint(),
            64,
            null,
            64,
            64);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        for (BlockPos pos : List.of(
            seedA, new BlockPos(1, 64, 0),
            seedB, new BlockPos(11, 64, 0))) {
            aggregate.recordPlacementOverride(pos, "minecraft:stone", "minecraft:air");
            aggregate.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
            aggregate.tunnelBlocks.add(pos);
        }

        JunctionFinalVoxelValidator.PostMergeReport disconnected = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);
        assertEquals(1, disconnected.disconnectedTunnelComponents());

        for (int x = 2; x <= 9; x++) {
            BlockPos bridge = new BlockPos(x, 64, 0);
            aggregate.recordPlacementOverride(bridge, "minecraft:stone", "minecraft:air");
            aggregate.placementLayers.put(bridge, RoadSolidLayer.TUNNEL);
            aggregate.tunnelBlocks.add(bridge);
        }
        JunctionFinalVoxelValidator.PostMergeReport connected = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);
        assertEquals(0, connected.disconnectedTunnelComponents());
    }

    @Test
    void emptyTunnelAirWithTwoSeedsMustFail() {
        BlockPos seedA = new BlockPos(0, 64, 0);
        BlockPos seedB = new BlockPos(10, 64, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(),
            List.of(),
            List.of(
                new TunnelPortalSeed("e1", seedA),
                new TunnelPortalSeed("e2", seedB)),
            largeFootprint(),
            64,
            null,
            64,
            64);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertTrue(
            report.disconnectedTunnelComponents() > 0,
            "fully blocked underpass with two portal seeds must not report clean");
        assertEquals(2, report.disconnectedTunnelComponents());
    }

    @Test
    void sidewalkReplacedByRoadIsSemanticMismatchNotStructuralGap() {
        BlockPos sidewalk = new BlockPos(1, 68, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(new JunctionSurfaceExpectation(sidewalk, RoadSolidLayer.SIDEWALK)),
            List.of(),
            List.of(),
            squareFootprint(),
            68,
            null,
            0,
            0);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(sidewalk, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(sidewalk, RoadSolidLayer.ROAD);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(1, report.surfaceSemanticMismatches());
    }

    @Test
    void markingOverlayOnExpectedRoadPassesSemanticCheck() {
        BlockPos road = new BlockPos(0, 68, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(new JunctionSurfaceExpectation(road, RoadSolidLayer.ROAD)),
            List.of(),
            List.of(),
            squareFootprint(),
            68,
            null,
            0,
            0);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(road, "minecraft:air", "minecraft:white_concrete");
        aggregate.placementLayers.put(road, RoadSolidLayer.MARKING);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(0, report.surfaceSemanticMismatches());
    }

    @Test
    void airAtExpectedRoadIsStructuralGap() {
        BlockPos road = new BlockPos(0, 68, 0);
        JunctionValidationManifest manifest = new JunctionValidationManifest(
            "n1",
            List.of(new JunctionSurfaceExpectation(road, RoadSolidLayer.ROAD)),
            List.of(),
            List.of(),
            squareFootprint(),
            68,
            null,
            0,
            0);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(road, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(road, RoadSolidLayer.TUNNEL);

        JunctionFinalVoxelValidator.PostMergeReport report = JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);

        assertEquals(1, report.missingSurfaceColumns());
        assertEquals(0, report.surfaceSemanticMismatches());
    }

    private static List<Vec2d> squareFootprint() {
        return List.of(
            new Vec2d(-4, -4),
            new Vec2d(4, -4),
            new Vec2d(4, 4),
            new Vec2d(-4, 4));
    }

    private static List<Vec2d> largeFootprint() {
        return List.of(
            new Vec2d(-20, -20),
            new Vec2d(20, -20),
            new Vec2d(20, 20),
            new Vec2d(-20, 20));
    }
}
