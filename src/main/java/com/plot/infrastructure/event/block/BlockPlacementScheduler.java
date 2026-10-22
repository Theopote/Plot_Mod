package com.plot.infrastructure.event.block;

import com.plot.api.world.IBlockPlacementService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 分帧发送 setblock 指令，避免单帧内堆积大量网络包。
 */
public final class BlockPlacementScheduler implements IBlockPlacementService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BlockPlacementScheduler");
    private static final int BLOCKS_PER_TICK = 40;

    private static BlockPlacementScheduler instance;

    @FunctionalInterface
    interface BlockWriter {
        boolean setBlockAt(BlockPos pos, String blockId);
    }

    private final Deque<PlacementJob> jobQueue = new ArrayDeque<>();
    private PlacementJob activeJob;
    private boolean tickRegistered;
    private BlockWriter blockWriter = (pos, blockId) -> BlockProjectionHandler.getInstance().setBlockAt(pos, blockId);

    public static BlockPlacementScheduler getInstance() {
        if (instance == null) {
            instance = new BlockPlacementScheduler();
        }
        return instance;
    }

    @Override
    public boolean isBusy() {
        return activeJob != null || !jobQueue.isEmpty();
    }

    @Override
    public ProgressSnapshot getProgressSnapshot() {
        if (activeJob != null) {
            return new ProgressSnapshot(
                activeJob.index,
                activeJob.writes.size(),
                activeJob.success,
                activeJob.failed
            );
        }
        PlacementJob queued = jobQueue.peekFirst();
        if (queued != null) {
            return new ProgressSnapshot(0, queued.writes.size(), 0, 0);
        }
        return null;
    }

    @Override
    public void enqueue(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete) {
        enqueue(writes, null, onComplete);
    }

    @Override
    public void enqueue(
            List<BlockWrite> writes,
            Function<BlockPos, String> baselineReaderBeforeWrite,
            Consumer<ExecutionResult> onComplete) {
        if (writes == null || writes.isEmpty()) {
            if (onComplete != null) {
                onComplete.accept(new ExecutionResult(0, 0, 0));
            }
            return;
        }

        jobQueue.addLast(new PlacementJob(
            new ArrayList<>(writes),
            baselineReaderBeforeWrite,
            onComplete));
        ensureTickRegistered();
    }

    /**
     * 取消当前任务及队列中所有待处理任务。
     *
     * @return 是否取消了至少一个任务
     */
    @Override
    public boolean cancelAll() {
        if (!isBusy()) {
            return false;
        }

        int cancelledJobs = 0;
        if (activeJob != null) {
            completeJob(activeJob, true);
            activeJob = null;
            cancelledJobs++;
        }

        while (!jobQueue.isEmpty()) {
            completeJob(jobQueue.pollFirst(), true);
            cancelledJobs++;
        }

        LOGGER.info("已取消 {} 个方块放置任务", cancelledJobs);
        return cancelledJobs > 0;
    }

    private void ensureTickRegistered() {
        if (tickRegistered) {
            return;
        }
        tickRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        if (activeJob == null) {
            activeJob = jobQueue.pollFirst();
            if (activeJob == null) {
                return;
            }
        }

        int budget = BLOCKS_PER_TICK;
        while (budget > 0 && activeJob.hasNext()) {
            int writeIndex = activeJob.index;
            BlockWrite write = activeJob.next();
            activeJob.captureBaselineBeforeWrite(writeIndex, write.pos());
            if (blockWriter.setBlockAt(write.pos(), write.blockId())) {
                activeJob.success++;
                activeJob.successfulWriteIndices.add(writeIndex);
            } else {
                activeJob.failed++;
            }
            budget--;
        }

        if (!activeJob.hasNext()) {
            completeJob(activeJob, false);
            activeJob = null;
        }
    }

    private void completeJob(PlacementJob job, boolean cancelled) {
        List<String> capturedBaselines = job.capturedBaselines.isEmpty()
            ? List.of()
            : List.copyOf(job.capturedBaselines);
        ExecutionResult result = cancelled
            ? new ExecutionResult(
                job.success,
                job.failed,
                job.writes.size(),
                true,
                List.copyOf(job.successfulWriteIndices),
                capturedBaselines)
            : new ExecutionResult(
                job.success,
                job.failed,
                job.writes.size(),
                false,
                List.copyOf(job.successfulWriteIndices),
                capturedBaselines);
        if (!cancelled) {
            LOGGER.info("方块放置批次完成: {}/{} 成功, {} 失败",
                result.success(), result.total(), result.failed());
        } else {
            LOGGER.info("方块放置批次已取消: 已发送 {}/{}，失败 {}",
                result.success(), result.total(), result.failed());
        }
        if (job.onComplete != null) {
            job.onComplete.accept(result);
        }
    }

    void setBlockWriterForTest(BlockWriter writer) {
        this.blockWriter = writer != null
            ? writer
            : (pos, blockId) -> BlockProjectionHandler.getInstance().setBlockAt(pos, blockId);
    }

    void resetForTest() {
        activeJob = null;
        jobQueue.clear();
        blockWriter = (pos, blockId) -> BlockProjectionHandler.getInstance().setBlockAt(pos, blockId);
    }

    void tickForTest() {
        tick();
    }

    private static final class PlacementJob {
        private final List<BlockWrite> writes;
        private final Function<BlockPos, String> baselineReaderBeforeWrite;
        private final Consumer<ExecutionResult> onComplete;
        private int index;
        private int success;
        private int failed;
        private final List<Integer> successfulWriteIndices = new ArrayList<>();
        private final List<String> capturedBaselines;

        private PlacementJob(
                List<BlockWrite> writes,
                Function<BlockPos, String> baselineReaderBeforeWrite,
                Consumer<ExecutionResult> onComplete) {
            this.writes = writes;
            this.baselineReaderBeforeWrite = baselineReaderBeforeWrite;
            this.onComplete = onComplete;
            this.capturedBaselines = baselineReaderBeforeWrite != null
                ? new ArrayList<>(java.util.Collections.nCopies(writes.size(), null))
                : List.of();
        }

        private void captureBaselineBeforeWrite(int writeIndex, BlockPos pos) {
            if (baselineReaderBeforeWrite == null || writeIndex < 0 || writeIndex >= capturedBaselines.size()) {
                return;
            }
            capturedBaselines.set(writeIndex, baselineReaderBeforeWrite.apply(pos));
        }

        private boolean hasNext() {
            return index < writes.size();
        }

        private BlockWrite next() {
            return writes.get(index++);
        }
    }
}
