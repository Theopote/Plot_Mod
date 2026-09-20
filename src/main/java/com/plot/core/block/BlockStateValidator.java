package com.plot.core.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 校验 BlockSpec 是否对应合法的 Minecraft BlockState（不修改输入）。 */
public final class BlockStateValidator {
    private BlockStateValidator() {
    }

    public static boolean isValid(BlockSpec spec) {
        return validate(spec).isEmpty();
    }

    public static List<String> validate(BlockSpec spec) {
        if (spec == null) {
            return List.of("null BlockSpec");
        }
        try {
            return validateWithRegistry(spec);
        } catch (Throwable ignored) {
            return validateWithStaticRules(spec);
        }
    }

    private static List<String> validateWithRegistry(BlockSpec spec) {
        List<String> errors = new ArrayList<>();
        Block block = resolveBlock(spec.blockId());
        if (block == null) {
            errors.add("unknown block: " + spec.blockId());
            return errors;
        }
        for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
            Property<?> property = block.getStateManager().getProperty(entry.getKey());
            if (property == null) {
                errors.add(spec.blockId() + ": unknown property '" + entry.getKey() + "'");
                continue;
            }
            Optional<?> parsed = property.parse(entry.getValue());
            if (parsed.isEmpty()) {
                errors.add(spec.blockId() + ": invalid " + entry.getKey() + "=" + entry.getValue());
            }
        }
        return errors;
    }

    private static List<String> validateWithStaticRules(BlockSpec spec) {
        String sanitized = BlockStateSanitizer.sanitize(spec).toSetBlockArgument();
        if (sanitized.equals(spec.toSetBlockArgument())) {
            return List.of();
        }
        return List.of("invalid BlockState: " + spec);
    }

    public static List<String> validateSetBlockArgument(String argument) {
        return validate(BlockSpec.parse(argument));
    }

    private static Block resolveBlock(String blockId) {
        try {
            String namespace = "minecraft";
            String path = blockId;
            if (blockId.contains(":")) {
                String[] parts = blockId.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            }
            Identifier identifier = Identifier.of(namespace, path);
            Block block = Registries.BLOCK.get(identifier);
            if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) {
                return null;
            }
            return block;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withProperty(
            BlockState state,
            Property<T> property,
            Object value) {
        return state.with(property, (T) value);
    }
}
