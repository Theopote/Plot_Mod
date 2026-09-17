package com.plot.plugin.pattern.ui;

import com.plot.core.command.BlockRecord;
import com.plot.core.material.BlockColorRegistry;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** 将 placement 记录映射到预览像素桶。 */
final class PatternPreviewBucketMapper {
    private PatternPreviewBucketMapper() {
    }

    static PatternPreviewBuckets aggregate(
            Map<BlockPos, BlockRecord> records,
            PatternPreviewRenderer.Bounds bounds,
            int bucketWidth,
            int bucketHeight) {
        PatternPreviewBuckets buckets = new PatternPreviewBuckets(bucketWidth, bucketHeight);
        if (records == null || records.isEmpty()) {
            return buckets;
        }
        float worldWidth = Math.max(1f, bounds.width());
        float worldDepth = Math.max(1f, bounds.depth());
        for (BlockRecord record : records.values()) {
            BlockPos pos = record.pos;
            int pixelX = toBucketCoordinate(pos.getX(), bounds.minX, worldWidth, bucketWidth);
            int pixelY = toBucketCoordinate(pos.getZ(), bounds.minZ, worldDepth, bucketHeight);
            buckets.add(pixelX, pixelY, BlockColorRegistry.colorFor(record.newBlockId));
        }
        return buckets;
    }

    private static int toBucketCoordinate(int worldCoord, int minCoord, float worldSpan, int bucketSpan) {
        float normalized = (worldCoord - minCoord + 0.5f) / worldSpan;
        int bucket = (int) (normalized * bucketSpan);
        if (bucket < 0) {
            return 0;
        }
        if (bucket >= bucketSpan) {
            return bucketSpan - 1;
        }
        return bucket;
    }
}
