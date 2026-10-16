package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JunctionSurfaceSemanticIntegrityTest {

    @Test
    void sidewalkReplacedByRoadFailsSemanticButNotStructural() {
        BlockPos pos = new BlockPos(2, 70, 1);
        JunctionValidationManifest manifest = surfaceManifest(pos, RoadSolidLayer.SIDEWALK);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(pos, RoadSolidLayer.ROAD);

        JunctionFinalVoxelValidator.PostMergeReport report = validate(manifest, aggregate);
        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(1, report.surfaceSemanticMismatches());
    }

    @Test
    void shoulderReplacedByRoadFailsSemantic() {
        BlockPos pos = new BlockPos(3, 70, 0);
        JunctionValidationManifest manifest = surfaceManifest(pos, RoadSolidLayer.SHOULDER);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone");
        aggregate.placementLayers.put(pos, RoadSolidLayer.ROAD);

        JunctionFinalVoxelValidator.PostMergeReport report = validate(manifest, aggregate);
        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(1, report.surfaceSemanticMismatches());
    }

    @Test
    void markingOnExpectedRoadPassesBothChecks() {
        BlockPos pos = new BlockPos(0, 70, 0);
        JunctionValidationManifest manifest = surfaceManifest(pos, RoadSolidLayer.ROAD);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:white_concrete");
        aggregate.placementLayers.put(pos, RoadSolidLayer.MARKING);

        JunctionFinalVoxelValidator.PostMergeReport report = validate(manifest, aggregate);
        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(0, report.surfaceSemanticMismatches());
    }

    @Test
    void airAtExpectedSurfaceIsStructuralGap() {
        BlockPos pos = new BlockPos(0, 70, 0);
        JunctionValidationManifest manifest = surfaceManifest(pos, RoadSolidLayer.ROAD);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(pos, "minecraft:stone", "minecraft:air");
        aggregate.placementLayers.put(pos, RoadSolidLayer.TUNNEL);

        JunctionFinalVoxelValidator.PostMergeReport report = validate(manifest, aggregate);
        assertEquals(1, report.missingSurfaceColumns());
        assertEquals(0, report.surfaceSemanticMismatches());
    }

    @Test
    void matchingSidewalkPassesBothChecks() {
        BlockPos pos = new BlockPos(1, 70, 2);
        JunctionValidationManifest manifest = surfaceManifest(pos, RoadSolidLayer.SIDEWALK);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.recordPlacementOverride(pos, "minecraft:air", "minecraft:stone_slab");
        aggregate.placementLayers.put(pos, RoadSolidLayer.SIDEWALK);

        JunctionFinalVoxelValidator.PostMergeReport report = validate(manifest, aggregate);
        assertEquals(0, report.missingSurfaceColumns());
        assertEquals(0, report.surfaceSemanticMismatches());
    }

    private static JunctionValidationManifest surfaceManifest(BlockPos pos, RoadSolidLayer expected) {
        return new JunctionValidationManifest(
            "surface-node",
            List.of(new JunctionSurfaceExpectation(pos, expected)),
            List.of(),
            List.of(),
            List.of(
                new Vec2d(-4, -4),
                new Vec2d(4, -4),
                new Vec2d(4, 4),
                new Vec2d(-4, 4)),
            70,
            null,
            0,
            0);
    }

    private static JunctionFinalVoxelValidator.PostMergeReport validate(
            JunctionValidationManifest manifest,
            RoadGenerationResult aggregate) {
        return JunctionFinalVoxelValidator.validateNode(
            manifest,
            aggregate,
            PlacementRecordColumnIndex.build(aggregate),
            IdentityCoordinateService.INSTANCE);
    }
}
