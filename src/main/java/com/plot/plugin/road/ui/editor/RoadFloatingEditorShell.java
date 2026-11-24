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
 * <p>
 * 专业设计器默认尺寸不可拖边框改小；打开时以设计宽高为下限，
 * 正文/页脚文案放不下时按内容抬高宽度（高度保持设计值）。
 */
public final class RoadFloatingEditorShell {
    /** 默认窗口相对屏宽（原 55% 可缩放 → 等宽双栏后约 42%）。 */
    public static final float FIXED_WIDTH_OF_SCREEN = 0.42f;
    /**
     * 默认窗口相对屏高。略低于旧值，避免确认栏下方再留一大块空区；
     * 底边距与左右窗边距同级（靠 WindowPadding，不再额外垫高）。
     */
    public static final float FIXED_HEIGHT_OF_SCREEN = 0.48f;
    /** 内容驱动加宽时的相对屏宽上限。 */
    public static final float FIXED_MAX_WIDTH_OF_SCREEN = 0.90f;
    /** 页脚实际占用：分隔线 + 按钮行（勿再加大，否则按钮下会出现空白）。 */
    static final float FOOTER_RESERVE_HEIGHT = 36f;
    /** 版本冲突横幅额外占用（中文标签较长时需两行）。 */
    static final float CONFLICT_BANNER_RESERVE = 52f;
    /** 窄面板阈值：低于此宽度时页脚按钮换行排列。 */
    static final float NARROW_PANEL_WIDTH = 420f;
    /** 页脚操作按钮最小宽度；文案更长时三者一起加宽。 */
    static final float FOOTER_ACTION_BUTTON_MIN_WIDTH = 96f;

    /** 本帧正文申报的最小内容宽（不含窗口 Padding）；{@link #applyPendingContentWidthGrow()} 消费。 */
    private static float pendingContentMinWidth;

    public static final int WINDOW_FLAGS =
        ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoResize;

    /** 纵断面等需要拖边框缩放的浮动窗（与固定专业窗共用错开策略）。 */
    public static final int RESIZABLE_WINDOW_FLAGS =
        ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoDocking
            | ImGuiWindowFlags.NoSavedSettings;

    /** 纵断面默认相对屏宽/高（图表需要比专业卡更大）。 */
    public static final float PROFILE_WIDTH_OF_SCREEN = 0.60f;
    public static final float PROFILE_HEIGHT_OF_SCREEN = 0.60f;
    public static final float PROFILE_MIN_OF_SCREEN = 0.40f;
    public static final float PROFILE_MAX_OF_SCREEN = 0.90f;

    private RoadFloatingEditorShell() {
    }

    /** 连开多个专业窗时错开的像素步长（相对屏短边比例再夹紧）。 */
    public static final float CASCADE_STEP_OF_MIN_SIDE = 0.035f;
    public static final float CASCADE_STEP_MIN_PX = 28f;
    public static final float CASCADE_STEP_MAX_PX = 56f;

    /**
     * 隧道 / 预设库 / 纵断面等非 Host 窗的 cascade 基址：
     * {@code RoadFloatingEditorKind.values().length + offset}。
     */
    public static final int CASCADE_AFTER_HOST_TUNNEL = 0;
    public static final int CASCADE_AFTER_HOST_PRESET = 1;
    public static final int CASCADE_AFTER_HOST_PROFILE = 2;
    public static final int CASCADE_AFTER_HOST_COMPLEX_JUNCTION = 3;

    public static int cascadeAfterHost(int offset) {
        return RoadFloatingEditorKind.values().length + Math.max(0, offset);
    }

    /**
     * @param cascadeIndex 同屏错开序号（0 = 居中；专业窗用 {@link RoadFloatingEditorKind#ordinal()}）
     */
    public static void prepareWindow(
            String imguiId,
            ImBoolean open,
            boolean focusOnOpen,
            int cascadeIndex) {
        prepareWindow(
            imguiId,
            open,
            focusOnOpen,
            cascadeIndex,
            FIXED_WIDTH_OF_SCREEN,
            FIXED_HEIGHT_OF_SCREEN);
    }

