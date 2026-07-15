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

        if (ImGui.button(displayName + buttonId, ImGui.getContentRegionAvailX() * 0.55f, 0)) {
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
            float[] ratio = {mix.getAccentRatio() > 0f ? mix.getAccentRatio() : 0.15f};
            if (ImGui.sliderFloat(
                PlotI18n.tr("plugin.material.accent_ratio", Math.round(ratio[0] * 100)) + buttonId,
                ratio,
                0f,
                0.5f,
                "%.0f%%")) {
                if (pushHistoryOnChange) {
                    ctx.networkManager().pushHistory();
                }
                MaterialMix updated = mix.copy();
                updated.setAccentRatio(ratio[0]);
                setter.set(updated);
            }
        }
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
