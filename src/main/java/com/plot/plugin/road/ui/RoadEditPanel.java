package com.plot.plugin.road.ui;

import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.RoadNetworkGenerator;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.terrain.MinecraftTerrainSampler;
import com.plot.plugin.road.terrain.TerrainSampler;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路编辑 Tab：网络级批量操作、边列表、基于选中态的节点/边属性编辑。
 */
public final class RoadEditPanel {
    private static final int ELEVATION_MIN = -64;
    private static final int ELEVATION_MAX = 320;

    private final RoadUiContext ctx;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadNodePropertyPanel nodePropertyPanel;

    /** 全网统一标高草稿（自定义 Y） */
    private final int[] uniformElevationDraft = {64};
    private String lastRecommendationSummary = "";

    public RoadEditPanel(
            RoadUiContext ctx,
            RoadEdgeListPanel edgeListPanel,
            RoadJunctionPanel junctionPanel,
            RoadNodePropertyPanel nodePropertyPanel) {
        this.ctx = ctx;
        this.edgeListPanel = edgeListPanel;
        this.junctionPanel = junctionPanel;
        this.nodePropertyPanel = nodePropertyPanel;
    }

    public void render() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        ctx.networkManager().ensureSelectionValid();

        renderUniformFlatElevationControls(network);
        ImGui.separator();

        List<RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_edges"));
            renderSelectionDispatch(network, true);
            return;
        }

        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edge_list_hint"));
        edgeListPanel.renderToolbar("##edit");
        edgeListPanel.renderList(true, "edit_edge_list");

        renderSelectionDispatch(network, false);
    }

    private void renderSelectionDispatch(RoadNetwork network, boolean edgesEmpty) {
        String selectedNodeId = ctx.networkManager().getSelectedNodeId();
        int selectedEdgeCount = ctx.networkManager().getSelectedEdgeIds().size();

        if (selectedNodeId != null && !selectedNodeId.isBlank()) {
            nodePropertyPanel.renderForSelectedNode(junctionPanel);
            nodePropertyPanel.renderAllNodesCollapsibleList();
            return;
        }

        if (edgesEmpty) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edit_select_hint"));
            return;
        }

        if (selectedEdgeCount > 1) {
            renderBatchEditPanel();
            ImGui.separator();
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.single_edge_disabled_multi", selectedEdgeCount));
            return;
        }

        if (selectedEdgeCount == 1) {
            renderBatchEditPanel();
            renderSingleEdgeDetail(network);
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.edit_select_hint"));
    }

    private void renderSingleEdgeDetail(RoadNetwork network) {
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

    private void renderUniformFlatElevationControls(RoadNetwork network) {
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.uniform_flat_elevation"))) {
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.uniform_flat_elevation_hint"));

        boolean disabled = network.getEdges().isEmpty();
        if (disabled) {
            ImGui.beginDisabled();
        }

        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;

        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_auto_apply"), half, 0)) {
            applyUniformFlatElevationAuto();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_auto_apply_hint"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_sample"), half, 0)) {
            sampleUniformElevationSuggestion();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_sample_hint"));
        }

        if (!lastRecommendationSummary.isBlank()) {
            ImGui.textColored(PluginUiColors.STATUS_INFO, lastRecommendationSummary);
        }

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
        ImGui.sliderInt(
            PlotI18n.tr("plugin.road.uniform_elevation_custom_y") + "##uniform_y",
            uniformElevationDraft,
            ELEVATION_MIN,
            ELEVATION_MAX,
            "Y=%d"
        );
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.uniform_elevation_custom_apply"), 0, 0)) {
            ctx.networkManager().applyCustomUniformFlatElevation(uniformElevationDraft[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.uniform_elevation_custom_apply_hint"));
        }

        if (disabled) {
            ImGui.endDisabled();
        }
        ImGui.spacing();
    }

    private TerrainSampler requireTerrainOrNull() {
        World world = RoadNetworkGenerator.getClientWorld();
        if (world == null) {
            ctx.status().set(PlotI18n.tr("plugin.road.generate_world_unavailable"));
            return null;
        }
        return MinecraftTerrainSampler.of(world, CoordinateTransformer.getInstance());
    }

    private void applyUniformFlatElevationAuto() {
        TerrainSampler terrain = requireTerrainOrNull();
        if (terrain == null) {
            return;
        }
        var recommendation = ctx.networkManager().applyUniformFlatElevation(terrain);
        if (recommendation != null) {
            uniformElevationDraft[0] = recommendation.elevation();
            lastRecommendationSummary = formatRecommendation(recommendation);
        }
    }

    private void sampleUniformElevationSuggestion() {
        TerrainSampler terrain = requireTerrainOrNull();
        if (terrain == null) {
            return;
        }
        var recommendation = ctx.networkManager().previewUniformElevation(terrain);
        if (recommendation != null) {
            uniformElevationDraft[0] = recommendation.elevation();
            lastRecommendationSummary = formatRecommendation(recommendation);
        }
    }

    private static String formatRecommendation(
            com.plot.plugin.road.RoadUniformElevationUtils.ElevationRecommendation recommendation) {
        String strategy = recommendation.usedMode()
            ? PlotI18n.tr("plugin.road.uniform_elevation_strategy_mode")
            : PlotI18n.tr("plugin.road.uniform_elevation_strategy_average");
        return PlotI18n.tr(
            "plugin.road.uniform_elevation_preview",
            recommendation.elevation(),
            strategy,
            recommendation.sampleCount(),
            String.format("%.1f", recommendation.average()));
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
            if (ImGui.smallButton(PlotI18n.tr("plugin.road.delete") + "##rm")) {
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
        int selectedCount = ctx.networkManager().getSelectedEdgeIds().size();
        if (selectedCount == 0) {
            return;
        }
        int headerFlags = selectedCount > 1 ? ImGuiTreeNodeFlags.DefaultOpen : 0;
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.batch_edit"), headerFlags)) {
            return;
        }

        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().loadBatchEditDefaults();
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.batch_edit_hint", selectedCount));
        RoadBatchCrossSectionEditor.renderDraftFields(ctx, synced);
    }
}