    /**
     * 专业浮动窗：以设计宽高为下限，高度锁死；宽度可被内容抬高到
     * {@link #FIXED_MAX_WIDTH_OF_SCREEN}。
     */
    public static void prepareWindow(
            String imguiId,
            ImBoolean open,
            boolean focusOnOpen,
            int cascadeIndex,
            float widthOfScreen,
            float heightOfScreen) {
        float sw = ImGui.getIO().getDisplaySizeX();
        float sh = ImGui.getIO().getDisplaySizeY();
        float minW = sw * Math.clamp(widthOfScreen, 0.20f, 0.95f);
        float minH = sh * Math.clamp(heightOfScreen, 0.20f, 0.95f);
        float maxW = Math.max(minW, sw * FIXED_MAX_WIDTH_OF_SCREEN);
        if (focusOnOpen) {
            placeAppearingWindow(sw, sh, minW, minH, cascadeIndex);
        }
        // NoResize：用户不可拖边；高度固定为设计高，宽度允许内容驱动加宽。
        ImGui.setNextWindowSizeConstraints(minW, minH, maxW, minH);
        beginContentWidthPass();
    }

    /** 新开一帧内容宽申报（在 {@link #prepareWindow} 内调用）。 */
    public static void beginContentWidthPass() {
        pendingContentMinWidth = 0f;
    }

    /**
     * 申报正文所需最小内容宽度（不含 WindowPadding）。
     * 可多次调用，取最大值；须在浮动窗 begin 之后、{@link #applyPendingContentWidthGrow()} 之前。
     */
    public static void noteContentMinWidth(float contentWidth) {
        if (contentWidth > pendingContentMinWidth) {
            pendingContentMinWidth = contentWidth;
        }
    }

    /**
     * 按本帧申报与当前窗口宽度，把浮动窗加宽到能放下内容（不超过屏宽上限）。
     * 在浮动窗仍为 current window 时调用（body/footer 之后、{@code ImGui.end} 之前）。
     */
    public static void applyPendingContentWidthGrow() {
        float needed = pendingContentMinWidth;
        pendingContentMinWidth = 0f;
        if (!(needed > 0f)) {
            return;
        }
        float pad = ImGui.getStyle().getWindowPaddingX() * 2f;
        float target = needed + pad;
        float current = ImGui.getWindowWidth();
        if (target <= current + 1f) {
            return;
        }
        float maxW = ImGui.getIO().getDisplaySizeX() * FIXED_MAX_WIDTH_OF_SCREEN;
        ImGui.setWindowSize(Math.min(target, maxW), ImGui.getWindowHeight());
    }

    /** 变截面：左轨 + 右详情双栏，略宽于横断面设计器。 */
    public static final float VARIABLE_XS_WIDTH_OF_SCREEN = 0.52f;
    public static final float VARIABLE_XS_HEIGHT_OF_SCREEN = 0.58f;

    /** 中心线：上路级 HA + 下主从（分段轨/工具），略宽于默认专业窗。 */
    public static final float CENTERLINE_WIDTH_OF_SCREEN = 0.50f;
    public static final float CENTERLINE_HEIGHT_OF_SCREEN = 0.58f;

    /**
     * 可缩放浮动窗（纵断面）：默认较大，保留 min/max，打开时与专业窗同一套 cascade 错开。
     */
    public static void prepareResizableWindow(
            boolean focusOnOpen,
            int cascadeIndex,
            float widthOfScreen,
            float heightOfScreen,
            float minOfScreen,
            float maxOfScreen) {
        float sw = ImGui.getIO().getDisplaySizeX();
        float sh = ImGui.getIO().getDisplaySizeY();
        float w = sw * widthOfScreen;
        float h = sh * heightOfScreen;
        if (focusOnOpen) {
            placeAppearingWindow(sw, sh, w, h, cascadeIndex);
        }
        ImGui.setNextWindowSizeConstraints(
            sw * minOfScreen,
            sh * minOfScreen,
            sw * maxOfScreen,
            sh * maxOfScreen);
    }

    /** 仅定位/定尺寸（Appearing），不改 SizeConstraints；供预设库等自管约束的窗口使用。 */
    public static void placeCascadedAppearing(float width, float height, int cascadeIndex) {
        float sw = ImGui.getIO().getDisplaySizeX();
        float sh = ImGui.getIO().getDisplaySizeY();
        placeAppearingWindow(sw, sh, width, height, cascadeIndex);
    }

