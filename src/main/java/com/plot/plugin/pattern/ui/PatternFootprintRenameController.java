package com.plot.plugin.pattern.ui;

import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;

/** 区域列表内联重命名：双击进入、Enter/失焦提交、Esc 取消。 */
public final class PatternFootprintRenameController {
    private final PatternUiContext ctx;

    public PatternFootprintRenameController(PatternUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickFrame() {
        ctx.tickFootprintNameRenameCooldown();
    }

    public boolean isRenaming(String footprintId) {
        return footprintId != null && footprintId.equals(ctx.footprintNameEditingId());
    }

    public void beginRename(PatternFootprint footprint) {
        ctx.beginFootprintNameRename(footprint);
    }

    public void cancelActive() {
        if (ctx.footprintNameEditingId().isBlank()) {
            return;
        }
        PatternFootprint footprint = ctx.selection().primary(ctx.project());
        if (footprint != null && footprint.getId().equals(ctx.footprintNameEditingId())) {
            ctx.cancelFootprintNameRename(footprint);
            return;
        }
        for (PatternFootprint candidate : ctx.project().getFootprints().values()) {
            if (candidate.getId().equals(ctx.footprintNameEditingId())) {
                ctx.cancelFootprintNameRename(candidate);
                return;
            }
        }
        ctx.setFootprintNameEditingId("");
    }

    /**
     * @return {@code true} 时该行处于重命名编辑态（缩略图/操作按钮应隐藏）
     */
    public boolean renderNameField(
            PatternFootprint footprint,
            float columnWidth,
            boolean selected,
            String selectableLabel) {
        if (!isRenaming(footprint.getId())) {
            renderSelectableLabel(footprint, columnWidth, selected, selectableLabel);
            return false;
        }
        renderEditor(footprint, columnWidth);
        return true;
    }

    private void renderSelectableLabel(
            PatternFootprint footprint,
            float columnWidth,
            boolean selected,
            String selectableLabel) {
        ImGui.setNextItemWidth(columnWidth);
        if (ImGui.selectable(selectableLabel, selected)) {
            ctx.selectFootprint(footprint.getId(), ImGui.getIO().getKeyCtrl());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.pattern.overview_rename_hint"));
            if (ImGui.isMouseDoubleClicked(0)) {
                beginRename(footprint);
            }
        }
    }

    private void renderEditor(PatternFootprint footprint, float columnWidth) {
        ImGui.setNextItemWidth(columnWidth);
        if (ctx.consumeFootprintNameFocusPending()) {
            ImGui.setKeyboardFocusHere();
        }

        boolean enterPressed = ImGui.inputText(
            "##pattern_footprint_rename_" + footprint.getId(),
            ctx.footprintNameBuffer(),
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            ctx.cancelFootprintNameRename(footprint);
            return;
        }
        if (enterPressed || shouldCommitOnDeactivate()) {
            ctx.commitFootprintNameRename(footprint);
        }
    }

    private boolean shouldCommitOnDeactivate() {
        return ctx.isFootprintNameOutsideClickReady() && ImGui.isItemDeactivated();
    }
}
