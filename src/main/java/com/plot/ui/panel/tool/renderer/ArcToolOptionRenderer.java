package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.tools.impl.drawing.ArcTool;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;

/**
 * 弧形工具选项渲染器
 */
public class ArcToolOptionRenderer extends AbstractToolOptionRenderer {
    // 移除重复的常量定义，直接使用 ArcTool 中的常量
    private final int startEndDirectionIconId;
    private final int throughPointIconId;
    private final int centerStartEndIconId;
    
    private String arcToolType = ArcTool.CONFIG_MODE_START_END_DIRECTION;  // 弧形工具类型：start_end_direction/through_point/center_start_end

    public ArcToolOptionRenderer() {
        super("arc");
        
        // 加载图标
        this.startEndDirectionIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/arc_start_end_direction.png"));
        this.throughPointIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/arc_through_point.png"));
        this.centerStartEndIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/arc_center_start_end.png"));
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("arc_options");
        
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 使用 pushStyleVar 临时设置，避免永久修改共享 ImGui 样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
            
            // 绘制模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("绘制模式");
            
            // 设置按钮颜色样式
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            
            // 设置边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 渲染三个模式按钮
            String[] modes = {ArcTool.CONFIG_MODE_START_END_DIRECTION, ArcTool.CONFIG_MODE_THROUGH_POINT, ArcTool.CONFIG_MODE_CENTER_START_END};
            int[] icons = {startEndDirectionIconId, throughPointIconId, centerStartEndIconId};
            String[] tooltips = {"起点终点圆弧点模式", "经过点模式", "中心起点终点模式"};
            
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    ImGui.sameLine();
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2) * i);
                }
                
                boolean isSelected = arcToolType.equals(modes[i]);
                
                // 为选中的按钮应用特殊样式
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                    ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                }
                
                ImGui.pushID("arc_mode_" + i);
                boolean clicked = ImGui.imageButton(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                
                if (clicked && !isSelected) {
                    arcToolType = modes[i];
                    updateToolConfig(ArcTool.CONFIG_KEY_MODE, modes[i]);
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
            
            ImGui.popStyleVar();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    @Override
    public void initialize() {
        // 设置默认模式
        arcToolType = ArcTool.CONFIG_MODE_START_END_DIRECTION;
        // 发送初始配置
        updateToolConfig(ArcTool.CONFIG_KEY_MODE, arcToolType);
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(startEndDirectionIconId);
        ImGuiUtils.deleteTexture(throughPointIconId);
        ImGuiUtils.deleteTexture(centerStartEndIconId);
    }
}