package com.plot.plugin.powerline.ui;

import com.plot.core.config.ConfigManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;

/** 杆塔设计器左右分栏布局与拖拽分隔条。 */
final class PoleDesignerLayoutPanel {
    static final float DEFAULT_PREVIEW_COLUMN_WIDTH = 272f;
    static final float MIN_PREVIEW_COLUMN_WIDTH = 200f;
    private static final float MIN_PARAMS_COLUMN_WIDTH = 280f;
    private static final float SPLITTER_WIDTH = 6f;
    private static final String CONFIG_KEY_PREVIEW_COLUMN_WIDTH =
        "powerline.poleDesigner.previewColumnWidth";
    private static final int PREVIEW_COLUMN_FLAGS =
        ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

    static void restorePreviewColumnWidth(PowerLinePluginState state) {
        float saved = ConfigManager.getInstance().getFloat(
            CONFIG_KEY_PREVIEW_COLUMN_WIDTH,
            DEFAULT_PREVIEW_COLUMN_WIDTH);
        state.setPoleDesignerPreviewColumnWidth(saved);
    }

    void render(
            PowerLinePluginState state,
            float bodyHeight,
            Runnable renderPreview,
            Runnable renderParams) {
        float totalWidth = ImGui.getContentRegionAvail().x;
        if (totalWidth < 1f || bodyHeight < 1f) {
            return;
        }

        float previewColumnWidth = state.getPoleDesignerPreviewColumnWidth();
        float maxPreviewWidth = Math.max(
            MIN_PREVIEW_COLUMN_WIDTH,
            totalWidth - MIN_PARAMS_COLUMN_WIDTH - SPLITTER_WIDTH);
        previewColumnWidth = Math.min(maxPreviewWidth, Math.max(MIN_PREVIEW_COLUMN_WIDTH, previewColumnWidth));
        state.setPoleDesignerPreviewColumnWidth(previewColumnWidth);

        float safeHeight = Math.max(1f, bodyHeight);
        if (ImGui.beginChild("##designer_preview_column", previewColumnWidth, safeHeight, false, PREVIEW_COLUMN_FLAGS)) {
            renderPreview.run();
        }
        ImGui.endChild();

        ImGui.sameLine(0, 0);
        renderColumnSplitter(state, safeHeight, totalWidth);

        ImGui.sameLine(0, 0);
        float paramsWidth = Math.max(1f, totalWidth - previewColumnWidth - SPLITTER_WIDTH);
        if (ImGui.beginChild("##designer_params_column", paramsWidth, safeHeight, false)) {
            renderParams.run();
        }
        ImGui.endChild();
    }

    private void renderColumnSplitter(PowerLinePluginState state, float height, float totalWidth) {
        float safeHeight = Math.max(1f, height);
        ImGui.pushID("designer_column_splitter");
        ImGui.invisibleButton("##grab", SPLITTER_WIDTH, safeHeight);
        if (ImGui.isItemActive()) {
            float previewColumnWidth = state.getPoleDesignerPreviewColumnWidth();
            previewColumnWidth += ImGui.getIO().getMouseDeltaX();
            float maxPreviewWidth = Math.max(
                MIN_PREVIEW_COLUMN_WIDTH,
                totalWidth - MIN_PARAMS_COLUMN_WIDTH - SPLITTER_WIDTH);
            previewColumnWidth = Math.min(maxPreviewWidth, Math.max(MIN_PREVIEW_COLUMN_WIDTH, previewColumnWidth));
            state.setPoleDesignerPreviewColumnWidth(previewColumnWidth);
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            ConfigManager.getInstance().setFloat(
                CONFIG_KEY_PREVIEW_COLUMN_WIDTH,
                state.getPoleDesignerPreviewColumnWidth());
            ConfigManager.getInstance().saveConfig();
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
