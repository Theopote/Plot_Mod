package com.plot.ui.dialog;

import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * 对话框统一样式管理器
 * 为所有对话框提供一致的样式配置和风格应用
 */
public class DialogStyleManager {
    
    // ====== 布局常量 ======
    /** 控件之间的垂直间距 */
    public static final float ITEM_SPACING = 4.0f;
    
    /** 控件之间的水平间距 */
    public static final float ITEM_SPACING_H = 4.0f;
    
    /** 框架内边距（控制输入框、按钮等的内部填充及高度） */
    public static final float FRAME_PADDING = 2.0f;

    /** 对话框内容到边界的统一内边距 */
    public static final float PANEL_PADDING = ITEM_SPACING * 2.0f;
    
    /** 内容区域宽度 */
    public static final float CONTENT_WIDTH = 300.0f;
    
    /** 标签宽度 */
    public static final float LABEL_WIDTH = 60.0f;
    
    /** 控件宽度（内容宽度 - 标签宽度 - 间距） */
    public static final float CONTROL_WIDTH = CONTENT_WIDTH - LABEL_WIDTH - 2 * ITEM_SPACING;
    
    /** 输入框高度 - 所有输入框、按钮、标签、树结点都使用此高度 */
    public static final float CONTROL_HEIGHT = 0.0f; // 0表示使用ImGui默认计算值
    
    /** 分隔线间距 */
    public static final float SEPARATOR_SPACING = 4.0f;
    
    // ====== 窗口配置 ======
    /** 标准对话框宽度 */
    public static final float DIALOG_WIDTH = 340.0f;
    
    /** 标准对话框高度 */
    public static final float DIALOG_HEIGHT = 300.0f;

    /** 默认按钮组间距 */
    public static final float BUTTON_SPACING = ITEM_SPACING_H;

    /** 右上角关闭按钮尺寸 */
    public static final float CLOSE_BUTTON_SIZE = 18.0f;
    
    // ====== 样式作用域类 ======
    public static class DialogStyleScope {
        private final int colorCount;
        private final int varCount;
        
        public DialogStyleScope(int colorCount, int varCount) {
            this.colorCount = colorCount;
            this.varCount = varCount;
        }
        
        public int colorCount() {
            return colorCount;
        }
        
        public int varCount() {
            return varCount;
        }
    }
    
