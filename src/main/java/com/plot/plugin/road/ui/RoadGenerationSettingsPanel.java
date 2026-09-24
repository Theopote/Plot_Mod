package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;

/**
 * 全局生成参数（桥/隧阈值、采样、纵断面平衡系数、默认净空等）。
 * 主界面为产品化预设；工程参数折叠在「高级生成设置」。
 */
public final class RoadGenerationSettingsPanel {
    private RoadGenerationSettingsPanel() {
    }

    /** 建造 Tab 主界面：地形适应、坡度预设、自动桥隧说明。 */
    public static void renderPrimary(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.build.auto_terrain_hint"));
        ImGui.spacing();

        ImGui.text(PlotI18n.tr("plugin.road.build.bridge_mode"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.build.auto_mode"));
        ImGui.text(PlotI18n.tr("plugin.road.build.tunnel_mode"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.build.auto_mode"));
        ImGui.spacing();

        RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);
        RoadStyleProductControls.renderTerrainAdaptationPresets(ctx);

        ImBoolean bridgePillars = new ImBoolean(config.isGenerateBridgePillars());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.generate_bridge_pillars"), bridgePillars)) {
            config.setGenerateBridgePillars(bridgePillars.get());
            markChanged(ctx);
        }
    }

    /** 建造 Tab 高级：桥隧阈值、采样、净空等工程参数。 */
    public static void renderAdvanced(RoadUiContext ctx) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.build.advanced_generation"))) {
            return;
        }
        renderEngineering(ctx);
    }

    /**
     * @param defaultOpen 是否默认展开设置区域
     * @deprecated 使用 {@link #renderPrimary} 与 {@link #renderAdvanced}
     */
    @Deprecated
    public static void render(RoadUiContext ctx, boolean defaultOpen) {
        int flags = defaultOpen ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generation_settings"), flags)) {
            return;
        }
        renderEngineering(ctx);
    }

    private static void renderEngineering(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.generation_settings_hint"));

        int[] bridgeThreshold = {config.getBridgeThreshold()};
        if (ImGui.sliderInt(
            "##road_bridge_threshold_gen",
            bridgeThreshold,
            RoadParameterLimits.MIN_BRIDGE_THRESHOLD,
            RoadParameterLimits.MAX_BRIDGE_THRESHOLD,
            PlotI18n.tr("plugin.road.bridge_threshold", bridgeThreshold[0])
        )) {
            config.setBridgeThreshold(bridgeThreshold[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.bridge_threshold");

        int[] tunnelThreshold = {config.getTunnelThreshold()};
        if (ImGui.sliderInt(
            "##road_tunnel_threshold_gen",
            tunnelThreshold,
            RoadParameterLimits.MIN_TUNNEL_THRESHOLD,
            RoadParameterLimits.MAX_TUNNEL_THRESHOLD,
            PlotI18n.tr("plugin.road.tunnel_threshold", tunnelThreshold[0])
        )) {
            config.setTunnelThreshold(tunnelThreshold[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.tunnel_threshold");

        int[] tunnelClearance = {config.getTunnelClearanceHeight()};
        if (ImGui.sliderInt(
            "##road_tunnel_clearance_height",
            tunnelClearance,
            3,
            12,
            PlotI18n.tr("plugin.road.tunnel_clearance_height", tunnelClearance[0])
        )) {
            config.setTunnelClearanceHeight(tunnelClearance[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.tunnel_clearance_height");

        int[] tunnelSideClearance = {config.getTunnelSideClearance()};
        if (ImGui.sliderInt(
            "##road_tunnel_side_clearance",
            tunnelSideClearance,
            0,
            4,
            PlotI18n.tr("plugin.road.tunnel_side_clearance", tunnelSideClearance[0])
        )) {
            config.setTunnelSideClearance(tunnelSideClearance[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.tunnel_side_clearance");

        int[] tunnelLiningThickness = {config.getTunnelLiningThickness()};
        if (ImGui.sliderInt(
            "##road_tunnel_lining_thickness",
            tunnelLiningThickness,
            1,
            3,
            PlotI18n.tr("plugin.road.tunnel_lining_thickness", tunnelLiningThickness[0])
        )) {
            config.setTunnelLiningThickness(tunnelLiningThickness[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.tunnel_lining_thickness");

        int[] tunnelAccentSpacing = {config.getTunnelAccentSpacing()};
        if (ImGui.sliderInt(
            "##road_tunnel_accent_spacing",
            tunnelAccentSpacing,
            0,
            32,
            PlotI18n.tr("plugin.road.tunnel_accent_spacing", tunnelAccentSpacing[0])
        )) {
            config.setTunnelAccentSpacing(tunnelAccentSpacing[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.tunnel_accent_spacing");

        float[] fillFactor = {config.getFillFactor()};
        if (ImGui.sliderFloat(
            "##road_profile_fill_factor",
            fillFactor,
            1.0f,
            2.0f,
            PlotI18n.tr("plugin.road.profile_balance_factor", String.format("%.2f", fillFactor[0]))
        )) {
            config.setFillFactor(fillFactor[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.fill_factor");

        float[] sampleDistance = {(float) config.getPathSampleDistance()};
        if (ImGui.sliderFloat(
            "##road_path_sample_distance",
            sampleDistance,
            (float) RoadParameterLimits.MIN_PATH_SAMPLE_DISTANCE,
            (float) RoadParameterLimits.MAX_PATH_SAMPLE_DISTANCE,
            PlotI18n.tr("plugin.road.path_sample_distance", sampleDistance[0])
        )) {
            config.setPathSampleDistance(sampleDistance[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.path_sample_distance");

        int[] clearance = {(int) Math.round(config.getDefaultCrossingClearance())};
        if (ImGui.sliderInt(
            "##road_default_crossing_clearance",
            clearance,
            RoadParameterLimits.MIN_CROSSING_CLEARANCE,
            RoadParameterLimits.MAX_CROSSING_CLEARANCE,
            PlotI18n.tr("plugin.road.default_crossing_clearance", clearance[0])
        )) {
            config.setDefaultCrossingClearance(clearance[0]);
            markChanged(ctx);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.default_crossing_clearance");
    }

    private static void markChanged(RoadUiContext ctx) {
        ctx.networkManager().getConfig().markCustom();
        ctx.onGenerationConfigChanged();
    }
}
