package com.plot.plugin.road.ui;

import com.plot.core.model.Shape;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.List;

/**
 * 道路认领 Tab：路径选择、主 CTA 与默认参数。
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
        List<Shape> selectedPaths = ctx.toolManager().getSelectedPaths();

        RoadUiSections.step("plugin.road.section.adopt_step1_centerline");
        renderPathToolButtons();
        renderSelectionStatus(selectedPaths);

        ImGui.separator();
        defaultParamsPanel.renderRoadTypeStep();

        ImGui.separator();
        defaultParamsPanel.renderCrossSectionStep();

        ImGui.separator();
        renderAdoptCta(selectedPaths);
    }

    private void renderPathToolButtons() {
        float spacing = ImGui.getStyle().getItemSpacingX();
        float halfWidth = (ImGui.getContentRegionAvailX() - spacing) / 2.0f;
        if (ImGui.button(PlotI18n.tr("plugin.road.draw_path"), halfWidth, 0)) {
            ctx.toolManager().activatePathDrawingTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.draw_path_hint"));
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.pick_path"), halfWidth, 0)) {
            ctx.toolManager().activatePathPickTool();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.pick_path_hint"));
        }
        ImGui.spacing();
    }

    private void renderSelectionStatus(List<Shape> selectedPaths) {
        if (ctx.toolManager().getPathPickSession().isActive()) {
            int pickingCount = ctx.toolManager().getPathPickSession().getAccumulatedCount();
            if (pickingCount > 0) {
                double totalLength = ctx.host().appState().getSelectedShapes().stream()
                    .filter(RoadGeometryUtils::isAdoptablePath)
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(PlotI18n.tr("plugin.road.adopt_selection_summary", pickingCount, totalLength));
                return;
            }
            ImGui.textColored(PluginUiColors.STATUS_INFO, PlotI18n.tr("plugin.road.adopt_picking_active"));
            return;
        }

        if (!selectedPaths.isEmpty()) {
            if (selectedPaths.size() == 1) {
                Shape path = selectedPaths.getFirst();
                ImGui.text(PlotI18n.tr(
                    "plugin.road.adopt_selection_summary",
                    1,
                    RoadToolManager.calculatePathLength(path)));
                ImGui.textColored(
                    PluginUiColors.INFO_BLUE,
                    PlotI18n.tr("plugin.road.path_type", RoadToolManager.getPathTypeName(path)));
            } else {
                double totalLength = selectedPaths.stream()
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(PlotI18n.tr(
                    "plugin.road.adopt_selection_summary",
                    selectedPaths.size(),
                    totalLength));
            }
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.adopt_selection_empty"));
        List<Shape> availablePaths = ctx.toolManager().findAvailablePaths();
        if (!availablePaths.isEmpty()) {
            if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                for (Shape path : availablePaths) {
                    String label = String.format(
                        PlotI18n.tr("plugin.road.path_combo_item"),
                        RoadToolManager.getPathTypeName(path),
                        RoadToolManager.calculatePathLength(path));
                    if (ImGui.selectable(label)) {
                        selectedPaths.clear();
                        selectedPaths.add(path);
                        ctx.host().appState().setSelectedShapes(List.of(path));
                    }
                }
                ImGui.endCombo();
            }
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_path_found"));
            ImGui.textWrapped(PlotI18n.tr("plugin.road.draw_path_hint"));
        }
    }

    private void renderAdoptCta(List<Shape> selectedPaths) {
        boolean canAdopt = !selectedPaths.isEmpty();
        if (!canAdopt) {
            ImGui.beginDisabled();
        }

        ImGui.pushStyleColor(ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, PluginUiColors.INFO_BLUE);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, PluginUiColors.ACCENT_BLUE);
        float ctaHeight = ImGui.getFrameHeight() * 1.35f;
        if (ImGui.button(
            adoptButtonLabel(selectedPaths.size()) + "##adopt_cta",
            ImGui.getContentRegionAvailX(),
            ctaHeight)) {
            ctx.networkManager().adoptSelectedPaths(ctx.toolManager().getSelectedPaths());
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.adopt_as_road"));
        }

        if (!canAdopt) {
            ImGui.endDisabled();
        }
        ImGui.spacing();
        renderAdoptIntersectionRepairPrompt();
    }

    private static String adoptButtonLabel(int pathCount) {
        if (pathCount > 1) {
            return PlotI18n.tr("plugin.road.adopt_cta_batch", pathCount);
        }
        return PlotI18n.tr("plugin.road.adopt_as_road");
    }

    private void renderAdoptIntersectionRepairPrompt() {
        if (!ctx.networkManager().isAdoptIntersectionRepairPending()) {
            return;
        }
        ImGui.textColored(PluginUiColors.WARNING, PlotI18n.tr("plugin.road.adopt_intersection_repair_prompt"));
        if (ImGui.button(PlotI18n.tr("plugin.road.validation.reconcile_intersections") + "##adopt_reconcile")) {
            RoadTopologyWorkflow.reconcileIntersections(ctx, false);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.adopt_intersection_repair_hint"));
        }
        ImGui.spacing();
    }
}
