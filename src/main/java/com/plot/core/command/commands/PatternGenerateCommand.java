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
 * 铺装图案落地命令（支持撤销/重做）。
 *
 * <p>Undo / Redo 只操作 {@code appliedRecords}，避免部分失败时撤销未改动的格子。
 */
public class PatternGenerateCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(PatternGenerateCommand.class);

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

        public boolean isTotalFailure() {
            return !cancelled && total > 0 && success == 0;
        }
    }

    @FunctionalInterface
    interface BlockWriter {
        boolean setBlockAt(net.minecraft.util.math.BlockPos pos, String blockId);
    }

    private final List<BlockRecord> requestedRecords;
    private List<BlockRecord> appliedRecords = List.of();
    private final Date timestamp;
    private final BlockWriter blockWriter;
    private final boolean schedulePlacement;
    private final IBlockPlacementService placementScheduler;
    private ExecutionResult lastExecutionResult;

    public PatternGenerateCommand(List<BlockRecord> records) {
        this(records, BlockProjectionHandler.getInstance(), BlockPlacementScheduler.getInstance());
    }

    public PatternGenerateCommand(
            List<BlockRecord> records,
            IBlockProjectionService projectionHandler,
            IBlockPlacementService placementScheduler) {
        this(records, projectionHandler::setBlockAt, true, placementScheduler);
    }

    PatternGenerateCommand(List<BlockRecord> records, BlockWriter blockWriter) {
        this(records, blockWriter, false, BlockPlacementScheduler.getInstance());
    }

    private PatternGenerateCommand(
            List<BlockRecord> records,
            BlockWriter blockWriter,
            boolean schedulePlacement,
            IBlockPlacementService placementScheduler) {
        this.requestedRecords = records != null ? List.copyOf(records) : List.of();
        this.timestamp = new Date();
        this.blockWriter = blockWriter;
        this.schedulePlacement = schedulePlacement;
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

    @Override
    public void execute() {
        if (schedulePlacement) {
            enqueueWrites(requestedRecords, true, () -> { });
            return;
        }
        lastExecutionResult = applySync(requestedRecords, true);
        captureAppliedFromIndices(requestedRecords, allIndices(requestedRecords.size()));
        LOGGER.info("铺装图案落地完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public void undo() {
        if (appliedRecords.isEmpty()) {
            return;
        }
        if (schedulePlacement) {
            enqueueWritesReverse(appliedRecords, () -> { });
            return;
        }
        lastExecutionResult = applySyncUndo(appliedRecords);
        LOGGER.info("铺装图案撤销完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public void redo() {
        List<BlockRecord> toApply = appliedRecords.isEmpty() ? requestedRecords : appliedRecords;
        if (schedulePlacement) {
            enqueueWrites(toApply, true, () -> { });
            return;
        }
        lastExecutionResult = applySync(toApply, true);
        captureAppliedFromIndices(toApply, allIndices(toApply.size()));
    }

    @Override
    public String getDescription() {
        int count = hasAppliedRecords() ? appliedRecords.size() : requestedRecords.size();
        return PlotI18n.tr("plugin.pattern.history.generate", count);
    }

    @Override
    public String getDetailedDescription() {
        int count = hasAppliedRecords() ? appliedRecords.size() : requestedRecords.size();
        return PlotI18n.tr("plugin.pattern.history.generate.detail", count);
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public int getRecordCount() {
        return requestedRecords.size();
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    public boolean hasAppliedRecords() {
        return !appliedRecords.isEmpty();
    }

    private void enqueueWrites(
            List<BlockRecord> source,
            boolean applyNewBlocks,
            Runnable onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (BlockRecord record : source) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, blockId));
        }

        if (schedulePlacement) {
            List<BlockRecord> sourceSnapshot = List.copyOf(source);
            placementScheduler.enqueue(writes, result -> {
                lastExecutionResult = toExecutionResult(result);
                captureAppliedFromIndices(sourceSnapshot, lastExecutionResult.successfulWriteIndices());
                LOGGER.info("铺装图案{}完成: {}/{} 成功, {} 失败（applied {}）",
                    applyNewBlocks ? "落地" : "撤销",
                    lastExecutionResult.success(),
                    lastExecutionResult.total(),
                    lastExecutionResult.failed(),
                    appliedRecords.size());
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            return;
        }

        lastExecutionResult = applySync(source, applyNewBlocks);
        captureAppliedFromIndices(source, allIndices(source.size()));
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void enqueueWritesReverse(List<BlockRecord> source, Runnable onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (int i = source.size() - 1; i >= 0; i--) {
            BlockRecord record = source.get(i);
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, record.previousBlockId));
        }

        if (schedulePlacement) {
            placementScheduler.enqueue(writes, result -> {
                lastExecutionResult = toExecutionResult(result);
                LOGGER.info("铺装图案撤销完成: {}/{} 成功, {} 失败",
                    lastExecutionResult.success(),
                    lastExecutionResult.total(),
                    lastExecutionResult.failed());
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            return;
        }

        lastExecutionResult = applySyncUndo(source);
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private ExecutionResult applySync(List<BlockRecord> source, boolean applyNewBlocks) {
        int success = 0;
        for (BlockRecord record : source) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            if (blockWriter.setBlockAt(record.pos, blockId)) {
                success++;
            }
        }
        return new ExecutionResult(success, source.size() - success, source.size());
    }

    private ExecutionResult applySyncUndo(List<BlockRecord> source) {
        int success = 0;
        for (int i = source.size() - 1; i >= 0; i--) {
            BlockRecord record = source.get(i);
            if (blockWriter.setBlockAt(record.pos, record.previousBlockId)) {
                success++;
            }
        }
        return new ExecutionResult(success, source.size() - success, source.size());
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

    private static List<Integer> allIndices(int size) {
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        return indices;
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
