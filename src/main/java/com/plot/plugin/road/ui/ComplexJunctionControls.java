package com.plot.plugin.road.ui;

/**
 * 复杂多路交叉口浮动设计器入口（路径 Tab / 属性侧栏共用）。
 */
public final class ComplexJunctionControls {
    private static final ComplexJunctionFloatingEditor FLOATING = new ComplexJunctionFloatingEditor();

    private ComplexJunctionControls() {
    }

    public static ComplexJunctionFloatingEditor floatingEditor() {
        return FLOATING;
    }

    public static void renderFloatingWindow(RoadUiContext ctx) {
        FLOATING.render(ctx);
    }

    public static void openOrFocus(RoadUiContext ctx, String nodeId) {
        if (FLOATING.isOpen() && nodeId != null && nodeId.equals(FLOATING.nodeId())) {
            FLOATING.focus();
            return;
        }
        FLOATING.open(ctx, nodeId);
    }
}
