package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 样式 / 建造 Tab 的产品化控件（坡度预设、地形适应预设），隐藏底层工程参数。
 */
public final class RoadStyleProductControls {
    private static final float SLOPE_GENTLE = 6.0f;
    private static final float SLOPE_STANDARD = 10.0f;
    private static final float SLOPE_STEEP = 15.0f;
    private static final float SLOPE_EPSILON = 0.05f;

    private static final float TERRAIN_FOLLOW_FILL = 1.05f;
    private static final int TERRAIN_FOLLOW_BRIDGE = 5;
    private static final int TERRAIN_FOLLOW_TUNNEL = 6;

    private static final float TERRAIN_BALANCED_FILL = 1.1f;
    private static final int TERRAIN_BALANCED_BRIDGE = 3;
    private static final int TERRAIN_BALANCED_TUNNEL = 4;

    private static final float TERRAIN_FLATTEN_FILL = 1.25f;
    private static final int TERRAIN_FLATTEN_BRIDGE = 2;
    private static final int TERRAIN_FLATTEN_TUNNEL = 3;

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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.build.terrain_adaptation_hint"));
        TerrainPreset active = detectTerrainPreset(config);
        renderTerrainPresetButton(
            TerrainPreset.FOLLOW,
            active,
            () -> applyTerrainPreset(ctx, config, TerrainPreset.FOLLOW));
        ImGui.sameLine();
        renderTerrainPresetButton(
            TerrainPreset.BALANCED,
            active,
            () -> applyTerrainPreset(ctx, config, TerrainPreset.BALANCED));
        ImGui.sameLine();
        renderTerrainPresetButton(
            TerrainPreset.FLATTEN,
            active,
            () -> applyTerrainPreset(ctx, config, TerrainPreset.FLATTEN));
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
        if (selected) {
            ImGui.popStyleColor();
        }
    }

    private static void renderTerrainPresetButton(TerrainPreset preset, TerrainPreset active, Runnable onClick) {
        boolean selected = preset == active;
        if (selected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        if (ImGui.button(PlotI18n.tr(preset.labelKey) + "##road_terrain_" + preset.name())) {
            onClick.run();
        }
        if (selected) {
            ImGui.popStyleColor();
        }
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

    private static TerrainPreset detectTerrainPreset(RoadSystemConfig config) {
        if (matchesTerrain(config, TERRAIN_FOLLOW_FILL, TERRAIN_FOLLOW_BRIDGE, TERRAIN_FOLLOW_TUNNEL)) {
            return TerrainPreset.FOLLOW;
        }
        if (matchesTerrain(config, TERRAIN_BALANCED_FILL, TERRAIN_BALANCED_BRIDGE, TERRAIN_BALANCED_TUNNEL)) {
            return TerrainPreset.BALANCED;
        }
        if (matchesTerrain(config, TERRAIN_FLATTEN_FILL, TERRAIN_FLATTEN_BRIDGE, TERRAIN_FLATTEN_TUNNEL)) {
            return TerrainPreset.FLATTEN;
        }
        return TerrainPreset.CUSTOM;
    }

    private static boolean matchesTerrain(RoadSystemConfig config, float fill, int bridge, int tunnel) {
        return Math.abs(config.getFillFactor() - fill) <= 0.01f
            && config.getBridgeThreshold() == bridge
            && config.getTunnelThreshold() == tunnel;
    }

    private static void applyTerrainPreset(RoadUiContext ctx, RoadSystemConfig config, TerrainPreset preset) {
        switch (preset) {
            case FOLLOW -> {
                config.setFillFactor(TERRAIN_FOLLOW_FILL);
                config.setBridgeThreshold(TERRAIN_FOLLOW_BRIDGE);
                config.setTunnelThreshold(TERRAIN_FOLLOW_TUNNEL);
            }
            case BALANCED -> {
                config.setFillFactor(TERRAIN_BALANCED_FILL);
                config.setBridgeThreshold(TERRAIN_BALANCED_BRIDGE);
                config.setTunnelThreshold(TERRAIN_BALANCED_TUNNEL);
            }
            case FLATTEN -> {
                config.setFillFactor(TERRAIN_FLATTEN_FILL);
                config.setBridgeThreshold(TERRAIN_FLATTEN_BRIDGE);
                config.setTunnelThreshold(TERRAIN_FLATTEN_TUNNEL);
            }
            case CUSTOM -> {
            }
        }
        markConfigChanged(ctx);
    }

    private static void markConfigChanged(RoadUiContext ctx) {
        ctx.networkManager().getConfig().markCustom();
        ctx.onGenerationConfigChanged();
    }

    private enum SlopePreset {
        GENTLE("plugin.road.slope_preset.gentle"),
        STANDARD("plugin.road.slope_preset.standard"),
        STEEP("plugin.road.slope_preset.steep"),
        CUSTOM("plugin.road.slope_preset.custom");

        private final String labelKey;

        SlopePreset(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    private enum TerrainPreset {
        FOLLOW("plugin.road.terrain_preset.follow"),
        BALANCED("plugin.road.terrain_preset.balanced"),
        FLATTEN("plugin.road.terrain_preset.flatten"),
        CUSTOM("plugin.road.terrain_preset.custom");

        private final String labelKey;

        TerrainPreset(String labelKey) {
            this.labelKey = labelKey;
        }
    }
}
