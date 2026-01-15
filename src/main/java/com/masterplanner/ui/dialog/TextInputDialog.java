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
    
    // 统一的布局常量
    private static final float WINDOW_PADDING = 8.0f; // 窗口内边距（左右统一）
    private static final float VERTICAL_SPACING = 8.0f; // 垂直间距（所有控件上下间距统一）
    private static final float HORIZONTAL_SPACING = 8.0f; // 水平间距（控件之间的水平间距）
    private static final float LABEL_COLUMN_WIDTH = 80.0f; // 标签列宽度
    private static final float BUTTON_WIDTH = 100.0f; // 按钮宽度
    private static final float BUTTON_SPACING = 8.0f; // 按钮之间的间距

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
        float width = 400.0f;
        // 计算窗口高度：标题 + 输入框 + 样式标题 + 表格(4行) + 按钮 + 间距
        // 使用估算值，因为FramePadding会在窗口内设置
        float textLineHeight = ImGui.getTextLineHeight();
        float framePadding = 4.0f; // 与工具属性面板一致
        float estimatedFrameHeight = textLineHeight + framePadding * 2; // 估算控件高度
        
        float titleHeight = textLineHeight;
        float inputHeight = 80.0f; // 输入框高度（约为原来的1/3：260/3 ≈ 87）
        float styleTitleHeight = textLineHeight;
        float tableRowHeight = estimatedFrameHeight; // 每行高度
        float tableHeight = tableRowHeight * 4; // 4行：字体大小、行高、字形、对齐
        float buttonHeight = estimatedFrameHeight;
        float totalContentHeight = titleHeight + VERTICAL_SPACING + inputHeight + VERTICAL_SPACING 
                + styleTitleHeight + VERTICAL_SPACING + tableHeight + VERTICAL_SPACING + buttonHeight;
        float height = totalContentHeight + WINDOW_PADDING * 2; // 加上上下边距，不需要滚动条
        
        // 在窗口开始之前设置WindowPadding，确保边距正确应用
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, WINDOW_PADDING, WINDOW_PADDING);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Appearing);
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        // 移除AlwaysAutoResize标志，避免窗口自动变宽
        // 使用固定宽度和高度，不需要滚动条
        int windowFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.NoScrollbar;

        if (ImGui.beginPopupModal(DIALOG_TITLE, windowFlags)) {
            // 主题样式
            var theme = ThemeManager.getInstance().getCurrentTheme();
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0, VERTICAL_SPACING); // 统一垂直间距
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 4.0f); // 与工具属性面板一致，增加控件高度
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
                // 计算内容区域宽度（统一右边界）
                float contentWidth = width - WINDOW_PADDING * 2;
                
                // 标题说明（统一左对齐，使用统一的左边距）
                ImGui.text("请输入文字内容（可多行）");

                // 文本输入区域（宽度为内容宽度的一半）
                float inputWidth = contentWidth; // 输入框宽度为内容宽度的一半
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
                renderStyleSection(contentWidth);


                // 按钮区域（右边界与内容区域对齐）
                // 按钮应该从内容区域的右边界开始向左排列
                float totalButtonsWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
                // 计算按钮起始X位置：窗口左边界 + 内容宽度 - 按钮总宽度
                float buttonStartX = WINDOW_PADDING + contentWidth - totalButtonsWidth;
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
                ImGui.popStyleColor(10);
                ImGui.popStyleVar(4); // FrameRounding, FrameBorderSize, ItemSpacing, FramePadding
                ImGui.endPopup();
            }
        }
        // 弹出在窗口开始之前设置的WindowPadding
        ImGui.popStyleVar(1);
    }

    private void renderStyleSection(float contentWidth) {
        // 使用统一的布局常量
        // contentWidth 已经是减去左右边距后的宽度
        float labelColWidth = LABEL_COLUMN_WIDTH;
        // 计算值列宽度：内容宽度 - 标签列宽度 - 表格内部间距
        // 表格内部有垂直边框，需要减去边框宽度
        float tableBorderWidth = 1.0f; // 表格垂直边框宽度
        float valueColWidth = contentWidth - labelColWidth - tableBorderWidth;

        // 使用固定列宽，避免表格自动扩展
        // 设置表格外层尺寸为contentWidth，确保表格右边界与内容区域对齐
        int tableFlags = imgui.flag.ImGuiTableFlags.BordersInnerV | imgui.flag.ImGuiTableFlags.SizingFixedFit;
        if (ImGui.beginTable("##text_style_table", 2, tableFlags, contentWidth, 0)) {
            ImGui.tableSetupColumn("label", imgui.flag.ImGuiTableColumnFlags.WidthFixed, labelColWidth);
            ImGui.tableSetupColumn("value", imgui.flag.ImGuiTableColumnFlags.WidthFixed, valueColWidth);

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
            // 修复：直接使用bold和italic变量，而不是创建新变量
            if (ImGui.checkbox("粗体##bold", bold)) {
                bold = !bold;
            }
            ImGui.sameLine(0, HORIZONTAL_SPACING);
            if (ImGui.checkbox("斜体##italic", italic)) {
                italic = !italic;
            }

            // 对齐方式（水平 + 垂直）
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("对齐");
            ImGui.tableNextColumn();
            // 确保两个下拉框宽度完全一致，并且总宽度（包括间距）等于滑动条宽度
            // 为下拉箭头预留空间（每个下拉框的箭头大约需要15像素）
            float arrowSpace = 15.0f; // 每个下拉箭头所需的空间
            // 计算：总宽度 - 间距 - 两个箭头空间，然后平分
            // 这样：comboWidth1 + arrowSpace + spacing + comboWidth2 + arrowSpace = valueColWidth
            float comboWidth = (valueColWidth - HORIZONTAL_SPACING - arrowSpace * 2) / 2.0f;
            // 第一个下拉框：水平对齐
            ImGui.setNextItemWidth(comboWidth);
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
            // 第二个下拉框：垂直对齐，使用相同的宽度和样式
            // 两个下拉框之间有明确的间距
            ImGui.sameLine(0, HORIZONTAL_SPACING);
            ImGui.setNextItemWidth(comboWidth); // 确保宽度完全一致
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

    public record TextInputResult(String text, float fontSize, boolean bold, boolean italic,
                                  TextAlignment.Horizontal hAlign, TextAlignment.Vertical vAlign, float lineHeight) {
    }
}


