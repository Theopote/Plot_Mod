package com.plot.plugin.pattern.image;

import com.plot.plugin.powerline.preview.BlockPreviewColors;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 RGB 颜色匹配到调色盘方块（最近邻）。
 */
public final class BlockColorMatcher {
    private final List<PaletteEntry> entries;

    public BlockColorMatcher(List<String> blockIds) {
        this.entries = buildEntries(blockIds);
    }

    public String nearestBlock(int red, int green, int blue) {
        if (entries.isEmpty()) {
            return "minecraft:stone";
        }
        PaletteEntry best = entries.getFirst();
        double bestDistance = colorDistance(red, green, blue, best);
        for (int i = 1; i < entries.size(); i++) {
            PaletteEntry candidate = entries.get(i);
            double distance = colorDistance(red, green, blue, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best.blockId;
    }

    public int paletteSize() {
        return entries.size();
    }

    private static List<PaletteEntry> buildEntries(List<String> blockIds) {
        List<PaletteEntry> built = new ArrayList<>();
        if (blockIds == null) {
            return built;
        }
        for (String blockId : blockIds) {
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            int argb = BlockPreviewColors.colorFor(blockId.trim());
            built.add(new PaletteEntry(
                blockId.trim(),
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF));
        }
        return built;
    }

    private static double colorDistance(int red, int green, int blue, PaletteEntry entry) {
        double dr = red - entry.red;
        double dg = green - entry.green;
        double db = blue - entry.blue;
        return dr * dr + dg * dg + db * db;
    }

    private record PaletteEntry(String blockId, int red, int green, int blue) {
    }
}
