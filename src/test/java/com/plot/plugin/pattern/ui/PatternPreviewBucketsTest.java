package com.plot.plugin.pattern.ui;

import com.plot.core.command.BlockRecord;
import com.plot.core.material.BlockColorRegistry;
import com.plot.plugin.pattern.PatternGenerationResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPreviewBucketsTest {

    @Test
    void aggregatesManyCellsIntoSinglePreviewPixelByDominantColor() {
        PatternPreviewBuckets buckets = new PatternPreviewBuckets(1, 1);
        int wool = BlockColorRegistry.colorFor("minecraft:white_wool");
        int stone = BlockColorRegistry.colorFor("minecraft:stone");

        for (int i = 0; i < 7; i++) {
            buckets.add(0, 0, wool);
        }
        for (int i = 0; i < 3; i++) {
            buckets.add(0, 0, stone);
        }

        assertEquals(wool, buckets.colorAt(0, 0));
        assertEquals(7, buckets.countAt(0, 0));
    }

    @Test
    void mapsWorldCellsToScreenBucketsWithoutStrideGaps() {
        PatternGenerationResult result = new PatternGenerationResult();
        for (int x = 0; x < 120; x++) {
            for (int z = 0; z < 120; z++) {
                String blockId = (x + z) % 2 == 0 ? "minecraft:white_wool" : "minecraft:black_wool";
                result.placementRecords.put(
                    new BlockPos(x, 64, z),
                    new BlockRecord(new BlockPos(x, 64, z), "minecraft:grass_block", blockId));
            }
        }

        PatternPreviewRenderer.Bounds bounds = PatternPreviewRenderer.Bounds.from(result.placementRecords);
        PatternPreviewBuckets buckets = PatternPreviewBucketMapper.aggregate(
            result.placementRecords,
            bounds,
            60,
            60);

        int filled = 0;
        for (int y = 0; y < buckets.height(); y++) {
            for (int x = 0; x < buckets.width(); x++) {
                if (buckets.countAt(x, y) > 0) {
                    filled++;
                }
            }
        }

        assertEquals(60 * 60, filled);
        assertTrue(buckets.countAt(0, 0) > 0);
        assertTrue(buckets.countAt(59, 59) > 0);
    }
}
