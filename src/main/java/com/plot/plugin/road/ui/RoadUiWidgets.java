package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;
import java.util.function.Consumer;

/**
 * 道路 UI 通用控件。
 */
public final class RoadUiWidgets {

    private RoadUiWidgets() {
    }

    /** 当前内容区右边界，供 {@link ImGui#pushTextWrapPos(float)} 使用。 */
    public static float wrapPos() {
        return ImGui.getCursorPosX() + ImGui.getContentRegionAvailX();
    }

    public static void textWrapped(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ImGui.pushTextWrapPos(wrapPos());
        ImGui.textWrapped(text);
        ImGui.popTextWrapPos();
    }

    public static void textWrappedColored(int color, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ImGui.pushTextWrapPos(wrapPos());
        ImGui.textColored(color, text);
        ImGui.popTextWrapPos();
    }

    public static float wrappedTextHeight(String text, float width) {
        if (text == null || text.isBlank() || width <= 0f) {
            return ImGui.getTextLineHeight();
        }
        return ImGui.calcTextSize(text, false, width).y;
    }

    @FunctionalInterface
    public interface MaterialSetter {
        void set(String material);
    }

    @FunctionalInterface
    public interface MaterialMixSetter {
        void set(MaterialMix material);
    }

    public static void renderBlockMaterialPicker(
            RoadUiContext ctx,
            String buttonId,
            String label,
            String currentValue,
            MaterialSetter setter,
            boolean pushHistoryOnChange) {
        textWrappedColored(PluginUiColors.HINT_GRAY, label);
        String displayName = RoadMaterialUtils.getDisplayName(currentValue);
        if (ImGui.button(displayName + buttonId, ImGui.getContentRegionAvailX(), 0)) {
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
    }

    public static void renderMaterialMixPicker(
            RoadUiContext ctx,
            String buttonId,
            String label,
            MaterialMix currentValue,
            MaterialMixSetter setter,
            boolean pushHistoryOnChange) {
        MaterialMix defaultMix = MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
        UIUtils.renderMaterialMixPicker(
            buttonId,
            label,
            currentValue,
            defaultMix,
            setter::set,
            pushHistoryOnChange ? () -> ctx.networkManager().pushHistory() : null);
    }

    /**
     * 点缀比例滑条（0–50%）。
     */
    public static void renderAccentRatioSlider(
            MaterialMix mix,
            MaterialMixSetter setter,
            String id,
            Runnable onActivated) {
        UIUtils.renderAccentRatioSlider(mix, setter::set, id, onActivated);
    }

    public static MaterialMix fromPaletteSelection(List<String> blockIds, float existingRatio) {
        return UIUtils.fromPaletteSelection(
            blockIds,
            existingRatio,
            MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK));
    }

    public static void openPalettePicker(List<String> initialBlockIds, Consumer<List<String>> onConfirm) {
        UIUtils.openPalettePicker(initialBlockIds, onConfirm);
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
            textWrappedColored(PluginUiColors.WARNING, message);
        }
    }

    /** 字段处于继承默认态时在控件下方显示灰色提示。 */
    public static void renderInheritanceHint(boolean inherited, String inheritedLabel) {
        if (!inherited || inheritedLabel == null || inheritedLabel.isBlank()) {
            return;
        }
        textWrappedColored(PluginUiColors.HINT_GRAY, inheritedLabel);
    }

    /**
     * 继承态显示灰色提示；覆盖态显示「恢复继承」小按钮。
     */
    public static void renderOverrideFooter(
            boolean inherited,
            String inheritedLabel,
            String resetId,
            Runnable onReset) {
        if (inherited) {
            renderInheritanceHint(true, inheritedLabel);
            return;
        }
        if (ImGui.smallButton(PlotI18n.tr("plugin.road.reset_to_inherit") + "##" + resetId)) {
            if (onReset != null) {
                onReset.run();
            }
        }
    }
}