    private static void placeAppearingWindow(
            float sw,
            float sh,
            float w,
            float h,
            int cascadeIndex) {
        float step = Math.clamp(
            Math.min(sw, sh) * CASCADE_STEP_OF_MIN_SIDE,
            CASCADE_STEP_MIN_PX,
            CASCADE_STEP_MAX_PX);
        int index = Math.max(0, cascadeIndex);
        float x = Math.clamp(sw * 0.5f - w * 0.5f + index * step, 8f, Math.max(8f, sw - w - 8f));
        float y = Math.clamp(sh * 0.5f - h * 0.5f + index * step, 8f, Math.max(8f, sh - h - 8f));
        ImGui.setNextWindowDockID(0);
        ImGui.setNextWindowSize(w, h, ImGuiCond.Appearing);
        ImGui.setNextWindowPos(x, y, ImGuiCond.Appearing);
        ImGui.setNextWindowFocus();
    }

    public static float footerReserveHeight(boolean revisionConflictPending) {
        return FOOTER_RESERVE_HEIGHT
            + (revisionConflictPending ? CONFLICT_BANNER_RESERVE : 0f);
    }

    /** 只读窗页脚：仅关闭。 */
    public static void renderReadOnlyCloseFooter(Runnable onClose) {
        ImGui.separator();
        String closeLabel = PlotI18n.tr("button.plot.close");
        float buttonWidth = equalFooterActionButtonWidth(closeLabel);
        noteContentMinWidth(buttonWidth);
        if (onClose != null
                && ImGui.button(closeLabel + "##readonly_close", buttonWidth, 0)) {
            onClose.run();
        }
    }

    public static void renderFooter(
            RoadUiContext ctx,
            boolean dirty,
            Runnable onRestore,
            Runnable onCancel,
            Runnable onConfirm) {
        renderFooter(ctx, dirty, false, false, null, null, onRestore, onCancel, onConfirm);
    }

