package com.plot.plugin.road.solid;

import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadPlacementRecorderTest {

    @Test
    void approachClearanceRemovesOtherApproachsWallInEitherMergeOrder() {
        var pos = new BlockPos(2, 67, 0);
        var wall = new RoadGenerationResult(0);
        wall.recordPlacementByLayerPriority(pos, "minecraft:stone", "minecraft:stone_bricks",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.STRUCTURAL);
        var clearance = new RoadGenerationResult(0);
        clearance.recordPlacementByLayerPriority(pos, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR);
        for (boolean wallFirst : new boolean[]{false, true}) {
            var merged = new RoadGenerationResult(0);
            merged.mergeFrom(wallFirst ? wall : clearance);
            merged.mergeFrom(wallFirst ? clearance : wall);
            assertEquals("minecraft:air", merged.placementRecords.get(pos).newBlockId);
            assertEquals(RoadSolidMaterialPriority.AIR, merged.placementMaterialPriorities.get(pos));
        }
    }

    @Test
    void pavementWinsOverAnotherApproachsLiningAndBatterInEitherMergeOrder() {
        var pos = new BlockPos(2, 64, 0);
        for (var layer : java.util.List.of(RoadSolidLayer.TUNNEL, RoadSolidLayer.SUBGRADE)) {
            var obstruction = new RoadGenerationResult(0);
            obstruction.recordPlacementByLayerPriority(pos, "minecraft:stone", "minecraft:stone_bricks",
                layer, RoadSolidMaterialPriority.STRUCTURAL);
            var surface = new RoadGenerationResult(0);
            surface.recordPlacementByLayerPriority(pos, "minecraft:stone", "minecraft:white_concrete",
                RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
            for (boolean obstructionFirst : new boolean[]{false, true}) {
                var merged = new RoadGenerationResult(0);
                merged.mergeFrom(obstructionFirst ? obstruction : surface);
                merged.mergeFrom(obstructionFirst ? surface : obstruction);
                assertEquals("minecraft:white_concrete", merged.placementRecords.get(pos).newBlockId);
                assertEquals(RoadSolidLayer.ROAD, merged.placementLayers.get(pos));
                assertEquals(RoadSolidMaterialPriority.DEFAULT, merged.placementMaterialPriorities.get(pos));
            }
        }
    }

    @Test
    void mergingRoadSurfaceAndOtherEdgesExcavationIsOrderIndependent() {
        var pos = new BlockPos(2, 76, 0);
        var air = new RoadGenerationResult(0);
        air.recordPlacementByLayerPriority(pos, "minecraft:stone", "minecraft:air",
            RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR);
        for (var layer : java.util.List.of(RoadSolidLayer.ROAD, RoadSolidLayer.SHOULDER, RoadSolidLayer.BRIDGE)) {
            var surface = new RoadGenerationResult(0);
            surface.recordPlacementByLayerPriority(pos, "minecraft:stone", "minecraft:white_concrete",
                layer, RoadSolidMaterialPriority.DEFAULT);
            for (boolean airFirst : new boolean[]{false, true}) {
                var merged = new RoadGenerationResult(0);
                merged.mergeFrom(airFirst ? air : surface);
                merged.mergeFrom(airFirst ? surface : air);
                assertEquals("minecraft:white_concrete", merged.placementRecords.get(pos).newBlockId);
                assertEquals("minecraft:stone", merged.placementRecords.get(pos).previousBlockId);
                assertEquals(layer, merged.placementLayers.get(pos));
                assertEquals(RoadSolidMaterialPriority.DEFAULT, merged.placementMaterialPriorities.get(pos));
            }
        }
    }

    @Test
    void recordIfAbsentKeepsFirstClaim() {
        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        BlockPos pos = new BlockPos(1, 64, 2);

        RoadPlacementRecorder.recordIfAbsent(records, pos, "minecraft:grass_block", "minecraft:stone");
        RoadPlacementRecorder.recordIfAbsent(records, pos, "minecraft:dirt", "minecraft:gravel");

        assertEquals(1, records.size());
        assertEquals("minecraft:grass_block", records.get(pos).previousBlockId);
        assertEquals("minecraft:stone", records.get(pos).newBlockId);
    }

    @Test
    void recordOverridePreservesOriginalPreviousBlockId() {
        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        BlockPos pos = new BlockPos(4, 65, 6);

        RoadPlacementRecorder.recordIfAbsent(records, pos, "minecraft:grass_block", "minecraft:stone");
        RoadPlacementRecorder.recordOverride(records, pos, "minecraft:dirt", "minecraft:black_concrete");

        assertEquals("minecraft:grass_block", records.get(pos).previousBlockId);
        assertEquals("minecraft:black_concrete", records.get(pos).newBlockId);
    }

    @Test
    void mergeFromUsesFirstClaimForOverlappingPlacements() {
        RoadGenerationResult first = new RoadGenerationResult(0);
        RoadGenerationResult second = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(2, 64, 3);
        first.recordPlacementIfAbsent(pos, "minecraft:grass_block", "minecraft:stone");
        second.recordPlacementIfAbsent(pos, "minecraft:dirt", "minecraft:gravel");

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.mergeFrom(first);
        aggregate.mergeFrom(second);

        assertEquals(1, aggregate.placementRecords.size());
        assertEquals("minecraft:stone", aggregate.placementRecords.get(pos).newBlockId);
        assertEquals("minecraft:grass_block", aggregate.placementRecords.get(pos).previousBlockId);
    }

    @Test
    void mergeFromCopiesWinningClearanceRecordAndMetadataTogether() {
        RoadGenerationResult first = new RoadGenerationResult(0);
        RoadGenerationResult second = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(2, 66, 3);
        first.recordPlacementIfAbsent(pos, "minecraft:stone", "minecraft:stone_bricks");
        first.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        first.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.STRUCTURAL);
        second.recordPlacementIfAbsent(pos, "minecraft:dirt", "minecraft:air");
        second.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        second.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.CLEARANCE);

        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        aggregate.mergeFrom(first);
        aggregate.mergeFrom(second);

        assertEquals("minecraft:air", aggregate.placementRecords.get(pos).newBlockId);
        assertEquals(RoadSolidLayer.TUNNEL, aggregate.placementLayers.get(pos));
        assertEquals(RoadSolidMaterialPriority.CLEARANCE, aggregate.placementMaterialPriorities.get(pos));
    }

    @Test
    void recordByLayerPriorityOverridesExistingRecordWhenLayerMissing() {
        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        Map<BlockPos, RoadSolidLayer> layers = new LinkedHashMap<>();
        Map<BlockPos, Integer> materialPriorities = new LinkedHashMap<>();
        BlockPos pos = new BlockPos(0, 66, 0);
        records.put(pos, new BlockRecord(pos, "minecraft:stone", "minecraft:stone_bricks"));

        RoadPlacementRecorder.recordByLayerPriority(
            records, layers, materialPriorities, pos, "minecraft:stone",
            "minecraft:air", RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.CLEARANCE);

        assertEquals("minecraft:air", records.get(pos).newBlockId);
        assertEquals(RoadSolidLayer.TUNNEL, layers.get(pos));
        assertEquals(RoadSolidMaterialPriority.CLEARANCE, materialPriorities.get(pos));
    }

    @Test
    void clearanceAirOverridesMergedTunnelLining() {
        RoadGenerationResult aggregate = new RoadGenerationResult(0);
        BlockPos pos = new BlockPos(0, 66, 0);
        RoadGenerationResult edge = new RoadGenerationResult(0);
        edge.recordPlacementOverride(pos, "minecraft:stone", "minecraft:stone_bricks");
        edge.placementLayers.put(pos, RoadSolidLayer.TUNNEL);
        edge.placementMaterialPriorities.put(pos, RoadSolidMaterialPriority.STRUCTURAL);
        aggregate.mergeFrom(edge);

        RoadSolidModel solids = new RoadSolidModel();
        solids.add(
            new com.plot.api.geometry.Vec2d(0, 0),
            66,
            RoadSolidLayer.TUNNEL,
            "minecraft:air",
            RoadSolidMaterialPriority.CLEARANCE);
        RoadVoxelRasterizer.flushJunctionSolids(
            aggregate,
            solids,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance(),
            "minecraft:stone",
            "minecraft:stone",
            "minecraft:white_concrete");

        assertEquals("minecraft:air", aggregate.placementRecords.get(pos).newBlockId);
        assertEquals(RoadSolidLayer.TUNNEL, aggregate.placementLayers.get(pos));
    }

    @Test
    void recordByLayerPriorityOverridesLowerLayerAndMaterialPriority() {
        Map<BlockPos, BlockRecord> records = new LinkedHashMap<>();
        Map<BlockPos, RoadSolidLayer> layers = new LinkedHashMap<>();
        Map<BlockPos, Integer> materialPriorities = new LinkedHashMap<>();
        BlockPos pos = new BlockPos(0, 64, 0);

        RoadPlacementRecorder.recordByLayerPriority(
            records, layers, materialPriorities, pos, "minecraft:grass_block",
            "minecraft:stone", RoadSolidLayer.ROAD, RoadSolidMaterialPriority.DEFAULT);
        RoadPlacementRecorder.recordByLayerPriority(
            records, layers, materialPriorities, pos, "minecraft:dirt",
            "minecraft:air", RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.AIR);
        RoadPlacementRecorder.recordByLayerPriority(
            records, layers, materialPriorities, pos, "minecraft:dirt",
            "minecraft:sea_lantern", RoadSolidLayer.TUNNEL, RoadSolidMaterialPriority.DECORATIVE);

        assertEquals("minecraft:sea_lantern", records.get(pos).newBlockId);
        assertEquals(RoadSolidLayer.TUNNEL, layers.get(pos));
    }

    @Test
    void flushJunctionSolidsAppliesLayerMaterialOverrides() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        RoadSolidModel solids = new RoadSolidModel();
        solids.add(new com.plot.api.geometry.Vec2d(0, 0), 64, RoadSolidLayer.ROAD);
        solids.add(new com.plot.api.geometry.Vec2d(1, 0), 64, RoadSolidLayer.SIDEWALK);
        solids.add(new com.plot.api.geometry.Vec2d(2, 0), 64, RoadSolidLayer.MARKING);

        RoadVoxelRasterizer.flushJunctionSolids(result, solids, null, com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance(), "minecraft:stone", "minecraft:oak_planks", "minecraft:white_concrete");

        assertEquals(2, result.roadBlocks.size());
        assertEquals(1, result.sidewalkBlocks.size());
        assertEquals(3, result.placementRecords.size());
        assertEquals("minecraft:stone", result.placementRecords.get(new BlockPos(0, 64, 0)).newBlockId);
        assertEquals("minecraft:oak_planks", result.placementRecords.get(new BlockPos(1, 64, 0)).newBlockId);
        assertEquals("minecraft:white_concrete", result.placementRecords.get(new BlockPos(2, 64, 0)).newBlockId);
    }
}
