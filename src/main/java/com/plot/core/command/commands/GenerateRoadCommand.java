package com.plot.core.command.commands;

import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
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
 * 道路落地命令（支持撤销/重做）。
 *
 * <p>区分请求集与实际写入集：
 * <ul>
 *   <li>{@code requestedRecords} — 预览阶段计划写入的全部记录</li>
 *   <li>{@code appliedRecords} — 执行后真正成功写入世界的子集</li>
 * </ul>
 * Undo / Redo 只操作 {@code appliedRecords}，避免恢复从未改动的格子。
 */
public class GenerateRoadCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateRoadCommand.class);

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

    private final List<BlockRecord> requestedRecords;
    private List<BlockRecord> appliedRecords = List.of();
    private final Date timestamp;
    private final BlockWriter blockWriter;
    private final boolean schedulePlacement;
    private final IBlockPlacementService placementScheduler;
    private ExecutionResult lastExecutionResult;

    @FunctionalInterface
    interface BlockWriter {
        boolean setBlockAt(BlockPos pos, String blockId);
    }

    public GenerateRoadCommand(List<BlockRecord> records) {
        this(records, BlockProjectionHandler.getInstance(), BlockPlacementScheduler.getInstance());
    }

    /** 插件经 PluginContext 注入世界服务时使用。 */
    public GenerateRoadCommand(
            List<BlockRecord> records,
            IBlockProjectionService projectionHandler,
            IBlockPlacementService placementScheduler) {
        this(records, projectionHandler::setBlockAt, true, placementScheduler);
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter) {
        this(records, blockWriter, false, BlockPlacementScheduler.getInstance());
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter, boolean schedulePlacement) {
        this(records, blockWriter, schedulePlacement, BlockPlacementScheduler.getInstance());
    }

    private GenerateRoadCommand(
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
        enqueueWrites(requestedRecords, true, result -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void undoScheduled(Runnable onComplete) {
        enqueueWritesReverse(appliedRecords, result -> {
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    @Override
    public void execute() {
        if (schedulePlacement) {
            enqueueWrites(requestedRecords, true, result -> { });
            return;
        }
        lastExecutionResult = applySync(requestedRecords, true);
        captureAppliedFromIndices(requestedRecords, lastExecutionResult.successfulWriteIndices());
        LOGGER.info("道路落地完成: {}/{}（applied {}）",
            lastExecutionResult.success(),
            lastExecutionResult.total(),
            appliedRecords.size());
    }

    @Override
    public void undo() {
        if (appliedRecords.isEmpty()) {
            LOGGER.debug("道路撤销跳过：无已应用记录");
            return;
        }
        if (schedulePlacement) {
            enqueueWritesReverse(appliedRecords, result -> { });
            return;
        }
        lastExecutionResult = applySyncUndo(appliedRecords);
        LOGGER.info("道路撤销完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public void redo() {
        List<BlockRecord> toApply = appliedRecords.isEmpty() ? requestedRecords : appliedRecords;
        if (schedulePlacement) {
            enqueueWrites(toApply, true, result -> { });
            return;
        }
        lastExecutionResult = applySync(toApply, true);
        captureAppliedFromIndices(toApply, lastExecutionResult.successfulWriteIndices());
        LOGGER.info("道路重做完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public String getDescription() {
        int count = hasAppliedRecords() ? appliedRecords.size() : requestedRecords.size();
        return PlotI18n.tr("plugin.road.history.generate", count);
    }

    @Override
    public String getDetailedDescription() {
        int count = hasAppliedRecords() ? appliedRecords.size() : requestedRecords.size();
        return PlotI18n.tr("plugin.road.history.generate.detail", count);
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    /** 请求写入的记录数（预览全集）。 */
    public int getRecordCount() {
        return requestedRecords.size();
    }

    /** 实际成功写入的记录数。 */
    public int getAppliedRecordCount() {
        return appliedRecords.size();
    }

    public boolean hasAppliedRecords() {
        return !appliedRecords.isEmpty();
    }

    public List<BlockRecord> getAppliedRecords() {
        return appliedRecords;
    }

    public List<BlockRecord> getRequestedRecords() {
        return requestedRecords;
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    private void enqueueWrites(
            List<BlockRecord> source,
            boolean applyNewBlocks,
            java.util.function.Consumer<ExecutionResult> onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (BlockRecord record : source) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, blockId));
        }

        if (schedulePlacement) {
            // 异步路径：成功索引相对本次 source；首次落地 source=requested，redo 时可能是 applied。
            List<BlockRecord> sourceSnapshot = List.copyOf(source);
            placementScheduler.enqueue(writes, result -> {
                lastExecutionResult = toExecutionResult(result);
                captureAppliedFromIndices(sourceSnapshot, lastExecutionResult.successfulWriteIndices());
                LOGGER.info("道路{}完成: {}/{} 成功, {} 失败（applied {}）",
                    applyNewBlocks ? "落地" : "撤销",
                    lastExecutionResult.success(),
                    lastExecutionResult.total(),
                    lastExecutionResult.failed(),
                    appliedRecords.size());
                if (onComplete != null) {
                    onComplete.accept(lastExecutionResult);
                }
            });
            return;
        }

        lastExecutionResult = applySync(source, applyNewBlocks);
        if (applyNewBlocks) {
            captureAppliedFromIndices(source, lastExecutionResult.successfulWriteIndices());
        }
        if (onComplete != null) {
            onComplete.accept(lastExecutionResult);
        }
    }

    private void enqueueWritesReverse(
            List<BlockRecord> source,
            java.util.function.Consumer<ExecutionResult> onComplete) {
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(source.size());
        for (int i = source.size() - 1; i >= 0; i--) {
            BlockRecord record = source.get(i);
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, record.previousBlockId));
        }

        if (schedulePlacement) {
            placementScheduler.enqueue(writes, result -> {
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

    private ExecutionResult applySync(List<BlockRecord> source, boolean applyNewBlocks) {
        int success = 0;
        List<Integer> successfulWriteIndices = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            BlockRecord record = source.get(i);
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            if (blockWriter.setBlockAt(record.pos, blockId)) {
                success++;
                successfulWriteIndices.add(i);
            }
        }
        return new ExecutionResult(success, source.size() - success, source.size(), false, successfulWriteIndices);
    }

    private ExecutionResult applySyncUndo(List<BlockRecord> source) {
        int success = 0;
        List<Integer> successfulWriteIndices = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0; i--) {
            BlockRecord record = source.get(i);
            if (blockWriter.setBlockAt(record.pos, record.previousBlockId)) {
                success++;
                successfulWriteIndices.add(i);
            }
        }
        return new ExecutionResult(success, source.size() - success, source.size(), false, successfulWriteIndices);
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
