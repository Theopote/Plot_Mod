package com.plot.api.world;

import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;

/**
 * 分帧世界落地调度（无 MinecraftClient 类型暴露）。
 */
public interface IBlockPlacementService {
    record BlockWrite(BlockPos pos, String blockId) {
    }

    record ExecutionResult(int success, int failed, int total, boolean cancelled, List<Integer> successfulWriteIndices) {
        public ExecutionResult(int success, int failed, int total) {
            this(success, failed, total, false, List.of());
        }

        public ExecutionResult(int success, int failed, int total, boolean cancelled) {
            this(success, failed, total, cancelled, List.of());
        }

        public static ExecutionResult cancelled(int success, int failed, int total) {
            return new ExecutionResult(success, failed, total, true, List.of());
        }

        public static ExecutionResult cancelled(int success, int failed, int total, List<Integer> successfulWriteIndices) {
            return new ExecutionResult(
                success,
                failed,
                total,
                true,
                successfulWriteIndices != null ? List.copyOf(successfulWriteIndices) : List.of());
        }

        public boolean hasTrackedSuccessfulWrites() {
            return !successfulWriteIndices.isEmpty();
        }

        public boolean isFullSuccess() {
            return !cancelled && total > 0 && failed == 0 && success == total;
        }

        public boolean isTotalFailure() {
            return !cancelled && total > 0 && success == 0;
        }
    }

    record ProgressSnapshot(int processed, int total, int success, int failed) {
    }

    boolean isBusy();

    ProgressSnapshot getProgressSnapshot();

    /**
     * @return 是否取消了至少一个任务
     */
    boolean cancelAll();

    void enqueue(List<BlockWrite> writes, Consumer<ExecutionResult> onComplete);
}
