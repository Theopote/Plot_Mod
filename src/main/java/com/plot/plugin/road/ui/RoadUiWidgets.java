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
        if (ImGui.button(displayName + "##pick", ImGui.getContentRegionAvailX() * 0.55f, 0)) {
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
        ImGui.sameLine();
        ImGui.textColored(PluginUiColors.HINT_GRAY, label);

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
            ImGui.textColored(PluginUiColors.WARNING, message);
        }
    }
}
