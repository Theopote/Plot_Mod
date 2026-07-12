package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadEdgeListHelper;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.ui.component.Icons;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;

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
        RoadSystemConfig config = ctx.networkManager().getConfig();
        List<RoadEdge> allEdges = new ArrayList<>(network.getEdges().values());
        if (allEdges.isEmpty()) {
            ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.no_edges"));
            return;
        }

        ctx.networkManager().ensureSelectionValid();
        ImGui.text(PlotI18n.tr("plugin.road.edge_list"));
        edgeListPanel.renderToolbar("##edit");
        edgeListPanel.renderList(120, true, "edit_edge_list");

        renderBatchEditPanel();
        junctionPanel.renderEditor();

        ImGui.separator();
        String primaryId = ctx.networkManager().getPrimarySelectedEdgeId();
        RoadEdge current = network.getEdge(primaryId);
        if (current == null) {
            return;
        }

        ImGui.text(PlotI18n.tr("plugin.road.single_edge_edit",
            RoadEdgeListHelper.formatEdgeLabel(network, current)));

        int[] width = {current.getWidth() != null ? current.getWidth() : config.getRoadWidth()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", width[0]) + "##edge_width", width, 3, 20, "%d")) {
            current.setWidth(width[0]);
        }
        if (ImGui.isItemActivated()) {
            ctx.networkManager().pushHistory();
        }

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##edge_road_material",
            PlotI18n.tr("plugin.road.material"),
            current.getMaterial() != null ? current.getMaterial() : config.getSelectedMaterial(),
            current::setMaterial,
            true
        );

        ImBoolean edgeSidewalkRef = new ImBoolean(current.getEffectiveIncludeSidewalk(config));
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), edgeSidewalkRef)) {
            ctx.networkManager().pushHistory();
            current.setIncludeSidewalk(edgeSidewalkRef.get());
        }

        if (current.getEffectiveIncludeSidewalk(config)) {
            int[] sidewalkWidth = {current.getSidewalkWidth() != null ? current.getSidewalkWidth() : config.getSidewalkWidth()};
            if (ImGui.sliderInt(PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##sw", sidewalkWidth, 1, 3, "%d")) {
                current.setSidewalkWidth(sidewalkWidth[0]);
            }
            if (ImGui.isItemActivated()) {
                ctx.networkManager().pushHistory();
            }

            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##edge_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                current.getSidewalkMaterial() != null
                    ? current.getSidewalkMaterial()
                    : config.getSelectedSidewalkMaterial(),
                current::setSidewalkMaterial,
                true
            );
        }

        float[] maxSlope = {current.getMaxSlope() != null ? current.getMaxSlope() : config.getMaxSlope()};
        if (EngineeringSlopeInput.render(
            "edge_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            current.setMaxSlope(maxSlope[0]);
            ctx.networkManager().pushHistory();
        }

        int[] lightSpacing = {current.getStreetlightSpacing() != null ? current.getStreetlightSpacing() : 0};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.streetlight_spacing") + "##lights", lightSpacing, 0, 50, "%dm")) {
            current.setStreetlightSpacing(lightSpacing[0] > 0 ? lightSpacing[0] : null);
        }
        if (ImGui.isItemActivated()) {
            ctx.networkManager().pushHistory();
        }

        renderSlopeOverrides(current);
    }

    private void renderSlopeOverrides(RoadEdge edge) {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.slope_overrides"));
        ImGui.textColored((int) 0xFF808080FFL, PlotI18n.tr("plugin.road.slope_override_hint"));
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
            ImGui.pushStyleColor(ImGuiCol.Button, (int) 0xFF0000FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, (int) 0xFF2020FFL);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, (int) 0xFF0000CCL);
            if (ImGui.button(Icons.PLUGIN_REMOVE + "##rm")) {
                ctx.networkManager().pushHistory();
                overrides.remove(i);
                edge.setSlopeOverrides(overrides);
                ImGui.popStyleColor(3);
                ImGui.popID();
                return;
            }
            ImGui.popStyleColor(3);

            if (EngineeringSlopeInput.render(
                "slope_override_" + i,
                PlotI18n.tr("plugin.road.slope_value"),
                slope,
                EngineeringSlopeInput.ValueKind.GRADE
            )) {
                ctx.networkManager().pushHistory();
            }
            override.maxSlope = slope[0];

            if (override.startDistance > override.endDistance) {
                ImGui.textColored((int) 0xFF4040FFFFL, PlotI18n.tr("plugin.road.slope_range_invalid"));
            } else if (RoadNetworkManager.hasOverlappingOverride(overrides, i)) {
                ImGui.textColored((int) 0xFFFF8040FFL, PlotI18n.tr("plugin.road.slope_range_overlap"));
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

        RoadNetworkManager.BatchEditDefaults synced = ctx.networkManager().syncBatchEditDefaults();
        int width = synced.width();
        final String[] material = {synced.material()};
        boolean includeSidewalk = synced.includeSidewalk();
        int sidewalkWidth = synced.sidewalkWidth();
        final String[] sidewalkMaterial = {synced.sidewalkMaterial()};
        float maxSlope = synced.maxSlope();

        ImGui.textColored((int) 0xFF808080FFL,
            PlotI18n.tr("plugin.road.batch_edit_hint", ctx.networkManager().getSelectedEdgeIds().size()));

        int[] widthArr = {width};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", widthArr[0]) + "##batch_width", widthArr, 3, 20, "%d")) {
            width = widthArr[0];
        }

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##batch_road_material",
            PlotI18n.tr("plugin.road.material"),
            material[0],
            value -> material[0] = value,
            false
        );

        ctx.batchIncludeSidewalkRef().set(includeSidewalk);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##batch_sw", ctx.batchIncludeSidewalkRef())) {
            includeSidewalk = ctx.batchIncludeSidewalkRef().get();
        }

        if (includeSidewalk) {
            int[] sidewalkWidthArr = {sidewalkWidth};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidthArr[0]) + "##batch_sw_w",
                sidewalkWidthArr, 1, 3, "%d")) {
                sidewalkWidth = sidewalkWidthArr[0];
            }

            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##batch_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                sidewalkMaterial[0],
                value -> sidewalkMaterial[0] = value,
                false
            );
        }

        float[] maxSlopeArr = {maxSlope};
        if (EngineeringSlopeInput.render(
            "batch_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlopeArr,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            maxSlope = maxSlopeArr[0];
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.networkManager().applyBatchEdit(new RoadNetworkManager.BatchEditDefaults(
                width, material[0], includeSidewalk, sidewalkWidth, sidewalkMaterial[0], maxSlope));
        }
    }
}
