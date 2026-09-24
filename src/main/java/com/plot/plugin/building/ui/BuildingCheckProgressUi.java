package com.plot.plugin.building.ui;

import com.plot.plugin.ui.PluginJobProgressUi;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 「检查可生成」专用进度 UI，与方块落地进度条分离。
 */
public final class BuildingCheckProgressUi {
    private BuildingCheckProgressUi() {
    }

    public static void render(
            String statusText,
            int completedSteps,
            int totalSteps,
            float barWidth,
            String cancelLabelKey,
            Runnable onCancel) {
        if (statusText != null && !statusText.isEmpty()) {
            ImGui.textColored(PluginUiColors.STATUS_INFO, statusText);
        }
        float width = barWidth > 0f ? barWidth : ImGui.getContentRegionAvailX();
        float barHeight = Math.max(ImGui.getTextLineHeight(), ImGui.getFrameHeight() * 0.75f);
        ImGui.pushID("building_check_progress_bar");
        ImGui.progressBar(PluginJobProgressUi.fraction(completedSteps, totalSteps), width, barHeight);
        ImGui.popID();
        if (cancelLabelKey != null && onCancel != null) {
            if (ImGui.button(PlotI18n.tr(cancelLabelKey), 0, 0)) {
                onCancel.run();
            }
        }
    }
}
