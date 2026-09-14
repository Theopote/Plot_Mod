package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 将预览阶段的放置意图转换为可执行 {@link BlockRecord}。
 * <p>
 * 预览生成时写入的 {@code previousBlockId} 仅作参考，Undo baseline 必须在真正落地前
 * 重新读取当前世界状态。
 */
public final class BuildPlacementPreparer {
    private BuildPlacementPreparer() {
    }

    public static List<BlockRecord> prepareExecutionRecords(
            Collection<BlockRecord> previewRecords,
            IBlockProjectionService projection) {
        if (previewRecords == null || previewRecords.isEmpty()) {
            return List.of();
        }
        if (projection == null) {
            throw new IllegalArgumentException("projection cannot be null");
        }
        List<BlockRecord> executionRecords = new ArrayList<>(previewRecords.size());
        for (BlockRecord preview : previewRecords) {
            if (preview == null || preview.pos == null) {
                continue;
            }
            String actualPrevious = projection.getBlockIdAt(preview.pos);
            executionRecords.add(new BlockRecord(preview.pos, actualPrevious, preview.newBlockId));
        }
        return List.copyOf(executionRecords);
    }
}
