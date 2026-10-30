package com.plot.plugin.road.ui;

import com.plot.plugin.road.overlay.IntersectionOverlaySource;

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
        if (ctx == null || nodeId == null || nodeId.isBlank()) {
            return;
        }
        ctx.networkManager().focusIntersection(IntersectionOverlaySource.LEGACY_NODE, nodeId);
        ctx.requestOverlayRefresh();
        if (FLOATING.isOpen() && nodeId.equals(FLOATING.nodeId())) {
            FLOATING.focus();
            return;
        }
        FLOATING.open(ctx, nodeId);
    }
}
