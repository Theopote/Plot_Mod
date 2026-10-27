package com.plot.plugin.road.ui.editor;

import com.plot.plugin.road.ui.RoadUiContext;
import com.plot.plugin.road.ui.RoadUiWidgets;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.function.BooleanSupplier;

/**
 * 共享 ImGui 浮动编辑器壳：窗口尺寸、页脚与关闭确认弹窗。
 */
public final class RoadFloatingEditorShell {
    private static final float WINDOW_SIZE_OF_SCREEN = 0.55f;
    private static final float WINDOW_MIN_SIZE_OF_SCREEN = 0.35f;
    private static final float WINDOW_MAX_SIZE_OF_SCREEN = 0.85f;
    /** 为页脚按钮预留高度，避免内容区挤占确认/取消。 */
    static final float FOOTER_RESERVE_HEIGHT = 56f;
    /** 版本冲突横幅额外占用（中文标签较长时需两行）。 */
    static final float CONFLICT_BANNER_RESERVE = 52f;
    /** 窄面板阈值：低于此宽度时页脚按钮换行排列。 */
    static final float NARROW_PANEL_WIDTH = 420f;

    static final int WINDOW_FLAGS =
        ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoSavedSettings;

    private RoadFloatingEditorShell() {
    }

    public static void prepareWindow(String imguiId, ImBoolean open, boolean focusOnOpen) {
        if (focusOnOpen) {
            ImGui.setNextWindowDockID(0);
            float sw = ImGui.getIO().getDisplaySizeX();
            float sh = ImGui.getIO().getDisplaySizeY();
            float w = sw * WINDOW_SIZE_OF_SCREEN;
            float h = sh * WINDOW_SIZE_OF_SCREEN;
            ImGui.setNextWindowSize(w, h, ImGuiCond.Appearing);
            ImGui.setNextWindowPos(sw * 0.5f - w * 0.5f, sh * 0.5f - h * 0.5f, ImGuiCond.Appearing);
            ImGui.setNextWindowFocus();
        }
        ImGui.setNextWindowSizeConstraints(
            ImGui.getIO().getDisplaySizeX() * WINDOW_MIN_SIZE_OF_SCREEN,
            ImGui.getIO().getDisplaySizeY() * WINDOW_MIN_SIZE_OF_SCREEN,
            ImGui.getIO().getDisplaySizeX() * WINDOW_MAX_SIZE_OF_SCREEN,
            ImGui.getIO().getDisplaySizeY() * WINDOW_MAX_SIZE_OF_SCREEN);
    }

    public static float footerReserveHeight(boolean revisionConflictPending) {
        return FOOTER_RESERVE_HEIGHT
            + (revisionConflictPending ? CONFLICT_BANNER_RESERVE : 0f);
    }

    public static void renderFooter(
            RoadUiContext ctx,
            boolean dirty,
            Runnable onRestore,
            Runnable onCancel,
            Runnable onConfirm) {
        ImGui.separator();
        boolean narrow = ImGui.getContentRegionAvailX() < NARROW_PANEL_WIDTH;
        if (dirty && onRestore != null) {
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.restore") + "##restore")) {
                onRestore.run();
            }
            if (!narrow) {
                ImGui.sameLine();
            }
        }
        if (onCancel != null) {
            if (ImGui.button(PlotI18n.tr("button.plot.cancel") + "##cancel")) {
                onCancel.run();
            }
            if (!narrow) {
                ImGui.sameLine();
            }
        }
        if (onConfirm != null) {
            if (!dirty) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.confirm") + "##confirm")) {
                onConfirm.run();
            }
            if (!dirty) {
                ImGui.endDisabled();
            }
        }
        if (dirty) {
            if (!narrow) {
                ImGui.sameLine();
            }
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.floating_editor.unsaved_hint"));
        }
    }

    public static boolean renderCloseConfirmDialog(
            String popupId,
            boolean pending,
            Runnable clearPending,
            BooleanSupplier onApplyAndClose,
            Runnable onDiscardAndClose,
            Runnable onContinueEditing) {
        if (!RoadUiWidgets.beginDeferredPopupModal(popupId, pending, clearPending)) {
            return false;
        }
        try {
            ImGui.textWrapped(PlotI18n.tr("plugin.road.floating_editor.close_confirm_message"));
            ImGui.separator();
            boolean narrow = ImGui.getContentRegionAvailX() < NARROW_PANEL_WIDTH;
            float buttonWidth = narrow ? -1f : 120f;
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.confirm") + "##close_apply", buttonWidth, 0)) {
                if (onApplyAndClose.getAsBoolean()) {
                    ImGui.closeCurrentPopup();
                }
            }
            if (!narrow) {
                ImGui.sameLine();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.discard") + "##close_discard", buttonWidth, 0)) {
                onDiscardAndClose.run();
                ImGui.closeCurrentPopup();
            }
            if (!narrow) {
                ImGui.sameLine();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.continue_edit") + "##close_continue", buttonWidth, 0)) {
                onContinueEditing.run();
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
        return true;
    }

    public static void renderReloadConfirmDialog(
            String popupId,
            boolean pending,
            Runnable clearPending,
            Runnable onReload,
            Runnable onContinueEditing) {
        if (!RoadUiWidgets.beginDeferredPopupModal(popupId, pending, clearPending)) {
            return;
        }
        try {
            ImGui.textWrapped(PlotI18n.tr("plugin.road.floating_editor.reload_from_live_confirm"));
            ImGui.separator();
            boolean narrow = ImGui.getContentRegionAvailX() < NARROW_PANEL_WIDTH;
            float buttonWidth = narrow ? -1f : 140f;
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.reload_from_live") + "##reload_confirm", buttonWidth, 0)) {
                onReload.run();
                ImGui.closeCurrentPopup();
            }
            if (!narrow) {
                ImGui.sameLine();
            }
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.continue_edit") + "##reload_continue", buttonWidth, 0)) {
                onContinueEditing.run();
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
    }
}
