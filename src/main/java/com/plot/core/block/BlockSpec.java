package com.plot.core.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minecraft 方块放置规格：方块 ID + BlockState 属性。
 * <p>
 * 序列化为 {@code minecraft:lightning_rod[facing=east]} 形式，供 /setblock 直接使用。
 */
public final class BlockSpec {
    private final String blockId;
    private final Map<String, String> properties;

    private BlockSpec(String blockId, Map<String, String> properties) {
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.properties = properties == null || properties.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static BlockSpec of(String blockId) {
        return new BlockSpec(normalizeId(blockId), Map.of());
    }

    public static BlockSpec with(String blockId, String property, String value) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(property, value);
        return new BlockSpec(normalizeId(blockId), props);
    }

    public static BlockSpec with(String blockId, Map<String, String> properties) {
        return new BlockSpec(normalizeId(blockId), properties);
    }

    public static BlockSpec parse(String argument) {
        if (argument == null || argument.isBlank()) {
            return of("minecraft:air");
        }
        String trimmed = argument.trim();
        int bracketStart = trimmed.indexOf('[');
        if (bracketStart < 0) {
            return of(trimmed);
        }
        int bracketEnd = trimmed.lastIndexOf(']');
        if (bracketEnd <= bracketStart) {
            return of(trimmed.substring(0, bracketStart));
        }
        String baseId = trimmed.substring(0, bracketStart).trim();
        String body = trimmed.substring(bracketStart + 1, bracketEnd).trim();
        if (body.isEmpty()) {
            return of(baseId);
        }
        Map<String, String> props = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            String token = pair.trim();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            props.put(token.substring(0, eq).trim(), token.substring(eq + 1).trim());
        }
        return new BlockSpec(normalizeId(baseId), props);
    }

    public String blockId() {
        return blockId;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    public String property(String key) {
        return properties.get(key);
    }

    public BlockSpec withProperty(String key, String value) {
        Map<String, String> merged = new LinkedHashMap<>(properties);
        merged.put(key, value);
        return new BlockSpec(blockId, merged);
    }

    /** /setblock 参数字符串（含 BlockState）。 */
    public String toSetBlockArgument() {
        if (properties.isEmpty()) {
            return blockId;
        }
        StringBuilder builder = new StringBuilder(blockId);
        builder.append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockSpec spec)) {
            return false;
        }
        return blockId.equals(spec.blockId) && properties.equals(spec.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, properties);
    }

    @Override
    public String toString() {
        return toSetBlockArgument();
    }

    private static String normalizeId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "minecraft:air";
        }
        return blockId.trim();
    }
}
