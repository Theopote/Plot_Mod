package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/** 编辑 Tab 主工作区：预设、横断面、基础参数、材质与高级设计。 */
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
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoad(ctx, road, ctx.networkManager()::pushHistory);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.edit.basic_params");
        RoadRouteQuickTune.renderForRoad(ctx, road, ctx.networkManager()::pushHistory);
        RoadStyleProductControls.renderRoadMaxSlopePresets(
            ctx, road, ctx.networkManager()::pushHistory);
        ImGui.spacing();

        renderCrossSectionAppearance(road);

        if (ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.edit.materials_facilities"),
            ImGuiTreeNodeFlags.DefaultOpen)) {
            renderMaterialsAndFurniture(road);
        }

        designPanel.renderAdvancedDesignSection(network, road);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.more_road_settings"))) {
            defaultParamsPanel.renderAdvancedDefaultsCollapsible();
        }
    }

    void renderMulti() {
        var selectedRoadIds = ctx.networkManager().getSelectedRoadIds();
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        RoadPresetCards.renderForRoads(ctx, selectedRoadIds, ctx.networkManager()::pushHistory);
        ImGui.spacing();

        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", selectedRoadIds.size()));
        RoadBatchCrossSectionEditor.renderDraftFields(ctx, synced);
    }

    void renderNoSelectionDefaults() {
        RoadUiSections.section("plugin.road.route.new_road_defaults");
        RoadCrossSectionPreviewSection.render(ctx);
        ImGui.spacing();
        defaultParamsPanel.renderRoutePrimary();
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
        CrossSectionDraftEditor.renderMaterials(ctx, mutator, options);
        ImGui.spacing();
        CrossSectionDraftEditor.renderFurniture(ctx, mutator, options);
    }
}
