package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.tools.impl.modify.constants.FilletConstants;

import imgui.ImGui;
import imgui.type.ImFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 圆角工具选项渲染器
 * 
 * <p>提供圆角工具的配置选项，包括：</p>
 * <ul>
 *   <li>圆角半径设置（支持动态步长）</li>
 *   <li>使用说明和快捷键提示</li>
 *   <li>实时参数验证</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 7.0 - 优化版本，添加动态步长和实时验证
 */
public class FilletToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilletToolOptionRenderer.class);
    
    // 当前配置状态
    private final ImFloat radiusValue = new ImFloat((float)FilletConstants.DEFAULT_RADIUS);
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    public FilletToolOptionRenderer() {
        super(FilletConstants.TOOL_ID);
        
        this.resourcesInitialized = true;
        
        LOGGER.debug("FilletToolOptionRenderer 初始化完成");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("fillet_options");
        
        try {
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // === 半径设置 ===
            height += renderRadiusSettings(currentTheme);
            
            // 恢复原始样式
            ImGui.getStyle().setFrameRounding(originalRounding);
            
        } catch (Exception e) {
            LOGGER.error("渲染圆角工具选项时发生错误", e);
            height += renderErrorState();
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    /**
     * 渲染半径设置（圆角模式）
     */
    private float renderRadiusSettings(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("半径");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(120.0f);
        
        // 获取动态步长
        float step = getDynamicStep();
        
        // 半径滑块
        float[] radiusArray = {radiusValue.get()};
        if (ImGui.dragFloat("##radius", radiusArray, step, 
                           (float)FilletConstants.MIN_RADIUS, (float)FilletConstants.MAX_RADIUS, "%.1f")) {
            radiusValue.set(radiusArray[0]);
            updateToolConfig(FilletConstants.CONFIG_KEY_RADIUS, String.valueOf(radiusValue.get()));
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(String.format("设置圆角圆弧的半径大小\n当前步长: %.1f", step));
        }
        
        ImGui.popItemWidth();
        height += ImGui.getFrameHeight() + ImGui.getStyle().getItemSpacingY();
        
        return height;
    }
    
    /**
     * 获取动态步长
     */
    private float getDynamicStep() {
        float currentRadius = radiusValue.get();
        if (currentRadius >= FilletConstants.RADIUS_THRESHOLD_LARGE_STEP) {
            return (float)FilletConstants.KEYBOARD_STEP_LARGE;
        } else {
            return (float)FilletConstants.KEYBOARD_STEP_SMALL;
        }
    }
    
    /**
     * 渲染使用说明
     */
    private float renderUsageInstructions(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("使用说明");
        
        ImGui.tableNextColumn();
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + 200.0f);
        
        String instructions = """
                1. 选择第一个图形（直线、矩形、圆弧等）
                2. 选择第二个图形（直线、矩形、圆弧等）
                3. 使用+/-键或滚轮调整参数
                4. 按Enter确认圆角""";
            
        ImGui.textColored(theme.mutedText, instructions);
        ImGui.popTextWrapPos();
        
        height += ImGui.getFrameHeight() * 4 + ImGui.getStyle().getItemSpacingY();
        
        return height;
    }
    
    /**
     * 渲染快捷键提示
     */
    private float renderShortcutTips(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("快捷键");
        
        ImGui.tableNextColumn();
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + 200.0f);
        
        String shortcuts = """
                Enter: 确认圆角
                +/-: 调整参数
                滚轮: 调整参数
                ESC: 取消操作""";
            
        ImGui.textColored(theme.mutedText, shortcuts);
        ImGui.popTextWrapPos();
        
        height += ImGui.getFrameHeight() * 4 + ImGui.getStyle().getItemSpacingY();
        
        return height;
    }
    
    /**
     * 渲染错误状态
     */
    private float renderErrorState() {
        float height = 0;
        UITheme.ThemeColors theme = ThemeManager.getInstance().getCurrentTheme();
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.textColored(theme.errorText, "错误");
        
        ImGui.tableNextColumn();
        ImGui.textColored(theme.errorText, "渲染圆角工具选项时发生错误");
        
        height += ImGui.getFrameHeight() + ImGui.getStyle().getItemSpacingY();
        
        return height;
    }
    
    @Override
    public void initialize() {
        try {
            // 发送默认配置值
            updateToolConfig(FilletConstants.CONFIG_KEY_RADIUS, String.valueOf(radiusValue.get()));
            
            LOGGER.debug("圆角工具选项渲染器初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("初始化圆角工具选项渲染器失败", e);
        }
    }
    
    @Override
    public void cleanup() {
        try {
            // 清理资源
            if (resourcesInitialized) {
                // 这里可以清理纹理资源等
                resourcesInitialized = false;
            }
            
            LOGGER.debug("圆角工具选项渲染器清理完成");
            
        } catch (Exception e) {
            LOGGER.error("清理圆角工具选项渲染器失败", e);
        }
    }
} 