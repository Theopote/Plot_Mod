package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.plugin.building.BuildingFootprintSelectionAnalysis;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/** 建筑轮廓 Tab：画布拾取、认领与已认领建筑总览（合并原认领 + 概览）。 */
public final class BuildingFootprintsPanel {
    private final BuildingUiContext ctx;

    public BuildingFootprintsPanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickPickSession() {
        ctx.handlePickSessionTick();
    }

    public void render() {
        renderCanvasSection();
        ImGui.separator();
        renderAdoptedSection();
    }

    private void renderCanvasSection() {
        if (ctx.pickSession().isActive()) {
            renderPickSessionState();
            renderCanvasPreview(ctx.canvasSelectionAnalysis());
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.footprints.canvas_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.building.pick_footprint"), 0, 0)) {
            ctx.startPickSession();
        }
        ImGui.spacing();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.draw_footprint_hint"));
    }

    private void renderPickSessionState() {
        int count = ctx.pickSession().getAccumulatedCount();
        if (count > 0) {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.building.footprints.picking_count", count));
        } else {
            ImGui.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.building.footprints.picking_active"));
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.footprints.right_click_finish"));
        ImGui.spacing();
    }

    private void renderCanvasPreview(BuildingFootprintSelectionAnalysis canvas) {
        if (!canvas.hasCanvasSelection()) {
            return;
        }

        if (!canvas.invalid().isEmpty()) {
            ImGui.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.building.footprints.invalid_selection", canvas.invalid().size()));
        }
        if (!canvas.alreadyAdopted().isEmpty()) {
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.footprints.already_adopted", canvas.alreadyAdopted().size()));
        }

        List<Shape> previewShapes = previewShapes(canvas);
        if (previewShapes.isEmpty()) {
            return;
        }

        ImGui.text(PlotI18n.tr(
            "plugin.building.footprints.canvas_preview_title",
            previewShapes.size(),
            String.format("%.1f", computePreviewArea(previewShapes))));

        ImGui.beginChild("building_canvas_preview_list", 0, 120, true);
        int index = 0;
        for (Shape shape : previewShapes) {
            renderCanvasCandidateRow(shape, index++, canvas);
        }
        ImGui.endChild();
    }

    private static List<Shape> previewShapes(BuildingFootprintSelectionAnalysis canvas) {
        List<Shape> shapes = new ArrayList<>(canvas.adoptable());
        shapes.addAll(canvas.alreadyAdopted());
        shapes.addAll(canvas.invalid());
        return shapes;
    }

    private void renderCanvasCandidateRow(Shape shape, int index, BuildingFootprintSelectionAnalysis canvas) {
        ImGui.pushID(shape.getId());
        List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
        boolean invalid = containsShape(canvas.invalid(), shape);
        boolean adopted = containsShape(canvas.alreadyAdopted(), shape);
        boolean selected = containsShape(ctx.selectedFootprints(), shape);

        BuildingOverviewRenderer.renderFootprintThumbnail(points, selected || adopted, index);
        ImGui.sameLine();
        double area = points.isEmpty() ? 0.0 : Math.abs(BuildingFootprint.signedArea(points));
        String statusKey = invalid
            ? "plugin.building.footprints.canvas_item_invalid"
            : adopted
                ? "plugin.building.footprints.canvas_item_adopted"
                : "plugin.building.footprints.canvas_item_ready";
        ImGui.textColored(
            invalid ? PluginUiColors.WARNING : PluginUiColors.HINT_GRAY,
            PlotI18n.tr(statusKey, index + 1, String.format("%.1f", area)));
        ImGui.popID();
    }

    private static boolean containsShape(List<Shape> shapes, Shape shape) {
        if (shape == null || shapes == null) {
            return false;
        }
        for (Shape candidate : shapes) {
            if (candidate != null && candidate.getId().equals(shape.getId())) {
                return true;
            }
        }
        return false;
    }

    private static double computePreviewArea(List<Shape> shapes) {
        double area = 0.0;
        for (Shape shape : shapes) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            area += Math.abs(BuildingFootprint.signedArea(points));
        }
        return area;
    }

