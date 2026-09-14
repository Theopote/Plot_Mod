package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/** 按塔体实际占用的体素膨胀清障，避免整块 bbox 挖空。 */
public final class StructureAwareDecorationClearance {
    private static final String AIR = "minecraft:air";
    private static final int DEFAULT_INFLATE_BLOCKS = 1;

    private StructureAwareDecorationClearance() {
    }

    public static void clearNearStructureBlocks(
            Set<BlockPos> structureBlocks,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            IBlockProjectionService projection) {
        clearNearStructureBlocks(
            structureBlocks,
            DEFAULT_INFLATE_BLOCKS,
            terrain,
            result,
            projection);
    }

    public static void clearNearStructureBlocks(
            Set<BlockPos> structureBlocks,
            int inflateBlocks,
            TerrainSampler terrain,
            PowerLineGenerationResult result,
            IBlockProjectionService projection) {
        if (structureBlocks == null
                || structureBlocks.isEmpty()
                || terrain == null
                || result == null
                || projection == null) {
            return;
        }
        Set<BlockPos> expanded = inflate(structureBlocks, inflateBlocks);
        for (BlockPos pos : expanded) {
            if (!terrain.isRoadClearableDecoration(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            PlacementWriter.put(result, projection, pos, AIR, PlacementCategory.CLEARANCE);
        }
    }

    private static Set<BlockPos> inflate(Set<BlockPos> source, int radius) {
        Set<BlockPos> expanded = new LinkedHashSet<>();
        for (BlockPos origin : source) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        expanded.add(origin.add(dx, dy, dz));
                    }
                }
            }
        }
        return expanded;
    }
}
