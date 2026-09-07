package com.plot.plugin.building.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.road.RoadMaterialUtils;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.screen.BlockConfigNativeScreen;
import com.plot.ui.screen.PlotScreen;
import com.plot.ui.screen.PlotScreenState;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

/** 建筑插件共享 ImGui 控件。 */
public final class BuildingUiWidgets {
    private BuildingUiWidgets() {
    }

    public static void renderSelectionSummary(BuildingUiContext ctx) {
        if (ctx.selection().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.selection_empty"));
            return;
        }
        ImGui.text(PlotI18n.tr(
            "plugin.building.selection_summary",
            ctx.selection().size(),
            String.format("%.1f", ctx.selection().totalArea(ctx.project()))));
        BuildingFootprint primary = ctx.selection().primary(ctx.project());
        if (primary != null && ctx.selection().size() > 1) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.building.selection_primary", primary.getName()));
        }
    }

    public static void renderBuildingSelector(BuildingUiContext ctx) {
        if (ctx.project().getBuildingCount() == 0) {
            return;
        }
        List<BuildingFootprint> buildings = BuildingListHelper.sorted(ctx.project(), ctx.buildingSortMode());
        String[] labels = buildings.stream()
            .map(BuildingFootprint::getName)
            .toArray(String[]::new);
        String[] ids = buildings.stream()
            .map(BuildingFootprint::getId)
            .toArray(String[]::new);
        String primaryId = ctx.selection().primaryId();
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(primaryId)) {
                current = i;
                break;
            }
        }
        ImInt buildingIndex = new ImInt(current);
        if (ImGui.combo(PlotI18n.tr("plugin.building.select_building"), buildingIndex, labels)) {
            ctx.selection().select(ids[buildingIndex.get()], false);
        }
    }

    public static void renderMaterialMixButton(
            BuildingUiContext ctx,
            String label,
            MaterialMix currentMix,
            Consumer<MaterialMix> onSelected) {
        MaterialMix mix = currentMix != null
            ? currentMix
            : MaterialMix.single(BuildingFootprint.DEFAULT_WALL_MATERIAL);
        String displayName = RoadMaterialUtils.getDisplayName(mix.getPrimaryMaterial());
        if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
            displayName += " + " + RoadMaterialUtils.getDisplayName(mix.getAccentMaterial());
        }
        ImGui.text(label);
        ImGui.sameLine();
        if (ImGui.button(displayName + "##" + label, 0, 0)) {
            List<String> initial = new java.util.ArrayList<>();
            if (mix.getPrimaryMaterial() != null && !mix.getPrimaryMaterial().isBlank()) {
                initial.add(mix.getPrimaryMaterial());
            }
            if (mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank()) {
                initial.add(mix.getAccentMaterial());
            }
            openPalettePicker(initial, blockIds ->
                onSelected.accept(RoadUiWidgets.fromPaletteSelection(blockIds, mix.getAccentRatio())));
        }

        boolean hasAccentMaterial = mix.getAccentMaterial() != null && !mix.getAccentMaterial().isBlank();
        if (hasAccentMaterial) {
            RoadUiWidgets.renderAccentRatioSlider(mix, onSelected::accept, label, null);
        }
    }

    public static void openPalettePicker(
            List<String> initialBlockIds,
            Consumer<List<String>> onConfirm) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            client.setScreen(BlockConfigNativeScreen.forPaletteSelection(
                client.currentScreen, initialBlockIds, onConfirm));
        });
    }

    public static void renderMaterialButton(
            BuildingUiContext ctx,
            String label,
            String currentBlockId,
            Consumer<String> onSelected) {
        ImGui.text(label);
        ImGui.sameLine();
        if (ImGui.button(currentBlockId + "##" + label, 0, 0)) {
            openBlockPicker(currentBlockId, onSelected);
        }
    }

    public static void openBlockPicker(String currentBlockId, Consumer<String> onSelected) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof PlotScreen) {
                PlotScreenState.markSwitchingToPlotSubScreen();
            }
            client.setScreen(BlockConfigNativeScreen.forSingleSelection(
                client.currentScreen, currentBlockId, onSelected));
        });
    }
}
