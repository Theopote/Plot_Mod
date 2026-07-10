package com.plot.ui.layout;

import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据已渲染面板的屏幕边界，在边缘附近显示 resize 光标。
 * 仅使用 ImGui 公共 API，避免访问 internal Dock 节点导致 native 崩溃。
 */
public final class PanelEdgeCursorHelper {

    private static final float SPLITTER_HIT_PX = 8.0f;

    private final List<VerticalEdge> verticalEdges = new ArrayList<>();
    private final List<HorizontalEdge> horizontalEdges = new ArrayList<>();

    public void clear() {
        verticalEdges.clear();
        horizontalEdges.clear();
    }

    /** 记录竖直分割条（左右拖动，↔ 光标）。 */
    public void addVerticalEdge(float x, float yStart, float yEnd) {
        if (yEnd <= yStart) {
            return;
        }
        verticalEdges.add(new VerticalEdge(x, yStart, yEnd));
    }

    /** 记录水平分割条（上下拖动，↕ 光标）。 */
    public void addHorizontalEdge(float y, float xStart, float xEnd) {
        if (xEnd <= xStart) {
            return;
        }
        horizontalEdges.add(new HorizontalEdge(y, xStart, xEnd));
    }

    /**
     * @return 应显示的光标类型；若不在边缘则返回 {@link ImGuiMouseCursor#Arrow}
     */
    public int resolveCursorNearEdge() {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        for (VerticalEdge edge : verticalEdges) {
            if (Math.abs(mouseX - edge.x) <= SPLITTER_HIT_PX
                    && mouseY >= edge.yStart
                    && mouseY <= edge.yEnd) {
                return ImGuiMouseCursor.ResizeEW;
            }
        }

        for (HorizontalEdge edge : horizontalEdges) {
            if (Math.abs(mouseY - edge.y) <= SPLITTER_HIT_PX
                    && mouseX >= edge.xStart
                    && mouseX <= edge.xEnd) {
                return ImGuiMouseCursor.ResizeNS;
            }
        }

        return ImGuiMouseCursor.Arrow;
    }

    public void applyIfNearEdge() {
        int cursor = resolveCursorNearEdge();
        if (cursor != ImGuiMouseCursor.Arrow) {
            ImGui.setMouseCursor(cursor);
        }
    }

    private record VerticalEdge(float x, float yStart, float yEnd) {
    }

    private record HorizontalEdge(float y, float xStart, float xEnd) {
    }
}
