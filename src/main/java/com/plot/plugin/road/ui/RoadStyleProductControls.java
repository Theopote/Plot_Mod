package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics.TerrainAdaptationPreset;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 样式 / 建造 Tab 的产品化控件（坡度预设、地形适应预设），隐藏底层工程参数。
 */
public final class RoadStyleProductControls {
    /** 平缓：园区、步行友好纵坡。 */
    private static final float SLOPE_GENTLE = 5.0f;
    /** 标准：与全局默认一致。 */
    private static final float SLOPE_STANDARD = 10.0f;
    /** 陡峭：山地/服务道路，仍控制在常见工程上限内。 */
    private static final float SLOPE_STEEP = 18.0f;
    private static final float SLOPE_EPSILON = 0.05f;

    private RoadStyleProductControls() {
    }

    public static void renderRoadMaxSlopePresets(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        float effective = road.getEffectiveMaxSlope(config);
        ImGui.text(PlotI18n.tr("plugin.road.style.max_slope"));
        renderSlopePresetButtons(
            effective,
            value -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setMaxSlope(value);
            });
        ImGui.spacing();
    }

    public static void renderConfigMaxSlopePresets(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.style.max_slope"));
        renderSlopePresetButtons(
            config.getMaxSlope(),
            value -> {
                config.setMaxSlope(value);
                markConfigChanged(ctx);
            });
        ImGui.spacing();
    }

    public static void renderTerrainAdaptationPresets(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.build.terrain_adaptation"));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.build.terrain_adaptation_hint"));
        }
        TerrainAdaptationPreset active = config.getTerrainAdaptation();
        renderTerrainPresetButton(
            TerrainAdaptationPreset.FOLLOW,
            active,
            () -> applyTerrainPreset(ctx, config, TerrainAdaptationPreset.FOLLOW));
        ImGui.sameLine();
        renderTerrainPresetButton(
            TerrainAdaptationPreset.BALANCED,
            active,
            () -> applyTerrainPreset(ctx, config, TerrainAdaptationPreset.BALANCED));
        ImGui.sameLine();
        renderTerrainPresetButton(
            TerrainAdaptationPreset.FLATTEN,
            active,
            () -> applyTerrainPreset(ctx, config, TerrainAdaptationPreset.FLATTEN));
        ImGui.spacing();
    }

    private static void renderSlopePresetButtons(float current, java.util.function.Consumer<Float> onSelect) {
        SlopePreset active = detectSlopePreset(current);
        renderSlopeButton(SlopePreset.GENTLE, active, () -> onSelect.accept(SLOPE_GENTLE));
        ImGui.sameLine();
        renderSlopeButton(SlopePreset.STANDARD, active, () -> onSelect.accept(SLOPE_STANDARD));
        ImGui.sameLine();
        renderSlopeButton(SlopePreset.STEEP, active, () -> onSelect.accept(SLOPE_STEEP));
    }

    private static void renderSlopeButton(SlopePreset preset, SlopePreset active, Runnable onClick) {
        boolean selected = preset == active;
        if (selected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        if (ImGui.button(PlotI18n.tr(preset.labelKey) + "##road_slope_" + preset.name())) {
            onClick.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(preset.hintKey, preset.slopePercent()));
        }
        if (selected) {
            ImGui.popStyleColor();
        }
    }

    private static void renderTerrainPresetButton(
            TerrainAdaptationPreset preset,
            TerrainAdaptationPreset active,
            Runnable onClick) {
        boolean selected = preset == active;
        if (selected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        if (ImGui.button(PlotI18n.tr(terrainLabelKey(preset)) + "##road_terrain_" + preset.name())) {
            onClick.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(terrainHintKey(preset)));
        }
        if (selected) {
            ImGui.popStyleColor();
        }
    }

    private static String terrainLabelKey(TerrainAdaptationPreset preset) {
        return switch (preset) {
            case FOLLOW -> "plugin.road.terrain_preset.follow";
            case BALANCED -> "plugin.road.terrain_preset.balanced";
            case FLATTEN -> "plugin.road.terrain_preset.flatten";
        };
    }

    private static String terrainHintKey(TerrainAdaptationPreset preset) {
        return switch (preset) {
            case FOLLOW -> "plugin.road.terrain_preset.follow.hint";
            case BALANCED -> "plugin.road.terrain_preset.balanced.hint";
            case FLATTEN -> "plugin.road.terrain_preset.flatten.hint";
        };
    }

    private static void applyTerrainPreset(
            RoadUiContext ctx,
            RoadSystemConfig config,
            TerrainAdaptationPreset preset) {
        config.setTerrainAdaptation(preset);
        markConfigChanged(ctx);
    }

    private static SlopePreset detectSlopePreset(float value) {
        if (Math.abs(value - SLOPE_GENTLE) <= SLOPE_EPSILON) {
            return SlopePreset.GENTLE;
        }
        if (Math.abs(value - SLOPE_STANDARD) <= SLOPE_EPSILON) {
            return SlopePreset.STANDARD;
        }
        if (Math.abs(value - SLOPE_STEEP) <= SLOPE_EPSILON) {
            return SlopePreset.STEEP;
        }
        return SlopePreset.CUSTOM;
    }

    private static void markConfigChanged(RoadUiContext ctx) {
        ctx.networkManager().getConfig().markCustom();
        ctx.onGenerationConfigChanged();
    }

    private enum SlopePreset {
        GENTLE("plugin.road.slope_preset.gentle", "plugin.road.slope_preset.gentle.hint", SLOPE_GENTLE),
        STANDARD("plugin.road.slope_preset.standard", "plugin.road.slope_preset.standard.hint", SLOPE_STANDARD),
        STEEP("plugin.road.slope_preset.steep", "plugin.road.slope_preset.steep.hint", SLOPE_STEEP),
        CUSTOM("plugin.road.slope_preset.custom", "plugin.road.slope_preset.custom", Float.NaN);

        private final String labelKey;
        private final String hintKey;
        private final float slopeValue;

        SlopePreset(String labelKey, String hintKey, float slopeValue) {
            this.labelKey = labelKey;
            this.hintKey = hintKey;
            this.slopeValue = slopeValue;
        }

        String slopePercent() {
            return Float.isNaN(slopeValue) ? "—" : String.format("%.0f", slopeValue);
        }
    }
}
