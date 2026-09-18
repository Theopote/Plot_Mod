package com.plot.plugin.pattern.model;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Set;

/** 图案预设/配置中的方块 ID 校验。 */
public final class PatternBlockIdValidator {
    /** 常见笔误或非方块 ID（如物品语义）。 */
    private static final Set<String> KNOWN_INVALID = Set.of(
        "minecraft:brick");

    private PatternBlockIdValidator() {
    }

    public static boolean isKnownInvalid(String blockId) {
        return blockId != null && KNOWN_INVALID.contains(blockId.trim());
    }

    public static boolean looksLikeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        int colon = blockId.indexOf(':');
        return colon > 0 && colon < blockId.length() - 1;
    }

    /** 在 Minecraft 已启动、Registry 可用时校验。 */
    public static boolean isRegisteredBlock(String blockId) {
        if (!looksLikeBlockId(blockId) || isKnownInvalid(blockId)) {
            return false;
        }
        try {
            return Registries.BLOCK.containsId(Identifier.of(blockId.trim()));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
