package com.plot.plugin.road;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 道路材质与方块 ID 的互转。
 */
public final class RoadMaterialUtils {
    public static final String DEFAULT_ROAD_BLOCK = "minecraft:white_concrete";
    public static final String DEFAULT_FALLBACK_BLOCK = "minecraft:stone";
    /** Stable fill/shoulder default (no gravity). */
    public static final String DEFAULT_SLOPE_FILL_BLOCK = "minecraft:coarse_dirt";

    private RoadMaterialUtils() {
    }

    /**
     * 将存储值规范为 block ID。null/空白保持 null（表示沿用全局默认）。
     */
    public static String normalizeStoredMaterial(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }
        if (looksLikeBlockId(material)) {
            return material;
        }
        return null;
    }

    /**
     * 解析为可用于生成的 block ID，无效时回退到默认石头。
     */
    public static String resolveBlockId(String material) {
        String normalized = normalizeStoredMaterial(material);
        if (normalized == null) {
            return DEFAULT_FALLBACK_BLOCK;
        }
        if (isRegisteredBlock(normalized)) {
            return normalized;
        }
        if (looksLikeBlockId(normalized)) {
            return normalized;
        }
        return DEFAULT_FALLBACK_BLOCK;
    }

    public static String getDisplayName(String material) {
        String blockId = resolveBlockId(material);
        try {
            Block block = Registries.BLOCK.get(Identifier.of(blockId));
            String name = block.getName().getString();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return blockId;
    }

    public static Block resolveBlock(String material) {
        String blockId = normalizeStoredMaterial(material);
        if (blockId == null) {
            blockId = DEFAULT_ROAD_BLOCK;
        }
        try {
            Block block = Registries.BLOCK.get(Identifier.of(blockId));
            if (block != null && block != Blocks.AIR) {
                return block;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return Blocks.STONE;
    }

    private static boolean looksLikeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        int colon = blockId.indexOf(':');
        return colon > 0 && colon < blockId.length() - 1;
    }

    private static boolean isRegisteredBlock(String blockId) {
        if (!looksLikeBlockId(blockId)) {
            return false;
        }
        try {
            return Registries.BLOCK.containsId(Identifier.of(blockId));
        } catch (Throwable e) {
            return false;
        }
    }
}
