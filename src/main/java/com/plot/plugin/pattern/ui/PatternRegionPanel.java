package com.plot.plugin.pattern.ui;

import com.plot.core.model.Shape;
import com.plot.plugin.pattern.PatternGeometryUtils;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/** 区域管理 Tab（概览 + 认领）。 */
public final class PatternRegionPanel {
    private final PatternUiContext ctx;

    public PatternRegionPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickPickSession() {
        ctx.handlePickSessionTick();
    }

    public void render() {
        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        if (ImGui.button(PlotI18n.tr("plugin.pattern.add_region_from_canvas"), half, 0)) {
            ctx.state().setShowAddRegionPanel(!ctx.state().isShowAddRegionPanel());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.pattern.select_all_closed"), half, 0)) {
            ctx.selectAllClosedShapesOnCanvas();
            ctx.state().setShowAddRegionPanel(true);
        }

        if (ctx.state().isShowAddRegionPanel()) {
            renderAddRegionSection();
            ImGui.spacing();
        }

        ImGui.text(PlotI18n.tr(
            "plugin.pattern.region_list_header",
            ctx.project().getFootprintCount()));
        if (ctx.project().getFootprintCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.no_footprints"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());
        PatternUiWidgets.renderSelectionSummary(ctx);

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.pattern.select_all"), buttonWidth, 0)) {
            ctx.selection().selectAll(ctx.project().getFootprints().keySet());
        }
        ImGui.sameLine();
        boolean clearDisabled = ctx.selection().isEmpty();
        if (clearDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.clear_selection"), buttonWidth, 0)) {
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
        if (ImGui.button(PlotI18n.tr("plugin.pattern.delete_selected"), buttonWidth, 0)) {
            ctx.pendingDeleteFootprintIds().clear();
            ctx.pendingDeleteFootprintIds().addAll(ctx.selection().ids());
            ctx.setDeleteConfirmPending(true);
        }
        if (deleteDisabled) {
            ImGui.endDisabled();
        }

        ImGui.beginChild("pattern_region_list", 0, 220, true);
        for (PatternFootprint footprint : ctx.project().getFootprints().values()) {
            ImGui.pushID(footprint.getId());
            boolean selected = ctx.selection().contains(footprint.getId());
            if (ImGui.selectable(footprint.getName() + "##row", selected)) {
                ctx.selection().select(footprint.getId(), ImGui.getIO().getKeyCtrl());
            }
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.pattern.overview_item",
                String.format("%.1f", footprint.computeArea()),
                PatternUiWidgets.sourceLabel(footprint)));

            if (ImGui.button(PlotI18n.tr("plugin.pattern.locate"), 60, 0)) {
                ctx.locateFootprint(footprint);
            }
            ImGui.popID();
        }
        ImGui.endChild();

        PatternFootprint primary = ctx.selection().primary(ctx.project());
        if (primary != null) {
            ImGui.spacing();
            PatternUiWidgets.renderFootprintGeometrySection(ctx, primary, () -> ctx.actions().invalidatePreview());
        }
    }

    private void renderAddRegionSection() {
        ImGui.separator();
        ImGui.text(PlotI18n.tr("plugin.pattern.add_region_title"));

        if (ctx.pickSession().isActive()) {
            int count = ctx.pickSession().getAccumulatedCount();
            if (count > 0) {
                ImGui.text(String.format(PlotI18n.tr("plugin.pattern.regions_selected"), count));
            }
        } else {
            ctx.updateSelectedRegions();
        }

        List<Shape> selected = ctx.selectedRegions();
        if (!selected.isEmpty()) {
            PatternGeometryUtils.AdoptSelectionSummary summary =
                PatternGeometryUtils.summarizeAdoptSelection(selected);
            ImGui.text(PlotI18n.tr(
                "plugin.pattern.adopt_selection_summary",
                summary.selectedShapeCount(),
                summary.outerCount(),
                summary.holeCount()));
            ImGui.text(PlotI18n.tr(
                "plugin.pattern.regions_selected_detail",
                selected.size(),
                String.format("%.1f", ctx.computeSelectedRegionArea())));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.draw_region_hint"));
        }

        if (ImGui.button(PlotI18n.tr("plugin.pattern.pick_region"), 0, 0)) {
            ctx.startPickSession();
        }
        ImGui.sameLine();
        boolean adoptDisabled = selected.isEmpty();
        if (adoptDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.pattern.confirm_add_regions"), 0, 0)) {
            ctx.adoptSelectedRegions();
            ctx.state().setShowAddRegionPanel(false);
        }
        if (adoptDisabled) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.pattern.add_region_cancel"), 0, 0)) {
            ctx.state().setShowAddRegionPanel(false);
        }
    }

    public void renderDeleteConfirmPopup() {
        if (ctx.deleteConfirmPending()) {
            ImGui.openPopup("##pattern_delete_confirm");
            ctx.setDeleteConfirmPending(false);
        }

        if (ImGui.beginPopupModal("##pattern_delete_confirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            int count = ctx.pendingDeleteFootprintIds().size();
            ImGui.text(PlotI18n.tr("plugin.pattern.delete_confirm", count));
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.delete_confirm_yes"), 120, 0)) {
                ctx.deleteFootprints(new java.util.ArrayList<>(ctx.pendingDeleteFootprintIds()));
                ctx.pendingDeleteFootprintIds().clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.delete_confirm_no"), 120, 0)) {
                ctx.pendingDeleteFootprintIds().clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
