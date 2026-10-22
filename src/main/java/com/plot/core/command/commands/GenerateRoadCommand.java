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
import java.util.function.Function;

/**
 * 道路落地命令（支持撤销/重做）。
 *
 * <p>区分请求集与实际写入集：
 * <ul>
 *   <li>{@code requestedRecords} — 预览阶段计划写入的全部记录</li>
 *   <li>{@code appliedRecords} — 最近一次执行真正成功写入的子集（undo 用）</li>
 *   <li>{@code originalAppliedRecords} — 首次执行成功写入的固定范围（redo 用）</li>
 * </ul>
 * Undo 写回执行时捕获的旧方块，而非预览阶段的 previousBlockId。
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
    private List<BlockRecord> originalAppliedRecords = List.of();
    private final Date timestamp;
    private final BlockWriter blockWriter;
    private final Function<BlockPos, String> blockReader;
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
        this(
            records,
            projectionHandler::setBlockAt,
            projectionHandler::getBlockIdAt,
            true,
            placementScheduler);
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter) {
        this(records, blockWriter, null, false, BlockPlacementScheduler.getInstance());
    }

    GenerateRoadCommand(List<BlockRecord> records, BlockWriter blockWriter, boolean schedulePlacement) {
        this(records, blockWriter, null, schedulePlacement, BlockPlacementScheduler.getInstance());
    }

    /** 测试：可注入 blockReader 与同步/异步模式。 */
    GenerateRoadCommand(
            List<BlockRecord> records,
            BlockWriter blockWriter,
            Function<BlockPos, String> blockReader,
            boolean schedulePlacement,
            IBlockPlacementService placementScheduler) {
        this.requestedRecords = records != null ? List.copyOf(records) : List.of();
        this.timestamp = new Date();
        this.blockWriter = blockWriter;
        this.blockReader = blockReader;
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
        enqueueWritesReverse(recordsForUndo(), result -> {
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
        lastExecutionResult = applySync(prepareExecuteSource(requestedRecords, true), true);
        LOGGER.info("道路落地完成: {}/{}（applied {}）",
            lastExecutionResult.success(),
            lastExecutionResult.total(),
            appliedRecords.size());
    }

    @Override
    public void undo() {
        List<BlockRecord> toUndo = recordsForUndo();
        if (toUndo.isEmpty()) {
            LOGGER.debug("道路撤销跳过：无已应用记录");
            return;
        }
        if (schedulePlacement) {
            enqueueWritesReverse(toUndo, result -> { });
            return;
        }
        lastExecutionResult = applySyncUndo(toUndo);
        LOGGER.info("道路撤销完成: {}/{}", lastExecutionResult.success(), lastExecutionResult.total());
    }

    @Override
    public void redo() {
        List<BlockRecord> toApply = recordsForRedo();
        if (schedulePlacement) {
            enqueueWrites(toApply, true, result -> { });
            return;
        }
        lastExecutionResult = applySync(prepareExecuteSource(toApply, true), true);
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
        return !appliedRecords.isEmpty() || !originalAppliedRecords.isEmpty();
    }

    public List<BlockRecord> getAppliedRecords() {
        return appliedRecords;
    }

    public List<BlockRecord> getOriginalAppliedRecords() {
        return originalAppliedRecords;
    }

    public List<BlockRecord> getRequestedRecords() {
        return requestedRecords;
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecutionResult;
    }

    private List<BlockRecord> recordsForUndo() {
        return !appliedRecords.isEmpty() ? appliedRecords : originalAppliedRecords;
    }

    private List<BlockRecord> recordsForRedo() {
        return !originalAppliedRecords.isEmpty() ? originalAppliedRecords : requestedRecords;
    }

    private void enqueueWrites(
            List<BlockRecord> source,
            boolean applyNewBlocks,
            java.util.function.Consumer<ExecutionResult> onComplete) {
        List<BlockRecord> executeSource = prepareExecuteSource(source, applyNewBlocks);
        List<IBlockPlacementService.BlockWrite> writes = new ArrayList<>(executeSource.size());
        for (BlockRecord record : executeSource) {
            String blockId = applyNewBlocks ? record.newBlockId : record.previousBlockId;
            writes.add(new IBlockPlacementService.BlockWrite(record.pos, blockId));
        }

        if (schedulePlacement) {
            List<BlockRecord> sourceSnapshot = List.copyOf(executeSource);
            placementScheduler.enqueue(writes, result -> {
                lastExecutionResult = toExecutionResult(result);
                if (applyNewBlocks) {
                    captureAppliedFromExecution(sourceSnapshot, lastExecutionResult.successfulWriteIndices());
                }
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

        lastExecutionResult = applySync(executeSource, applyNewBlocks);
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

    private List<BlockRecord> prepareExecuteSource(List<BlockRecord> source, boolean applyNewBlocks) {
        if (!applyNewBlocks) {
            return source;
        }
        List<BlockRecord> executeSource = new ArrayList<>(source.size());
        for (BlockRecord record : source) {
            executeSource.add(recordWithExecuteBaseline(record));
        }
        return executeSource;
    }

    private BlockRecord recordWithExecuteBaseline(BlockRecord plan) {
        if (blockReader == null) {
            return plan;
        }
        String previous = blockReader.apply(plan.pos);
        if (previous == null || previous.isBlank()) {
            previous = plan.previousBlockId;
        }
        return new BlockRecord(plan.pos, previous, plan.newBlockId);
    }

    private void captureAppliedFromExecution(
            List<BlockRecord> executedSource,
            List<Integer> successfulWriteIndices) {
        if (successfulWriteIndices == null || successfulWriteIndices.isEmpty() || executedSource.isEmpty()) {
            appliedRecords = List.of();
            return;
        }
        List<BlockRecord> applied = new ArrayList<>(successfulWriteIndices.size());
        for (int index : successfulWriteIndices) {
            if (index >= 0 && index < executedSource.size()) {
                applied.add(executedSource.get(index));
            }
        }
        appliedRecords = List.copyOf(applied);
        if (originalAppliedRecords.isEmpty() && !appliedRecords.isEmpty()) {
            originalAppliedRecords = List.copyOf(appliedRecords);
        }
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
        if (applyNewBlocks) {
            captureAppliedFromExecution(source, successfulWriteIndices);
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
