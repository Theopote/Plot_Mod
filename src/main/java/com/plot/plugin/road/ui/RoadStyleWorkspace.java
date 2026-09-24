package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/** 样式 Tab 主工作区：材质、附属设施与高级道路设计（不含预设与基础尺寸）。 */
final class RoadStyleWorkspace {
    private final RoadUiContext ctx;
    private final RoadDesignPanel designPanel;

    RoadStyleWorkspace(RoadUiContext ctx, RoadDesignPanel designPanel) {
        this.ctx = ctx;
        this.designPanel = designPanel;
    }

    void renderSingle(RoadNetwork network, Road road) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forRoad(
            road, config, ctx::pushRoadEditHistory);
        CrossSectionDraftEditorOptions options = CrossSectionDraftEditorOptions.styleAppearance();

        if (ImGui.collapsingHeader(
            PlotI18n.tr("plugin.road.edit_materials"),
            ImGuiTreeNodeFlags.DefaultOpen)) {
            CrossSectionDraftEditor.renderMaterials(ctx, mutator, options);
        }
        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.edit_furniture"))) {
            CrossSectionDraftEditor.renderFurniture(ctx, mutator, options);
        }

        designPanel.renderAdvancedDesignSection(network, road);
    }

    void renderMulti() {
        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.style.batch_hint", ctx.networkManager().getSelectedRoadIds().size()));
        RoadBatchCrossSectionEditor.renderStyleFields(ctx, synced);
    }
}
