package com.plot.api.world;

import com.plot.api.geometry.Vec2d;
import net.minecraft.util.math.BlockPos;

/**
 * 幽灵方块预览服务（无 MinecraftClient 类型暴露）。
 */
public interface IGhostBlockService {
    void clearAllGhostBlocks();

    void addGhostBlock(BlockPos position, String blockType);

    void addGhostBlock(Vec2d position, double height, String blockType);

    int getVisibleGhostBlockCount();
}
