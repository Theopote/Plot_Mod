package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;

/**
 * 椭圆工具选项渲染器
 */
public class EllipseToolOptionRenderer extends AbstractToolOptionRenderer {
    // 配置键常量，与EllipseTool中的常量保持一致
    private static final String CONFIG_KEY_TYPE = "mode";
    private static final String CONFIG_VALUE_THREE_POINTS_AXIS = "three_points_axis";
    private static final String CONFIG_VALUE_THREE_POINTS_CENTER = "three_points_center";
    private static final String CONFIG_VALUE_TWO_POINTS = "two_points";

    private final int ellipseThreePointsAxisIconId;
    private final int ellipseThreePointsCenterIconId;
    private final int ellipseTwoPointsIconId;
    
    private String ellipseToolType = CONFIG_VALUE_THREE_POINTS_AXIS;  // 椭圆工具类型：three_points_axis/three_points_center/two_points

    public EllipseToolOptionRenderer() {
        super("ellipse");
        
        // 加载图标
        this.ellipseThreePointsAxisIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/ellipse_three_points_axis.png"));
        this.ellipseThreePointsCenterIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/ellipse_three_points_center.png"));
        this.ellipseTwoPointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/ellipse_two_points.png"));
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("ellipse_options");
        
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式（影响其他模组）
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
            String[] modes = {CONFIG_VALUE_THREE_POINTS_AXIS, CONFIG_VALUE_THREE_POINTS_CENTER, CONFIG_VALUE_TWO_POINTS};
            int[] icons = {ellipseThreePointsAxisIconId, ellipseThreePointsCenterIconId, ellipseTwoPointsIconId};
            String[] tooltips = {"三点轴模式", "三点中心模式", "两点模式"};
            
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    ImGui.sameLine(0, BUTTON_SPACING);
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING) * i);
                }
                
                boolean isSelected = ellipseToolType.equals(modes[i]);
                
                // 为选中的按钮应用特殊样式
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                    ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                }
                
                ImGui.pushID("ellipse_mode_" + i);
                boolean clicked = com.plot.ui.component.UIUtils.imageButtonNoPadding(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                
                if (clicked && !isSelected) {
                    ellipseToolType = modes[i];
                    updateToolConfig(CONFIG_KEY_TYPE, modes[i]);
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
            ImGui.popStyleVar();  // FrameRounding

            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    @Override
    public void initialize() {
        ellipseToolType = CONFIG_VALUE_THREE_POINTS_AXIS; // 默认使用三点轴模式
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(ellipseThreePointsAxisIconId);
        ImGuiUtils.deleteTexture(ellipseThreePointsCenterIconId);
        ImGuiUtils.deleteTexture(ellipseTwoPointsIconId);
    }
} 