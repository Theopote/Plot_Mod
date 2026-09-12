package com.plot.plugin.powerline.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;

/** 杆塔设计器左右分栏布局与拖拽分隔条。 */
final class PoleDesignerLayoutPanel {
    private static final float DEFAULT_PREVIEW_COLUMN_WIDTH = 272f;
    private static final float MIN_PREVIEW_COLUMN_WIDTH = 200f;
    private static final float MIN_PARAMS_COLUMN_WIDTH = 280f;
    private static final float SPLITTER_WIDTH = 6f;
    private static final int PREVIEW_COLUMN_FLAGS =
        ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

    private float previewColumnWidth = DEFAULT_PREVIEW_COLUMN_WIDTH;

    void render(float bodyHeight, Runnable renderPreview, Runnable renderParams) {
        float totalWidth = ImGui.getContentRegionAvail().x;
        float maxPreviewWidth = Math.max(
            MIN_PREVIEW_COLUMN_WIDTH,
            totalWidth - MIN_PARAMS_COLUMN_WIDTH - SPLITTER_WIDTH);
        previewColumnWidth = Math.min(maxPreviewWidth, Math.max(MIN_PREVIEW_COLUMN_WIDTH, previewColumnWidth));

        if (ImGui.beginChild("##designer_preview_column", previewColumnWidth, bodyHeight, false, PREVIEW_COLUMN_FLAGS)) {
            renderPreview.run();
        }
        ImGui.endChild();

        ImGui.sameLine(0, 0);
        renderColumnSplitter(bodyHeight, totalWidth);

        ImGui.sameLine(0, 0);
        float paramsWidth = Math.max(0f, totalWidth - previewColumnWidth - SPLITTER_WIDTH);
        if (ImGui.beginChild("##designer_params_column", paramsWidth, bodyHeight, false)) {
            renderParams.run();
        }
        ImGui.endChild();
    }

    private void renderColumnSplitter(float height, float totalWidth) {
        ImGui.pushID("designer_column_splitter");
        ImGui.invisibleButton("##grab", SPLITTER_WIDTH, height);
        if (ImGui.isItemActive()) {
            previewColumnWidth += ImGui.getIO().getMouseDeltaX();
            float maxPreviewWidth = Math.max(
                MIN_PREVIEW_COLUMN_WIDTH,
                totalWidth - MIN_PARAMS_COLUMN_WIDTH - SPLITTER_WIDTH);
            previewColumnWidth = Math.min(maxPreviewWidth, Math.max(MIN_PREVIEW_COLUMN_WIDTH, previewColumnWidth));
        }
        if (ImGui.isItemHovered() || ImGui.isItemActive()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }

        ImVec2 min = ImGui.getItemRectMin();
        ImVec2 max = ImGui.getItemRectMax();
        float centerX = (min.x + max.x) * 0.5f;
        ImDrawList drawList = ImGui.getWindowDrawList();
        boolean active = ImGui.isItemActive() || ImGui.isItemHovered();
        int lineColor = active ? 0xFF90CAF9 : 0xFF606060;
        drawList.addLine(centerX, min.y, centerX, max.y, lineColor, active ? 2f : 1f);
        ImGui.popID();
    }
}
