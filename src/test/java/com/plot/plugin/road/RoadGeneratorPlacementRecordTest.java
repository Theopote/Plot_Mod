package com.plot.plugin.road;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.road.solid.RoadGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGeneratorPlacementRecordTest {

    @Test
    void recordPlacementIfAbsentKeepsFirstClaimAtSamePosition() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos overlap = new BlockPos(3, 64, 7);

        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:stone");
        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:dirt", "minecraft:gravel");

        assertEquals(1, result.placementRecords.size());
        BlockRecord record = result.placementRecords.get(overlap);
        assertEquals("minecraft:grass_block", record.previousBlockId);
        assertEquals("minecraft:stone", record.newBlockId);
    }

    @Test
    void junctionRoadOverrideReplacesEdgePlacement() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos overlap = new BlockPos(12, 65, 18);

        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:stone");
        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:gravel");

        assertEquals("minecraft:stone", result.placementRecords.get(overlap).newBlockId);

        String previous = result.placementRecords.get(overlap).previousBlockId;
        result.placementRecords.put(overlap, new BlockRecord(overlap, previous, "minecraft:black_concrete"));

        assertEquals("minecraft:black_concrete", result.placementRecords.get(overlap).newBlockId);
    }

    @Test
    void junctionRoadThenSidewalkOverlapKeepsRoadMaterial() {
        RoadGenerationResult result = new RoadGenerationResult(0);
        BlockPos overlap = new BlockPos(12, 65, 18);

        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:stone");
        RoadGenerator.recordPlacementIfAbsent(
            result, overlap, "minecraft:grass_block", "minecraft:oak_planks");

        assertEquals(1, result.placementRecords.size());
        assertEquals("minecraft:stone", result.placementRecords.get(overlap).newBlockId);
    }

    @Test
    void rasterizeSpanDiagonalProducesThinLineInsteadOfBoundingBox() {
        List<BlockPos> positions = RoadGenerator.rasterizeSpan(
            new Vec2d(0, 0),
            new Vec2d(3, 3),
            64
        );

        assertEquals(7, positions.size(), "4-connected (3,3) diagonal is manhattan 6 + endpoints");
        assertTrue(positions.size() < 16, "diagonal span should not expand to a dense bbox-like fill");
        assertTrue(positions.stream().anyMatch(pos -> pos.getX() == 0 && pos.getZ() == 0));
        assertTrue(positions.stream().anyMatch(pos -> pos.getX() == 3 && pos.getZ() == 3));
        for (int i = 1; i < positions.size(); i++) {
            int manhattan = Math.abs(positions.get(i).getX() - positions.get(i - 1).getX())
                + Math.abs(positions.get(i).getZ() - positions.get(i - 1).getZ());
            assertEquals(1, manhattan, "consecutive span cells must be 4-connected");
        }
    }

    @Test
    void rasterizeSpanHorizontalCoversEndpoints() {
        List<BlockPos> positions = RoadGenerator.rasterizeSpan(
            new Vec2d(2, 5),
            new Vec2d(6, 5),
            70
        );

        assertTrue(positions.stream().anyMatch(pos -> pos.getX() == 2 && pos.getZ() == 5));
        assertTrue(positions.stream().anyMatch(pos -> pos.getX() == 6 && pos.getZ() == 5));
        assertTrue(positions.size() >= 5,
            "horizontal span should cover intermediate points along the strip");
    }
}
