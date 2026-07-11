package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.infrastructure.event.block.BlockPlacementScheduler;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.utils.PlotI18n;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 道路落地命令（支持撤销/重做）
 */
public class GenerateRoadCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateRoadCommand.class);

    public static class BlockRecord {
        public final BlockPos pos;
        public final String previousBlockId;
        public final String newBlockId;

        public BlockRecord(BlockPos pos, String previousBlockId, String newBlockId) {
            this.pos = pos;
            this.previousBlockId = previousBlockId;
            this.newBlockId = newBlockId;
        }
    }

    public record ExecutionResult(int success, int failed, int total) {
        public boolean isFullSuccess() {
            return total > 0 && failed == 0 && success == total;
        }

        public boolean isTotalFailure() {
            return total > 0 && success == 0;
        }
    }

    private final List<BlockRecord> records;
    private final Date timestamp;
    private final BlockWriter blockWriter;
    private final boolean schedulePlacement;
    private ExecutionResult lastExecutionResult;

    @FunctionalInterface
    interface BlockWriter {
        boolean setBlockAt(BlockPos pos, String blockId);
    }

    public GenerateRoadCommand(List<BlockRecord> records) {
        this(records, BlockProjectionHandler.getInstance()::setBlockAt, true);
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter) {
        this(records, blockWriter, false);
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter, boolean schedulePlacement) {
        this.records = records != null ? new ArrayList<>(records) : new ArrayList<>();
        this.timestamp = new Date();
        this.blockWriter = blockWriter;
        this.schedulePlacement = schedulePlacement;
    }

    public void executeScheduled(Runnable onComplete) {
        enqueueWrites(records, true, result -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void undoScheduled(Runnable onComplete) {
        enqueueWritesReverse(records, result -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    @Override
    public void execute() {
        if (schedulePlacement) {
            enqueueWrites(records, true, result -> { });
            return;
        }
        lastExecutionResult = applySync(records, true);
        LOGGER.info("道路落地完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public void undo() {
        if (schedulePlacement) {
            enqueueWritesReverse(records, result -> { });
            return;
        }
        lastExecutionResult = applySyncUndo(records);
        LOGGER.info("道路撤销完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public void redo() {
        execute();
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("plugin.road.history.generate", records.size());
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("plugin.road.history.generate.detail", records.size());
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public int getRecordCount() {
        return records.size();
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    private void enqueueWrites(List<BlockRecord> source, boolean applyNewBlocks, java.util.function.Consumer<ExecutionResult> onComplete) {
        List<BlockPlacementScheduler.BlockWrite> writes = new ArrayList<>(source.size());
        for (BlockRecord record : source) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            writes.add(new BlockPlacementScheduler.BlockWrite(record.pos, blockId));
        }

        if (schedulePlacement) {
            BlockPlacementScheduler.getInstance().enqueue(writes, result -> {
                lastExecutionResult = toExecutionResult(result);
                LOGGER.info("道路{}完成: {}/{} 成功, {} 失败",
                    applyNewBlocks ? "落地" : "撤销",
                    lastExecutionResult.success(),
                    lastExecutionResult.total(),
                    lastExecutionResult.failed());
                if (onComplete != null) {
                    onComplete.accept(lastExecutionResult);
                }
            });
            return;
        }

        lastExecutionResult = applySync(records, applyNewBlocks);
        if (onComplete != null) {
            onComplete.accept(lastExecutionResult);
        }
    }

    private void enqueueWritesReverse(List<BlockRecord> source, java.util.function.Consumer<ExecutionResult> onComplete) {
        List<BlockPlacementScheduler.BlockWrite> writes = new ArrayList<>(source.size());
        for (int i = source.size() - 1; i >= 0; i--) {
            BlockRecord record = source.get(i);
            writes.add(new BlockPlacementScheduler.BlockWrite(record.pos, record.previousBlockId));
        }

        if (schedulePlacement) {
            BlockPlacementScheduler.getInstance().enqueue(writes, result -> {
                lastExecutionResult = toExecutionResult(result);
                LOGGER.info("道路撤销完成: {}/{} 成功, {} 失败",
                    lastExecutionResult.success(),
                    lastExecutionResult.total(),
                    lastExecutionResult.failed());
                if (onComplete != null) {
                    onComplete.accept(lastExecutionResult);
                }
            });
            return;
        }

        lastExecutionResult = applySyncUndo(source);
        if (onComplete != null) {
            onComplete.accept(lastExecutionResult);
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

    private static ExecutionResult toExecutionResult(BlockPlacementScheduler.ExecutionResult result) {
        return new ExecutionResult(result.success(), result.failed(), result.total());
    }
}
