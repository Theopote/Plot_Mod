package com.plot.ui.dialog;

import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;

/**
 * 对话框布局辅助工具。
 * 负责 section / form / footer 等高层布局语法，避免业务对话框重复书写排版算法。
 */
public final class DialogLayoutHelper {
    private DialogLayoutHelper() {}

    public static final class DenseEditorStyleScope {
        private final int colorCount;
        private final int varCount;

        private DenseEditorStyleScope(int colorCount, int varCount) {
            this.colorCount = colorCount;
            this.varCount = varCount;
        }
    }

    public static void rowGap() {
        ImGui.dummy(0, DialogStyleManager.ROW_GAP);
    }

    public static void subsectionGap() {
        ImGui.dummy(0, DialogStyleManager.SUBSECTION_GAP);
    }

    public static void sectionGap() {
        ImGui.dummy(0, DialogStyleManager.SECTION_GAP);
    }

    public static void beginSection(String title) {
        if (title != null && !title.isEmpty()) {
            ImGui.text(title);
            subsectionGap();
        }
    }

    public static void endSection() {
        sectionGap();
    }

    public static void sectionSeparator() {
        sectionGap();
        ImGui.separator();
        sectionGap();
    }

    public static void helpText(String text) {
        renderTextWithColor(text, ThemeManager.getInstance().getCurrentTheme().mutedText);
    }

    public static void warningText(String text) {
        renderTextWithColor(text, ThemeManager.getInstance().getCurrentTheme().warningText);
    }

    public static void errorText(String text) {
        renderTextWithColor(text, ThemeManager.getInstance().getCurrentTheme().errorText);
    }

    public static boolean beginForm(String id) {
        int tableFlags = ImGuiTableFlags.SizingStretchProp;
        if (ImGui.beginTable(id, 2, tableFlags, 0, 0)) {
            ImGui.tableSetupColumn("Label", ImGuiTableColumnFlags.WidthFixed, DialogStyleManager.LABEL_WIDTH);
            ImGui.tableSetupColumn("Control", ImGuiTableColumnFlags.WidthStretch, 1.0f);
            return true;
        }
        return false;
    }

    public static void endForm() {
        ImGui.endTable();
    }

    public static void formRowLabel(String label) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        ImGui.alignTextToFramePadding();
        ImGui.text(label);
        ImGui.tableSetColumnIndex(1);
        ImGui.setNextItemWidth(-1.0f);
    }

    public static void formRowHelp(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        ImGui.textDisabled(" ");
        ImGui.tableSetColumnIndex(1);
        helpText(text);
    }

    public static InlineToggleResult formRowCheckboxPair(String leftLabel, boolean leftValue,
                                                         String rightLabel, boolean rightValue) {
        boolean leftClicked = ImGui.checkbox(leftLabel, leftValue);
        ImGui.sameLine(0, DialogStyleManager.ITEM_SPACING_H);
        boolean rightClicked = ImGui.checkbox(rightLabel, rightValue);
        return new InlineToggleResult(leftClicked, rightClicked);
    }

    public static float reserveTrailingButton(float buttonWidth) {
        float inputWidth = Math.max(0.0f,
                ImGui.getContentRegionAvailX() - buttonWidth - DialogStyleManager.FOOTER_BUTTON_GAP);
        ImGui.setNextItemWidth(inputWidth);
        return inputWidth;
    }

    public static boolean trailingButton(String label, float buttonWidth) {
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        return ImGui.button(label, buttonWidth, 0);
    }

    public static boolean beginRemainingChild(String id, float reservedBottomHeight, boolean border, int windowFlags) {
        float height = reservedBottomHeight > 0.0f ? -reservedBottomHeight : 0.0f;
        return ImGui.beginChild(id, 0, height, border, windowFlags);
    }

    public static boolean beginSettingsPageBody(String id, float footerReservedHeight) {
        return beginRemainingChild(id, footerReservedHeight, false, ImGuiWindowFlags.NoScrollbar);
    }

    public static boolean beginPinnedBottomRegion(String id) {
        return ImGui.beginChild(id, 0, 0, false,
                ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
    }

    public static float getReservedTextHeight(float lineCount) {
        float lines = Math.max(1.0f, lineCount);
        return ImGui.getTextLineHeightWithSpacing() * lines + DialogStyleManager.SUBSECTION_GAP;
    }

    public static float getStandardFooterReservedHeight() {
        return ImGui.getFrameHeight()
                + DialogStyleManager.FOOTER_TOP_GAP
                + DialogStyleManager.SECTION_GAP;
    }

    public static void beginFooter() {
        ImGui.dummy(0, DialogStyleManager.FOOTER_TOP_GAP);
    }

    public static boolean isConfirmShortcutPressed() {
        return ImGui.isKeyPressed(ImGuiKey.Enter);
    }

    public static boolean isCancelShortcutPressed() {
        return ImGui.isKeyPressed(ImGuiKey.Escape);
    }

    public static boolean shouldSuppressDialogHotkeys(boolean... conditions) {
        if (conditions == null) {
            return false;
        }
        for (boolean condition : conditions) {
            if (condition) {
                return true;
            }
        }
        return false;
    }

    public static DenseEditorStyleScope pushDenseEditorStyle() {
        int colorCount = 0;
        int varCount = 0;

        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        varCount++;

        return new DenseEditorStyleScope(colorCount, varCount);
    }

    public static void popDenseEditorStyle(DenseEditorStyleScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.varCount > 0) {
            ImGui.popStyleVar(scope.varCount);
        }
        if (scope.colorCount > 0) {
            ImGui.popStyleColor(scope.colorCount);
        }
    }

    public static boolean footerSingleCentered(String label, float availableWidth) {
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(
                Math.min(availableWidth, DialogStyleManager.BUTTON_MAX_WIDTH), 1);
        DialogStyleManager.centerByWidth(buttonWidth);
        return ImGui.button(label, buttonWidth, 0);
    }

    public static FooterResult footerConfirmCancelRight(String cancelLabel, String confirmLabel, float availableWidth) {
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(availableWidth * 0.65f, 2);
        float totalWidth = buttonWidth * 2.0f + DialogStyleManager.FOOTER_BUTTON_GAP;
        float startX = DialogStyleManager.getContentStartX() + Math.max(0.0f, availableWidth - totalWidth);
        ImGui.setCursorPosX(startX);

        boolean cancel = ImGui.button(cancelLabel, buttonWidth, 0);
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        boolean confirm = ImGui.button(confirmLabel, buttonWidth, 0);
        return new FooterResult(confirm, cancel);
    }

    public static FooterResult footerConfirmCancelCentered(String cancelLabel, String confirmLabel, float availableWidth) {
        float buttonWidth = DialogStyleManager.getStandardButtonWidth(availableWidth * 0.65f, 2);
        float totalWidth = buttonWidth * 2.0f + DialogStyleManager.FOOTER_BUTTON_GAP;
        DialogStyleManager.centerByWidth(totalWidth);

        boolean cancel = ImGui.button(cancelLabel, buttonWidth, 0);
        ImGui.sameLine(0, DialogStyleManager.FOOTER_BUTTON_GAP);
        boolean confirm = ImGui.button(confirmLabel, buttonWidth, 0);
        return new FooterResult(confirm, cancel);
    }

    private static void renderTextWithColor(String text, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + ImGui.getContentRegionAvailX());
        ImGui.textWrapped(text);
        ImGui.popTextWrapPos();
        ImGui.popStyleColor();
    }

    public record InlineToggleResult(boolean firstClicked, boolean secondClicked) {
    }

    public record FooterResult(boolean confirmClicked, boolean cancelClicked) {
    }
}
