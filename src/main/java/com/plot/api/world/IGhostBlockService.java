package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * 幽灵方块预览服务（无 MinecraftClient 类型暴露）。
 * <p>
 * 各功能模块应使用独立 {@code ownerId}，通过 {@link #clearGhostBlocks(String)} /
 * {@link #replaceGhostBlocks(String, Map)} 管理自己的预览，避免误清其它模块的 ghost。
 */
public interface IGhostBlockService {
    /** 清空所有 owner 的幽灵方块（全局操作，如 Esc、关闭画布）。 */
    void clearAllGhostBlocks();

    /** 仅清空指定 owner 的幽灵方块。 */
    default void clearGhostBlocks(String ownerId) {
    }

    /**
     * 原子替换指定 owner 的预览方块（先清后加）。
     */
    default void replaceGhostBlocks(String ownerId, Map<BlockPos, String> blocks) {
        clearGhostBlocks(ownerId);
        addGhostBlocks(ownerId, blocks);
    }

    default void addGhostBlock(BlockPos position, String blockType) {
        addGhostBlock(GhostBlockOwners.LEGACY, position, blockType);
    }

    default void addGhostBlock(Vec2d position, double height, String blockType) {
        addGhostBlock(GhostBlockOwners.LEGACY, position, height, blockType);
    }

    default void addGhostBlock(String ownerId, BlockPos position, String blockType) {
    }

    default void addGhostBlock(String ownerId, Vec2d position, double height, String blockType) {
    }

    default void addGhostBlocks(Map<BlockPos, String> blocks) {
        addGhostBlocks(GhostBlockOwners.LEGACY, blocks);
    }

    default void addGhostBlocks(String ownerId, Map<BlockPos, String> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                addGhostBlock(ownerId, entry.getKey(), entry.getValue());
            }
        }
    }

    int getVisibleGhostBlockCount();

    /** 指定 owner 的可见幽灵方块数量；默认实现返回 0。 */
    default int getVisibleGhostBlockCount(String ownerId) {
        return 0;
    }
}