    /**
     * 页脚：左侧撤销/重做，再还原/取消/确认。
     */
    public static void renderFooter(
            RoadUiContext ctx,
            boolean dirty,
            boolean canUndo,
            boolean canRedo,
            Runnable onUndo,
            Runnable onRedo,
            Runnable onRestore,
            Runnable onCancel,
            Runnable onConfirm) {
        ImGui.separator();
        boolean showUndoRedo = onUndo != null || onRedo != null;
        boolean showRestore = dirty && onRestore != null;
        String undoLabel = PlotI18n.tr("plugin.road.floating_editor.undo");
        String redoLabel = PlotI18n.tr("plugin.road.floating_editor.redo");
        String restoreLabel = PlotI18n.tr("plugin.road.floating_editor.restore");
        String cancelLabel = PlotI18n.tr("button.plot.cancel");
        String confirmLabel = PlotI18n.tr("plugin.road.floating_editor.confirm");
        String unsavedHint = dirty
            ? PlotI18n.tr("plugin.road.floating_editor.unsaved_hint")
            : null;
        float buttonWidth = equalFooterActionButtonWidth(
            showUndoRedo ? undoLabel : null,
            showUndoRedo ? redoLabel : null,
            showRestore ? restoreLabel : null,
            onCancel != null ? cancelLabel : null,
            onConfirm != null ? confirmLabel : null);
        float spacing = ImGui.getStyle().getItemSpacingX();
        float rowWidth = footerActionRowWidth(
            buttonWidth,
            showUndoRedo,
            showRestore,
            onCancel != null,
            onConfirm != null,
            unsavedHint,
            spacing);
        noteContentMinWidth(rowWidth);
        boolean narrow = ImGui.getContentRegionAvailX() < NARROW_PANEL_WIDTH
            || ImGui.getContentRegionAvailX() + 1f < rowWidth;

        if (showUndoRedo) {
            if (!canUndo || onUndo == null) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(undoLabel + "##session_undo", buttonWidth, 0) && onUndo != null) {
                onUndo.run();
            }
            if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
                com.plot.ui.component.UIUtils.setTooltip(
                    PlotI18n.tr("plugin.road.floating_editor.undo_tip"));
            }
            if (!canUndo || onUndo == null) {
                ImGui.endDisabled();
            }
            if (!narrow) {
                ImGui.sameLine(0f, spacing);
            }
            if (!canRedo || onRedo == null) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(redoLabel + "##session_redo", buttonWidth, 0) && onRedo != null) {
                onRedo.run();
            }
            if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
                com.plot.ui.component.UIUtils.setTooltip(
                    PlotI18n.tr("plugin.road.floating_editor.redo_tip"));
            }
            if (!canRedo || onRedo == null) {
                ImGui.endDisabled();
            }
            if (!narrow) {
                ImGui.sameLine(0f, spacing);
            }
        }
        if (showRestore) {
            if (ImGui.button(restoreLabel + "##restore", buttonWidth, 0)) {
                onRestore.run();
            }
            if (!narrow) {
                ImGui.sameLine(0f, spacing);
            }
        }
        if (onCancel != null) {
            if (ImGui.button(cancelLabel + "##cancel", buttonWidth, 0)) {
                onCancel.run();
            }
            if (!narrow) {
                ImGui.sameLine(0f, spacing);
            }
        }
        if (onConfirm != null) {
            if (!dirty) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(confirmLabel + "##confirm", buttonWidth, 0)) {
                onConfirm.run();
            }
            if (!dirty) {
                ImGui.endDisabled();
            }
        }
        if (dirty) {
            if (!narrow) {
                ImGui.sameLine(0f, spacing);
            }
            RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, unsavedHint);
        }
    }

    private static float footerActionRowWidth(
            float buttonWidth,
            boolean showUndoRedo,
            boolean showRestore,
            boolean showCancel,
            boolean showConfirm,
            String unsavedHint,
            float spacing) {
        int count = 0;
        float width = 0f;
        if (showUndoRedo) {
            width += buttonWidth * 2f;
            count += 2;
        }
        if (showRestore) {
            width += buttonWidth;
            count++;
        }
        if (showCancel) {
            width += buttonWidth;
            count++;
        }
        if (showConfirm) {
            width += buttonWidth;
            count++;
        }
        if (count > 1) {
            width += spacing * (count - 1);
        }
        if (unsavedHint != null && !unsavedHint.isBlank()) {
            width += spacing + ImGui.calcTextSize(unsavedHint).x;
        }
        return width;
    }

    /**
     * 页脚操作钮等宽：至少 {@link #FOOTER_ACTION_BUTTON_MIN_WIDTH}，
     * 并按当前可见标签中最宽文案加 FramePadding 抬高。
     */
    static float equalFooterActionButtonWidth(String... labels) {
        float pad = ImGui.getStyle().getFramePaddingX() * 2f;
        float width = FOOTER_ACTION_BUTTON_MIN_WIDTH;
        if (labels == null) {
            return width;
        }
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            width = Math.max(width, ImGui.calcTextSize(label).x + pad);
        }
        return width;
    }

    public static boolean renderCloseConfirmDialog(
            String popupId,
            boolean pending,
            Runnable clearPending,
            BooleanSupplier onApplyAndClose,
            Runnable onDiscardAndClose,
            Runnable onContinueEditing) {
        String confirmLabel = PlotI18n.tr("plugin.road.floating_editor.confirm");
        String discardLabel = PlotI18n.tr("plugin.road.floating_editor.discard");
        String continueLabel = PlotI18n.tr("plugin.road.floating_editor.continue_edit");
        float buttonWidth = equalFooterActionButtonWidth(confirmLabel, discardLabel, continueLabel);
        float spacing = ImGui.getStyle().getItemSpacingX();
        float padX = ImGui.getStyle().getWindowPaddingX();
        // 宽度刚好放下三枚等宽按钮；高度随正文换行由 AlwaysAutoResize 抬高。
        float windowWidth = buttonWidth * 3f + spacing * 2f + padX * 2f;
        if (!RoadUiWidgets.beginDeferredPopupModalFixedWidth(
                popupId, pending, clearPending, windowWidth)) {
            return false;
        }
        try {
            ImGui.textWrapped(PlotI18n.tr("plugin.road.floating_editor.close_confirm_message"));
            ImGui.separator();
            if (ImGui.button(confirmLabel + "##close_apply", buttonWidth, 0)) {
                if (onApplyAndClose.getAsBoolean()) {
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.sameLine(0f, spacing);
            if (ImGui.button(discardLabel + "##close_discard", buttonWidth, 0)) {
                onDiscardAndClose.run();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine(0f, spacing);
            if (ImGui.button(continueLabel + "##close_continue", buttonWidth, 0)) {
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
            ImGui.text(PlotI18n.tr("plugin.road.floating_editor.reload_from_live_confirm"));
            ImGui.separator();
            float buttonWidth = 140f;
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.reload_from_live") + "##reload_confirm", buttonWidth, 0)) {
                onReload.run();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(PlotI18n.tr("plugin.road.floating_editor.continue_edit") + "##reload_continue", buttonWidth, 0)) {
                onContinueEditing.run();
                ImGui.closeCurrentPopup();
            }
        } finally {
            ImGui.endPopup();
        }
    }
}
