package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.utils.ImGuiUtils;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.tools.impl.drawing.RectangleTool;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.tool.BaseTool;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 矩形工具选项渲染器
 */
public class RectangleToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RectangleToolOptionRenderer.class);
    private static final String CONFIG_KEY_TYPE = "type";

    private final int rectangleTwoPointsIconId;
    private final int rectangleThreePointsIconId;
    private final int rectangleCenterIconId;
    private final int rectangleRoundedIconId;
    
    private String rectangleToolType = "two_points";  // 矩形工具类型：two_points/three_points/center/rounded

    public RectangleToolOptionRenderer() {
        super("rectangle");
        
        // 加载图标
        this.rectangleTwoPointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/rectangle_two_points.png"));
        this.rectangleThreePointsIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/rectangle_three_points.png"));
        this.rectangleCenterIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/rectangle_center.png"));
        this.rectangleRoundedIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/rectangle_rounded.png"));
    }

    @Override
    public float render() {
        // 在每次渲染时同步工具状态
        syncFromTool();
        
        float height = 0;
        ImGui.pushID("rectangle_options");
        
        try {
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 保存当前的圆角样式
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // 绘制模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("绘制模式");
            
            // 设置按钮的圆角和样式，使用工具栏控件圆角
            ImGui.getStyle().setFrameRounding(currentTheme.toolbarControlRounding);
            
            // 设置按钮颜色样式
            ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            
            // 设置边框样式
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 渲染四个模式按钮
            String[] modes = {"two_points", "three_points", "center_point", "rounded"}; // 修正ID
            int[] icons = {rectangleTwoPointsIconId, rectangleThreePointsIconId, 
                          rectangleCenterIconId, rectangleRoundedIconId};
            String[] tooltips = {"两点模式", "三点模式", "中心点模式", "圆角模式"};
            
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) {
                    ImGui.sameLine();
                    ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2) * i);
                }
                
                boolean isSelected = rectangleToolType.equals(modes[i]);
                
                // 为选中的按钮应用特殊样式
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                    ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
                }
                
                ImGui.pushID("rectangle_mode_" + i);
                boolean clicked = ImGui.imageButton(icons[i], BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                
                if (clicked && !isSelected) {
                    rectangleToolType = modes[i];
                    updateToolConfig(CONFIG_KEY_TYPE, modes[i]);
                    LOGGER.debug("矩形工具模式已切换为: {}", modes[i]);
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
            ImGui.getStyle().setFrameRounding(originalRounding);
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    /**
     * 从当前工具同步状态
     */
    private void syncFromTool() {
        try {
            RectangleTool currentTool = getCurrentRectangleTool();
            if (currentTool != null) {
                RectangleTool.RectangleMode toolMode = currentTool.getCurrentMode();
                String toolModeId = toolMode.id;
                
                // 只有当UI状态与工具状态不一致时才更新
                if (!rectangleToolType.equals(toolModeId)) {
                    rectangleToolType = toolModeId;
                    LOGGER.debug("RectangleToolOptionRenderer: 从工具同步模式状态: {} -> {}", 
                               rectangleToolType, toolModeId);
                }
            } else {
                LOGGER.debug("RectangleToolOptionRenderer: 未找到当前矩形工具，使用默认状态");
            }
        } catch (Exception e) {
            LOGGER.warn("RectangleToolOptionRenderer: 同步工具状态失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前矩形工具实例
     */
    private RectangleTool getCurrentRectangleTool() {
        try {
            AppState appState = AppState.getInstance();
            BaseTool currentTool = appState.getCurrentTool();
            
            if (currentTool instanceof RectangleTool) {
                return (RectangleTool) currentTool;
            } else {
                LOGGER.debug("当前工具不是RectangleTool: {}", 
                    currentTool != null ? currentTool.getClass().getSimpleName() : "null");
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("获取当前矩形工具失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void initialize() {
        LOGGER.debug("RectangleToolOptionRenderer: 初始化开始");
        
        // 从当前工具同步状态，而不是重置为默认值
        syncFromTool();
        
        // 如果同步失败，使用默认值作为回退
        if (rectangleToolType == null || rectangleToolType.isEmpty()) {
            rectangleToolType = "two_points";
            LOGGER.debug("RectangleToolOptionRenderer: 使用默认模式: {}", rectangleToolType);
        }
        
        LOGGER.debug("RectangleToolOptionRenderer: 初始化完成，当前模式: {}", rectangleToolType);
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(rectangleTwoPointsIconId);
        ImGuiUtils.deleteTexture(rectangleThreePointsIconId);
        ImGuiUtils.deleteTexture(rectangleCenterIconId);
        ImGuiUtils.deleteTexture(rectangleRoundedIconId);
        
        LOGGER.debug("RectangleToolOptionRenderer: 资源清理完成");
    }
} 