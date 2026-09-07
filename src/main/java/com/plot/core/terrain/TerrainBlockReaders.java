package com.plot.core.terrain;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * {@link TerrainBlockReader} 适配器。
 */
public final class TerrainBlockReaders {
    private TerrainBlockReaders() {
    }

    public static TerrainBlockReader of(World world) {
        if (world == null) {
            return empty();
        }
        return new TerrainBlockReader() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return world.getBlockState(pos);
            }

            @Override
            public boolean isChunkLoaded(int worldX, int worldZ) {
                return world.isChunkLoaded(worldX >> 4, worldZ >> 4);
            }
        };
    }

    public static TerrainBlockReader empty() {
        return pos -> null;
    }
}
