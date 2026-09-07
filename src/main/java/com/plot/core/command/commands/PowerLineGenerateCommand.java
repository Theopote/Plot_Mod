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
 */
public class PowerLineGenerateCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(PowerLineGenerateCommand.class);

    public record ExecutionResult(int success, int failed, int total, boolean cancelled) {
        public ExecutionResult(int success, int failed, int total) {
            this(success, failed, total, false);
        }

        public boolean isFullSuccess() {
            return !cancelled && total > 0 && failed == 0 && success == total;
        }
    }

    private final List<BlockRecord> records;
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
        this.records = records != null ? new ArrayList<>(records) : new ArrayList<>();
        this.timestamp = new Date();
        this.schedulePlacement = true;
        this.placementScheduler = placementScheduler != null
            ? placementScheduler
            : BlockPlacementScheduler.getInstance();
    }

    public void executeScheduled(Runnable onComplete) {
        enqueueWrites(records, true, onComplete);
    }

    public void undoScheduled(Runnable onComplete) {
        enqueueWritesReverse(records, onComplete);
    }

    @Override
    public void execute() {
        enqueueWrites(records, true, () -> { });
    }

    @Override
    public void undo() {
        enqueueWritesReverse(records, () -> { });
    }

    @Override
    public void redo() {
        execute();
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("plugin.powerline.history.generate", records.size());
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("plugin.powerline.history.generate.detail", records.size());
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    private void enqueueWrites(List<BlockRecord> source, boolean applyNewBlocks, Runnable onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (BlockRecord record : source) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, blockId));
        }
        placementScheduler.enqueue(writes, result -> {
            lastExecutionResult = new ExecutionResult(
                result.success(),
                result.failed(),
                result.total(),
                result.cancelled());
            LOGGER.info("电力线路{}完成: {}/{}",
                applyNewBlocks ? "落地" : "撤销",
                lastExecutionResult.success(),
                lastExecutionResult.total());
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
            lastExecutionResult = new ExecutionResult(
                result.success(),
                result.failed(),
                result.total(),
                result.cancelled());
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}
