package com.masterplanner.ui.dialog;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.graphics.style.TextAlignment;
import com.masterplanner.core.graphics.style.TextStyle;
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;

import java.util.function.Consumer;

/**
 * 纯 ImGui 实现的文字输入对话框（无 Swing 依赖，适用于 headless 环境）
 */
public class TextInputDialog {
    private static final String DIALOG_TITLE = "添加文字";

    private static final TextInputDialog INSTANCE = new TextInputDialog();

    public static TextInputDialog getInstance() {
        return INSTANCE;
    }

    private boolean visible = false;
    private final ImString textBuffer = new ImString(2048);
    private Consumer<TextInputResult> onConfirm;
    private Runnable onCancel;

    // 跨线程安全的延迟打开标记与参数（避免在非渲染线程调用 ImGui.openPopup）
    private volatile boolean pendingOpen = false;
    private String pendingPreset = "";
    private Consumer<TextInputResult> pendingOnConfirm;
    private Runnable pendingOnCancel;

    // 样式参数（跟随 TextStyle 的默认值与范围）
    private float fontSize = TextStyle.DEFAULT_FONT_SIZE;
    private boolean bold = false;
    private boolean italic = false;
    private TextAlignment.Horizontal hAlign = TextAlignment.Horizontal.LEFT;
    private TextAlignment.Vertical vAlign = TextAlignment.Vertical.TOP;
    private float lineHeight = TextStyle.DEFAULT_LINE_HEIGHT;

    private TextInputDialog() {}

