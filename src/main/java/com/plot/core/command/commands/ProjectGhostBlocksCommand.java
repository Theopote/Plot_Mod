package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.Command;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.infrastructure.event.block.BlockProjectionEvent;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.infrastructure.event.block.GhostBlockManager;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 幽灵方块投影命令
 * 支持撤销/重做：
 * - execute: 投影到世界并移除幽灵方块
 * - undo: 恢复投影前的方块
 * - redo: 重新投影
 */
public class ProjectGhostBlocksCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectGhostBlocksCommand.class);

    private final List<GhostSnapshot> sourceGhostBlocks;
    private final List<ProjectedBlockRecord> projectedRecords = new ArrayList<>();
    private final BlockProjectionEvent.ProjectionMode projectionMode;
    private final Integer elevation;
    private final Date timestamp;

    private final GhostBlockManager ghostBlockManager;
    private final BlockProjectionHandler projectionHandler;
    private final EventBus eventBus;

    private boolean initialized = false;
    private int projectedCount = 0;

    public ProjectGhostBlocksCommand(
            List<GhostBlockManager.GhostBlock> ghostBlocks,
            BlockProjectionEvent.ProjectionMode projectionMode,
            Integer elevation
    ) {
        this.sourceGhostBlocks = new ArrayList<>();
        if (ghostBlocks != null) {
            for (GhostBlockManager.GhostBlock ghostBlock : ghostBlocks) {
                if (ghostBlock == null) {
                    continue;
                }
                Vec2d pos = ghostBlock.getPosition();
                this.sourceGhostBlocks.add(new GhostSnapshot(
                        ghostBlock.getId(),
                        pos.x,
                        pos.y,
                        ghostBlock.getHeight(),
                        ghostBlock.getBlockType()
                ));
            }
        }

        this.projectionMode = projectionMode == null
                ? BlockProjectionEvent.ProjectionMode.GROUND
                : projectionMode;
        this.elevation = elevation;
        this.timestamp = new Date();

        this.ghostBlockManager = GhostBlockManager.getInstance();
        this.projectionHandler = BlockProjectionHandler.getInstance();
        this.eventBus = EventBus.getInstance();
    }

    @Override
    public void execute() {
        if (!initialized) {
            executeInitialProjection();
            return;
        }

        int successCount = 0;
        for (ProjectedBlockRecord record : projectedRecords) {
            if (projectionHandler.setBlockAt(record.finalPos, record.newBlockId)) {
                successCount++;
            }
        }
        projectedCount = successCount;
        LOGGER.info("重做投影完成: {}/{}", successCount, projectedRecords.size());
        eventBus.publish(new Events.WarningEvent("ProjectGhostBlocksCommand",
                String.format("已重做投影 %d 个方块", successCount)));
    }

    @Override
    public void undo() {
        int restoredCount = 0;
        for (int i = projectedRecords.size() - 1; i >= 0; i--) {
            ProjectedBlockRecord record = projectedRecords.get(i);
            if (projectionHandler.setBlockAt(record.finalPos, record.previousBlockId)) {
                restoredCount++;
            }
        }

        LOGGER.info("撤销投影完成: {}/{}", restoredCount, projectedRecords.size());
        eventBus.publish(new Events.WarningEvent("ProjectGhostBlocksCommand",
                String.format("已撤销投影 %d 个方块", restoredCount)));
    }

    @Override
    public void redo() {
        execute();
    }

    @Override
    public String getDescription() {
        return String.format("投影方块 (%d)", projectedCount);
    }

    @Override
    public String getDetailedDescription() {
        return String.format("投影模式: %s, 标高: %s, 成功投影: %d/%d",
                projectionMode,
                elevation == null ? "自动" : elevation,
                projectedCount,
                sourceGhostBlocks.size());
    }

    @Override
    public Date getTimestamp() {
        return timestamp;
    }

    public int getProjectedCount() {
        return projectedCount;
    }

    private void executeInitialProjection() {
        projectedRecords.clear();

        int skippedCount = 0;
        for (GhostSnapshot snapshot : sourceGhostBlocks) {
            BlockProjectionHandler.ProjectionResult result = projectionHandler.projectBlockWithResult(
                    snapshot.blockType,
                    snapshot.x,
                    snapshot.height,
                    snapshot.z,
                    projectionMode,
                    elevation,
                    false
            );

            if (!result.isSuccess() || result.getFinalPos() == null || result.getPreviousBlockId() == null) {
                skippedCount++;
                continue;
            }

            projectedRecords.add(new ProjectedBlockRecord(
                    snapshot.ghostId,
                    result.getFinalPos(),
                    result.getPreviousBlockId(),
                    result.getNormalizedBlockId()
            ));

            ghostBlockManager.removeGhostBlock(snapshot.ghostId);
        }

        initialized = true;
        projectedCount = projectedRecords.size();

        if (projectedCount == 0) {
            throw new IllegalStateException("没有可投影的幽灵方块（可能距离过远、区块未加载或不在创造模式）");
        }

        LOGGER.info("投影命令执行完成: 成功 {}, 跳过 {}", projectedCount, skippedCount);
        eventBus.publish(new Events.WarningEvent("ProjectGhostBlocksCommand",
                String.format("已成功投影 %d 个方块，跳过 %d 个", projectedCount, skippedCount)));
    }

    private static class GhostSnapshot {
        private final String ghostId;
        private final double x;
        private final double z;
        private final double height;
        private final String blockType;

        private GhostSnapshot(String ghostId, double x, double z, double height, String blockType) {
            this.ghostId = ghostId;
            this.x = x;
            this.z = z;
            this.height = height;
            this.blockType = blockType;
        }
    }

    private static class ProjectedBlockRecord {
        private final String ghostId;
        private final BlockPos finalPos;
        private final String previousBlockId;
        private final String newBlockId;

        private ProjectedBlockRecord(String ghostId, BlockPos finalPos, String previousBlockId, String newBlockId) {
            this.ghostId = ghostId;
            this.finalPos = finalPos;
            this.previousBlockId = previousBlockId;
            this.newBlockId = newBlockId;
        }
    }
}