package com.plot.plugin.road.ui;

import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec2;

/** 多选批处理模式顶栏：明显区别于单路精细编辑。 */
final class RoadBatchEditBanner {
    private RoadBatchEditBanner() {
    }

    static void render(int selectedCount) {
        float width = ImGui.getContentRegionAvail().x;
        ImVec2 origin = ImGui.getCursorScreenPos();
        float height = ImGui.getFrameHeightWithSpacing() * 2.4f;
        var drawList = ImGui.getWindowDrawList();
        int bg = PluginUiColors.INFO_BLUE & 0x00FFFFFF | 0x28000000;
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, bg);
        drawList.addRect(origin.x, origin.y, origin.x + width, origin.y + height, PluginUiColors.ACCENT_BLUE);

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8f, 6f);
        ImGui.beginChild("##road_batch_edit_banner", width, height, false);
        try {
            ImGui.textColored(
                PluginUiColors.INFO_BLUE,
                PlotI18n.tr("plugin.road.batch_edit_title", selectedCount));
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.batch_edit_shared_only"));
        } finally {
            ImGui.endChild();
            ImGui.popStyleVar();
        }
        ImGui.spacing();
    }
}
