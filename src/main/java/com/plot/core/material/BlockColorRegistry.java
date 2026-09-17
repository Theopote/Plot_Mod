package com.plot.core.material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 方块预览/匹配用 RGB 色表（与具体插件 UI 解耦）。
 */
public final class BlockColorRegistry {
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
        KNOWN.put("minecraft:oak_slab", 0xFFA0826D);
        KNOWN.put("minecraft:dark_oak_slab", 0xFF4A3728);
        KNOWN.put("minecraft:birch_slab", 0xFFD8C89A);
        KNOWN.put("minecraft:spruce_slab", 0xFF6B5037);
        KNOWN.put("minecraft:mossy_cobblestone_slab", 0xFF6E7A5E);
        KNOWN.put("minecraft:smooth_stone_slab", 0xFF9E9E9E);
        KNOWN.put("minecraft:gold_block", 0xFFFFD54F);
        KNOWN.put("minecraft:cut_copper", 0xFFE08A5A);
        KNOWN.put("minecraft:oxidized_copper", 0xFF6FA08A);
        KNOWN.put("minecraft:orange_terracotta", 0xFFA45729);
        KNOWN.put("minecraft:sea_lantern", 0xFFB8E8F0);
        KNOWN.put("minecraft:soul_lantern", 0xFF5EC8D8);
        KNOWN.put("minecraft:cobweb", 0xFFE8E8E8);
        KNOWN.put("minecraft:quartz_block", 0xFFECEFF1);
        KNOWN.put("minecraft:iron_trapdoor", 0xFFB0B0B0);
        KNOWN.put("minecraft:amethyst_cluster", 0xFFCE93D8);
        KNOWN.put("minecraft:white_wool", 0xFFE9ECEC);
        KNOWN.put("minecraft:light_gray_wool", 0xFF8E8E86);
        KNOWN.put("minecraft:gray_wool", 0xFF474F4D);
        KNOWN.put("minecraft:black_wool", 0xFF141519);
        KNOWN.put("minecraft:brown_wool", 0xFF7C5328);
        KNOWN.put("minecraft:red_wool", 0xFFA02722);
        KNOWN.put("minecraft:orange_wool", 0xFFF07613);
        KNOWN.put("minecraft:yellow_wool", 0xFFF8C627);
        KNOWN.put("minecraft:lime_wool", 0xFF70B919);
        KNOWN.put("minecraft:green_wool", 0xFF546D1B);
        KNOWN.put("minecraft:cyan_wool", 0xFF169C9C);
        KNOWN.put("minecraft:light_blue_wool", 0xFF3AB3DA);
        KNOWN.put("minecraft:blue_wool", 0xFF35399D);
        KNOWN.put("minecraft:purple_wool", 0xFF7E3DB5);
        KNOWN.put("minecraft:magenta_wool", 0xFFC74EBD);
        KNOWN.put("minecraft:pink_wool", 0xFFF38BAA);
        KNOWN.put("minecraft:white_concrete", 0xFFCFD5D6);
        KNOWN.put("minecraft:gray_concrete", 0xFF36393D);
        KNOWN.put("minecraft:black_concrete", 0xFF080A0F);
        KNOWN.put("minecraft:brown_concrete", 0xFF603B1F);
        KNOWN.put("minecraft:red_concrete", 0xFF8E2121);
        KNOWN.put("minecraft:orange_concrete", 0xFFE06101);
        KNOWN.put("minecraft:yellow_concrete", 0xFFF1AF15);
        KNOWN.put("minecraft:lime_concrete", 0xFF5EA818);
        KNOWN.put("minecraft:green_concrete", 0xFF495B24);
        KNOWN.put("minecraft:cyan_concrete", 0xFF157788);
        KNOWN.put("minecraft:light_blue_concrete", 0xFF2389C6);
        KNOWN.put("minecraft:blue_concrete", 0xFF2C2E8F);
        KNOWN.put("minecraft:purple_concrete", 0xFF641F9C);
        KNOWN.put("minecraft:magenta_concrete", 0xFFA9309F);
        KNOWN.put("minecraft:pink_concrete", 0xFFD5658E);
        KNOWN.put("minecraft:terracotta", 0xFF985E45);
    }

    private BlockColorRegistry() {
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

    /** 图片图案自动匹配使用的已知方块目录。 */
    public static List<String> knownBlockIds() {
        return List.copyOf(KNOWN.keySet());
    }
}
