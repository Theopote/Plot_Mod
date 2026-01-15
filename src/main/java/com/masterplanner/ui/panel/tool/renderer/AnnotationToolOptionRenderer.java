package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.AnnotationTool;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 标注工具选项渲染器
 * 提供模式选择：距离、角度、半径、面积
 * 参考阵列工具的工具选项面板样式
 */
public class AnnotationToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationToolOptionRenderer.class);
    private static final String CONFIG_KEY_MODE = "mode";
    
    // 图标ID
    private final int distanceIconId;
    private final int angleIconId;
    private final int radiusIconId;
    private final int areaIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    private AnnotationTool.AnnotationMode currentMode = AnnotationTool.AnnotationMode.DISTANCE;
    
    public AnnotationToolOptionRenderer() {
        super("annotation");
        
        // 加载图标
        // 图标路径：textures/gui/tooloptionspanel/annotation_distance.png
        //          textures/gui/tooloptionspanel/annotation_angle.png
        //          textures/gui/tooloptionspanel/annotation_radius.png
        //          textures/gui/tooloptionspanel/annotation_area.png
        this.distanceIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/annotation_distance.png"));
        this.angleIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/annotation_angle.png"));
        this.radiusIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/annotation_radius.png"));
        this.areaIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/annotation_area.png"));
        this.resourcesInitialized = true;
        
        // 添加图标加载调试信息
        LOGGER.debug("标注工具图标加载完成 - 距离: {}, 角度: {}, 半径: {}, 面积: {}", 
            distanceIconId, angleIconId, radiusIconId, areaIconId);
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("annotation_options");
        
        try {
            // 模式选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("模式");
            
            ImGui.tableNextColumn();
            
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // 检查图标是否加载成功
            boolean iconsLoaded = distanceIconId > 0 && angleIconId > 0 && radiusIconId > 0 && areaIconId > 0;
            LOGGER.debug("标注工具图标加载状态: {}", iconsLoaded);
            
            if (iconsLoaded) {
                // 使用图标按钮
                // 距离模式按钮
                boolean isDistanceSelected = currentMode == AnnotationTool.AnnotationMode.DISTANCE;
                pushButtonStyle(currentTheme, isDistanceSelected);
                ImGui.pushID("annotation_distance");
                boolean distanceClicked = ImGui.imageButton(distanceIconId, BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("距离标注：以方块为单位");
                }
                ImGui.popStyleColor(4);
                
                // 处理按钮点击事件
                if (distanceClicked && !isDistanceSelected) {
                    currentMode = AnnotationTool.AnnotationMode.DISTANCE;
                    updateToolConfig(CONFIG_KEY_MODE, "distance");
                }
                
                ImGui.sameLine();
                
                // 角度模式按钮
                boolean isAngleSelected = currentMode == AnnotationTool.AnnotationMode.ANGLE;
                pushButtonStyle(currentTheme, isAngleSelected);
                ImGui.pushID("annotation_angle");
                boolean angleClicked = ImGui.imageButton(angleIconId, BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("角度标注：两条线段之间的家教");
                }
                ImGui.popStyleColor(4);
                
                // 处理按钮点击事件
                if (angleClicked && !isAngleSelected) {
                    currentMode = AnnotationTool.AnnotationMode.ANGLE;
                    updateToolConfig(CONFIG_KEY_MODE, "angle");
                }
                
                ImGui.sameLine();
                
                // 半径模式按钮
                boolean isRadiusSelected = currentMode == AnnotationTool.AnnotationMode.RADIUS;
                pushButtonStyle(currentTheme, isRadiusSelected);
                ImGui.pushID("annotation_radius");
                boolean radiusClicked = ImGui.imageButton(radiusIconId, BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("半径标注：标注圆形、半圆、圆弧的半径");
                }
                ImGui.popStyleColor(4);
                
                // 处理按钮点击事件
                if (radiusClicked && !isRadiusSelected) {
                    currentMode = AnnotationTool.AnnotationMode.RADIUS;
                    updateToolConfig(CONFIG_KEY_MODE, "radius");
                }
                
                ImGui.sameLine();
                
                // 面积模式按钮
                boolean isAreaSelected = currentMode == AnnotationTool.AnnotationMode.AREA;
                pushButtonStyle(currentTheme, isAreaSelected);
                ImGui.pushID("annotation_area");
                boolean areaClicked = ImGui.imageButton(areaIconId, BUTTON_SIZE, BUTTON_SIZE);
                ImGui.popID();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("区域标注：区域内的方块数量");
                }
                ImGui.popStyleColor(4);
                
                // 处理按钮点击事件
                if (areaClicked && !isAreaSelected) {
                    currentMode = AnnotationTool.AnnotationMode.AREA;
                    updateToolConfig(CONFIG_KEY_MODE, "area");
                }
            } else {
                // 使用文本按钮作为备用方案
                LOGGER.warn("标注工具图标加载失败，使用文本按钮作为备用方案");
                
                // 距离模式按钮
                boolean isDistanceSelected = currentMode == AnnotationTool.AnnotationMode.DISTANCE;
                pushButtonStyle(currentTheme, isDistanceSelected);
                if (ImGui.button("距离", BUTTON_SIZE, BUTTON_SIZE)) {
                    if (!isDistanceSelected) {
                        currentMode = AnnotationTool.AnnotationMode.DISTANCE;
                        updateToolConfig(CONFIG_KEY_MODE, "distance");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("标注两点之间的距离（以方块为单位）");
                }
                ImGui.popStyleColor(4);
                
                ImGui.sameLine();
                
                // 角度模式按钮
                boolean isAngleSelected = currentMode == AnnotationTool.AnnotationMode.ANGLE;
                pushButtonStyle(currentTheme, isAngleSelected);
                if (ImGui.button("角度", BUTTON_SIZE, BUTTON_SIZE)) {
                    if (!isAngleSelected) {
                        currentMode = AnnotationTool.AnnotationMode.ANGLE;
                        updateToolConfig(CONFIG_KEY_MODE, "angle");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("选中两条有夹角的直线，右键完成选中时自动标注角度");
                }
                ImGui.popStyleColor(4);
                
                ImGui.sameLine();
                
                // 半径模式按钮
                boolean isRadiusSelected = currentMode == AnnotationTool.AnnotationMode.RADIUS;
                pushButtonStyle(currentTheme, isRadiusSelected);
                if (ImGui.button("半径", BUTTON_SIZE, BUTTON_SIZE)) {
                    if (!isRadiusSelected) {
                        currentMode = AnnotationTool.AnnotationMode.RADIUS;
                        updateToolConfig(CONFIG_KEY_MODE, "radius");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("点选或框选圆形、半圆、圆弧图形，右键完成选中时自动标注半径");
                }
                ImGui.popStyleColor(4);
                
                ImGui.sameLine();
                
                // 面积模式按钮
                boolean isAreaSelected = currentMode == AnnotationTool.AnnotationMode.AREA;
                pushButtonStyle(currentTheme, isAreaSelected);
                if (ImGui.button("面积", BUTTON_SIZE, BUTTON_SIZE)) {
                    if (!isAreaSelected) {
                        currentMode = AnnotationTool.AnnotationMode.AREA;
                        updateToolConfig(CONFIG_KEY_MODE, "area");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("标注区域内的方块数量");
                }
                ImGui.popStyleColor(4);
            }
            
            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 设置按钮的样式（参考阵列工具的实现）
     */
    private void pushButtonStyle(UITheme.ThemeColors theme, boolean isSelected) {
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonSelected);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonSelectedHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonSelectedActive);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonActiveBorder);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, theme.buttonNormal);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, theme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, theme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        }
    }
    
    @Override
    public void initialize() {
        currentMode = AnnotationTool.AnnotationMode.DISTANCE;
        updateToolConfig(CONFIG_KEY_MODE, "distance");
    }
    
    @Override
    public void cleanup() {
        // 释放纹理资源
        if (resourcesInitialized) {
            try {
                ImGuiUtils.deleteTexture(distanceIconId);
                ImGuiUtils.deleteTexture(angleIconId);
                ImGuiUtils.deleteTexture(radiusIconId);
                ImGuiUtils.deleteTexture(areaIconId);
                resourcesInitialized = false;
                LOGGER.debug("标注工具选项渲染器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("释放标注工具选项渲染器资源失败: {}", e.getMessage());
            }
        }
    }
}
