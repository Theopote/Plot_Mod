package com.plot.ui.dialog;

import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;

/**
 * 对话框布局辅助工具。
 * 负责 section / form / footer 等高层布局语法，避免业务对话框重复书写排版算法。
 */
public final class DialogLayoutHelper {
    private DialogLayoutHelper() {}

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

    public static void beginFooter() {
        ImGui.separator();
        ImGui.dummy(0, DialogStyleManager.FOOTER_TOP_GAP);
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

    public record FooterResult(boolean confirmClicked, boolean cancelClicked) {
    }
}
