package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * 全局生成参数（桥/隧阈值、采样、纵断面平衡系数、默认净空等）。
 * 供「生成」Tab 与认领页高级区复用；变更会失效预览。
 */
public final class RoadGenerationSettingsPanel {
    private RoadGenerationSettingsPanel() {
    }

    /**
     * @param defaultOpen 生成 Tab 建议默认展开；认领页高级区可折叠
     */
    public static void render(RoadUiContext ctx, boolean defaultOpen) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        int flags = defaultOpen ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.generation_settings"), flags)) {
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.generation_settings_hint"));

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
