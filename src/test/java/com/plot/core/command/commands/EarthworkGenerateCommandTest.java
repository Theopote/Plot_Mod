package com.plot.core.command.commands;

import com.plot.api.world.IBlockPlacementService;
import com.plot.core.command.BlockRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthworkGenerateCommandTest {

    @Test
    void executeThenUndoRestoresOriginalBlockIds() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos cut = new BlockPos(10, 65, 20);
        BlockPos fill = new BlockPos(11, 63, 20);
        writer.seed(cut, "minecraft:stone");
        writer.seed(fill, "minecraft:grass_block");

        List<BlockRecord> records = List.of(
            new BlockRecord(cut, "minecraft:stone", "minecraft:air"),
            new BlockRecord(fill, "minecraft:grass_block", "minecraft:dirt")
        );

        EarthworkGenerateCommand command = new EarthworkGenerateCommand(records, writer);
        command.execute();

        assertEquals("minecraft:air", writer.get(cut));
        assertEquals("minecraft:dirt", writer.get(fill));
        assertEquals(2, command.getAppliedRecordCount());

        command.undo();

        assertEquals("minecraft:stone", writer.get(cut));
        assertEquals("minecraft:grass_block", writer.get(fill));
    }

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

        EarthworkGenerateCommand command = new EarthworkGenerateCommand(records, writer);
        command.execute();

        assertEquals(3, command.getRecordCount());
        assertEquals(2, command.getAppliedRecordCount());
        assertTrue(command.hasAppliedRecords());
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
    void cancelledScheduledPlacementUndoOnlyTouchesAppliedRecords() {
        Map<BlockPos, String> world = new LinkedHashMap<>();
        List<BlockPos> undoWrites = new ArrayList<>();
        TrackingPlacementService placement = new TrackingPlacementService(world, 40);

        List<BlockRecord> records = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            world.put(pos, "minecraft:grass_block");
            records.add(new BlockRecord(pos, "minecraft:grass_block", "minecraft:stone"));
        }

        EarthworkGenerateCommand command = new EarthworkGenerateCommand(
            records, placement.asBlockWriter(), true, placement);
        AtomicReference<EarthworkGenerateCommand.ExecutionResult> result = new AtomicReference<>();
        command.executeScheduled(() -> result.set(command.getLastExecutionResult()));

        EarthworkGenerateCommand.ExecutionResult execution = result.get();
        assertTrue(execution.cancelled());
        assertEquals(40, execution.success());
        assertEquals(40, command.getAppliedRecordCount());

        placement.trackWritesTo(undoWrites);
        command.undo();

        assertEquals(40, undoWrites.size());
        for (int i = 0; i < 40; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            assertTrue(undoWrites.contains(pos), "undo should restore applied cell " + i);
            assertEquals("minecraft:grass_block", world.get(pos));
        }
        for (int i = 40; i < 80; i++) {
            assertEquals("minecraft:grass_block", world.get(new BlockPos(i, 64, 0)),
                "unapplied cell must stay untouched after undo");
        }
    }

    @Test
    void totalFailureLeavesNoAppliedRecords() {
        InMemoryBlockWriter writer = new InMemoryBlockWriter();
        BlockPos pos = new BlockPos(0, 64, 0);
        writer.seed(pos, "minecraft:grass_block");
        writer.failOn(pos);

        EarthworkGenerateCommand command = new EarthworkGenerateCommand(
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

    private static final class TrackingPlacementService implements IBlockPlacementService {
        private final Map<BlockPos, String> world;
        private final int appliedWriteCount;
        private List<BlockPos> trackedWrites;

        private TrackingPlacementService(Map<BlockPos, String> world, int appliedWriteCount) {
            this.world = world;
            this.appliedWriteCount = appliedWriteCount;
        }

        EarthworkGenerateCommand.BlockWriter asBlockWriter() {
            return (pos, blockId) -> {
                if (trackedWrites != null) {
                    trackedWrites.add(pos.toImmutable());
                }
                world.put(pos.toImmutable(), blockId);
                return true;
            };
        }

        void trackWritesTo(List<BlockPos> trackedWrites) {
            this.trackedWrites = trackedWrites;
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public ProgressSnapshot getProgressSnapshot() {
            return null;
        }

        @Override
        public void enqueue(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete) {
            List<Integer> successfulIndices = new ArrayList<>();
            int limit = Math.min(appliedWriteCount, writes.size());
            for (int i = 0; i < limit; i++) {
                BlockWrite write = writes.get(i);
                BlockPos pos = write.pos().toImmutable();
                if (trackedWrites != null) {
                    trackedWrites.add(pos);
                }
                world.put(pos, write.blockId());
                successfulIndices.add(i);
            }
            boolean cancelled = limit < writes.size();
            onComplete.accept(cancelled
                ? ExecutionResult.cancelled(limit, 0, writes.size(), successfulIndices)
                : new ExecutionResult(limit, writes.size() - limit, writes.size(), false, successfulIndices));
        }

        @Override
        public boolean cancelAll() {
            return false;
        }
    }

    private static final class InMemoryBlockWriter implements EarthworkGenerateCommand.BlockWriter {
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
