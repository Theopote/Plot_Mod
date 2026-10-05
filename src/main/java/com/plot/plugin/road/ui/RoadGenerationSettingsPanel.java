package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
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

    /** 建造 Tab 主界面：地形适应、坡度预设、桥梁选项。 */
    public static void renderPrimary(RoadUiContext ctx) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }

        if (showsTerrainAdaptiveControls(ctx)) {
            RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);
            RoadStyleProductControls.renderTerrainAdaptationPresets(ctx);
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
