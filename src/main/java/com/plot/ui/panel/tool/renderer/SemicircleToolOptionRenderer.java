package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;

/**
 * 半圆工具选项渲染器
 */
public class SemicircleToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_MODE = "mode";
    public static final String MODE_TWO_POINTS = "two_points";
    public static final String MODE_THREE_POINTS = "three_points";

    private final int semicircleTwoPointsIconId;
    private final int semicircleThreePointsIconId;
    
    private String semicircleToolMode = MODE_TWO_POINTS;  // 半圆工具模式：two_points/three_points

    public SemicircleToolOptionRenderer() {
        super("semicircle");
        
        // 加载图标
        this.semicircleTwoPointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/semicircle_two_points.png"));
        this.semicircleThreePointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/semicircle_three_points.png"));
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("semicircle_options");
        
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 保存当前的圆角样式
            // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
            
            // 绘制模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("绘制模式");
            
            // 设置按钮的圆角和样式，使用工具栏控件圆角
            // 已通过 pushStyleVar 在开头设置
            
            // 设置按钮颜色样式
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            
            // 设置边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 渲染两个模式按钮
            String[] modes = {MODE_TWO_POINTS, MODE_THREE_POINTS};
            int[] icons = {semicircleTwoPointsIconId, semicircleThreePointsIconId};
            String[] tooltips = {"两点模式", "三点模式"};
            
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    ImGui.sameLine();
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2) * i);
                }
                
                boolean isSelected = semicircleToolMode.equals(modes[i]);
                
                // 为选中的按钮应用特殊样式
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                    ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                }
                
                ImGui.pushID("semicircle_mode_" + i);
                boolean clicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                
                if (clicked && !isSelected) {
                    semicircleToolMode = modes[i];
                    updateToolConfig(CONFIG_KEY_MODE, modes[i]);
                }
                
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(tooltips[i]);
                }
                
                // 恢复选中按钮的样式
                if (isSelected) {
                    ImGui.popStyleColor(4);
                }
            }
            
            // 恢复样式
            ImGui.popStyleVar();
            ImGui.popStyleColor(4);

            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
            // 恢复原始的圆角设置
            ImGui.popStyleVar();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    @Override
    public void initialize() {
        semicircleToolMode = MODE_TWO_POINTS;
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(semicircleTwoPointsIconId);
        ImGuiUtils.deleteTexture(semicircleThreePointsIconId);
    }
} 