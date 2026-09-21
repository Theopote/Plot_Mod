package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockProjectionService;
import com.plot.core.block.BlockSpec;
import com.plot.core.block.BlockStateSanitizer;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.powerline.PowerLineGenerationResult;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * 带优先级的放置写入：高优先级方块不会被低优先级覆盖。
 * <p>
 * 预览期的 {@code previousBlockId} 以首次写入为准；真正 Undo baseline 由
 * {@link BuildPlacementPreparer} 在落地前重采。
 */
public final class PlacementWriter {
    private PlacementWriter() {
    }

    public static void put(
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            BlockPos pos,
            String blockId,
            PlacementCategory category) {
        put(result, projection, pos, blockId, category, null);
    }

    public static void put(
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            BlockPos pos,
            BlockSpec block,
            PlacementCategory category) {
        if (block == null) {
            return;
        }
        put(result, projection, pos, block.toSetBlockArgument(), category, null);
    }

    public static void put(
            PowerLineGenerationResult result,
            IBlockProjectionService projection,
            BlockPos pos,
            String blockId,
            PlacementCategory category,
            Set<BlockPos> structureScratch) {
        if (result == null || pos == null || blockId == null || blockId.isBlank()) {
            return;
        }
        if (category == PlacementCategory.CLEARANCE
                && "minecraft:air".equals(blockId)
                && !result.placementRecords.containsKey(pos)) {
            String current = projection != null ? projection.getBlockIdAt(pos) : "minecraft:air";
            if ("minecraft:air".equals(current)) {
                return;
            }
        }
        PlacementCategory existingCategory = result.placementCategories.get(pos);
        if (existingCategory != null && existingCategory.priority() > category.priority()) {
            return;
        }
        BlockRecord existing = result.placementRecords.get(pos);
        String previous = existing != null
            ? existing.previousBlockId
            : (projection != null ? projection.getBlockIdAt(pos) : "minecraft:air");
        String resolvedBlockId = BlockStateSanitizer.sanitizeSetBlockArgument(
            mergeIronBarsIfNeeded(existing, blockId));
        result.placementRecords.put(pos, new BlockRecord(pos, previous, resolvedBlockId));
        result.placementCategories.put(pos, category);
        if (structureScratch != null && contributesToStructureClearance(category)) {
            structureScratch.add(pos.toImmutable());
        }
    }

    static boolean contributesToStructureClearance(PlacementCategory category) {
        return category == PlacementCategory.LEG
            || category == PlacementCategory.ARM
            || category == PlacementCategory.BRACE
            || category == PlacementCategory.DECORATION
            || category == PlacementCategory.STRUCTURE
            || category == PlacementCategory.FOUNDATION;
    }

    private static String mergeIronBarsIfNeeded(BlockRecord existing, String incomingBlockId) {
        if (existing == null || incomingBlockId == null || incomingBlockId.isBlank()) {
            return incomingBlockId;
        }
        return DirectionalBlockSpecs.mergeIronBarsPlacements(existing.newBlockId, incomingBlockId);
    }
}
