package com.plot.ui.component;

import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImString;
import com.plot.ui.theme.UITheme;
import net.minecraft.util.Identifier;
import com.plot.utils.ImGuiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.plot.ui.theme.ThemeManager;

/**
 * UI 工具类，提供通用的 UI 相关方法
 */
public class UIUtils {
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_PADDING = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger(UIUtils.class);
    


    /**
     * 创建分隔符
     */
    public static void separator() {
        ImGui.dummy(BUTTON_PADDING, 0);
        ImGui.separator();
        ImGui.dummy(BUTTON_PADDING, 0);
    }

    /**
     * 创建带标签的图标按钮
     * @param icon 图标
     * @param label 标签文本
     * @param selected 是否选中状态
     * @return 是否点击
     */
    public static boolean iconButton(String icon, String label, boolean selected) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
        }

        float width = ImGui.getWindowContentRegionMaxX() - ImGui.getCursorPosX() - 60;
        boolean clicked = ImGui.button(icon + " " + label, width, BUTTON_HEIGHT);

        if (selected) {
            ImGui.popStyleColor(3);
        }

        return clicked;
    }

    /**
     * 创建可选择的卡片
     * @param label 卡片标签
     * @param selected 是否选中
     * @param width 卡片宽度
     * @param height 卡片高度
     * @return 是否点击
     */
    public static boolean selectableCard(String label, boolean selected, float width, float height) {
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        ImGui.pushStyleColor(ImGuiCol.ChildBg, selected ? theme.buttonSelected : theme.panelBackground);
        ImGui.beginChild("##" + label, width, height, true);
        
        // 居中显示文本
        float textWidth = ImGui.calcTextSize(label).x;
        float textX = (width - textWidth) * 0.5f;
        float textY = (height - ImGui.getTextLineHeight()) * 0.5f;
        ImGui.setCursorPos(textX, textY);
        ImGui.text(label);
        
        boolean clicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left);
        
        ImGui.endChild();
        ImGui.popStyleColor();
        
        return clicked;
    }

    /**
     * 创建图片按钮（基础方法）
     * @param icon 图标
     * @param tooltip 提示文本
     * @param width 按钮宽度
     * @param height 按钮高度
     * @param isSelected 是否选中状态
     * @param rounded 是否使用圆角
     * @return 是否点击
     */
    public static boolean imageButton(Identifier icon, String tooltip, float width, float height, boolean isSelected, boolean rounded) {
        boolean clicked = false;
        
        try {
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 根据按钮状态设置边框和背景颜色
            int borderColor;
            int buttonColor;
            int hoveredColor;
            int activeColor;
            
            if (isSelected) {
                borderColor = currentTheme.buttonActiveBorder;
                buttonColor = currentTheme.buttonSelected;
                hoveredColor = currentTheme.buttonSelectedHovered;
                activeColor = currentTheme.buttonSelectedActive;
            } else {
                borderColor = currentTheme.buttonBorder;
                buttonColor = currentTheme.buttonNormal;
                hoveredColor = currentTheme.buttonHovered;
                activeColor = currentTheme.buttonActive;
            }
            
            // 设置按钮样式
            ImGui.pushStyleColor(ImGuiCol.Border, borderColor);
            ImGui.pushStyleColor(ImGuiCol.Button, buttonColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
            
            // 设置提示文字样式
            ImGui.pushStyleColor(ImGuiCol.PopupBg, currentTheme.tooltipBackground);
            ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.tooltipText);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            
            // 设置按钮样式
            // PNG 图标按钮不保留内边距，保证图标铺满按钮可用区域
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, rounded ? currentTheme.toolbarControlRounding : 0);
            
            // 获取纹理ID并渲染按钮
            int textureId = ImGuiUtils.getTextureId(icon);
            if (ImGui.imageButton(textureId, width, height, 0, 0, 1, 1)) {
                clicked = true;
            }
            
            // 如果鼠标悬停，显示提示
            if (ImGui.isItemHovered() && tooltip != null && !tooltip.isEmpty()) {
                ImGui.setTooltip(tooltip);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error rendering image button for {}: {}", icon, e.getMessage());
        } finally {
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(7);
        }
        
        return clicked;
    }

    /**
     * 创建图片按钮（方形按钮）
     * @param icon 图标
     * @param tooltip 提示文本
     * @param size 按钮大小
     * @param isSelected 是否选中状态
     * @return 是否点击
     */
    public static boolean imageButton(Identifier icon, String tooltip, float size, boolean isSelected) {
        return imageButton(icon, tooltip, size, size, isSelected, true);  // 默认使用圆角
    }

    /**
     * 创建无内边距图片按钮（用于工具选项面板中的PNG按钮）
     * @param textureId 纹理ID
     * @param width 按钮宽度
     * @param height 按钮高度
     * @return 是否点击
     */
    public static boolean imageButtonNoPadding(int textureId, float width, float height) {
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
        try {
            return ImGui.imageButton(textureId, width, height);
        } finally {
            ImGui.popStyleVar();
        }
    }

    /**
     * 渲染带图标的输入框
     * @param id 输入框ID
     * @param icon 图标字符串
     * @param hint 提示文字
     * @param text ImString对象，用于存储输入的文本
     * @return 如果文本发生改变则返回true
     */
    public static boolean iconInput(String id, String icon, String hint, ImString text) {
        boolean changed;
        float iconWidth = ImGui.calcTextSize(icon).x;
        float spacing = 4.0f;  // 图标和输入框之间的间距
        
        // 开始一个组，这样图标和输入框会在同一行
        ImGui.beginGroup();
        
        // 渲染图标
        ImGui.textDisabled(icon);
        
        // 将输入框放在图标旁边
        ImGui.sameLine(0, spacing);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - iconWidth - spacing);
        
        // 渲染输入框
        changed = ImGui.inputText(id, text);
        
        // 如果输入框为空，显示提示文字
        if (text.get().isEmpty() && !ImGui.isItemActive()) {
            float cursorPosY = ImGui.getCursorPosY();
            ImGui.setCursorPos(ImGui.getCursorPosX() - ImGui.getContentRegionAvailX() + iconWidth + spacing + 4, cursorPosY - ImGui.getFrameHeight());
            ImGui.textDisabled(hint);
        }
        
        ImGui.endGroup();
        
        return changed;
    }
}
