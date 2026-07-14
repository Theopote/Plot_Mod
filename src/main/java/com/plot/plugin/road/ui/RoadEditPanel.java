package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.ui.component.Icons;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路编辑 Tab：边列表、批量编辑、单条边属性与坡度覆盖。
 */
public final class RoadEditPanel {
    private final RoadUiContext ctx;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;

    public RoadEditPanel(RoadUiContext ctx, RoadEdgeListPanel edgeListPanel, RoadJunctionPanel junctionPanel) {
        this.ctx = ctx;
        this.edgeListPanel = edgeListPanel;
        this.junctionPanel = junctionPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        List<RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_edges"));
            return;
        }

        ctx.networkManager().ensureSelectionValid();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edge_list_hint"));
        edgeListPanel.renderToolbar("##edit");
        edgeListPanel.renderList(180, true, "edit_edge_list");

        renderBatchEditPanel();
        junctionPanel.renderEditor();

        ImGui.separator();
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        RoadEdge current = network.getEdge(primaryId);
        if (current == null) {
            return;
        }
        Road road = ctx.networkManager().getRoadForEdge(current);
        if (road == null) {
            return;
        }

        ImGui.text(PlotI18n.tr("plugin.road.single_edge_edit",
            RoadEdgeListHelper.formatEdgeLabel(network, current)));
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.editing_road", RoadEdgeListHelper.formatRoadLabel(network, road)));

        renderElevationHint(current);

        RoadCrossSectionEditor.renderPreview(road, ctx.networkManager().getConfig());
        RoadCrossSectionEditor.renderPresetButtons(ctx, road, null);
        RoadCrossSectionEditor.renderFields(ctx, road, ctx.networkManager()::pushHistory);

        renderSlopeOverrides(current);
    }

    private void renderElevationHint(RoadEdge edge) {
        RoadGenerationResult edgeResult = ctx.previewManager().getLastEdgeResult(edge.getId());
        if (edgeResult == null || !edgeResult.hasProfileData()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.elevation_hint_preview_required"));
            return;
        }

        int startGround = edgeResult.profileGroundHeights.getFirst();
        int endGround = edgeResult.profileGroundHeights.getLast();
        int startGuide = edgeResult.profileGuideLine.getFirst();
        int endGuide = edgeResult.profileGuideLine.getLast();
        ImGui.text(PlotI18n.tr("plugin.road.elevation_hint_start", startGround, startGuide));
        ImGui.text(PlotI18n.tr("plugin.road.elevation_hint_end", endGround, endGuide));
    }

    private void renderSlopeOverrides(RoadEdge edge) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.slope_overrides"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.slope_override_hint"));
        List<RoadEdge.SlopeOverride> overrides = new ArrayList<>(edge.getSlopeOverrides());
        List<RoadEdge.SlopeOverride> originalOverrides = RoadNetworkManager.snapshotSlopeOverrides(overrides);

        for (int i = 0; i < overrides.size(); i++) {
            RoadEdge.SlopeOverride override = overrides.get(i);
            float[] start = {(float) override.startDistance};
            float[] end = {(float) override.endDistance};
            float[] slope = {override.maxSlope};
            ImGui.pushID(i);

            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_start") + "##s", start, 0, (float) edge.getLength(), "%.1fm");
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }
            override.startDistance = start[0];
            if (override.startDistance > override.endDistance) {
                override.endDistance = override.startDistance;
                end[0] = (float) override.endDistance;
            }

            ImGui.sameLine();
            ImGui.sliderFloat(PlotI18n.tr("plugin.road.slope_end") + "##e", end, start[0], (float) edge.getLength(), "%.1fm");
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }
            override.endDistance = end[0];

            ImGui.sameLine();
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.DELETE);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.DELETE_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.DELETE_ACTIVE);
            if (ImGui.button(Icons.PLUGIN_REMOVE + "##rm")) {
                ctx.networkManager().pushHistory();
                overrides.remove(i);
                edge.setSlopeOverrides(overrides);
                ImGui.popStyleColor(3);
                ImGui.popID();
                return;
            }
            ImGui.popStyleColor(3);

            if (com.plot.ui.component.EngineeringSlopeInput.render(
                "slope_override_" + i,
                PlotI18n.tr("plugin.road.slope_value"),
                slope,
                com.plot.ui.component.EngineeringSlopeInput.ValueKind.GRADE
            )) {
                ctx.networkManager().pushHistory();
            }
            override.maxSlope = slope[0];

            if (override.startDistance > override.endDistance) {
                ImGui.textColored(PluginUiColors.INVALID, PlotI18n.tr("plugin.road.slope_range_invalid"));
            } else if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                ImGui.textColored(PluginUiColors.WARNING_OVERLAP, PlotI18n.tr("plugin.road.slope_range_overlap"));
            }

            ImGui.popID();
        }

        if (!RoadNetworkManager.slopeOverridesEqual(overrides, originalOverrides)) {
            edge.setSlopeOverrides(overrides);
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.add_slope_override"))) {
            ctx.networkManager().pushHistory();
            overrides.add(new RoadEdge.SlopeOverride(0, (float) edge.getLength(), config.getMaxSlope()));
            edge.setSlopeOverrides(overrides);
        }
    }

    private void renderBatchEditPanel() {
        if (ctx.networkManager().getSelectedEdgeIds().isEmpty()) {
            return;
        }
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.batch_edit"))) {
            return;
        }

        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", ctx.networkManager().getSelectedEdgeIds().size()));
        RoadBatchCrossSectionEditor.renderDraftFields(ctx, synced);
    }
}
