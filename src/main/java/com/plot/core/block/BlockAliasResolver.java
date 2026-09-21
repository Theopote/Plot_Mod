package com.plot.core.block;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 方块 ID 别名解析：将配置/生成阶段沿用的旧 ID 映射为当前 Minecraft 注册表 ID。
 * <p>
 * Validator、Normalizer、PlacementWriter、Preview 等路径应统一先 {@link #resolve(BlockSpec)}，
 * 再各自做校验、落地或着色，避免同一 BlockSpec 在不同阶段出现两套“真相”。
 */
public final class BlockAliasResolver {
    private BlockAliasResolver() {
    }

    public static BlockSpec resolve(BlockSpec spec) {
        if (spec == null) {
            return null;
        }
        String resolvedId = resolveBlockId(spec.blockId());
        if (resolvedId.equals(spec.blockId())) {
            return spec;
        }
        return spec.withBlockId(resolvedId);
    }

    public static String resolveSetBlockArgument(String argument) {
        if (argument == null || argument.isBlank()) {
            return argument;
        }
        return resolve(BlockSpec.parse(argument)).toSetBlockArgument();
    }

    public static String resolveBlockId(String blockId) {
        if ("minecraft:chain".equals(blockId)) {
            return resolveChainBlockId();
        }
        return blockId;
    }

    /** 1.21.9+ 将 {@code chain} 重命名为 {@code iron_chain}。 */
    public static String resolveChainBlockId() {
        try {
            if (Registries.BLOCK.containsId(Identifier.of("minecraft", "iron_chain"))) {
                return "minecraft:iron_chain";
            }
        } catch (Throwable ignored) {
            // Registry not ready (tests/offline).
        }
        return "minecraft:chain";
    }
}
