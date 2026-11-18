package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;

/** 多选批处理模式顶栏：明显区别于单路精细编辑。 */
final class RoadBatchEditBanner {
    private static final float PAD_X = 8f;
    private static final float PAD_Y = 6f;

    private RoadBatchEditBanner() {
    }

    static void render(int selectedCount) {
        float width = Math.max(1f, ImGui.getContentRegionAvailX());
        String title = PlotI18n.tr("plugin.road.batch_edit_title", selectedCount);
        String shared = PlotI18n.tr("plugin.road.batch_edit_shared_only");
        float textWidth = Math.max(1f, width - PAD_X * 2f);
        float titleH = RoadUiWidgets.wrappedTextHeight(title, textWidth);
        float sharedH = RoadUiWidgets.wrappedTextHeight(shared, textWidth);
        float height = PAD_Y * 2f + titleH + ImGui.getStyle().getItemSpacingY() + sharedH;

        ImVec2 origin = ImGui.getCursorScreenPos();
        var drawList = ImGui.getWindowDrawList();
        int bg = PluginUiColors.INFO_BLUE & 0x00FFFFFF | 0x28000000;
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, bg);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + height, PluginUiColors.ACCENT_BLUE);

        ImGui.setCursorScreenPos(origin.x + PAD_X, origin.y + PAD_Y);
        ImGui.pushTextWrapPos(origin.x + width - PAD_X);
        ImGui.textColored(PluginUiColors.INFO_BLUE, title);
        ImGui.textColored(PluginUiColors.HINT_GRAY, shared);
        ImGui.popTextWrapPos();

        ImGui.setCursorScreenPos(origin.x, origin.y + height);
        ImGui.spacing();
    }
}
