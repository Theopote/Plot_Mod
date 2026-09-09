package com.plot.plugin.powerline.preview;

import java.util.HashMap;
import java.util.Map;

/** 常见方块 → 预览色（ARGB），未知方块按 id 哈希着色。 */
public final class BlockPreviewColors {
    private static final Map<String, Integer> KNOWN = new HashMap<>();

    static {
        KNOWN.put("minecraft:oak_fence", 0xFFA0826D);
        KNOWN.put("minecraft:dark_oak_fence", 0xFF4A3728);
        KNOWN.put("minecraft:spruce_fence", 0xFF6B5037);
        KNOWN.put("minecraft:birch_fence", 0xFFD8C89A);
        KNOWN.put("minecraft:iron_bars", 0xFFB0B0B0);
        KNOWN.put("minecraft:iron_block", 0xFFD8D8D8);
        KNOWN.put("minecraft:stone_bricks", 0xFF7A7A7A);
        KNOWN.put("minecraft:mossy_cobblestone", 0xFF6E7A5E);
        KNOWN.put("minecraft:copper_block", 0xFFE08A5A);
        KNOWN.put("minecraft:weathered_copper", 0xFF8FA08A);
        KNOWN.put("minecraft:lightning_rod", 0xFFE8C060);
        KNOWN.put("minecraft:chain", 0xFF8A8A8A);
        KNOWN.put("minecraft:vine", 0xFF4A7A3A);
        KNOWN.put("minecraft:light_blue_stained_glass", 0xFF80D8FF);
        KNOWN.put("minecraft:glowstone", 0xFFFFE082);
        KNOWN.put("minecraft:lantern", 0xFFE8C060);
        KNOWN.put("minecraft:blackstone", 0xFF2A2A2A);
        KNOWN.put("minecraft:deepslate_bricks", 0xFF4A4F55);
        KNOWN.put("minecraft:polished_blackstone", 0xFF3A3A3A);
    }

    private BlockPreviewColors() {
    }

    public static int colorFor(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return 0xFF808080;
        }
        Integer known = KNOWN.get(blockId);
        if (known != null) {
            return known;
        }
        int hash = blockId.hashCode();
        int r = 80 + (hash & 0x7F);
        int g = 80 + ((hash >> 8) & 0x7F);
        int b = 80 + ((hash >> 16) & 0x7F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int highlight(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 28);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 28);
        int b = Math.min(255, (color & 0xFF) + 28);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int shadow(int color) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - 36);
        int g = Math.max(0, ((color >> 8) & 0xFF) - 36);
        int b = Math.max(0, (color & 0xFF) - 36);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
