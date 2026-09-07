package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingListHelper;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;

/** 建筑总览 Tab 与删除确认弹窗。 */
public final class BuildingOverviewPanel {
    private final BuildingUiContext ctx;

    public BuildingOverviewPanel(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
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

        renderDistrictMassingHome();

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

        ImGui.beginChild("building_overview_list", 0, 220, true);
        for (BuildingFootprint building : BuildingListHelper.sorted(ctx.project(), ctx.buildingSortMode())) {
            ImGui.pushID(building.getId());
            boolean selected = ctx.selection().contains(building.getId());
            if (ImGui.selectable(building.getName() + "##row", selected)) {
                ctx.selection().select(building.getId(), ImGui.getIO().getKeyCtrl());
            }

            ImGui.sameLine();
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
            ImGui.popID();
        }
        ImGui.endChild();
    }

    /** Massing-first 片区入口：预览 / 生成整片（细节参数见 Edit Tab）。 */
    private void renderDistrictMassingHome() {
        int count = ctx.project().getBuildingCount();
        if (count == 0) {
            return;
        }

        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.building.district_massing_home"));
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.district_massing_home_hint"));

        var readiness = ctx.host().projection().checkWorldModificationReadiness();
        boolean generateDisabled = !readiness.ready() || ctx.host().placement().isBusy();

        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        if (ImGui.button(PlotI18n.tr("plugin.building.preview_all", count), half, 0)) {
            ctx.selection().selectAll(ctx.project().getBuildings().keySet());
            ctx.actions().calculateDistrictPreview(
                new ArrayList<>(ctx.project().getBuildings().values()),
                true);
        }
        ImGui.sameLine();
        if (generateDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.generate_all", count), half, 0)) {
            ctx.selection().selectAll(ctx.project().getBuildings().keySet());
            if (ctx.actions().calculateDistrictPreview(
                    new ArrayList<>(ctx.project().getBuildings().values()),
                    true)) {
                ctx.setBuildConfirmPending(true);
            }
        }
        if (generateDisabled) {
            ImGui.endDisabled();
        }
        if (!readiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
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
