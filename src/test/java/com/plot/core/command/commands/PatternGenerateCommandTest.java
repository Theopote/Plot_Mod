package com.plot.core.command.commands;

import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternGenerateCommandTest {

    @Test
    void partialFailureCapturesOnlyAppliedRecordsForUndo() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 64, 0);
        BlockPos third = new BlockPos(2, 64, 0);
        writer.seed(first, "minecraft:grass_block");
        writer.seed(second, "minecraft:grass_block");
        writer.seed(third, "minecraft:grass_block");
        writer.failOn(second);

        List<BlockRecord> records = List.of(
            new BlockRecord(first, "minecraft:grass_block", "minecraft:stone"),
            new BlockRecord(second, "minecraft:grass_block", "minecraft:gravel"),
            new BlockRecord(third, "minecraft:grass_block", "minecraft:dirt")
        );

        PatternGenerateCommand command = new PatternGenerateCommand(records, writer);
        command.execute();

        assertEquals(3, command.getRecordCount());
        assertEquals(2, command.getAppliedRecordCount());
        assertTrue(command.hasAppliedRecords());
        assertEquals(List.of(records.get(0), records.get(2)), command.getAppliedRecords());
        assertEquals("minecraft:stone", writer.get(first));
        assertEquals("minecraft:grass_block", writer.get(second));
        assertEquals("minecraft:dirt", writer.get(third));

        writer.seed(second, "minecraft:gold_block");
        command.undo();

        assertEquals("minecraft:grass_block", writer.get(first));
        assertEquals("minecraft:gold_block", writer.get(second),
            "failed write must not be restored by undo");
        assertEquals("minecraft:grass_block", writer.get(third));
    }

    @Test
    void totalFailureLeavesNoAppliedRecords() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos pos = new BlockPos(0, 64, 0);
        writer.seed(pos, "minecraft:grass_block");
        writer.failOn(pos);

        PatternGenerateCommand command = new PatternGenerateCommand(
            List.of(new BlockRecord(pos, "minecraft:grass_block", "minecraft:stone")),
            writer
        );
        command.execute();

        assertFalse(command.hasAppliedRecords());
        assertEquals(0, command.getAppliedRecordCount());
        assertEquals("minecraft:grass_block", writer.get(pos));

        command.undo();
        assertEquals("minecraft:grass_block", writer.get(pos));
    }

    @Test
    void undoRestoresAppliedRecordsInReverseOrder() {
        List<String> writes = new ArrayList<>();
        PatternGenerateCommand.BlockWriter writer = (pos, blockId) -> {
            writes.add(pos.getX() + ":" + blockId);
            return true;
        };

        List<BlockRecord> records = List.of(
            new BlockRecord(new BlockPos(0, 64, 0), "minecraft:a", "minecraft:new_a"),
            new BlockRecord(new BlockPos(1, 64, 0), "minecraft:b", "minecraft:new_b"),
            new BlockRecord(new BlockPos(2, 64, 0), "minecraft:c", "minecraft:new_c")
        );

        PatternGenerateCommand command = new PatternGenerateCommand(records, writer);
        command.execute();
        writes.clear();
        command.undo();

        assertEquals(List.of(
            "2:minecraft:c",
            "1:minecraft:b",
            "0:minecraft:a"
        ), writes);
    }

    private static final class InMemoryBlockWriter implements PatternGenerateCommand.BlockWriter {
        private final Map<BlockPos, String> blocks = new LinkedHashMap<>();
        private final Set<BlockPos> failPositions = new java.util.HashSet<>();

        void seed(BlockPos pos, String blockId) {
            blocks.put(pos, blockId);
        }

        void failOn(BlockPos pos) {
            failPositions.add(pos);
        }

        String get(BlockPos pos) {
            return blocks.get(pos);
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            if (failPositions.contains(pos)) {
                return false;
            }
            blocks.put(pos, blockId);
            return true;
        }
    }
}
