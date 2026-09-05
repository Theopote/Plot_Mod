package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * 幽灵方块预览服务（无 MinecraftClient 类型暴露）。
 */
public interface IGhostBlockService {
    void clearAllGhostBlocks();

    void addGhostBlock(BlockPos position, String blockType);

    void addGhostBlock(Vec2d position, double height, String blockType);

    /**
     * 批量添加（默认逐个委托）；片区预览可覆盖以降低开销。
     */
    default void addGhostBlocks(Map<BlockPos, String> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                addGhostBlock(entry.getKey(), entry.getValue());
            }
        }
    }

    int getVisibleGhostBlockCount();
}
