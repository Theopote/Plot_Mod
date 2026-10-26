package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.profile.RoadProfileRoadList;
import com.plot.plugin.road.vertical.RoadVerticalStrategy;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * 全局生成参数（产品化预设与桥梁选项）。
 */
public final class RoadGenerationSettingsPanel {
    private RoadGenerationSettingsPanel() {
    }

    /** 单选水平道路时隐藏地形适应与全局最大坡度预设。 */
    public static boolean showsTerrainAdaptiveControls(RoadUiContext ctx) {
        if (ctx.networkManager().getSelectedRoadIds().size() != 1) {
            return true;
        }
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return true;
        }
        return RoadVerticalStrategy.fromRoad(road) != RoadVerticalStrategy.FLAT;
    }

    /** 单选地形适应道路时，生成设置展示道路级坡度/地形/纵断面来源。 */
    public static boolean showsPerRoadTerrainControls(RoadUiContext ctx, Road road) {
        return road != null
            && RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.TERRAIN_ADAPTIVE
            && showsTerrainAdaptiveControls(ctx);
    }

    /** 建造 Tab 主界面：目标道路、路径属性、坡度/地形/纵断面来源、桥梁选项。 */
    public static void renderPrimary(
            RoadUiContext ctx,
            RoadPathVerticalPropertyPanel pathPropertyPanel,
            Runnable openProfileEditorForSelectedRoad) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        if (!network.getEdges().isEmpty()) {
            RoadGenerateTargetBar.render(ctx, network);
            ImGui.spacing();
            if (pathPropertyPanel != null) {
                pathPropertyPanel.renderGenerationSettings(ctx);
                ImGui.spacing();
            }
        }

        Road activeRoad = RoadProfileRoadList.resolveActiveRoad(network, ctx);
        if (showsPerRoadTerrainControls(ctx, activeRoad)) {
            RoadStyleProductControls.renderRoadMaxSlopePresets(
                ctx, activeRoad, ctx.networkManager()::pushHistory);
            RoadStyleProductControls.renderRoadTerrainStylePresets(
                ctx, activeRoad, ctx.networkManager()::pushHistory);
            ProfileSourceControls.render(
                ctx,
                network,
                activeRoad,
                openProfileEditorForSelectedRoad);
        } else if (showsTerrainAdaptiveControls(ctx)) {
            RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);
            RoadStyleProductControls.renderTerrainStylePresets(ctx);
        } else {
            RoadUiWidgets.textWrappedColored(
                com.plot.plugin.ui.PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.generate.flat_terrain_controls_hidden"));
        }

        ImBoolean bridgePillars = new ImBoolean(config.isGenerateBridgePillars());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.generate_bridge_pillars"), bridgePillars)) {
            config.setGenerateBridgePillars(bridgePillars.get());
            markChanged(ctx);
        }
        ImBoolean bridgeGuardrail = new ImBoolean(config.isIncludeBridgeGuardrail());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_bridge_guardrail"), bridgeGuardrail)) {
            config.setIncludeBridgeGuardrail(bridgeGuardrail.get());
            markChanged(ctx);
        }

    }

    private static void markChanged(RoadUiContext ctx) {
        ctx.networkManager().getConfig().markCustom();
        ctx.onGenerationConfigChanged();
    }
}