    /**
     * 应用统一的对话框样式
     * @return 样式作用域，用于之后恢复样式
     */
    public static DialogStyleScope applyDialogStyle() {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        int colorCount = 0;
        int varCount = 0;
        
        // 推送颜色样式
        ImGui.pushStyleColor(ImGuiCol.WindowBg, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.TitleBg, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.PopupBg, theme.panelBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.Border, theme.border);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, theme.sliderGrab);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, theme.sliderGrabActive);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.Header, theme.tabNormal);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, theme.tabHovered);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, theme.tabActive);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.Separator, theme.separatorColor);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.SeparatorHovered, theme.separatorColor);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.SeparatorActive, theme.separatorColor);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.Text, theme.text);
        colorCount++;
        ImGui.pushStyleColor(ImGuiCol.TextDisabled, theme.mutedText);
        colorCount++;
        
        // 推送变量样式
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 0.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, 0.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, 0.0f);
        varCount++;
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 0.0f);
        varCount++;
        // 统一控件间距：输入框、按钮、标签、树结点都使用相同的垂直间距
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, ITEM_SPACING_H, ITEM_SPACING);
        varCount++;
        // 统一框架内边距：控制输入框、按钮等的高度
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, FRAME_PADDING, FRAME_PADDING);
        varCount++;
        
        return new DialogStyleScope(colorCount, varCount);
    }

    /**
     * 获取当前窗口内容区宽度。
     */
    public static float getContentWidth() {
        return ImGui.getWindowContentRegionMaxX() - ImGui.getWindowContentRegionMinX();
    }

    /**
     * 获取当前窗口内容区起始 X。
     */
    public static float getContentStartX() {
        return ImGui.getWindowContentRegionMinX();
    }

    /**
     * 根据标签宽度计算控件宽度，统一保留右侧呼吸空间。
     */
    public static float getControlWidth(float labelWidth) {
        return Math.max(0.0f, getContentWidth() - labelWidth - 2 * ITEM_SPACING_H);
    }

    /**
     * 计算双按钮布局时单个按钮宽度。
     */
    public static float getTwoButtonWidth(float availableWidth) {
        return Math.max(0.0f, (availableWidth - BUTTON_SPACING) / 2.0f);
    }

    /**
     * 在内容区内按给定总宽度居中设置光标 X。
     */
    public static void centerByWidth(float totalWidth) {
        float startX = getContentStartX() + Math.max(0.0f, (getContentWidth() - totalWidth) * 0.5f);
        ImGui.setCursorPosX(startX);
    }

    /**
     * 将单按钮行居中到当前窗口内容区。
     */
    public static void centerSingleButton(float buttonWidth) {
        centerByWidth(buttonWidth);
    }

    /**
     * 将双按钮行居中到当前窗口内容区。
     */
    public static void centerTwoButtons(float buttonWidth) {
        float totalWidth = buttonWidth * 2.0f + BUTTON_SPACING;
        float startX = getContentStartX() + Math.max(0.0f, (getContentWidth() - totalWidth) * 0.5f);
        ImGui.setCursorPosX(startX);
    }

    /**
     * 在对话框标题栏右端渲染统一的关闭按钮（×）。
     * 使用 DrawList + pushClipRect(false) 绘制，绕过内容区域裁剪限制，确保按钮
     * 显示在标题栏内而不被 ImGui 的内容区 clip rect 裁掉。
     *
     * @param idSuffix 按钮ID后缀（不使用，仅保留签名兼容性）
     * @return 是否点击关闭
     */
    public static boolean renderTopRightCloseButton(String idSuffix) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();

        float windowPosX  = ImGui.getWindowPosX();
        float windowPosY  = ImGui.getWindowPosY();
        float windowWidth = ImGui.getWindowWidth();
        float windowHeight = ImGui.getWindowHeight();

        // 标题栏高度 = 字体高度 + 框架内边距 * 2（ImGui 标准计算）
        float titleBarHeight = ImGui.getFrameHeight();

        // 按钮区域：与标题栏右端对齐，且按钮高度等于标题栏高度
        float btnX1 = windowPosX + windowWidth - titleBarHeight;
        float btnY1 = windowPosY;
        float btnX2 = windowPosX + windowWidth;
        float btnY2 = windowPosY + titleBarHeight;

        // 手动检测悬停（clip=false：不受当前内容区 clip rect 限制）
        boolean hovered = ImGui.isMouseHoveringRect(btnX1, btnY1, btnX2, btnY2, false);
        boolean clicked  = hovered && ImGui.isMouseClicked(0);

        // 用 DrawList 直接绘制（intersect=false 使 clip rect 覆盖整个窗口含标题栏）
        var drawList = ImGui.getWindowDrawList();
        drawList.pushClipRect(windowPosX, windowPosY,
                windowPosX + windowWidth, windowPosY + windowHeight, false);

        int bgColor = hovered ? theme.buttonHovered : theme.buttonNormal;
        drawList.addRectFilled(btnX1, btnY1, btnX2, btnY2, bgColor, 0.0f);

        // "×" 使用实际文本尺寸做几何中心对齐
        String closeText = "×";
        var textSize = ImGui.calcTextSize(closeText);
        float textX = btnX1 + (titleBarHeight - textSize.x) * 0.5f;
        float textY = btnY1 + (titleBarHeight - textSize.y) * 0.5f;
        drawList.addText(textX, textY, 0xFFFFFFFF, closeText);

        drawList.popClipRect();

        return clicked;
    }

    /**
     * 恢复对话框样式
     * @param scope 由applyDialogStyle()返回的样式作用域
     */
    public static void popDialogStyle(DialogStyleScope scope) {
        if (scope == null) {
            return;
        }
        ImGui.popStyleVar(scope.varCount());
        ImGui.popStyleColor(scope.colorCount());
    }
}
