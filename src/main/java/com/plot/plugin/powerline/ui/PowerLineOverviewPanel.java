package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** 电力线路概览 Tab。 */
public final class PowerLineOverviewPanel {
    private final PowerLineUiContext ctx;

    public PowerLineOverviewPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        ImGui.text(PlotI18n.tr(
            "plugin.powerline.project_stats",
            ctx.project().getLineCount(),
            String.format("%.1f", ctx.project().getTotalPathLength())));

        if (ctx.project().getLineCount() == 0) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.no_lines"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.powerline.select_all"), buttonWidth, 0)) {
            ctx.selection().selectAll(ctx.project().getLines().keySet());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_selection"), buttonWidth, 0)) {
            ctx.selection().clear();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.delete_selected"), buttonWidth, 0)) {
            ctx.pendingDeleteLineIds().clear();
            ctx.pendingDeleteLineIds().addAll(ctx.selection().ids());
            ctx.setDeleteConfirmPending(true);
        }

        ImGui.beginChild("powerline_overview_list", 0, 220, true);
        for (PowerLineFootprint line : ctx.project().getLines().values()) {
            ImGui.pushID(line.getId());
            boolean selected = ctx.selection().contains(line.getId());
            if (ImGui.selectable(line.getName() + "##row", selected)) {
                ctx.selectLine(line.getId(), ImGui.getIO().getKeyCtrl());
            }
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.powerline.overview_item",
                line.estimatePoleCount(),
                String.format("%.1f", line.computePathLength())));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.locate"), 60, 0)) {
                ctx.locateLine(line);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.delete"), 60, 0)) {
                ctx.pendingDeleteLineIds().clear();
                ctx.pendingDeleteLineIds().add(line.getId());
                ctx.setDeleteConfirmPending(true);
            }
            ImGui.popID();
        }
        ImGui.endChild();
    }

    public void renderDeleteConfirmPopup() {
        if (!ctx.deleteConfirmPending()) {
            return;
        }
        ImGui.openPopup("##powerline_delete_confirm");
        ctx.setDeleteConfirmPending(false);
        if (ImGui.beginPopupModal(
                "##powerline_delete_confirm",
                ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(PlotI18n.tr(
                "plugin.powerline.delete_confirm",
                ctx.pendingDeleteLineIds().size()));
            if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
                ctx.deleteLines(new java.util.ArrayList<>(ctx.pendingDeleteLineIds()));
                ctx.pendingDeleteLineIds().clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
                ctx.pendingDeleteLineIds().clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
