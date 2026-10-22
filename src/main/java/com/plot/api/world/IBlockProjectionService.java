package com.plot.api.world;

import net.minecraft.util.math.BlockPos;

/**
 * 世界方块读写与投影就绪检查（无 MinecraftClient 类型暴露）。
 */
public interface IBlockProjectionService {
    PlacementReadiness checkWorldModificationReadiness();

    String getBlockIdAt(BlockPos pos);

    /** 含 BlockState 的 /setblock 参数字符串，供施工撤销还原原始方块。 */
    default String getSetBlockArgumentAt(BlockPos pos) {
        return getBlockIdAt(pos);
    }

    boolean setBlockAt(BlockPos pos, String blockId);
}
