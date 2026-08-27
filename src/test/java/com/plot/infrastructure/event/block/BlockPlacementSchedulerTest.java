package com.plot.infrastructure.event.block;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPlacementSchedulerTest {

    private BlockPlacementScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = BlockPlacementScheduler.getInstance();
        scheduler.resetForTest();
        scheduler.setBlockWriterForTest((pos, blockId) -> true);
    }

    @Test
    void cancelQueuedJobMarksResultCancelled() {
        List<com.plot.api.world.IBlockPlacementService.BlockWrite> writes = new ArrayList<>();
        writes.add(new com.plot.api.world.IBlockPlacementService.BlockWrite(new BlockPos(0, 64, 0), "minecraft:stone"));

        AtomicReference<com.plot.api.world.IBlockPlacementService.ExecutionResult> result = new AtomicReference<>();
        scheduler.enqueue(writes, result::set);

        assertTrue(scheduler.cancelAll());
        assertFalse(scheduler.isBusy());
        assertTrue(result.get().cancelled());
        assertEquals(0, result.get().success());
        assertEquals(1, result.get().total());
    }

    @Test
    void cancelActiveJobReportsPartialProgress() {
        List<com.plot.api.world.IBlockPlacementService.BlockWrite> writes = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            writes.add(new com.plot.api.world.IBlockPlacementService.BlockWrite(new BlockPos(i, 64, 0), "minecraft:stone"));
        }

        AtomicReference<com.plot.api.world.IBlockPlacementService.ExecutionResult> result = new AtomicReference<>();
        scheduler.enqueue(writes, result::set);

        scheduler.tickForTest();
        assertTrue(scheduler.isBusy());
        com.plot.api.world.IBlockPlacementService.ProgressSnapshot progress = scheduler.getProgressSnapshot();
        assertEquals(40, progress.processed());
        assertEquals(80, progress.total());

        assertTrue(scheduler.cancelAll());
        assertTrue(result.get().cancelled());
        assertEquals(40, result.get().success());
        assertEquals(80, result.get().total());
        assertEquals(40, result.get().successfulWriteIndices().size());
        assertEquals(0, result.get().successfulWriteIndices().getFirst());
        assertEquals(39, result.get().successfulWriteIndices().getLast());
    }
}
