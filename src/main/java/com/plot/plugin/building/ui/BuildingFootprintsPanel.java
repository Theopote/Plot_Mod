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
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/** 建筑轮廓 Tab：仅回答「哪些轮廓属于建筑」。 */
public final class BuildingFootprintsPanel {
    private final BuildingUiContext ctx;
    private String renameBuildingId = "";

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
        renderRenamePopup();
    }

    private void renderCanvasSection() {
        ImGui.text(PlotI18n.tr("plugin.building.footprints.canvas_section"));
        if (ctx.pickSession().isActive()) {
            renderPickSessionState();
            renderCanvasPreview(ctx.canvasSelectionAnalysis());
            return;
        }

        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.footprints.canvas_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.building.pick_footprint"), 0, 0)) {
            ctx.startPickSession();
        }
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
    }

    private void renderCanvasPreview(BuildingFootprintSelectionAnalysis canvas) {
        if (!canvas.hasCanvasSelection()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.footprints.preview_waiting"));
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

        List<Shape> previewShapes = BuildingFootprintPreviewItems.previewShapes(canvas);
        if (previewShapes.isEmpty()) {
            return;
        }

        ImGui.text(PlotI18n.tr(
            "plugin.building.footprints.canvas_preview_title",
            previewShapes.size(),
            String.format("%.1f", BuildingFootprintPreviewItems.totalArea(previewShapes))));

        List<BuildingOverviewRenderer.FootprintMapItem> mapItems =
            BuildingFootprintPreviewItems.fromCanvasAnalysis(canvas);
        BuildingOverviewRenderer.renderFootprintPreviewMap(
            "building_canvas_pick_map",
            mapItems,
            PlotI18n.tr("plugin.building.footprints.preview_waiting"),
            hitIndex -> focusCanvasShape(BuildingFootprintPreviewItems.shapeAt(previewShapes, hitIndex)));

        ImGui.beginChild("building_canvas_pick_thumbs", 0, 132, true);
        for (int i = 0; i < previewShapes.size(); i++) {
            renderCanvasCandidateThumbnail(previewShapes.get(i), i, canvas);
        }
        ImGui.endChild();
    }

    private void focusCanvasShape(Shape shape) {
        if (shape == null) {
            return;
        }
        List<Shape> selected = new ArrayList<>(ctx.host().appState().getSelectedShapes());
        if (!selected.contains(shape)) {
            selected.add(shape);
            ctx.host().appState().setSelectedShapes(selected);
        }
    }

    private void renderCanvasCandidateThumbnail(Shape shape, int index, BuildingFootprintSelectionAnalysis canvas) {
        ImGui.pushID(shape.getId());
        List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
        boolean invalid = canvas.invalid().stream().anyMatch(s -> s.getId().equals(shape.getId()));
        boolean adopted = canvas.alreadyAdopted().stream().anyMatch(s -> s.getId().equals(shape.getId()));
        BuildingOverviewRenderer.renderFootprintThumbnail(points, !invalid, index);
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

    private void renderAdoptedSection() {
        ImGui.text(PlotI18n.tr("plugin.building.footprints.project_section"));
        ImGui.text(PlotI18n.tr("plugin.building.footprints.project_stats",
            ctx.project().getBuildingCount(),
            String.format("%.1f", ctx.project().getTotalArea())));

        if (ctx.project().getBuildingCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.no_buildings"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());

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

        BuildingOverviewRenderer.renderProjectMap(
            ctx.project(),
            ctx.selection().ids(),
            id -> ctx.selection().select(id, ImGui.getIO().getKeyCtrl()));

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.building.footprints.list_section"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.building.footprints.list_hint"));

        ImGui.beginChild("building_footprints_list", 0, 0, true);
        for (BuildingFootprint building : BuildingListHelper.sorted(ctx.project(), ctx.buildingSortMode())) {
            renderAdoptedBuildingRow(building);
        }
        ImGui.endChild();
    }

    private void renderAdoptedBuildingRow(BuildingFootprint building) {
        ImGui.pushID(building.getId());
        boolean selected = ctx.selection().contains(building.getId());
        String label = PlotI18n.tr(
            "plugin.building.footprints.list_item",
            building.getName(),
            building.getFloors(),
            String.format("%.1f", building.computeArea()));
        if (ImGui.selectable(label + "##row", selected, ImGuiSelectableFlags.SpanAllColumns)) {
            ctx.selection().select(building.getId(), ImGui.getIO().getKeyCtrl());
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            ctx.locateBuilding(building);
        }
        if (ImGui.beginPopupContextItem("building_row_ctx", ImGuiPopupFlags.MouseButtonRight)) {
            if (ImGui.menuItem(PlotI18n.tr("plugin.building.locate"))) {
                ctx.locateBuilding(building);
            }
            if (ImGui.menuItem(PlotI18n.tr("plugin.building.rename"))) {
                renameBuildingId = building.getId();
                ctx.buildingNameBuffer().set(building.getName());
                ImGui.openPopup("##building_rename_popup");
            }
            if (ImGui.menuItem(PlotI18n.tr("plugin.building.delete"))) {
                ctx.pendingDeleteBuildingIds().clear();
                ctx.pendingDeleteBuildingIds().add(building.getId());
                ctx.setDeleteConfirmPending(true);
            }
            ImGui.endPopup();
        }
        ImGui.popID();
    }

    private void renderRenamePopup() {
        if (ImGui.beginPopupModal("##building_rename_popup", ImGuiWindowFlags.AlwaysAutoResize)) {
            BuildingFootprint building = ctx.project().getBuilding(renameBuildingId);
            if (building == null) {
                renameBuildingId = "";
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }
            ImGui.text(PlotI18n.tr("plugin.building.rename_prompt", building.getName()));
            if (ImGui.inputText("##rename", ctx.buildingNameBuffer())) {
                building.setName(ctx.buildingNameBuffer().get());
            }
            if (ImGui.isItemActivated()) {
                ctx.projectHistory().push(ctx.project());
            }
            ImGui.separator();
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 100, 0)) {
                renameBuildingId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 100, 0)) {
                renameBuildingId = "";
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
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
