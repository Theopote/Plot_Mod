package com.plot.ui.dialog;

import com.plot.core.graphics.style.TextAlignment;
import com.plot.core.graphics.style.TextStyle;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
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
    
    // 统一的布局常量
    // 使用DialogStyleManager中定义的统一间距常数
    private static final float VERTICAL_SPACING = DialogStyleManager.ITEM_SPACING; // 垂直间距（所有控件上下间距统一）
    private static final float HORIZONTAL_SPACING = DialogStyleManager.ITEM_SPACING_H; // 水平间距（控件之间的水平间距）
    private static final float LABEL_COLUMN_WIDTH = 80.0f; // 标签列宽度
    private static final float BUTTON_WIDTH = 100.0f; // 按钮宽度
    private static final float BUTTON_SPACING = DialogStyleManager.BUTTON_SPACING; // 按钮之间的间距
    private static final float MIN_INPUT_ROWS = 6.0f; // 多行输入框最小可见行数

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
    private TextInputPreset pendingStylePreset;

    // 样式参数（跟随 TextStyle 的默认值与范围）
    // 注意：字体大小滑动条范围为 100~200，所以初始值设为 100.0f
    private float fontSize = 100.0f;
    private boolean bold = false;
    private boolean italic = false;
    private TextAlignment.Horizontal hAlign = TextAlignment.Horizontal.LEFT;
    private TextAlignment.Vertical vAlign = TextAlignment.Vertical.TOP;
    private float lineHeight = TextStyle.DEFAULT_LINE_HEIGHT;

    private TextInputDialog() {}

    public void open(String presetText, Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        // 渲染线程可直接打开
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.textBuffer.set(presetText != null ? presetText : "");
        applyPreset(TextInputPreset.defaults());
        this.visible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    public void open(String presetText, TextInputPreset preset,
                     Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.textBuffer.set(presetText != null ? presetText : "");
        applyPreset(preset);
        this.visible = true;
        ImGui.openPopup(DIALOG_TITLE);
    }

    /**
     * 在任意线程请求打开（将在下一帧渲染时真正打开）
     */
    public void scheduleOpen(String presetText, Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.pendingPreset = presetText != null ? presetText : "";
        this.pendingOnConfirm = onConfirm;
        this.pendingOnCancel = onCancel;
        this.pendingStylePreset = TextInputPreset.defaults();
        this.pendingOpen = true;
    }

    public void scheduleOpen(String presetText, TextInputPreset preset,
                             Consumer<TextInputResult> onConfirm, Runnable onCancel) {
        this.pendingPreset = presetText != null ? presetText : "";
        this.pendingOnConfirm = onConfirm;
        this.pendingOnCancel = onCancel;
        this.pendingStylePreset = preset;
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
            applyPreset(pendingStylePreset);
            this.visible = true;
            this.pendingOpen = false;
            ImGui.openPopup(DIALOG_TITLE);
        }

        if (!visible) return;

        // 居中并设置窗口属性
        float width = 420.0f;
        float inputHeight = Math.max(
            ImGui.getTextLineHeightWithSpacing() * MIN_INPUT_ROWS,
            ImGui.getFrameHeightWithSpacing() * 4.0f
        );
        
        // 在窗口开始之前设置WindowPadding，确保边距正确应用
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,
            DialogStyleManager.PANEL_PADDING, DialogStyleManager.PANEL_PADDING);
        ImGui.setNextWindowSize(width, 0.0f, ImGuiCond.Always);
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        // 固定宽度 + 自动高度，防止高DPI和字体缩放导致按钮区域被裁剪
        int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings |
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.AlwaysAutoResize;

        // 应用统一的对话框样式
        DialogStyleManager.DialogStyleScope styleScope = DialogStyleManager.applyDialogStyle();
        
        if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
            if (DialogStyleManager.renderTopRightCloseButton("text_input")) {
                cancelAndClose();
                ImGui.endPopup();
                DialogStyleManager.popDialogStyle(styleScope);
                ImGui.popStyleVar(1);
                return;
            }

            // 推送TextInputDialog特有的样式（在DialogStyleManager样式之后）
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, VERTICAL_SPACING); // 保持垂直间距，水平间距为0
            var theme = ThemeManager.getInstance().getCurrentTheme();
            ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.controlBackground);
            ImGui.pushStyleColor(ImGuiCol.Header, theme.buttonActive);
            
            try {
                float inputWidth = DialogStyleManager.getContentWidth();
                
                // 标题说明（统一左对齐，使用统一的左边距）
                ImGui.text("请输入文字内容（可多行）");

                // 文本输入区域（宽度为内容宽度的一半）
                // 输入框宽度为内容宽度的一半
                // inputHeight 已在上面定义（87.0f，约为原来的1/3）
                
                if (ImGui.isWindowAppearing()) {
                    ImGui.setKeyboardFocusHere();
                }
                boolean multilineOk = true;
                try {
                    // 使用调整后的输入框宽度和高度
                    ImGui.inputTextMultiline("##text_input", textBuffer, inputWidth, inputHeight,
                            ImGuiInputTextFlags.AllowTabInput);
                } catch (Throwable t) {
                    multilineOk = false;
                }
                if (!multilineOk) {
                    ImGui.inputText("##text_input", textBuffer, ImGuiInputTextFlags.CallbackHistory);
                }

                
                // 样式标题（与输入框标题左对齐）
                ImGui.text("文字样式");


                // 样式区域（使用统一的内容宽度，确保右边界对齐）
                renderStyleSection(inputWidth, theme);


                // 按钮区域（右边界与内容区域对齐）
                // 按钮应该从内容区域的右边界开始向左排列
                float totalButtonsWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
                float buttonStartX = DialogStyleManager.getContentStartX()
                    + Math.max(0.0f, inputWidth - totalButtonsWidth);
                float currentY = ImGui.getCursorPosY();
                ImGui.setCursorPos(buttonStartX, currentY);
                if (ImGui.button("取消", BUTTON_WIDTH, 0)) {
                    cancelAndClose();
                }
                ImGui.sameLine(0, BUTTON_SPACING);
                if (ImGui.button("确定", BUTTON_WIDTH, 0)) {
                    confirmAndClose();
                }
            } finally {
                ImGui.popStyleColor(2);  // FrameBg, Header
                ImGui.popStyleVar(2);    // FrameBorderSize, ItemSpacing
                ImGui.endPopup();
            }
            DialogStyleManager.popDialogStyle(styleScope);
        }
        // 弹出在窗口开始之前设置的WindowPadding
        ImGui.popStyleVar(1);
    }

    private void renderStyleSection(float contentWidth, UITheme.ThemeColors theme) {
        float labelColWidth = LABEL_COLUMN_WIDTH;
        // 主表格：标签列固定，值列自动拉伸
        int tableFlags = imgui.flag.ImGuiTableFlags.BordersInnerV | imgui.flag.ImGuiTableFlags.SizingStretchProp;
        if (ImGui.beginTable("##text_style_table", 2, tableFlags, contentWidth, 0)) {
            ImGui.tableSetupColumn("label", imgui.flag.ImGuiTableColumnFlags.WidthFixed, labelColWidth);
            ImGui.tableSetupColumn("value", imgui.flag.ImGuiTableColumnFlags.WidthStretch, 1.0f);

            // 字体大小（范围100~200）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("字体大小");
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(-1); // 使用全部可用宽度
            float[] sizeArr = new float[]{fontSize};
            if (ImGui.sliderFloat("##font_size", sizeArr, 100.0f, 200.0f, "%.1f")) {
                fontSize = sizeArr[0];
            }

            // 行高
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("行高");
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(-1); // 使用全部可用宽度
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
            // 设置复选框样式，确保勾选状态可见（参考工具属性面板的实现）
            ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.controlBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            
            // 修复：直接使用bold和italic变量，而不是创建新变量
            if (ImGui.checkbox("粗体##bold", bold)) {
                bold = !bold;
            }
            ImGui.sameLine(0, HORIZONTAL_SPACING);
            if (ImGui.checkbox("斜体##italic", italic)) {
                italic = !italic;
            }
            
            // 恢复样式
            ImGui.popStyleVar();
            ImGui.popStyleColor(5);

            // 对齐方式（水平 + 垂直）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("对齐");
            ImGui.tableNextColumn();
            int alignTableFlags = imgui.flag.ImGuiTableFlags.SizingStretchSame;
            if (ImGui.beginTable("##text_align_table", 2, alignTableFlags, 0, 0)) {
                ImGui.tableSetupColumn("h", imgui.flag.ImGuiTableColumnFlags.WidthStretch, 1.0f);
                ImGui.tableSetupColumn("v", imgui.flag.ImGuiTableColumnFlags.WidthStretch, 1.0f);
                ImGui.tableNextRow();

                ImGui.tableNextColumn();
                ImGui.setNextItemWidth(-1.0f);
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

                ImGui.tableNextColumn();
                ImGui.setNextItemWidth(-1.0f);
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
        pendingStylePreset = null;
    }

    private void applyPreset(TextInputPreset preset) {
        TextInputPreset p = preset != null ? preset : TextInputPreset.defaults();
        this.fontSize = clamp(p.fontSize(), 100.0f, 200.0f);
        this.bold = p.bold();
        this.italic = p.italic();
        this.hAlign = p.hAlign() != null ? p.hAlign() : TextAlignment.Horizontal.LEFT;
        this.vAlign = p.vAlign() != null ? p.vAlign() : TextAlignment.Vertical.TOP;
        this.lineHeight = clamp(p.lineHeight(), 0.5f, 3.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record TextInputResult(String text, float fontSize, boolean bold, boolean italic,
                                  TextAlignment.Horizontal hAlign, TextAlignment.Vertical vAlign, float lineHeight) {
    }

    public record TextInputPreset(float fontSize, boolean bold, boolean italic,
                                  TextAlignment.Horizontal hAlign, TextAlignment.Vertical vAlign, float lineHeight) {
        public static TextInputPreset defaults() {
            return new TextInputPreset(100.0f, false, false,
                    TextAlignment.Horizontal.LEFT, TextAlignment.Vertical.TOP,
                    TextStyle.DEFAULT_LINE_HEIGHT);
        }
    }
}


