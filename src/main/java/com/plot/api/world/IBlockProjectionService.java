package com.plot.api.world;

import net.minecraft.util.math.BlockPos;

/**
 * 世界方块读写与投影就绪检查（无 MinecraftClient 类型暴露）。
 */
public interface IBlockProjectionService {
    PlacementReadiness checkWorldModificationReadiness();

    String getBlockIdAt(BlockPos pos);

    boolean setBlockAt(BlockPos pos, String blockId);
}
