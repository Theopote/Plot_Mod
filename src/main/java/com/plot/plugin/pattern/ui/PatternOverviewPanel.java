package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** 图案概览 Tab 与删除确认弹窗。 */
public final class PatternOverviewPanel {
    private final PatternUiContext ctx;

    public PatternOverviewPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ImGui.text(PlotI18n.tr("plugin.pattern.project_stats",
            ctx.project().getFootprintCount(),
            String.format("%.1f", ctx.project().getTotalArea())));

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

        ImGui.beginChild("pattern_overview_list", 0, 220, true);
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
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.delete"), 60, 0)) {
                ctx.pendingDeleteFootprintIds().clear();
                ctx.pendingDeleteFootprintIds().add(footprint.getId());
                ctx.setDeleteConfirmPending(true);
            }
            ImGui.popID();
        }
        ImGui.endChild();
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
