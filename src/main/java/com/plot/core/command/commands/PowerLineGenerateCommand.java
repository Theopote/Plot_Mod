package com.plot.core.command.commands;

import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.Command;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 电力线路落地命令（支持撤销/重做）。
 *
 * <p>Undo / Redo 只操作 {@code appliedRecords}，避免部分失败时撤销未改动的格子。
 */
public class PowerLineGenerateCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(PowerLineGenerateCommand.class);

    public record ExecutionResult(int success, int failed, int total, boolean cancelled, List<Integer> successfulWriteIndices) {
        public ExecutionResult(int success, int failed, int total) {
            this(success, failed, total, false, List.of());
        }

        public ExecutionResult(int success, int failed, int total, boolean cancelled) {
            this(success, failed, total, cancelled, List.of());
        }

        public boolean isFullSuccess() {
            return !cancelled && total > 0 && failed == 0 && success == total;
        }
    }

    private final List<BlockRecord> requestedRecords;
    private List<BlockRecord> appliedRecords = List.of();
    private final Date timestamp;
    private final boolean schedulePlacement;
    private final IBlockPlacementService placementScheduler;
    private ExecutionResult lastExecutionResult;

    public PowerLineGenerateCommand(List<BlockRecord> records) {
        this(records, BlockProjectionHandler.getInstance(), BlockPlacementScheduler.getInstance());
    }

    public PowerLineGenerateCommand(
            List<BlockRecord> records,
            IBlockProjectionService projectionHandler,
            IBlockPlacementService placementScheduler) {
        this.requestedRecords = records != null ? List.copyOf(records) : List.of();
        this.timestamp = new Date();
        this.schedulePlacement = true;
        this.placementScheduler = placementScheduler != null
            ? placementScheduler
            : BlockPlacementScheduler.getInstance();
    }

    public void executeScheduled(Runnable onComplete) {
        enqueueWrites(requestedRecords, true, () -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void undoScheduled(Runnable onComplete) {
        enqueueWritesReverse(appliedRecords, onComplete);
    }

    /** 按记录中的 previousBlockId 恢复方块（用于移除已落地结构）。 */
    public void restoreBlocksScheduled(Runnable onComplete) {
        enqueueWritesReverse(requestedRecords, onComplete);
    }

    /** 按记录中的 newBlockId 重新落地（用于撤销移除）。 */
    public void reapplyBlocksScheduled(Runnable onComplete) {
        enqueueWrites(requestedRecords, true, onComplete);
    }

    @Override
    public void execute() {
        enqueueWrites(requestedRecords, true, () -> { });
    }

    @Override
    public void undo() {
        if (appliedRecords.isEmpty()) {
            return;
        }
        enqueueWritesReverse(appliedRecords, () -> { });
    }

    @Override
    public void redo() {
        List<BlockRecord> toApply = appliedRecords.isEmpty() ? requestedRecords : appliedRecords;
        enqueueWrites(toApply, true, () -> { });
    }

    @Override
    public String getDescription() {
        int count = hasAppliedRecords() ? appliedRecords.size() : requestedRecords.size();
        return PlotI18n.tr("plugin.powerline.history.generate", count);
    }

    @Override
    public String getDetailedDescription() {
        int count = hasAppliedRecords() ? appliedRecords.size() : requestedRecords.size();
        return PlotI18n.tr("plugin.powerline.history.generate.detail", count);
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    public boolean hasAppliedRecords() {
        return !appliedRecords.isEmpty();
    }

    public int getAppliedRecordCount() {
        return appliedRecords.size();
    }

    public List<BlockRecord> getAppliedRecords() {
        return List.copyOf(appliedRecords);
    }

    private void enqueueWrites(List<BlockRecord> source, boolean applyNewBlocks, Runnable onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (BlockRecord record : source) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, blockId));
        }
        List<BlockRecord> sourceSnapshot = List.copyOf(source);
        placementScheduler.enqueue(writes, result -> {
            lastExecutionResult = toExecutionResult(result);
            captureAppliedFromIndices(sourceSnapshot, lastExecutionResult.successfulWriteIndices());
            LOGGER.info("电力线路{}完成: {}/{} 成功, {} 失败（applied {}）",
                applyNewBlocks ? "落地" : "撤销",
                lastExecutionResult.success(),
                lastExecutionResult.total(),
                lastExecutionResult.failed(),
                appliedRecords.size());
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void enqueueWritesReverse(List<BlockRecord> source, Runnable onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (int i = source.size() - 1; i >= 0; i--) {
            BlockRecord record = source.get(i);
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, record.previousBlockId));
        }
        placementScheduler.enqueue(writes, result -> {
            lastExecutionResult = toExecutionResult(result);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void captureAppliedFromIndices(List<BlockRecord> source, List<Integer> successfulWriteIndices) {
        if (successfulWriteIndices == null || successfulWriteIndices.isEmpty() || source.isEmpty()) {
            appliedRecords = List.of();
            return;
        }
        List<BlockRecord> applied = new ArrayList<>(successfulWriteIndices.size());
        for (int index : successfulWriteIndices) {
            if (index >= 0 && index < source.size()) {
                applied.add(source.get(index));
            }
        }
        appliedRecords = List.copyOf(applied);
    }

    private static ExecutionResult toExecutionResult(IBlockPlacementService.ExecutionResult result) {
        return new ExecutionResult(
            result.success(),
            result.failed(),
            result.total(),
            result.cancelled(),
            result.successfulWriteIndices());
    }
}
