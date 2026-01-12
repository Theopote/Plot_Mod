package com.masterplanner.ui.component;

import com.masterplanner.ui.theme.TabTheme;
import imgui.*;
import imgui.flag.*;

public class CustomTabBar {
    private final TabTheme theme;
    private int selectedTabIndex = 0;
    private static final float TAB_SPACING = 2.0f;

    public CustomTabBar(TabTheme theme) {
        this.theme = theme;
    }
    
    public void render(String id, String[] tabLabels) {
        // 计算总宽度和每个标签的宽度
        float availableWidth = ImGui.getContentRegionAvailX();
        float tabWidth = (availableWidth - (TAB_SPACING * (tabLabels.length - 1))) / tabLabels.length;

        // 创建一个固定高度的容器
        float tabHeight = 30;
        ImGui.beginChild(id, availableWidth, tabHeight, false,
                ImGuiWindowFlags.NoScrollbar |
                ImGuiWindowFlags.NoScrollWithMouse);

        // 保存当前光标位置
        float startY = ImGui.getCursorPosY();
        
        for (int i = 0; i < tabLabels.length; i++) {
            // 设置每个标签的起始X位置
            float startX = i * (tabWidth + TAB_SPACING);
            ImGui.setCursorPos(startX, startY);

            String label = tabLabels[i];
            CustomTabItem tab = new CustomTabItem(label, theme);
            tab.render(startX, tabHeight, tabWidth, selectedTabIndex == i);

            // 检查点击
            if (ImGui.isItemClicked()) {
                selectedTabIndex = i;
            }
        }

        ImGui.endChild();
    }

    public int getSelectedTabIndex() {
        return selectedTabIndex;
    }
}

class CustomTabItem {
    private final String label;
    private final TabTheme theme;

    public CustomTabItem(String label, TabTheme theme) {
        this.label = label;
        this.theme = theme;
    }

    public void render(float startX, float height, float width, boolean isSelected) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float startY = ImGui.getCursorPosY();
        float windowX = ImGui.getWindowPosX();
        float windowY = ImGui.getWindowPosY();

        // 计算标签的颜色
        int bgColor = isSelected ? theme.getTabActiveColor() : 
                     (ImGui.isItemHovered() ? theme.getTabHoveredColor() : theme.getTabNormalColor());

        // 绘制标签背景
        float rounding = 4.0f;
        drawList.addRectFilled(
                windowX + startX,
                windowY + startY,
                windowX + startX + width - 1, // 减1避免重叠
                windowY + startY + height,
                bgColor,
                rounding,
                ImDrawFlags.RoundCornersTop
        );

        // 如果选中，绘制底部边框
        if (isSelected) {
            float borderY = windowY + startY + height - 1;
            drawList.addLine(
                    windowX + startX,
                    borderY,
                    windowX + startX + width - 1,
                    borderY,
                    theme.getBorderColor(),
                    2.0f
            );
        }

        // 计算并绘制居中文字
        float textWidth = ImGui.calcTextSize(label).x;
        float textX = startX + (width - textWidth) * 0.5f;
        float textY = startY + (height - ImGui.getTextLineHeight()) * 0.5f;

        drawList.addText(
                windowX + textX,
                windowY + textY,
                theme.getTextColor(),
                label
        );

        // 创建不可见按钮
        ImGui.setCursorPos(startX, startY);
        ImGui.invisibleButton("##" + label, width - 1, height);
    }
} 