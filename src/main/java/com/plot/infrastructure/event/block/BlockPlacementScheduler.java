package com.plot.infrastructure.event.block;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * 分帧发送 setblock 指令，避免单帧内堆积大量网络包。
 */
public final class BlockPlacementScheduler {
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

    public record BlockWrite(BlockPos pos, String blockId) {
    }

    public record ExecutionResult(int success, int failed, int total, boolean cancelled) {
        public ExecutionResult(int success, int failed, int total) {
            this(success, failed, total, false);
        }

        public static ExecutionResult cancelled(int success, int failed, int total) {
            return new ExecutionResult(success, failed, total, true);
        }

        public boolean isFullSuccess() {
            return !cancelled && total > 0 && failed == 0 && success == total;
        }

        public boolean isTotalFailure() {
            return !cancelled && total > 0 && success == 0;
        }
    }

    public record ProgressSnapshot(int processed, int total, int success, int failed) {
    }

    public static BlockPlacementScheduler getInstance() {
        if (instance == null) {
            instance = new BlockPlacementScheduler();
        }
        return instance;
    }

    public boolean isBusy() {
        return activeJob != null || !jobQueue.isEmpty();
    }

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

    public void enqueue(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete) {
        if (writes == null || writes.isEmpty()) {
            if (onComplete != null) {
                onComplete.accept(new ExecutionResult(0, 0, 0));
            }
            return;
        }

        jobQueue.addLast(new PlacementJob(new ArrayList<>(writes), onComplete));
        ensureTickRegistered();
    }

    /**
     * 取消当前任务及队列中所有待处理任务。
     *
     * @return 是否取消了至少一个任务
     */
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
            BlockWrite write = activeJob.next();
            if (blockWriter.setBlockAt(write.pos(), write.blockId())) {
                activeJob.success++;
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
        ExecutionResult result = cancelled
            ? ExecutionResult.cancelled(job.success, job.failed, job.writes.size())
            : new ExecutionResult(job.success, job.failed, job.writes.size());
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
        private final Consumer<ExecutionResult> onComplete;
        private int index;
        private int success;
        private int failed;

        private PlacementJob(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete) {
            this.writes = writes;
            this.onComplete = onComplete;
        }

        private boolean hasNext() {
            return index < writes.size();
        }

        private BlockWrite next() {
            return writes.get(index++);
        }
    }
}
