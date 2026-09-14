package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;

/** Route Tab 中的项目线路管理组件。 */
public final class PowerLineOverviewPanel {
    private final PowerLineUiContext ctx;

    public PowerLineOverviewPanel(PowerLineUiContext ctx) {
        this.ctx = ctx;
    }

    public void renderProjectSection() {
        ctx.tickLineNameRenameCooldown();

        PowerLineUiWidgets.text(PlotI18n.tr(
            "plugin.powerline.project_stats",
            ctx.project().getLineCount(),
            String.format("%.1f", ctx.project().getTotalWorldPathLength(ctx.coordinates()))));

        if (ctx.project().getLineCount() == 0) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.no_lines"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());

        boolean selectionFrozen = ctx.isLineSelectionFrozen();
        if (selectionFrozen) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.selection.frozen_replace_confirm"));
            ImGui.spacing();
        }

        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2) / 3.0f;
        if (selectionFrozen) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.select_all"), buttonWidth, 0)) {
            ctx.selectAll(ctx.project().getLines().keySet());
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.clear_selection"), buttonWidth, 0)) {
            ctx.clearSelection();
        }
        ImGui.sameLine();
        boolean deleteDisabled = ctx.selection().isEmpty();
        if (deleteDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.delete_selected"), buttonWidth, 0)) {
            ctx.pendingDeleteLineIds().clear();
            ctx.pendingDeleteLineIds().addAll(ctx.selection().ids());
            ctx.setDeleteConfirmPending(true);
        }
        if (deleteDisabled) {
            ImGui.endDisabled();
        }
        if (selectionFrozen) {
            ImGui.endDisabled();
        }

        if (selectionFrozen) {
            ImGui.beginDisabled();
        }
        PowerLineOverviewRenderer.renderProjectMap(
            ctx.project(),
            ctx.selection().ids(),
            lineId -> ctx.selectLine(lineId, ImGui.getIO().getKeyCtrl()),
            ctx.coordinates());
        if (selectionFrozen) {
            ImGui.endDisabled();
        }

        if (!ctx.project().getLines().isEmpty()) {
            ImGui.spacing();
        }
        for (PowerLineFootprint line : ctx.project().getLines().values()) {
            renderLineRow(line, selectionFrozen);
        }
    }

    private void renderLineRow(PowerLineFootprint line, boolean selectionFrozen) {
        ImGui.pushID(line.getId());
        if (selectionFrozen) {
            ImGui.beginDisabled();
        }
        boolean selected = ctx.selection().contains(line.getId());
        boolean renaming = line.getId().equals(ctx.lineNameEditingId());

        if (PowerLineOverviewRenderer.renderLineThumbnail(line, selected, ctx.coordinates())) {
            if (!renaming) {
                ctx.selectLine(line.getId(), ImGui.getIO().getKeyCtrl());
            }
        }
        ImGui.sameLine();

        float columnWidth = Math.max(120f, ImGui.getContentRegionAvailX() - 8f);
        ImGui.beginGroup();
        renderLineNameLabel(line, columnWidth, selected, selectionFrozen);
        if (!renaming) {
            PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.powerline.overview_item",
                line.estimatePoleCount(ctx.coordinates()),
                String.format("%.1f", line.computeWorldPathLength(ctx.coordinates()))));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.delete") + "##delete", 0, 0)) {
                ctx.pendingDeleteLineIds().clear();
                ctx.pendingDeleteLineIds().add(line.getId());
                ctx.setDeleteConfirmPending(true);
            }
        }
        ImGui.endGroup();
        if (selectionFrozen) {
            ImGui.endDisabled();
        }
        ImGui.popID();
    }

    private void renderLineNameLabel(
            PowerLineFootprint line,
            float columnWidth,
            boolean selected,
            boolean selectionFrozen) {
        if (!line.getId().equals(ctx.lineNameEditingId())) {
            ImGui.setNextItemWidth(columnWidth);
            if (ImGui.selectable(
                    PowerLineUiWidgets.stableSelectableLabel(line.getName(), line.getId()),
                    selected)) {
                ctx.selectLine(line.getId(), ImGui.getIO().getKeyCtrl());
            }
            if (!selectionFrozen && ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("plugin.powerline.overview_rename_hint"));
                if (ImGui.isMouseDoubleClicked(0)) {
                    ctx.beginLineNameRename(line);
                }
            }
            return;
        }

        ImGui.setNextItemWidth(columnWidth);
        if (ctx.consumeLineNameFocusPending()) {
            ImGui.setKeyboardFocusHere();
        }

        boolean enterPressed = ImGui.inputText(
            "##powerline_line_rename_" + line.getId(),
            ctx.lineNameBuffer(),
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

        boolean inputActive = ImGui.isItemActive();
        boolean inputHovered = ImGui.isItemHovered();
        boolean finished = false;
        boolean canceled = false;

        if (enterPressed || ImGui.isKeyPressed(ImGuiKey.Enter)) {
            finished = true;
        } else if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            canceled = true;
        } else if (ImGui.isItemDeactivated()) {
            finished = true;
        } else if (ctx.isLineNameOutsideClickReady()
                && ImGui.isMouseClicked(0)
                && !inputHovered
                && !inputActive) {
            finished = true;
        } else if (ctx.isLineNameOutsideClickReady()
                && ImGui.isMouseClicked(0)
                && !ImGui.getIO().getWantCaptureMouse()) {
            finished = true;
        }

        if (canceled) {
            ctx.cancelLineNameRename(line);
        } else if (finished) {
            ctx.commitLineNameRename(line);
        }
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
