package com.plot.plugin.road.ui;

import com.plot.plugin.road.model.Road;
import com.plot.ui.utils.ImStringUtf8;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
/** 路径 Tab 道路列表内联重命名：双击进入、Enter/失焦提交、Esc 取消。 */
public final class RoadListRenameController {
    private static final int MAX_NAME_LENGTH = 128;

    private final RoadUiContext ctx;
    /** 输入框已成功获得焦点后才允许“失焦提交”，避免刚进入编辑态就被误判为结束。 */
    private String editorActivatedRoadId = "";

    public RoadListRenameController(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void tickFrame() {
        ctx.tickRoadNameRenameCooldown();
        if (ctx.roadNameEditingId().isBlank()) {
            editorActivatedRoadId = "";
        }
    }

    public boolean isRenaming(String roadId) {
        return roadId != null && roadId.equals(ctx.roadNameEditingId());
    }

    public void beginRename(Road road) {
        ctx.beginRoadNameRename(road);
        editorActivatedRoadId = "";
    }

    public void cancelActive() {
        if (ctx.roadNameEditingId().isBlank()) {
            return;
        }
        Road road = ctx.networkManager().getNetwork().getRoad(ctx.roadNameEditingId());
        if (road != null) {
            ctx.cancelRoadNameRename(road);
        } else {
            ctx.endRoadNameRename();
        }
        editorActivatedRoadId = "";
    }

    /**
     * @return {@code true} 时该行处于重命名编辑态（副信息/删除按钮应隐藏）
     */
    public boolean renderNameField(
            Road road,
            float columnWidth,
            boolean selected,
            String selectableLabel,
            Runnable onSelect) {
        if (road == null) {
            return false;
        }
        if (!isRenaming(road.getId())) {
            renderSelectableLabel(road, columnWidth, selected, selectableLabel, onSelect);
            return false;
        }
        renderEditor(road, columnWidth);
        return true;
    }

    private void renderSelectableLabel(
            Road road,
            float columnWidth,
            boolean selected,
            String selectableLabel,
            Runnable onSelect) {
        ImGui.setNextItemWidth(columnWidth);
        if (ImGui.selectable(selectableLabel, selected)) {
            if (onSelect != null) {
                onSelect.run();
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.road.path.rename_hint"));
            if (ImGui.isMouseDoubleClicked(0)) {
                beginRename(road);
            }
        }
    }

    private void renderEditor(Road road, float columnWidth) {
        ImGui.setNextItemWidth(columnWidth);
        if (ctx.consumeRoadNameFocusPending()) {
            ImGui.setKeyboardFocusHere();
        }

        boolean enterPressed = ImGui.inputText(
            "##road_path_rename_" + road.getId(),
            ctx.roadNameBuffer(),
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

        if (ImGui.isItemActivated()) {
            editorActivatedRoadId = road.getId();
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            ctx.cancelRoadNameRename(road);
            editorActivatedRoadId = "";
            return;
        }
        if (enterPressed || shouldCommitOnDeactivate(road.getId())) {
            ctx.commitRoadNameRename(road);
            editorActivatedRoadId = "";
            return;
        }
        if (!road.getId().equals(editorActivatedRoadId)
                && ctx.isRoadNameOutsideClickReady()
                && ImGui.isMouseClicked(0)
                && !ImGui.isItemHovered()) {
            ctx.cancelRoadNameRename(road);
            editorActivatedRoadId = "";
        }
    }

    private boolean shouldCommitOnDeactivate(String roadId) {
        return roadId.equals(editorActivatedRoadId)
            && ctx.isRoadNameOutsideClickReady()
            && ImGui.isItemDeactivated();
    }

    static String normalizeDraftName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