    public void open(Vec2d point, String presetText, Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        // 渲染线程可直接打开
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.textBuffer.set(presetText != null ? presetText : "");
        this.visible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    /**
     * 在任意线程请求打开（将在下一帧渲染时真正打开）
     */
    public void scheduleOpen(Vec2d point, String presetText, Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.pendingPreset = presetText != null ? presetText : "";
        this.pendingOnConfirm = onConfirm;
        this.pendingOnCancel = onCancel;
        this.pendingOpen = true;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render() {
        // 若收到延迟打开请求，则在渲染线程执行真正的打开
        if (pendingOpen) {
            this.onConfirm = pendingOnConfirm;
            this.onCancel = pendingOnCancel;
            this.textBuffer.set(pendingPreset);
            this.visible = true;
            this.pendingOpen = false;
            ImGui.openPopup(DIALOG_TITLE);
        }

        if (!visible) return;

        // 居中并设置窗口属性
        float width = 600.0f;
        float height = 0.0f; // 0表示自动计算高度
        ImGui.setNextWindowSize(width, height, ImGuiCond.Appearing);
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        // 移除AlwaysAutoResize标志，避免窗口自动变宽
        // 使用固定宽度，高度自动计算
        int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings;

        if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
            // 主题样式
            var theme = ThemeManager.getInstance().getCurrentTheme();
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10, 10);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
            ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.controlBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Header, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.HeaderHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.HeaderActive, theme.buttonActive);

            try {
                // 标题说明
                ImGui.text("请输入文字内容（可多行）");
                ImGui.separator();

                // 文本输入区域（带边框的子区域）
                // 使用固定宽度，避免子窗口导致父窗口变宽
                float inputHeight = 260.0f;
                float childWidth = width - 20.0f; // 固定宽度，减去左右padding
                ImGui.beginChild("##text_input_child", childWidth, inputHeight, true, ImGuiWindowFlags.NoScrollbar);
                if (ImGui.isWindowAppearing()) {
                    ImGui.setKeyboardFocusHere();
                }
                boolean multilineOk = true;
                try {
                    // 使用固定宽度，避免输入框导致窗口变宽
                    float inputWidth = childWidth - 20.0f; // 减去子窗口的左右padding
                    ImGui.inputTextMultiline("##text_input", textBuffer, inputWidth, inputHeight - 20,
                            ImGuiInputTextFlags.AllowTabInput);
                } catch (Throwable t) {
                    multilineOk = false;
                }
                if (!multilineOk) {
                    ImGui.inputText("##text_input", textBuffer, ImGuiInputTextFlags.CallbackHistory);
                }
                ImGui.endChild();

                ImGui.spacing();
                ImGui.separator();
                ImGui.text("文字样式");
                ImGui.spacing();

                // 样式区域
                renderStyleSection(width);

                ImGui.spacing();
                ImGui.separator();

                // 按钮区域靠右对齐
                float buttonWidth = 120.0f;
                float spacing = ImGui.getStyle().getItemSpacingX();
                float totalButtonsWidth = buttonWidth * 2 + spacing;
                float rightX = ImGui.getWindowWidth() - totalButtonsWidth - 4; // 4px内边距
                float currentY = ImGui.getCursorPosY();
                ImGui.setCursorPos(rightX, currentY);
                if (ImGui.button("取消", buttonWidth, 0)) {
                    cancelAndClose();
                }
                ImGui.sameLine(0, spacing);
                if (ImGui.button("确定", buttonWidth, 0)) {
                    confirmAndClose();
                }
            } finally {
                ImGui.popStyleColor(10);
                ImGui.popStyleVar(3);
                ImGui.endPopup();
            }
        }
    }

    private void renderStyleSection(float totalWidth) {
        float labelColWidth = 90.0f;
        // 确保value列宽度不会导致表格变宽
        float valueColWidth = Math.max(100.0f, totalWidth - labelColWidth - 40.0f);

        // 使用固定列宽，避免表格自动扩展
        int tableFlags = imgui.flag.ImGuiTableFlags.BordersInnerV | imgui.flag.ImGuiTableFlags.SizingFixedFit;
        if (ImGui.beginTable("##text_style_table", 2, tableFlags)) {
            ImGui.tableSetupColumn("label", imgui.flag.ImGuiTableColumnFlags.WidthFixed, labelColWidth);
            ImGui.tableSetupColumn("value", imgui.flag.ImGuiTableColumnFlags.WidthFixed, valueColWidth);

            // 字体大小
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("字体大小");
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(-1);
            float[] sizeArr = new float[]{fontSize};
            if (ImGui.sliderFloat("##font_size", sizeArr, TextStyle.MIN_FONT_SIZE, TextStyle.MAX_FONT_SIZE, "%.1f")) {
                fontSize = sizeArr[0];
            }

            // 行高
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("行高");
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(-1);
            float[] lineArr = new float[]{lineHeight};
            if (ImGui.sliderFloat("##line_height", lineArr, 0.5f, 3.0f, "%.2f")) {
                lineHeight = lineArr[0];
            }

            // 粗体 / 斜体
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("字形");
            ImGui.tableNextColumn();
            boolean b = bold; boolean i = italic;
            if (ImGui.checkbox("粗体", b)) {
                bold = !bold;
            }
            ImGui.sameLine();
            if (ImGui.checkbox("斜体", i)) {
                italic = !italic;
            }

            // 对齐方式（水平 + 垂直）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("对齐");
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth((valueColWidth - ImGui.getStyle().getItemSpacingX()) / 2.0f);
            String currentH = hAlign.name();
            if (ImGui.beginCombo("##h_align", currentH)) {
                for (TextAlignment.Horizontal h : TextAlignment.Horizontal.values()) {
                    boolean selected = h == hAlign;
                    if (ImGui.selectable(h.name(), selected)) {
                        hAlign = h;
                    }
                    if (selected) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }
            ImGui.sameLine();
            ImGui.setNextItemWidth((valueColWidth - ImGui.getStyle().getItemSpacingX()) / 2.0f);
            String currentV = vAlign.name();
            if (ImGui.beginCombo("##v_align", currentV)) {
                for (TextAlignment.Vertical v : TextAlignment.Vertical.values()) {
                    boolean selected = v == vAlign;
                    if (ImGui.selectable(v.name(), selected)) {
                        vAlign = v;
                    }
                    if (selected) ImGui.setItemDefaultFocus();
                }
                ImGui.endCombo();
            }

            ImGui.endTable();
        }
    }

    private void confirmAndClose() {
        String text = textBuffer.get();
        if (onConfirm != null) {
            onConfirm.accept(new TextInputResult(text, fontSize, bold, italic, hAlign, vAlign, lineHeight));
        }
        closeInternal();
        ImGui.closeCurrentPopup();
    }

    private void cancelAndClose() {
        if (onCancel != null) {
            onCancel.run();
        }
        closeInternal();
        ImGui.closeCurrentPopup();
    }

    private void closeInternal() {
        visible = false;
        onConfirm = null;
        onCancel = null;
    }

    public static final class TextInputResult {
        public final String text;
        public final float fontSize;
        public final boolean bold;
        public final boolean italic;
        public final TextAlignment.Horizontal hAlign;
        public final TextAlignment.Vertical vAlign;
        public final float lineHeight;

        public TextInputResult(String text, float fontSize, boolean bold, boolean italic,
                               TextAlignment.Horizontal hAlign, TextAlignment.Vertical vAlign,
                               float lineHeight) {
            this.text = text;
            this.fontSize = fontSize;
            this.bold = bold;
            this.italic = italic;
            this.hAlign = hAlign;
            this.vAlign = vAlign;
            this.lineHeight = lineHeight;
        }
    }
}


