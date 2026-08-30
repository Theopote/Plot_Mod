package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.ui.component.UIUtils;
import com.plot.ui.dialog.BlockConfigDialog.BlockConfigManager;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
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
        MaterialMix mix = currentValue != null
            ? currentValue
            : MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
        String displayName = RoadMaterialUtils.getDisplayName(mix.getPrimaryMaterial());
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            displayName += " + " + RoadMaterialUtils.getDisplayName(mix.getAccentMaterial());
        }

        ImGui.pushID(buttonId);
        textWrappedColored(PluginUiColors.HINT_GRAY, label);
        if (ImGui.button(displayName + "##pick", ImGui.getContentRegionAvailX(), 0)) {
            List<String> initial = new ArrayList<>();
            if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
                initial.add(mix.getPrimaryMaterial());
            }
            if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
                initial.add(mix.getAccentMaterial());
            }
            openPalettePicker(initial, blockIds -> {
                if (pushHistoryOnChange) {
                    ctx.networkManager().pushHistory();
                }
                setter.set(fromPaletteSelection(blockIds, mix.getAccentRatio()));
            });
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.select_block_hint"));
        }

        boolean hasAccentMaterial = mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank();
        if (hasAccentMaterial) {
            renderAccentRatioSlider(
                mix,
                setter,
                buttonId,
                pushHistoryOnChange ? () -> ctx.networkManager().pushHistory() : null);
        }
        ImGui.popID();
    }

    /**
     * 点缀比例滑条（0–50%）。
     * ImGui 的 format 参数必须是 printf 格式（如 "%.0f%%"），不能把翻译后的 "点缀比例：15%" 传进去，
     * 否则末尾的 '%' 会破坏滑条交互。
     */
    public static void renderAccentRatioSlider(
            MaterialMix mix,
            MaterialMixSetter setter,
            String id,
            Runnable onActivated) {
        MaterialMix current = mix;
        if (current.getAccentRatio() <= 0f) {
            MaterialMix updated = current.copy();
            updated.setAccentRatio(0.15f);
            setter.set(updated);
            current = updated;
        }

        float[] ratioPercent = {current.getAccentRatio() * 100f};
        ImGui.pushID(id);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        boolean ratioChanged = ImGui.sliderFloat(
            PlotI18n.tr("plugin.material.accent_ratio", Math.round(ratioPercent[0])) + "##slider",
            ratioPercent,
            0f,
            50f,
            "%.0f%%");
        if (ImGui.isItemActivated() && onActivated != null) {
            onActivated.run();
        }
        if (ratioChanged) {
            MaterialMix updated = current.copy();
            updated.setAccentRatio(ratioPercent[0] / 100f);
            setter.set(updated);
        }
        ImGui.popID();
    }

    public static MaterialMix fromPaletteSelection(List<String> blockIds, float existingRatio) {
        if (blockIds == null || blockIds.isEmpty()) {
            return MaterialMix.single(RoadMaterialUtils.DEFAULT_ROAD_BLOCK);
        }
        if (blockIds.size() == 1) {
            return MaterialMix.single(blockIds.getFirst());
        }
        float ratio = existingRatio > 0f ? existingRatio : 0.15f;
        return new MaterialMix(blockIds.get(0), blockIds.get(1), ratio);
    }

    public static void openPalettePicker(List<String> initialBlockIds, Consumer<List<String>> onConfirm) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            BlockConfigManager.getInstance().setPaletteFromBlockIds(initialBlockIds);
            client.setScreen(BlockConfigNativeScreen.forPaletteSelection(
                client.currentScreen, initialBlockIds, onConfirm));
        });
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
