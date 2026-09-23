package com.plot.plugin.ui;

import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;

/**
 * 插件标签页内容区滚动：工具栏与 Tab 栏固定，仅 Tab 正文可垂直滚动。
 */
public final class PluginTabScrollUi {
    private PluginTabScrollUi() {
    }

    /**
     * 在剩余面板高度内渲染可滚动正文。
     *
     * @param childId 唯一 ImGui child id（建议含 {@code ##} 前缀）
     */
    public static void renderScrollBody(String childId, Runnable body) {
        float availX = ImGui.getContentRegionAvailX();
        float availY = ImGui.getContentRegionAvailY();
        if (availX < 8f || availY < 8f) {
            body.run();
            return;
        }
        if (ImGui.beginChild(
                childId,
                0,
                0,
                false,
                ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            try {
                body.run();
            } finally {
                ImGui.endChild();
            }
        }
    }

    public static boolean renderTab(String labelKey, String childId, Runnable body) {
        return renderTab(labelKey, ImGuiTabItemFlags.None, childId, body);
    }

    public static boolean renderTab(String labelKey, int tabItemFlags, String childId, Runnable body) {
        if (ImGui.beginTabItem(PlotI18n.tr(labelKey), tabItemFlags)) {
            renderScrollBody(childId, body);
            ImGui.endTabItem();
            return true;
        }
        return false;
    }
}
