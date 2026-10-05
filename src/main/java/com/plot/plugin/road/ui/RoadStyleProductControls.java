package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 样式 / 建造 Tab 的产品化控件（坡度预设、地形风格预设），隐藏底层工程参数。
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

    public static void renderTerrainStylePresets(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        renderTerrainStyleButtons(
            config.getTerrainStyle(),
            style -> {
                config.setTerrainStyle(style);
                markConfigChanged(ctx);
            });
    }

    public static void renderRoadTerrainStylePresets(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        renderTerrainStyleButtons(
            road.getEffectiveTerrainStyle(config),
            style -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setTerrainStyle(style);
                ctx.onGenerationConfigChanged();
            });
    }

    private static void renderTerrainStyleButtons(
            RoadTerrainStyle active,
            java.util.function.Consumer<RoadTerrainStyle> onSelect) {
        ImGui.text(PlotI18n.tr("plugin.road.terrain_style"));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.terrain_style.hint"));
        }
        RoadTerrainStyle effective = active != null ? active : RoadTerrainStyle.BALANCED;
        renderTerrainStyleButton(RoadTerrainStyle.FOLLOW, effective, () -> onSelect.accept(RoadTerrainStyle.FOLLOW));
        ImGui.sameLine();
        renderTerrainStyleButton(RoadTerrainStyle.BALANCED, effective, () -> onSelect.accept(RoadTerrainStyle.BALANCED));
        ImGui.sameLine();
        renderTerrainStyleButton(RoadTerrainStyle.SMOOTH, effective, () -> onSelect.accept(RoadTerrainStyle.SMOOTH));
        ImGui.spacing();
    }

    private static void renderTerrainStyleButton(
            RoadTerrainStyle style,
            RoadTerrainStyle active,
            Runnable onClick) {
        boolean selected = style == active;
        if (selected) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        if (ImGui.button(PlotI18n.tr(terrainStyleLabelKey(style)) + "##road_terrain_style_" + style.name())) {
            onClick.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr(terrainStyleHintKey(style)));
        }
        if (selected) {
            ImGui.popStyleColor();
        }
    }

    private static String terrainStyleLabelKey(RoadTerrainStyle style) {
        return switch (style) {
            case FOLLOW -> "plugin.road.terrain_style.follow";
            case BALANCED -> "plugin.road.terrain_style.balanced";
            case SMOOTH -> "plugin.road.terrain_style.smooth";
        };
    }

    private static String terrainStyleHintKey(RoadTerrainStyle style) {
        return switch (style) {
            case FOLLOW -> "plugin.road.terrain_style.follow.hint";
            case BALANCED -> "plugin.road.terrain_style.balanced.hint";
            case SMOOTH -> "plugin.road.terrain_style.smooth.hint";
        };
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
