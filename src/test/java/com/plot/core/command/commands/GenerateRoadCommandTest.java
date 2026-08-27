package com.plot.core.command.commands;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerateRoadCommandTest {

    @Test
    void executeThenUndoRestoresOriginalBlockIds() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos road = new BlockPos(10, 64, 20);
        BlockPos sidewalk = new BlockPos(11, 64, 20);
        writer.seed(road, "minecraft:grass_block");
        writer.seed(sidewalk, "minecraft:dirt");

        List<BlockRecord> records = List.of(
            new BlockRecord(road, "minecraft:grass_block", "minecraft:stone"),
            new BlockRecord(sidewalk, "minecraft:dirt", "minecraft:oak_planks")
        );

        GenerateRoadCommand command = new GenerateRoadCommand(records, writer);
        command.execute();

        assertEquals("minecraft:stone", writer.get(road));
        assertEquals("minecraft:oak_planks", writer.get(sidewalk));

        command.undo();

        assertEquals("minecraft:grass_block", writer.get(road));
        assertEquals("minecraft:dirt", writer.get(sidewalk));
    }

    @Test
    void redoReappliesNewBlockIds() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos pos = new BlockPos(0, 70, 0);
        writer.seed(pos, "minecraft:air");

        GenerateRoadCommand command = new GenerateRoadCommand(
            List.of(new BlockRecord(pos, "minecraft:air", "minecraft:gravel")),
            writer
        );

        command.execute();
        command.undo();
        command.redo();

        assertEquals("minecraft:gravel", writer.get(pos));
    }

    @Test
    void undoRestoresRecordsInReverseOrder() {
        List<String> writes = new ArrayList<>();
        GenerateRoadCommand.BlockWriter writer = (pos, blockId) -> {
            writes.add(pos.getX() + ":" + blockId);
            return true;
        };

        List<BlockRecord> records = List.of(
            new BlockRecord(new BlockPos(0, 64, 0), "minecraft:a", "minecraft:new_a"),
            new BlockRecord(new BlockPos(1, 64, 0), "minecraft:b", "minecraft:new_b"),
            new BlockRecord(new BlockPos(2, 64, 0), "minecraft:c", "minecraft:new_c")
        );

        new GenerateRoadCommand(records, writer).undo();

        assertEquals(List.of(
            "2:minecraft:c",
            "1:minecraft:b",
            "0:minecraft:a"
        ), writes);
    }

    @Test
    void duplicatePositionsInRecordListUndoLeavesLastRestoredState() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos overlap = new BlockPos(5, 64, 5);
        writer.seed(overlap, "minecraft:grass_block");

        List<BlockRecord> records = List.of(
            new BlockRecord(overlap, "minecraft:grass_block", "minecraft:stone"),
            new BlockRecord(overlap, "minecraft:dirt", "minecraft:gravel")
        );

        GenerateRoadCommand command = new GenerateRoadCommand(records, writer);
        command.execute();
        assertEquals("minecraft:gravel", writer.get(overlap));

        command.undo();
        assertEquals("minecraft:grass_block", writer.get(overlap),
            "undo should end on the first record's previousBlockId when duplicate positions exist in the list");
    }

    @Test
    void dedupedPlacementRecordsRoundTripRestoresOriginalWorld() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos overlap = new BlockPos(8, 64, 8);
        writer.seed(overlap, "minecraft:grass_block");

        List<BlockRecord> records = List.of(
            new BlockRecord(overlap, "minecraft:grass_block", "minecraft:stone")
        );

        GenerateRoadCommand command = new GenerateRoadCommand(records, writer);
        command.execute();
        assertEquals("minecraft:stone", writer.get(overlap));

        command.undo();
        assertEquals("minecraft:grass_block", writer.get(overlap));
    }

    @Test
    void subsetByWriteIndicesKeepsOnlyAppliedRecords() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 64, 0);
        BlockPos third = new BlockPos(2, 64, 0);
        writer.seed(first, "minecraft:grass_block");
        writer.seed(second, "minecraft:grass_block");
        writer.seed(third, "minecraft:grass_block");

        List<BlockRecord> records = List.of(
            new BlockRecord(first, "minecraft:grass_block", "minecraft:stone"),
            new BlockRecord(second, "minecraft:grass_block", "minecraft:gravel"),
            new BlockRecord(third, "minecraft:grass_block", "minecraft:dirt")
        );

        GenerateRoadCommand full = new GenerateRoadCommand(records, writer);
        GenerateRoadCommand partial = full.subsetByWriteIndices(List.of(0, 2));

        assertEquals(3, full.getRecordCount());
        assertEquals(2, partial.getRecordCount());

        partial.execute();
        assertEquals("minecraft:stone", writer.get(first));
        assertEquals("minecraft:grass_block", writer.get(second));
        assertEquals("minecraft:dirt", writer.get(third));

        partial.undo();
        assertEquals("minecraft:grass_block", writer.get(first));
        assertEquals("minecraft:grass_block", writer.get(third));
    }

    private static final class InMemoryBlockWriter implements GenerateRoadCommand.BlockWriter {
        private final Map<BlockPos, String> blocks = new LinkedHashMap<>();

        void seed(BlockPos pos, String blockId) {
            blocks.put(pos, blockId);
        }

        String get(BlockPos pos) {
            return blocks.get(pos);
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            blocks.put(pos, blockId);
            return true;
        }
    }
}
