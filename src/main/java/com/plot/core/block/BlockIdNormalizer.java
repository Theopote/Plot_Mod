package com.plot.core.block;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 将用户输入的方块规格规范化为可安全执行的 /setblock 参数。
 * 未知方块 ID 返回失败，禁止静默回退为空气。
 */
public final class BlockIdNormalizer {
    private BlockIdNormalizer() {
    }

    public static BlockNormalizationResult normalize(String blockId, boolean allowAir) {
        String candidate = (blockId == null || blockId.isEmpty()) ? "minecraft:white_wool" : blockId.trim();
        try {
            BlockSpec spec = BlockSpec.parse(candidate);
            BlockSpec resolved = BlockAliasResolver.resolve(spec);
            Identifier blockIdentifier = toIdentifier(resolved.blockId());
            if (!isRegistered(blockIdentifier)) {
                return BlockNormalizationResult.invalid("Unknown block: " + blockIdentifier);
            }

            Block blockType = Registries.BLOCK.get(blockIdentifier);
            if (blockType == Blocks.AIR && !allowAir) {
                return BlockNormalizationResult.valid("minecraft:white_wool");
            }

            return BlockNormalizationResult.valid(
                BlockStateSanitizer.sanitizeSetBlockArgument(resolved.toSetBlockArgument()));
        } catch (Exception e) {
            return BlockNormalizationResult.invalid("Failed to parse block: " + candidate);
        }
    }

    private static boolean isRegistered(Identifier blockIdentifier) {
        try {
            return Registries.BLOCK.containsId(blockIdentifier);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Identifier toIdentifier(String blockId) {
        String namespace = "minecraft";
        String path = blockId;
        if (path.contains(":")) {
            String[] parts = path.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        return Identifier.of(namespace, path);
    }
}
