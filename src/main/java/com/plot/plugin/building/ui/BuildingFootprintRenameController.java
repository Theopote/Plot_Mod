package com.plot.plugin.building.ui;

import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.ui.utils.ImStringUtf8;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;

/** 建筑轮廓列表内联重命名：双击进入、Enter/失焦提交、Esc 取消。 */
public final class BuildingFootprintRenameController {
    private final BuildingUiContext ctx;
    /** 输入框已成功获得焦点后才允许“失焦提交”，避免刚进入编辑态就被误判为结束。 */
    private String editorActivatedBuildingId = "";

    public BuildingFootprintRenameController(BuildingUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickFrame() {
        ctx.tickBuildingNameRenameCooldown();
        if (ctx.buildingNameEditingId().isBlank()) {
            editorActivatedBuildingId = "";
        }
    }

    public boolean isRenaming(String buildingId) {
        return buildingId != null && buildingId.equals(ctx.buildingNameEditingId());
    }

    public void beginRename(BuildingFootprint building) {
        ctx.beginBuildingNameRename(building);
        editorActivatedBuildingId = "";
    }

    public void cancelActive() {
        if (ctx.buildingNameEditingId().isBlank()) {
            return;
        }
        BuildingFootprint primary = ctx.selection().primary(ctx.project());
        if (primary != null && primary.getId().equals(ctx.buildingNameEditingId())) {
            ctx.cancelBuildingNameRename(primary);
            editorActivatedBuildingId = "";
            return;
        }
        for (BuildingFootprint candidate : ctx.project().getBuildings().values()) {
            if (candidate.getId().equals(ctx.buildingNameEditingId())) {
                ctx.cancelBuildingNameRename(candidate);
                editorActivatedBuildingId = "";
                return;
            }
        }
        ctx.setBuildingNameEditingId("");
        editorActivatedBuildingId = "";
    }

    /**
     * @return {@code true} 时该行处于重命名编辑态（缩略图/副信息应隐藏）
     */
    public boolean renderNameField(
            BuildingFootprint building,
            float columnWidth,
            boolean selected,
            String selectableLabel) {
        if (!isRenaming(building.getId())) {
            renderSelectableLabel(building, columnWidth, selected, selectableLabel);
            return false;
        }
        renderEditor(building, columnWidth);
        return true;
    }

    private void renderSelectableLabel(
            BuildingFootprint building,
            float columnWidth,
            boolean selected,
            String selectableLabel) {
        ImGui.setNextItemWidth(columnWidth);
        if (ImGui.selectable(selectableLabel, selected)) {
            ctx.selection().select(building.getId(), ImGui.getIO().getKeyCtrl());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.building.overview_rename_hint"));
            if (ImGui.isMouseDoubleClicked(0)) {
                beginRename(building);
            }
        }
    }

    private void renderEditor(BuildingFootprint building, float columnWidth) {
        ImGui.setNextItemWidth(columnWidth);
        if (ctx.consumeBuildingNameFocusPending()) {
            ImGui.setKeyboardFocusHere();
        }

        boolean enterPressed = ImGui.inputText(
            "##building_footprint_rename_" + building.getId(),
            ctx.buildingNameBuffer(),
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

        if (ImGui.isItemActivated()) {
            editorActivatedBuildingId = building.getId();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            ctx.cancelBuildingNameRename(building);
            editorActivatedBuildingId = "";
            return;
        }
        if (enterPressed || shouldCommitOnDeactivate(building.getId())) {
            ctx.commitBuildingNameRename(building);
            editorActivatedBuildingId = "";
            return;
        }
        if (!building.getId().equals(editorActivatedBuildingId)
                && ctx.isBuildingNameOutsideClickReady()
                && ImGui.isMouseClicked(0)
                && !ImGui.isItemHovered()) {
            ctx.cancelBuildingNameRename(building);
            editorActivatedBuildingId = "";
        }
    }

    private boolean shouldCommitOnDeactivate(String buildingId) {
        return buildingId.equals(editorActivatedBuildingId)
            && ctx.isBuildingNameOutsideClickReady()
            && ImGui.isItemDeactivated();
    }
}