    private void renderAdoptedSection() {
        ImGui.text(PlotI18n.tr("plugin.building.project_stats",
            ctx.project().getBuildingCount(),
            String.format("%.1f", ctx.project().getTotalArea())));

        if (ctx.project().getBuildingCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.no_buildings"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());
        BuildingUiWidgets.renderSelectionSummary(ctx);

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.building.select_all"), buttonWidth, 0)) {
            ctx.selection().selectAll(ctx.project().getBuildings().keySet());
        }
        ImGui.sameLine();
        boolean clearDisabled = ctx.selection().isEmpty();
        if (clearDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.clear_selection"), buttonWidth, 0)) {
            ctx.selection().clear();
        }
        if (clearDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        boolean deleteDisabled = ctx.selection().isEmpty();
        if (deleteDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.delete_selected"), buttonWidth, 0)) {
            ctx.pendingDeleteBuildingIds().clear();
            ctx.pendingDeleteBuildingIds().addAll(ctx.selection().ids());
            ctx.setDeleteConfirmPending(true);
        }
        if (deleteDisabled) {
            ImGui.endDisabled();
        }

        BuildingDistrictMassingWidgets.renderOverviewHome(ctx);

        BuildingOverviewRenderer.renderProjectMap(
            ctx.project(),
            ctx.selection().ids(),
            id -> ctx.selection().select(id, ImGui.getIO().getKeyCtrl()));

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.multi_select_hint"));

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##building_sort", ctx.buildingSortMode().label())) {
            for (BuildingListHelper.SortMode mode : BuildingListHelper.SortMode.values()) {
                boolean selected = mode == ctx.buildingSortMode();
                if (ImGui.selectable(mode.label(), selected)) {
                    ctx.setBuildingSortMode(mode);
                }
            }
            ImGui.endCombo();
        }

        ImGui.beginChild("building_footprints_list", 0, 220, true);
        int index = 0;
        for (BuildingFootprint building : BuildingListHelper.sorted(ctx.project(), ctx.buildingSortMode())) {
            renderAdoptedBuildingRow(building, index++);
        }
        ImGui.endChild();
    }

    private void renderAdoptedBuildingRow(BuildingFootprint building, int index) {
        ImGui.pushID(building.getId());
        boolean selected = ctx.selection().contains(building.getId());
        if (BuildingOverviewRenderer.renderFootprintThumbnail(building.getOuterPoints(), selected, index)) {
            ctx.selection().select(building.getId(), ImGui.getIO().getKeyCtrl());
        }
        ImGui.sameLine();
        ImGui.beginGroup();
        if (ImGui.selectable(building.getName() + "##row", selected)) {
            ctx.selection().select(building.getId(), ImGui.getIO().getKeyCtrl());
        }
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.building.overview_item",
            String.format("%.1f", building.computeArea()),
            building.getFloors(),
            building.isSlopedRoofEligible()
                ? PlotI18n.tr("plugin.building.shape_rect")
                : PlotI18n.tr("plugin.building.shape_polygon")));
        if (ImGui.button(PlotI18n.tr("plugin.building.locate"), 60, 0)) {
            ctx.locateBuilding(building);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.building.delete"), 60, 0)) {
            ctx.pendingDeleteBuildingIds().clear();
            ctx.pendingDeleteBuildingIds().add(building.getId());
            ctx.setDeleteConfirmPending(true);
        }
        ImGui.endGroup();
        ImGui.popID();
    }

    public void renderDeleteConfirmPopup() {
        if (ctx.deleteConfirmPending()) {
            ImGui.openPopup("##building_delete_confirm");
            ctx.setDeleteConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##building_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int count = ctx.pendingDeleteBuildingIds().size();
            if (count > 1) {
                ImGui.text(PlotI18n.tr("plugin.building.delete_confirm_batch", count));
            } else {
                ImGui.text(PlotI18n.tr("plugin.building.delete_confirm"));
            }
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("plugin.building.delete"), 100, 0)) {
                if (!ctx.pendingDeleteBuildingIds().isEmpty()) {
                    ctx.projectHistory().push(ctx.project());
                    for (String id : ctx.pendingDeleteBuildingIds()) {
                        ctx.project().removeBuilding(id);
                        ctx.selection().remove(id);
                    }
                    ctx.selection().retainExisting(ctx.project());
                    ctx.clearPreview();
                }
                ctx.pendingDeleteBuildingIds().clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                ctx.pendingDeleteBuildingIds().clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
