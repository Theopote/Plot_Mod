package com.plot.plugin.road.ui;

import com.plot.core.model.Shape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 道路认领 Tab：拾取路径即创建道路；默认参数在拾取前可选。
 */
public final class RoadAdoptPanel {
    private final RoadUiContext ctx;
    private final RoadDefaultParamsPanel defaultParamsPanel;

    public RoadAdoptPanel(RoadUiContext ctx, RoadDefaultParamsPanel defaultParamsPanel) {
        this.ctx = ctx;
        this.defaultParamsPanel = defaultParamsPanel;
    }

    public void render() {
        ctx.toolManager().updateSelectedPaths();

        RoadUiSections.step("plugin.road.section.adopt_step1_centerline");
        renderPickPathButton();
        renderSelectionStatus();

        ImGui.separator();
        defaultParamsPanel.renderRoadTypeStep();

        ImGui.separator();
        defaultParamsPanel.renderCrossSectionStep();

        ImGui.separator();
        renderAdoptIntersectionRepairPrompt();
    }

    private void renderPickPathButton() {
        if (ImGui.button(
            PlotI18n.tr("plugin.road.pick_path"),
            ImGui.getContentRegionAvailX(),
            ImGui.getFrameHeight() * 1.2f)) {
            ctx.toolManager().activatePathPickTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.pick_path_hint"));
        }
        ImGui.spacing();
    }

    private void renderSelectionStatus() {
        if (ctx.toolManager().getPathPickSession().isActive()) {
            List<Shape> overlayPaths = ctx.toolManager().getPickOverlayPaths();
            if (!overlayPaths.isEmpty()) {
                double totalLength = overlayPaths.stream()
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(PlotI18n.tr(
                    "plugin.road.adopt_selection_summary",
                    overlayPaths.size(),
                    totalLength));
            }
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.road.adopt_picking_active"));
            return;
        }

        int roadCount = ctx.networkManager().getNetwork().getRoads().size();
        if (roadCount > 0) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.road.adopt_existing_network", roadCount));
            return;
        }

        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.adopt_selection_empty"));
        List<Shape> availablePaths = ctx.toolManager().findAvailablePaths();
        if (!availablePaths.isEmpty()) {
            if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                for (Shape path : availablePaths) {
                    String label = String.format(
                        PlotI18n.tr("plugin.road.path_combo_item"),
                        RoadToolManager.getPathTypeName(path),
                        RoadToolManager.calculatePathLength(path));
                    if (ImGui.selectable(label)) {
                        ctx.networkManager().adoptSelectedPaths(List.of(path));
                        ctx.notifyPathsAdopted();
                    }
                }
                ImGui.endCombo();
            }
        } else {
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_path_found"));
            RoadUiWidgets.textWrapped(PlotI18n.tr("plugin.road.adopt_use_plot_tools_hint"));
        }
    }

    private void renderAdoptIntersectionRepairPrompt() {
        if (!ctx.networkManager().isAdoptIntersectionRepairPending()) {
            return;
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.adopt_intersection_repair_prompt"));
        if (ImGui.button(PlotI18n.tr("plugin.road.validation.reconcile_intersections") + "##adopt_reconcile")) {
            RoadTopologyWorkflow.reconcileIntersections(ctx, false);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.adopt_intersection_repair_hint"));
        }
        ImGui.spacing();
    }
}
