package com.plot.plugin.pattern.ui;

import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.plugin.pattern.PatternBlockCountCache;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;

/** 区域管理 Tab：拾取范围 + 预览范围。 */
public final class PatternRegionPanel {
    private final PatternUiContext ctx;

    public PatternRegionPanel(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickPickSession() {
        ctx.handlePickSessionTick();
    }

    public void render() {
        renderPickSection();
        ImGui.separator();
        renderPreviewSection();

        PatternFootprint primary = ctx.selection().primary(ctx.project());
        if (primary != null) {
            ImGui.separator();
            PatternUiWidgets.renderFootprintGeometrySection(ctx, primary, () -> ctx.actions().invalidatePreview());
        }
    }

    private void renderPickSection() {
        if (ctx.pickSession().isActive()) {
            int count = ctx.pickSession().getAccumulatedCount();
            if (count > 0) {
                ImGui.textColored(
                    PluginUiColors.STATUS_INFO,
                    PlotI18n.tr("plugin.pattern.pick_picking_count", count));
            } else {
                ImGui.textColored(
                    PluginUiColors.STATUS_INFO,
                    PlotI18n.tr("plugin.pattern.pick_picking_active"));
            }
            ImGui.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.pattern.pick_right_click_finish"));
            if (ImGui.button(PlotI18n.tr("plugin.pattern.cancel_pick") + "##pattern_cancel_pick", 0, 0)) {
                ctx.cancelPickSession();
            }
            return;
        }

        if (ImGui.button(PlotI18n.tr("plugin.pattern.pick_range") + "##pattern_pick_range", 0, 0)) {
            ctx.startPickSession();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.pattern.pick_range_hint"));
        }
        ImGui.spacing();
        PatternUiWidgets.textColoredWrapped(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.pattern.draw_region_hint"));
    }

    private void renderPreviewSection() {
        ctx.tickFootprintNameRenameCooldown();

        var projection = ctx.currentProjection();
        ImGui.text(PlotI18n.tr("plugin.pattern.preview_section_title"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
            "plugin.pattern.project_stats",
            ctx.project().getFootprintCount(),
            ctx.project().totalBlockCount(projection)));

        if (ctx.project().getFootprintCount() == 0) {
            ImGui.spacing();
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.pattern.no_footprints"));
            return;
        }

        ctx.selection().retainExisting(ctx.project());
        var footprintIds = ctx.project().getFootprints().keySet();
        PatternOverviewLayoutCache.retainOnly(footprintIds);
        PatternBlockCountCache.retainOnly(footprintIds);

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

        ImGui.spacing();
        PatternOverviewRenderer.renderProjectMap(
            ctx.project(),
            ctx.selection().ids(),
            id -> ctx.selection().select(id, ImGui.getIO().getKeyCtrl()));

        ImGui.spacing();
        for (PatternFootprint footprint : ctx.project().getFootprints().values()) {
            renderFootprintRow(footprint, projection);
        }
    }

    private void renderFootprintRow(PatternFootprint footprint, WorldProjectionSnapshot projection) {
        ImGui.pushID(footprint.getId());
        boolean selected = ctx.selection().contains(footprint.getId());
        boolean renaming = footprint.getId().equals(ctx.footprintNameEditingId());

        if (PatternOverviewRenderer.renderFootprintThumbnail(footprint, selected)) {
            if (!renaming) {
                ctx.selection().select(footprint.getId(), ImGui.getIO().getKeyCtrl());
            }
        }
        ImGui.sameLine();

        float columnWidth = Math.max(120f, ImGui.getContentRegionAvailX() - 68f);
        ImGui.beginGroup();
        renderFootprintNameLabel(footprint, columnWidth, selected);
        if (!renaming) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(
                "plugin.pattern.overview_item",
                PatternBlockCountCache.blockCount(footprint, projection),
                PatternUiWidgets.sourceLabel(footprint)));
            if (ImGui.button(PlotI18n.tr("plugin.pattern.locate") + "##locate", 0, 0)) {
                ctx.locateFootprint(footprint);
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.pattern.delete") + "##delete", 0, 0)) {
                ctx.pendingDeleteFootprintIds().clear();
                ctx.pendingDeleteFootprintIds().add(footprint.getId());
                ctx.setDeleteConfirmPending(true);
            }
        }
        ImGui.endGroup();
        ImGui.popID();
    }

    private void renderFootprintNameLabel(PatternFootprint footprint, float columnWidth, boolean selected) {
        if (!footprint.getId().equals(ctx.footprintNameEditingId())) {
            ImGui.setNextItemWidth(columnWidth);
            if (ImGui.selectable(
                    PatternUiWidgets.stableSelectableLabel(footprint.getName(), footprint.getId()),
                    selected)) {
                ctx.selection().select(footprint.getId(), ImGui.getIO().getKeyCtrl());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("plugin.pattern.overview_rename_hint"));
                if (ImGui.isMouseDoubleClicked(0)) {
                    ctx.beginFootprintNameRename(footprint);
                }
            }
            return;
        }

        ImGui.setNextItemWidth(columnWidth);
        if (ctx.consumeFootprintNameFocusPending()) {
            ImGui.setKeyboardFocusHere();
        }

        boolean enterPressed = ImGui.inputText(
            "##pattern_footprint_rename_" + footprint.getId(),
            ctx.footprintNameBuffer(),
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
        } else if (ctx.isFootprintNameOutsideClickReady()
                && ImGui.isMouseClicked(0)
                && !inputHovered
                && !inputActive) {
            finished = true;
        } else if (ctx.isFootprintNameOutsideClickReady()
                && ImGui.isMouseClicked(0)
                && !ImGui.getIO().getWantCaptureMouse()) {
            finished = true;
        }

        if (canceled) {
            ctx.cancelFootprintNameRename(footprint);
        } else if (finished) {
            ctx.commitFootprintNameRename(footprint);
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
