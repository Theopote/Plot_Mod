package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 编辑 Tab 主工作区：概览、快速设计、专业工具启动器。 */
final class RoadEditWorkspace {
    private final RoadUiContext ctx;
    private final RoadDefaultParamsPanel defaultParamsPanel;
    private final RoadFloatingEditorHost floatingEditorHost;

    RoadEditWorkspace(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.ctx = ctx;
        this.defaultParamsPanel = defaultParamsPanel;
        this.floatingEditorHost = floatingEditorHost;
    }

    void renderSingle(RoadNetwork network, Road road) {
        int issueCount = RoadEditDiagnosticsBanner.countIssues(ctx, network, road);
        RoadEditOverviewStrip.render(ctx, network, road, issueCount);
        RoadEditDiagnosticsBanner.render(
            ctx,
            network,
            road,
            () -> floatingEditorHost.openOrFocus(ctx, RoadFloatingEditorKind.DIAGNOSTICS, road.getId()),
            issueCount);

        RoadUiSections.domain("plugin.road.edit.quick_design");
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoadCompact(ctx, road, ctx::pushRoadEditHistory);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.edit.quick_tune");
        RoadRouteQuickTune.renderForRoad(ctx, road, ctx::pushRoadEditHistory);
        ImGui.spacing();

        RoadEditToolLauncher.renderSingle(ctx, floatingEditorHost, road.getId());
    }

    void renderMulti() {
        var selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        RoadBatchEditBanner.render(selectedRoadIds.size());

        RoadUiSections.domain("plugin.road.edit.quick_design");
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoadsCompact(ctx, selectedRoadIds, ctx::pushRoadEditHistory);
        ImGui.spacing();

        RoadBatchQuickTune.render(ctx, selectedRoadIds, ctx::pushRoadEditHistory);
        ImGui.spacing();

        RoadEditToolLauncher.renderMulti(ctx, floatingEditorHost, selectedRoadIds);
    }

    /**
     * 未选中道路或空路网：不展示单路编辑面，仅保留选择入口与「新道路默认设置」。
     */
    void renderNoSelectionDefaults() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        boolean emptyNetwork = network.getEdges().isEmpty();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(emptyNetwork
                ? "plugin.road.style.no_roads_hint"
                : "plugin.road.edit.no_selection_hint"));
        ImGui.spacing();
        if (!RoadEditOverviewStrip.listEditableRoads(network).isEmpty()) {
            RoadEditOverviewStrip.renderRoadSelectorCombo(ctx, network, null);
            ImGui.spacing();
        }
        int defaultsFlags = emptyNetwork ? imgui.flag.ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.new_road_defaults"), defaultsFlags)) {
            RoadCrossSectionPreviewSection.render(ctx);
            ImGui.spacing();
            RoadPresetCards.renderConfigCompact(ctx);
            ImGui.spacing();
            RoadUiSections.section("plugin.road.edit.quick_tune");
            RoadRouteQuickTune.renderConfigDefaults(ctx);
            if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.advanced_defaults"))) {
                defaultParamsPanel.renderAdvancedDefaultsCollapsible();
            }
            ImGui.spacing();
            RoadUiSections.group("plugin.road.section.structure");
            TunnelStyleControls.renderGlobal(ctx);
        }
    }
}
