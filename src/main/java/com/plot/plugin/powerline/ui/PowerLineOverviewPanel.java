package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** 电力线路概览 Tab。 */
public final class PowerLineOverviewPanel {
    private final PowerLineUiContext ctx;

    public PowerLineOverviewPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderProjectSection() {
        renderProjectSection(false);
    }

    public void renderProjectSection(boolean compact) {
        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.project_stats",
            ctx.project().getLineCount(),
            String.format("%.1f", ctx.project().getTotalWorldPathLength(ctx.coordinates()))));

        if (ctx.project().getLineCount() == 0) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.no_lines"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;
        if (ImGui.button(PlotI18n.tr("plugin.powerline.select_all"), buttonWidth, 0)) {
            ctx.selectAll(ctx.project().getLines().keySet());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_selection"), buttonWidth, 0)) {
            ctx.clearSelection();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.delete_selected"), buttonWidth, 0)) {
            ctx.pendingDeleteLineIds().clear();
            ctx.pendingDeleteLineIds().addAll(ctx.selection().ids());
            ctx.setDeleteConfirmPending(true);
        }

        PowerLineOverviewRenderer.renderProjectMap(
            ctx.project(),
            ctx.selection().ids(),
            lineId -> ctx.selectLine(lineId, ImGui.getIO().getKeyCtrl()),
            ctx.coordinates());

        ImGui.spacing();
        float listHeight = compact ? 120f : 0f;
        ImGui.beginChild("powerline_overview_list", 0, listHeight, true);
        for (PowerLineFootprint line : ctx.project().getLines().values()) {
            renderLineRow(line);
        }
        ImGui.endChild();
    }

    private void renderLineRow(PowerLineFootprint line) {
        ImGui.pushID(line.getId());
        boolean selected = ctx.selection().contains(line.getId());

        if (PowerLineOverviewRenderer.renderLineThumbnail(line, selected, ctx.coordinates())) {
            ctx.selectLine(line.getId(), ImGui.getIO().getKeyCtrl());
        }
        ImGui.sameLine();

        float columnWidth = Math.max(120f, ImGui.getContentRegionAvailX() - 8f);
        ImGui.beginGroup();
        ImGui.setNextItemWidth(columnWidth);
        if (ImGui.selectable(
                PowerLineUiWidgets.stableSelectableLabel(line.getName(), line.getId()),
                selected)) {
            ctx.selectLine(line.getId(), ImGui.getIO().getKeyCtrl());
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.powerline.overview_item",
            line.estimatePoleCount(ctx.coordinates()),
            String.format("%.1f", line.computeWorldPathLength(ctx.coordinates()))));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.locate") + "##locate", 0, 0)) {
            ctx.locateLine(line);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.delete") + "##delete", 0, 0)) {
            ctx.pendingDeleteLineIds().clear();
            ctx.pendingDeleteLineIds().add(line.getId());
            ctx.setDeleteConfirmPending(true);
        }
        ImGui.endGroup();

        ImGui.separator();
        ImGui.popID();
    }

    public void renderDeleteConfirmPopup() {
        if (PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_delete_confirm",
                ctx.deleteConfirmPending(),
                () -> ctx.setDeleteConfirmPending(false))) {
            PowerLineUiWidgets.text(PlotI18n.tr(
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
