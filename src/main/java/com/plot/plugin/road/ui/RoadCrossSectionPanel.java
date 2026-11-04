package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * 横断面工作区：样式、基础尺寸、结构与设施入口（复用原编辑工作区）。
 */
public final class RoadCrossSectionPanel {
    private final RoadUiContext ctx;
    private final RoadEditPanel editPanel;

    public RoadCrossSectionPanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.ctx = ctx;
        this.editPanel = new RoadEditPanel(ctx, defaultParamsPanel, floatingEditorHost);
    }

    public RoadEditPanel editPanel() {
        return editPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.style.no_roads_hint"));
            return;
        }
        editPanel.render();
        ImGui.separator();
        renderGlobalStructureOptions();
    }

    /** 桥墩/护栏等仍属全局建造配置，在横断面结构区分组展示以免误认为逐路属性。 */
    private void renderGlobalStructureOptions() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (config == null) {
            return;
        }
        RoadUiSections.section("plugin.road.section.structure_global");
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.structure_global_scope_hint"));
        ImBoolean bridgePillars = new ImBoolean(config.isGenerateBridgePillars());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.generate_bridge_pillars"), bridgePillars)) {
            config.setGenerateBridgePillars(bridgePillars.get());
            config.markCustom();
            ctx.onGenerationConfigChanged();
        }
        ImBoolean bridgeGuardrail = new ImBoolean(config.isIncludeBridgeGuardrail());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_bridge_guardrail"), bridgeGuardrail)) {
            config.setIncludeBridgeGuardrail(bridgeGuardrail.get());
            config.markCustom();
            ctx.onGenerationConfigChanged();
        }
    }

    public boolean tryLeaveEditors() {
        return editPanel.tryLeaveEdit();
    }

    public boolean finishPendingEdits() {
        return editPanel.finishPendingEdits();
    }

    public void reconcileRoadScope() {
        editPanel.reconcileRoadScope();
    }
}
