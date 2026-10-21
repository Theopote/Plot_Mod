package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 编辑 Tab 主工作区：预设、横断面、快速调整、材质与高级设计。 */
final class RoadEditWorkspace {
    private final RoadUiContext ctx;
    private final RoadDesignPanel designPanel;
    private final RoadDefaultParamsPanel defaultParamsPanel;

    RoadEditWorkspace(
            RoadUiContext ctx,
            RoadDesignPanel designPanel,
            RoadDefaultParamsPanel defaultParamsPanel) {
        this.ctx = ctx;
        this.designPanel = designPanel;
        this.defaultParamsPanel = defaultParamsPanel;
    }

    void renderSingle(RoadNetwork network, Road road) {
        RoadEditDiagnosticsBanner.render(
            ctx,
            network,
            road,
            () -> designPanel.requestOpenDiagnostics());
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoadCompact(ctx, road, ctx.networkManager()::pushHistory);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.edit.quick_tune");
        RoadRouteQuickTune.renderForRoad(ctx, road, ctx.networkManager()::pushHistory);
        ImGui.spacing();

        renderCrossSectionAppearance(road);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.edit.materials_facilities"))) {
            renderMaterialsAndFurniture(road);
        }

        designPanel.renderAdvancedDesignSection(network, road);
    }

    void renderMulti() {
        var selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoadsCompact(ctx, selectedRoadIds, ctx.networkManager()::pushHistory);
        ImGui.spacing();

        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", selectedRoadIds.size()));
        RoadBatchQuickTune.render(ctx, selectedRoadIds, ctx.networkManager()::pushHistory);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.edit.more_cross_section_params"))) {
            RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
            RoadBatchCrossSectionEditor.renderDraftFieldsWithoutPreview(ctx, synced);
        }
    }

    void renderNoSelectionDefaults() {
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.edit.no_selection_hint"));
        ImGui.spacing();
        RoadUiSections.section("plugin.road.route.new_road_defaults");
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderConfigCompact(ctx);
        ImGui.spacing();
        RoadUiSections.section("plugin.road.edit.quick_tune");
        RoadRouteQuickTune.renderConfigDefaults(ctx);
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.advanced_defaults"))) {
            defaultParamsPanel.renderAdvancedDefaultsCollapsible();
        }
    }

    private void renderCrossSectionAppearance(Road road) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forRoad(
            road, config, ctx::pushRoadEditHistory);
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.styleAppearance();
        RoadUiSections.section("plugin.road.edit.cross_section");
        CrossSectionDraftEditor.renderCrossSection(ctx, mutator, options);
    }

    private void renderMaterialsAndFurniture(Road road) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forRoad(
            road, config, ctx::pushRoadEditHistory);
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.styleAppearance();
        RoadUiSections.group("plugin.road.edit.materials_group");
        CrossSectionDraftEditor.renderMaterials(ctx, mutator, options);
        ImGui.spacing();
        RoadUiSections.group("plugin.road.edit.facilities_group");
        CrossSectionDraftEditor.renderFurniture(ctx, mutator, options);
    }
}
