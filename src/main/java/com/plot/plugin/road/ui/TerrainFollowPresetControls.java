package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** FIT_TERRAIN 道路的地形趋势跟随强度（与 maxSlope、土方 fillFactor 解耦）。 */
public final class TerrainFollowPresetControls {

    private TerrainFollowPresetControls() {
    }

    public static void render(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.terrain_follow_preset"));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.terrain_follow_preset.hint"));
        }
        TerrainFollowPreset active = road.getEffectiveTerrainFollowPreset();
        renderPresetButton(PresetOption.GENTLE, active, () -> applyPreset(ctx, road, onHistory, TerrainFollowPreset.GENTLE));
        ImGui.sameLine();
        renderPresetButton(PresetOption.STANDARD, active, () -> applyPreset(ctx, road, onHistory, TerrainFollowPreset.STANDARD));
        ImGui.sameLine();
        renderPresetButton(PresetOption.TIGHT, active, () -> applyPreset(ctx, road, onHistory, TerrainFollowPreset.TIGHT));
        ImGui.spacing();
    }

    private static void applyPreset(
            RoadUiContext ctx,
            Road road,
            Runnable onHistory,
            TerrainFollowPreset preset) {
        if (onHistory != null) {
            onHistory.run();
        }
        road.setTerrainFollowPreset(preset);
        ctx.onGenerationConfigChanged();
    }

    private static void renderPresetButton(
            PresetOption option,
            TerrainFollowPreset active,
            Runnable onClick) {
        boolean selected = option.preset == active;
        if (selected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        if (ImGui.button(PlotI18n.tr(option.labelKey) + "##terrain_follow_" + option.name())) {
            onClick.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(
                option.hintKey,
                option.preset.medianWindowMeters(),
                option.preset.movingAverageWindowMeters()));
        }
        if (selected) {
            ImGui.popStyleColor();
        }
    }

    private enum PresetOption {
        GENTLE(
            "plugin.road.terrain_follow_preset.gentle",
            "plugin.road.terrain_follow_preset.gentle.hint",
            TerrainFollowPreset.GENTLE),
        STANDARD(
            "plugin.road.terrain_follow_preset.standard",
            "plugin.road.terrain_follow_preset.standard.hint",
            TerrainFollowPreset.STANDARD),
        TIGHT(
            "plugin.road.terrain_follow_preset.tight",
            "plugin.road.terrain_follow_preset.tight.hint",
            TerrainFollowPreset.TIGHT);

        private final String labelKey;
        private final String hintKey;
        private final TerrainFollowPreset preset;

        PresetOption(String labelKey, String hintKey, TerrainFollowPreset preset) {
            this.labelKey = labelKey;
            this.hintKey = hintKey;
            this.preset = preset;
        }
    }
}
