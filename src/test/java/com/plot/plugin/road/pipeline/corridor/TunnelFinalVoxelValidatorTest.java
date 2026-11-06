package com.plot.plugin.road.pipeline.corridor;

import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TunnelFinalVoxelValidatorTest {

    @Test
    void unclearedTunnelCavityCountsExpectedCellsWithTerrainObstruction() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos cavity = new BlockPos(10, 66, 0);
        result.recordExpectedTunnelCavity(cavity);
        result.recordPlacementByLayerPriority(
            cavity, "minecraft:coal_ore", "minecraft:coal_ore",
            RoadSolidLayer.SUBGRADE, RoadSolidMaterialPriority.DEFAULT);

        var terrain = new com.plot.core.terrain.TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return true;
            }
        };

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, terrain);
        assertEquals(1, report.unclearedTunnelCavityCount());
        assertEquals(0, report.floatingTunnelLightCount());
    }

    @Test
    void solidOverwriteRemovingPurposeStillCountsExpectedCavity() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos cavity = new BlockPos(14, 66, 0);
        result.recordExpectedTunnelCavity(cavity);
        // Plan air with purpose, then solid wins and purpose is deleted — mirrors recorder behavior.
        result.recordPlacementByLayerPriority(
            cavity, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);
        // Same layer, higher material priority solid — recorder drops clearance purpose.
        result.recordPlacementByLayerPriority(
            cavity, "minecraft:air", "minecraft:coal_ore",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
        assertEquals(null, result.placementClearancePurposes.get(cavity),
            "purpose must be gone after solid overwrite");
        // Purpose-map blindness: expected cavity + non-tunnel solid, no purpose entry.
        RoadGenerationResult blocked = new RoadGenerationResult(0);
        BlockPos rock = new BlockPos(16, 66, 0);
        blocked.recordExpectedTunnelCavity(rock);
        blocked.recordPlacementOverride(rock, "minecraft:stone", "minecraft:coal_ore");
        blocked.placementLayers.put(rock, RoadSolidLayer.SUBGRADE);
        blocked.placementMaterialPriorities.put(rock, RoadSolidMaterialPriority.DEFAULT);
        var terrain = new com.plot.core.terrain.TerrainSampler() {
            @Override
            public int sampleSurfaceY(com.plot.api.geometry.Vec2d planPoint) {
                return 80;
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return true;
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return true;
            }
        };
        assertEquals(1, TunnelFinalVoxelValidator.validate(blocked, terrain).unclearedTunnelCavityCount(),
            "expected cavity must detect blockage without purpose-map entry");
    }

    @Test
    void clearedTunnelAirIsNotCounted() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos cavity = new BlockPos(11, 66, 0);
        result.recordExpectedTunnelCavity(cavity);
        result.recordPlacementByLayerPriority(
            cavity, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR,
            ClearancePurpose.TUNNEL_INTERIOR);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(0, report.unclearedTunnelCavityCount());
    }

    @Test
    void missingLiningCountsExpectedShellWithoutSolid() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos lining = new BlockPos(15, 67, 3);
        result.recordExpectedTunnelLining(lining);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(1, report.missingTunnelLiningCount());
    }

    @Test
    void missingLiningExactCountsGapEvenWhenNeighborHasLining() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos missing = new BlockPos(20, 70, 0);
        BlockPos neighbor = missing.east();
        result.recordExpectedTunnelLining(missing);
        result.recordPlacementByLayerPriority(
            neighbor, "minecraft:stone", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(1, report.missingTunnelLiningCount(),
            "adjacent lining must not fulfill the missing expected cell");
        assertEquals(1, report.missingTunnelLiningToleratedCount(),
            "neighbor coverage is diagnostic only");
    }

    @Test
    void unauthorizedLiningInsideExpectedCavityCountsAsUncleared() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos cavity = new BlockPos(21, 66, 0);
        result.recordExpectedTunnelCavity(cavity);
        // Structural lining in cavity without being expected lining → intrusion.
        result.recordPlacementByLayerPriority(
            cavity, "minecraft:air", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(1, report.unclearedTunnelCavityCount(),
            "lining that is not an authorized expected lining must block the cavity");
    }

    @Test
    void authorizedExpectedLiningInsideCavityIsNotUncleared() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(22, 66, 0);
        result.recordExpectedTunnelCavity(pos);
        result.recordExpectedTunnelLining(pos);
        result.recordPlacementByLayerPriority(
            pos, "minecraft:air", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(0, report.unclearedTunnelCavityCount());
        assertEquals(0, report.missingTunnelLiningCount());
    }

    @Test
    void aggregateRevalidationDetectsPostMergeLiningOverwrite() {
        RoadGenerationResult tunnelEdge = new RoadGenerationResult(0);
        BlockPos lining = new BlockPos(30, 67, 0);
        tunnelEdge.recordExpectedTunnelLining(lining);
        tunnelEdge.recordPlacementByLayerPriority(
            lining, "minecraft:stone", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
        assertEquals(0, TunnelFinalVoxelValidator.validate(tunnelEdge, null).missingTunnelLiningCount());

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.mergeFrom(tunnelEdge);
        // Another edge overwrites the shell after merge.
        aggregate.recordPlacementOverride(lining, "minecraft:stone_bricks", "minecraft:oak_planks");
        aggregate.placementLayers.put(lining, RoadSolidLayer.ROAD);
        aggregate.placementMaterialPriorities.put(lining, RoadSolidMaterialPriority.DEFAULT);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(aggregate, null);
        assertEquals(1, report.missingTunnelLiningCount(),
            "post-merge overwrite of expected lining must be visible on aggregate revalidation");
    }

    @Test
    void floatingLightWithoutAdjacentLiningIsCounted() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos light = new BlockPos(12, 70, 0);
        result.recordPlacementByLayerPriority(
            light, "minecraft:air", "minecraft:lantern",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.DECORATIVE);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(1, report.floatingTunnelLightCount());
    }

    @Test
    void lightWithAdjacentLiningIsNotFloating() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos light = new BlockPos(13, 70, 0);
        BlockPos lining = light.west();
        result.recordPlacementByLayerPriority(
            lining, "minecraft:stone", "minecraft:cobblestone",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
        result.recordPlacementByLayerPriority(
            light, "minecraft:air", "minecraft:lantern",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.DECORATIVE);

        TunnelFinalVoxelValidator.Report report = TunnelFinalVoxelValidator.validate(result, null);
        assertEquals(0, report.floatingTunnelLightCount());
    }
}
