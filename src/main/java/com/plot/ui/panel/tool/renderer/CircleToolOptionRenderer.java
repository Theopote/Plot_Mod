package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import net.minecraft.util.Identifier;

/**
 * 圆形工具选项渲染器
 */
public class CircleToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_TYPE = "type";

    private final int circleRadiusIconId;
    private final int circleTwoPointsIconId;
    private final int circleThreePointsIconId;
    
    // 定义模式常量，确保与CircleTool中的常量一致
    private static final String MODE_RADIUS = "radius";
    private static final String MODE_TWO_POINTS = "twoPoints";
    private static final String MODE_THREE_POINTS = "threePoints";
    
    private String circleToolType = MODE_RADIUS;  // 圆形工具类型默认为半径模式

    public CircleToolOptionRenderer() {
        super("circle");
        
        // 加载图标
        this.circleRadiusIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/circle_radius.png"));
        this.circleTwoPointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/circle_two_points.png"));
        this.circleThreePointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/circle_three_points.png"));
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("circle_options");

        try {
            // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式（影响其他模组）
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, BUTTON_CORNER_ROUNDING);
            
            // 绘制模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text(PlotI18n.tr("option.plot.draw_mode"));
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 使用固定的BUTTON_SIZE作为按钮大小，确保按钮是正方形
            float availWidth = ImGui.getContentRegionAvail().x;
            float totalButtonsWidth = BUTTON_SIZE * 3 + BUTTON_SPACING * 2;
            float startX = firstButtonX + (availWidth - totalButtonsWidth) / 2;  // 居中对齐按钮组
            
            // 一次性设置所有按钮的默认样式
            // 不使用pushStyleColor，每个按钮会使用默认的Button样式
            
            // 渲染三个模式按钮
            renderModeButton(circleRadiusIconId, MODE_RADIUS, PlotI18n.tr("mode.plot.center_radius"), startX);
            ImGui.sameLine(0, BUTTON_SPACING);
            renderModeButton(circleTwoPointsIconId, MODE_TWO_POINTS, PlotI18n.tr("mode.plot.two_points"), ImGui.getCursorPosX());
            ImGui.sameLine(0, BUTTON_SPACING);
            renderModeButton(circleThreePointsIconId, MODE_THREE_POINTS, PlotI18n.tr("mode.plot.three_points"), ImGui.getCursorPosX());

            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
            ImGui.popStyleVar();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染模式按钮
     * @param iconId 按钮图标ID
     * @param mode 对应的模式值
     * @param tooltip 悬停提示文本
     * @param xPos X坐标位置
     */
    private void renderModeButton(int iconId, String mode, String tooltip, float xPos) {
        ImGui.setCursorPosX(xPos);
        boolean isSelected = mode.equals(circleToolType);
        
        // 处理选中状态的按钮样式 - 从主题获取颜色
        if (isSelected) {
            // 使用主题管理器获取当前主题的按钮选中颜色
            com.plot.ui.theme.UITheme.ThemeColors theme = com.plot.ui.theme.ThemeManager.getInstance().getCurrentTheme();
            
            // 应用选中状态按钮颜色
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
        }
        
        ImGui.pushID(mode);
        if (com.plot.ui.component.UIUtils.imageButtonNoPadding(iconId, BUTTON_SIZE, BUTTON_SIZE) && !isSelected) {
            circleToolType = mode;
            updateToolConfig(CONFIG_KEY_TYPE, mode);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        ImGui.popID();
        
        // 只有选中状态的按钮需要弹出样式
        if (isSelected) {
            ImGui.popStyleColor(3);
        }
    }
    
    /**
     * 将ImVec4颜色转换为整型颜色值
     * @param color ImVec4颜色对象
     * @return 整型颜色值
     */
    private int getColorFromImVec4(ImVec4 color) {
        return ((int)(color.w * 255) << 24) |
               ((int)(color.z * 255) << 16) |
               ((int)(color.y * 255) << 8) |
               ((int)(color.x * 255));
    }

    @Override
    public void initialize() {
        // 默认使用圆心-半径模式
        circleToolType = MODE_RADIUS;
        
        // 在初始化时发送默认配置，确保工具状态与UI一致
        updateToolConfig(CONFIG_KEY_TYPE, MODE_RADIUS);
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(circleRadiusIconId);
        ImGuiUtils.deleteTexture(circleTwoPointsIconId);
        ImGuiUtils.deleteTexture(circleThreePointsIconId);
    }
} 