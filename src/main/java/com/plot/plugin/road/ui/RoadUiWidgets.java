package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.function.Consumer;

/**
 * 道路 UI 通用控件。
 */
public final class RoadUiWidgets {

    private RoadUiWidgets() {
    }

    @FunctionalInterface
    public interface MaterialSetter {
        void set(String material);
    }

    public static void renderBlockMaterialPicker(
            RoadUiContext ctx,
            String buttonId,
            String label,
            String currentValue,
            MaterialSetter setter,
            boolean pushHistoryOnChange) {
        String displayName = RoadMaterialUtils.getDisplayName(currentValue);
        if (ImGui.button(displayName + buttonId, ImGui.getContentRegionAvailX() * 0.55f, 0)) {
            UIUtils.openBlockPicker(currentValue, blockId -> {
                if (pushHistoryOnChange) {
                    ctx.networkManager().pushHistory();
                }
                setter.set(blockId);
            });
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.select_block_hint"));
        }
        ImGui.sameLine();
        ImGui.textColored(PluginUiColors.HINT_GRAY, label);
    }

    public static void openBlockPicker(String currentBlockId, Consumer<String> onSelected) {
        UIUtils.openBlockPicker(currentBlockId, onSelected);
    }

    public static void renderEngineeringTooltip(String i18nKey) {
        UIUtils.renderEngineeringTooltip(i18nKey);
    }

    public static void renderRoadVisibilityWarning(RoadUiContext ctx) {
        String message = ctx.previewManager().formatVisibilityWarning();
        if (!message.isBlank()) {
            ImGui.textColored(PluginUiColors.WARNING, message);
        }
    }
}
