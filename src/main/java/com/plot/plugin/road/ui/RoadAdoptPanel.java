package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.manager.RoadToolManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

import java.util.List;

/**
 * 道路认领 Tab：路径选择、默认参数与认领操作。
 */
public final class RoadAdoptPanel {
    private final RoadUiContext ctx;
    private final RoadDefaultParamsPanel defaultParamsPanel;

    public RoadAdoptPanel(RoadUiContext ctx, RoadDefaultParamsPanel defaultParamsPanel) {
        this.ctx = ctx;
        this.defaultParamsPanel = defaultParamsPanel;
    }

    public void render() {
        List<Shape> selectedPaths = ctx.toolManager().getSelectedPaths();

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.adopt_hint"));
        ImGui.spacing();

        if (ctx.toolManager().getPathPickSession().isActive()) {
            int pickingCount = ctx.toolManager().getPathPickSession().getAccumulatedCount();
            if (pickingCount > 0) {
                double totalLength = AppState.getInstance().getSelectedShapes().stream()
                    .filter(RoadGeometryUtils::isAdoptablePath)
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.road.paths_selected"),
                    pickingCount,
                    totalLength));
            }
        } else {
            ctx.toolManager().updateSelectedPaths();
        }

        if (!selectedPaths.isEmpty()) {
            if (selectedPaths.size() == 1) {
                Shape path = selectedPaths.getFirst();
                ImGui.text(String.format(PlotI18n.tr("plugin.road.path_selected"),
                    RoadToolManager.calculatePathLength(path)));
                ImGui.textColored(PluginUiColors.INFO_BLUE,
                    PlotI18n.tr("plugin.road.path_type", RoadToolManager.getPathTypeName(path)));
            } else {
                double totalLength = selectedPaths.stream()
                    .mapToDouble(RoadToolManager::calculatePathLength)
                    .sum();
                ImGui.text(String.format(
                    PlotI18n.tr("plugin.road.paths_selected"),
                    selectedPaths.size(),
                    totalLength));
            }
        } else {
            List<Shape> availablePaths = ctx.toolManager().findAvailablePaths();
            if (!availablePaths.isEmpty()) {
                if (ImGui.beginCombo("##select_path", PlotI18n.tr("plugin.road.select_path_combo"))) {
                    for (Shape path : availablePaths) {
                        String label = String.format(PlotI18n.tr("plugin.road.path_combo_item"),
                            RoadToolManager.getPathTypeName(path), RoadToolManager.calculatePathLength(path));
                        boolean selected = selectedPaths.size() == 1 && path == selectedPaths.getFirst();
                        if (ImGui.selectable(label, selected)) {
                            selectedPaths.clear();
                            selectedPaths.add(path);
                            AppState.getInstance().setSelectedShapes(List.of(path));
                        }
                    }
                    ImGui.endCombo();
                }
            } else {
                ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.no_path_found"));
                ImGui.text(PlotI18n.tr("plugin.road.draw_path_hint"));
            }
        }

        ImGui.separator();
        defaultParamsPanel.render();

        float itemSpacing = ImGui.getStyle().getItemSpacingX();
        float third = (ImGui.getContentRegionAvailX() - itemSpacing * 2.0f) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.road.draw_path"), third, 0)) {
            ctx.toolManager().activatePathDrawingTool();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.road.pick_path"), third, 0)) {
            ctx.toolManager().activatePathPickTool();
        }
        ImGui.sameLine();
        boolean canAdopt = !selectedPaths.isEmpty();
        if (!canAdopt) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.road.adopt_as_road"), third, 0)) {
            ctx.networkManager().adoptSelectedPaths(ctx.toolManager().getSelectedPaths());
        }
        if (!canAdopt) {
            ImGui.endDisabled();
        }
    }
}
