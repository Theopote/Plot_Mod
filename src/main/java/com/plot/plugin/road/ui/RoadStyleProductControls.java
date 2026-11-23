package com.plot.plugin.road.ui;


import com.plot.ui.component.UIUtils;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.terrain.RoadTerrainStyle;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/**
 * 样式 / 建造 Tab 的产品化控件（坡度预设、地形风格预设、架桥净高阈值）。
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
        ImGui.text(PlotI18n.tr("plugin.road.style.max_slope_road"));
        renderSlopePresetButtons(
            effective,
            value -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setMaxSlope(value);
            });
        float[] slope = {effective};
        if (EngineeringSlopeInput.render(
                "road_max_slope_" + road.getId(),
                PlotI18n.tr("plugin.road.max_slope_label"),
                slope,
                EngineeringSlopeInput.ValueKind.GRADE)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setMaxSlope(slope[0]);
        }
        if (road.getMaxSlope() != null) {
            if (ImGui.button(PlotI18n.tr("plugin.road.style.max_slope_reset_default") + "##road_slope_reset")) {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setMaxSlope(null);
            }
            if (ImGui.isItemHovered()) {
                UIUtils.setTooltip(PlotI18n.tr("plugin.road.style.max_slope_reset_default.hint"));
            }
        }
        ImGui.spacing();
    }

    public static void renderConfigMaxSlopePresets(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        ImGui.text(PlotI18n.tr("plugin.road.style.max_slope_default"));
        renderSlopePresetButtons(
            config.getMaxSlope(),
            value -> {
                config.setMaxSlope(value);
                markConfigChanged(ctx);
            });
        float[] slope = {config.getMaxSlope()};
        if (EngineeringSlopeInput.render(
                "config_max_slope",
                PlotI18n.tr("plugin.road.max_slope_label"),
                slope,
                EngineeringSlopeInput.ValueKind.GRADE)) {
            config.setMaxSlope(slope[0]);
            markConfigChanged(ctx);
        }
        ImGui.spacing();
    }

    /** 全局默认：贴合/平衡/平整（不含架桥阈值）。 */
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

    /** 全局默认架桥净高阈值。 */
    public static void renderConfigBridgeThresholdPresets(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        renderConfigBridgeThreshold(ctx, config);
    }

    /**
     * 逐路贴合/平衡/平整。编辑曲线权威下整组隐藏。
     *
     * @return 是否渲染了地形适应组
     */
    /** EDITED 纵断面权威下地形风格不参与生成，主面板不应为此开空卡。 */
    public static boolean showsRoadTerrainStylePresets(Road road) {
        return road != null && road.getEffectiveProfileSource() != ProfileSource.EDITED;
    }

    public static boolean renderRoadTerrainStylePresets(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (!showsRoadTerrainStylePresets(road)) {
            return false;
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
        return true;
    }

    /** 逐路架桥净高阈值。 */
    public static void renderRoadBridgeThresholdPresets(
            RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        renderRoadBridgeThreshold(ctx, road, ctx.networkManager().getConfig(), onHistory);
    }

    private static void renderConfigBridgeThreshold(RoadUiContext ctx, RoadSystemConfig config) {
        int styleDefault = RoadConstructionHeuristics.bridgeThreshold(config.getTerrainStyle());
        int effective = RoadConstructionHeuristics.bridgeThreshold(config);
        int[] value = {effective};
        boolean changed = renderBridgeThresholdSliderRow(
            "##config_bridge_threshold",
            value,
            config.getBridgeThreshold() != null,
            PlotI18n.tr("plugin.road.bridge_threshold_reset_style") + "##config_bridge_threshold_reset",
            () -> {
                config.setBridgeThreshold(null);
                markConfigChanged(ctx);
            },
            PlotI18n.tr("plugin.road.bridge_threshold_reset_style.hint", styleDefault));
        if (changed) {
            config.setBridgeThreshold(value[0]);
            markConfigChanged(ctx);
        }
        ImGui.spacing();
    }

    private static void renderRoadBridgeThreshold(
            RoadUiContext ctx,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory) {
        int styleDefault = RoadConstructionHeuristics.bridgeThreshold(
            road.getEffectiveTerrainStyle(config));
        int inherited = RoadConstructionHeuristics.bridgeThreshold(config);
        int effective = road.getEffectiveBridgeThreshold(config);
        int[] value = {effective};
        boolean changed = renderBridgeThresholdSliderRow(
            "##road_bridge_threshold_" + road.getId(),
            value,
            road.getBridgeThreshold() != null,
            PlotI18n.tr("plugin.road.bridge_threshold_reset_inherit")
                + "##road_bridge_threshold_reset_" + road.getId(),
            () -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setBridgeThreshold(null);
                ctx.onGenerationConfigChanged();
            },
            PlotI18n.tr(
                "plugin.road.bridge_threshold_reset_inherit.hint",
                inherited,
                styleDefault));
        if (changed) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setBridgeThreshold(value[0]);
            ctx.onGenerationConfigChanged();
        }
        ImGui.spacing();
    }

    /**
     * 布局：标准宽度滑动条（仅「N格」）| 参数名；恢复按钮单独下一行。
     *
     * @return 滑条值是否变化
     */
    private static boolean renderBridgeThresholdSliderRow(
            String sliderId,
            int[] value,
            boolean showReset,
            String resetLabel,
            Runnable onReset,
            String resetTooltip) {
        String label = PlotI18n.tr("plugin.road.bridge_threshold_label");
        float spacing = ImGui.getStyle().getItemSpacingX();
        ImGui.setNextItemWidth(ImGui.calcItemWidth());
        boolean changed = ImGui.sliderInt(
            sliderId,
            value,
            RoadParameterLimits.MIN_BRIDGE_THRESHOLD,
            RoadParameterLimits.MAX_BRIDGE_THRESHOLD,
            PlotI18n.tr("plugin.road.bridge_threshold_blocks", value[0]));
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("hint.plot.road.bridge_threshold"));
        }
        ImGui.sameLine(0f, spacing);
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("hint.plot.road.bridge_threshold"));
        }
        if (showReset) {
            if (ImGui.button(resetLabel)) {
                onReset.run();
            }
            if (ImGui.isItemHovered()) {
                UIUtils.setTooltip(resetTooltip);
            }
        }
        return changed;
    }

    private static void renderTerrainStyleButtons(
            RoadTerrainStyle active,
            java.util.function.Consumer<RoadTerrainStyle> onSelect) {
        // 标题由纵断面主面板 PluginSectionCard（##vp_sec_terrain_style）承担。
        RoadTerrainStyle effective = active != null ? active : RoadTerrainStyle.BALANCED;
        renderTerrainStyleButton(RoadTerrainStyle.FOLLOW, effective, () -> onSelect.accept(RoadTerrainStyle.FOLLOW));
        ImGui.sameLine();
        renderTerrainStyleButton(RoadTerrainStyle.BALANCED, effective, () -> onSelect.accept(RoadTerrainStyle.BALANCED));
        ImGui.sameLine();
        renderTerrainStyleButton(RoadTerrainStyle.SMOOTH, effective, () -> onSelect.accept(RoadTerrainStyle.SMOOTH));
        if (ImGui.isItemHovered()) {
            UIUtils.setTooltip(PlotI18n.tr("plugin.road.terrain_style.hint"));
        }
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
            UIUtils.setTooltip(PlotI18n.tr(terrainStyleHintKey(style)));
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
            UIUtils.setTooltip(PlotI18n.tr(preset.hintKey, preset.slopePercent()));
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
