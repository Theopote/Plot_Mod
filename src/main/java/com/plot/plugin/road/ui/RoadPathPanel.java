package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;
import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;
import com.plot.plugin.ui.PluginSectionCard;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * 路线工作区：道路在哪里、当前选择、路径列表与交叉关系；中心线编辑入口在列表下方。
 * 道路列表与路网预览不套 {@link PluginSectionCard}（避免列表/缩略图嵌套 child）。
 */
public final class RoadPathPanel {
    private static final String PATH_SECTION = "plugin.road.path.section.";

    private final RoadUiContext ctx;
    private final RoadAdoptPanel adoptPanel;
    private final RoadEdgeListPanel edgeListPanel;
    private final RoadPathOverviewPanel overviewPanel;
    private final RoadIntersectionDetailPanel intersectionDetailPanel;
    private final RoadIntersectionListPanel intersectionListPanel;
    private final RoadJunctionPanel junctionPanel;
    private final RoadFloatingEditorHost floatingEditorHost;
    private final RoadPathSlopeBatterControls slopeBatterControls = new RoadPathSlopeBatterControls();

    public RoadPathPanel(
            RoadUiContext ctx,
            RoadAdoptPanel adoptPanel,
            RoadEdgeListPanel edgeListPanel,
            RoadPathOverviewPanel overviewPanel,
            RoadIntersectionDetailPanel intersectionDetailPanel,
            RoadIntersectionListPanel intersectionListPanel,
            RoadJunctionPanel junctionPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.ctx = ctx;
        this.adoptPanel = adoptPanel;
        this.edgeListPanel = edgeListPanel;
        this.overviewPanel = overviewPanel;
        this.intersectionDetailPanel = intersectionDetailPanel;
        this.intersectionListPanel = intersectionListPanel;
        this.junctionPanel = junctionPanel;
        this.floatingEditorHost = floatingEditorHost;
    }

    public void render() {
        PluginSectionCard.render(
            "##path_sec_tools",
            PlotI18n.tr(PATH_SECTION + "tools"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> RoadPathHeader.render(ctx, adoptPanel));

        renderRoadList();

        if (slopeBatterControls.hasSelection(ctx)) {
            PluginSectionCard.render(
                "##path_sec_slope_batter",
                PlotI18n.tr(PATH_SECTION + "slope_batter"),
                null,
                PluginSectionCard.SectionTone.NEUTRAL,
                true,
                () -> slopeBatterControls.render(ctx));
        }

        if (hasCenterlineTarget()) {
            PluginSectionCard.render(
                "##path_sec_centerline",
                PlotI18n.tr(PATH_SECTION + "centerline"),
                null,
                PluginSectionCard.SectionTone.NEUTRAL,
                true,
                this::renderCenterlineEntry);
        }

        RoadNetwork network = ctx.networkManager().getNetwork();
        if (!network.getEdges().isEmpty()) {
            ImGui.spacing();
            ImGui.text(PlotI18n.tr("plugin.road.path.intersections"));
            ImGui.spacing();
            renderSelectedIntersection(network);
            intersectionListPanel.render();
        }
    }

    private boolean hasCenterlineTarget() {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        if (mode == RoadSelectionHeader.Mode.NONE) {
            return false;
        }
        return ctx.networkManager().getPrimarySelectedRoad() != null;
    }

    private void renderCenterlineEntry() {
        RoadSelectionHeader.Mode mode = RoadSelectionHeader.resolveMode(ctx);
        Road road = ctx.networkManager().getPrimarySelectedRoad();
        if (road == null) {
            return;
        }
        int selectedCount = ctx.networkManager().getSelectedRoadIds().size();
        boolean multiSelected = mode == RoadSelectionHeader.Mode.MULTI && selectedCount > 1;
        if (multiSelected) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.edit.tools.batch_centerline.multi_hint", selectedCount));
        } else {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.route.centerline_hint"));
        }
        boolean active = floatingEditorHost != null
            && floatingEditorHost.isOpen(RoadFloatingEditorKind.CENTERLINE);
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        }
        ImGui.beginDisabled(multiSelected);
        if (ImGui.button(PlotI18n.tr("plugin.road.route.edit_centerline") + "##path_edit_centerline")) {
            ctx.requestEditCenterline(road.getId());
        }
        ImGui.endDisabled();
        if (active) {
            ImGui.popStyleColor();
        }
    }

    private void renderSelectedIntersection(RoadNetwork network) {
        if (!RoadIntersectionPropertySupport.hasIntersectionSelection(ctx.networkManager())) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.intersection_select_hint"));
            ImGui.spacing();
            return;
        }
        PluginSectionCard.render(
            "##path_sec_intersection",
            PlotI18n.tr(PATH_SECTION + "intersection"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> intersectionDetailPanel.renderFocusedSelection(
                network, RoadGradeSeparationControls.Layout.BLOCK));
        PluginSectionCard.render(
            "##path_sec_junction_geometry",
            PlotI18n.tr(PATH_SECTION + "junction_geometry"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> RoadIntersectionGeometrySection.render(ctx, network, junctionPanel));
        ImGui.spacing();
    }

    private void renderRoadList() {
        RoadNetwork network = ctx.networkManager().getNetwork();
        if (network.getEdges().isEmpty()) {
            RoadUiSections.section("plugin.road.path.road_list");
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.path.empty_hint"));
            return;
        }

        // 路网缩略图与道路列表保持裸区，不套 Section Card。
        overviewPanel.renderNetworkMap(network);
        ImGui.spacing();

        RoadUiSections.section("plugin.road.path.road_list");
        edgeListPanel.renderPathList("path_edge_list");
    }

}
