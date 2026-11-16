package com.plot.plugin.road.model.section;

/**
 * 桥墩横向柱列：单柱（中心线）/ 双柱（关于中心线对称）。
 * 旧存档 {@code AUTO}→{@link #SINGLE}，{@code MULTI}→{@link #DOUBLE}。
 */
public enum BridgePierStyle {
    SINGLE,
    DOUBLE;

    public static BridgePierStyle fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim().toUpperCase();
        return switch (key) {
            case "AUTO", "SINGLE" -> SINGLE;
            case "MULTI", "DOUBLE" -> DOUBLE;
            default -> {
                try {
                    yield valueOf(key);
                } catch (IllegalArgumentException ignored) {
                    yield null;
                }
            }
        };
    }

    public static BridgePierStyle fromStoredOrDefault(String id) {
        BridgePierStyle style = fromId(id);
        return style != null ? style : SINGLE;
    }
}
