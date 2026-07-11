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

    private final Deque<PlacementJob> jobQueue = new ArrayDeque<>();
    private PlacementJob activeJob;
    private boolean tickRegistered;

    public record BlockWrite(BlockPos pos, String blockId) {
    }

    public record ExecutionResult(int success, int failed, int total) {
        public boolean isFullSuccess() {
            return total > 0 && failed == 0 && success == total;
        }

        public boolean isTotalFailure() {
            return total > 0 && success == 0;
        }
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

        BlockProjectionHandler handler = BlockProjectionHandler.getInstance();
        int budget = BLOCKS_PER_TICK;
        while (budget > 0 && activeJob.hasNext()) {
            BlockWrite write = activeJob.next();
            if (handler.setBlockAt(write.pos(), write.blockId())) {
                activeJob.success++;
            } else {
                activeJob.failed++;
            }
            budget--;
        }

        if (!activeJob.hasNext()) {
            ExecutionResult result = new ExecutionResult(
                activeJob.success,
                activeJob.failed,
                activeJob.writes.size()
            );
            LOGGER.info("方块放置批次完成: {}/{} 成功, {} 失败",
                result.success(), result.total(), result.failed());
            Consumer<ExecutionResult> callback = activeJob.onComplete;
            activeJob = null;
            if (callback != null) {
                callback.accept(result);
            }
        }
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
