package com.plot.plugin.road.model.section;

/**
 * 桥墩横向柱列布局：自动（按路面宽）/ 单柱 / 多柱（中心+两侧）。
 */
public enum BridgePierStyle {
    AUTO,
    SINGLE,
    MULTI;

    public static BridgePierStyle fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static BridgePierStyle fromStoredOrDefault(String id) {
        BridgePierStyle style = fromId(id);
        return style != null ? style : AUTO;
    }
}
