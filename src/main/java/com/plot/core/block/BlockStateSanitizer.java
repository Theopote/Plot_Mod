package com.plot.core.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 依据 Minecraft Block {@link StateManager} 校验并规范化 BlockState。
 * <p>
 * 丢弃方块不认识的属性（例如 iron_bars 的 {@code up}/{@code down}），
 * 避免无效 /setblock 参数被误认为放置成功。
 * 注册表未就绪时回退到已知方块的静态规则。
 */
public final class BlockStateSanitizer {
    private static final Set<String> IRON_BAR_HORIZONTAL_AXES = Set.of(
        "north", "south", "east", "west");

    private BlockStateSanitizer() {
    }

    public static String sanitizeSetBlockArgument(String argument) {
        if (argument == null || argument.isBlank()) {
            return argument;
        }
        return sanitize(BlockSpec.parse(argument)).toSetBlockArgument();
    }

    public static BlockSpec sanitize(BlockSpec spec) {
        if (spec == null) {
            return BlockSpec.of("minecraft:air");
        }
        BlockSpec stripped = stripKnownInvalidProperties(spec);
        try {
            return sanitizeWithRegistry(stripped);
        } catch (Throwable ignored) {
            return stripped;
        }
    }

    private static BlockSpec stripKnownInvalidProperties(BlockSpec spec) {
        if (!"minecraft:iron_bars".equals(spec.blockId())) {
            return spec;
        }
        Map<String, String> props = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
            if (IRON_BAR_HORIZONTAL_AXES.contains(entry.getKey())) {
                props.put(entry.getKey(), entry.getValue());
            }
        }
        return BlockSpec.with(spec.blockId(), props);
    }

    private static BlockSpec sanitizeWithRegistry(BlockSpec spec) {
        Block block = resolveBlock(spec.blockId());
        if (block == null) {
            return spec;
        }

        BlockState state = block.getDefaultState();
        StateManager<Block, BlockState> manager = block.getStateManager();
        for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
            Property<?> property = manager.getProperty(entry.getKey());
            if (property == null) {
                continue;
            }
            Optional<?> parsed = property.parse(entry.getValue());
            if (parsed.isEmpty()) {
                continue;
            }
            state = withProperty(state, property, parsed.get());
        }
        return fromBlockState(state);
    }

    private static Block resolveBlock(String blockId) {
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
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withProperty(
            BlockState state,
            Property<T> property,
            Object value) {
        return state.with(property, (T) value);
    }

    private static BlockSpec fromBlockState(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        BlockState defaultState = state.getBlock().getDefaultState();
        Map<String, String> props = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            if (!isNonDefaultProperty(state, defaultState, property)) {
                continue;
            }
            props.put(property.getName(), namePropertyValue(property, state));
        }
        return BlockSpec.with(id.toString(), props);
    }

    private static boolean isNonDefaultProperty(
            BlockState state,
            BlockState defaultState,
            Property<?> property) {
        return !state.get(property).equals(defaultState.get(property));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String namePropertyValue(Property<?> property, BlockState state) {
        Property<T> typed = (Property<T>) property;
        return typed.name(state.get(typed));
    }
}
