package com.plot.core.command.commands;

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

        List<GenerateRoadCommand.BlockRecord> records = List.of(
            new GenerateRoadCommand.BlockRecord(road, "minecraft:grass_block", "minecraft:stone"),
            new GenerateRoadCommand.BlockRecord(sidewalk, "minecraft:dirt", "minecraft:oak_planks")
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
            List.of(new GenerateRoadCommand.BlockRecord(pos, "minecraft:air", "minecraft:gravel")),
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

        List<GenerateRoadCommand.BlockRecord> records = List.of(
            new GenerateRoadCommand.BlockRecord(new BlockPos(0, 64, 0), "minecraft:a", "minecraft:new_a"),
            new GenerateRoadCommand.BlockRecord(new BlockPos(1, 64, 0), "minecraft:b", "minecraft:new_b"),
            new GenerateRoadCommand.BlockRecord(new BlockPos(2, 64, 0), "minecraft:c", "minecraft:new_c")
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

        List<GenerateRoadCommand.BlockRecord> records = List.of(
            new GenerateRoadCommand.BlockRecord(overlap, "minecraft:grass_block", "minecraft:stone"),
            new GenerateRoadCommand.BlockRecord(overlap, "minecraft:dirt", "minecraft:gravel")
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

        List<GenerateRoadCommand.BlockRecord> records = List.of(
            new GenerateRoadCommand.BlockRecord(overlap, "minecraft:grass_block", "minecraft:stone")
        );

        GenerateRoadCommand command = new GenerateRoadCommand(records, writer);
        command.execute();
        assertEquals("minecraft:stone", writer.get(overlap));

        command.undo();
        assertEquals("minecraft:grass_block", writer.get(overlap));
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
