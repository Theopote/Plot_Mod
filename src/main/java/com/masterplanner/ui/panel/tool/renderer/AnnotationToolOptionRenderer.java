package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.AnnotationTool;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;

/**
 * 标注工具选项渲染器
 * 提供模式选择：距离、角度、半径、面积
 */
public class AnnotationToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_MODE = "mode";
    
    private AnnotationTool.AnnotationMode currentMode = AnnotationTool.AnnotationMode.DISTANCE;
    
    public AnnotationToolOptionRenderer() {
        super("annotation");
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
            
            // 距离模式按钮
            boolean isDistanceSelected = currentMode == AnnotationTool.AnnotationMode.DISTANCE;
            if (isDistanceSelected) {
                ImVec4 tabActive = ImGui.getStyle().getColor(ImGuiCol.TabActive);
                ImVec4 tabHovered = ImGui.getStyle().getColor(ImGuiCol.TabHovered);
                
                int activeColor = (int)(tabActive.w * 255) << 24 | 
                                (int)(tabActive.z * 255) << 16 | 
                                (int)(tabActive.y * 255) << 8 | 
                                (int)(tabActive.x * 255);
                
                int hoveredColor = (int)(tabHovered.w * 255) << 24 | 
                                 (int)(tabHovered.z * 255) << 16 | 
                                 (int)(tabHovered.y * 255) << 8 | 
                                 (int)(tabHovered.x * 255);
                
                ImGui.pushStyleColor(ImGuiCol.Button, activeColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
            }
            
            if (ImGui.button("距离", -1, 0)) {
                if (!isDistanceSelected) {
                    currentMode = AnnotationTool.AnnotationMode.DISTANCE;
                    updateToolConfig(CONFIG_KEY_MODE, "distance");
                }
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("标注两点之间的距离（以方块为单位）");
            }
            
            if (isDistanceSelected) {
                ImGui.popStyleColor(3);
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 角度模式按钮
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("");
            
            ImGui.tableNextColumn();
            boolean isAngleSelected = currentMode == AnnotationTool.AnnotationMode.ANGLE;
            if (isAngleSelected) {
                ImVec4 tabActive = ImGui.getStyle().getColor(ImGuiCol.TabActive);
                ImVec4 tabHovered = ImGui.getStyle().getColor(ImGuiCol.TabHovered);
                
                int activeColor = (int)(tabActive.w * 255) << 24 | 
                                (int)(tabActive.z * 255) << 16 | 
                                (int)(tabActive.y * 255) << 8 | 
                                (int)(tabActive.x * 255);
                
                int hoveredColor = (int)(tabHovered.w * 255) << 24 | 
                                 (int)(tabHovered.z * 255) << 16 | 
                                 (int)(tabHovered.y * 255) << 8 | 
                                 (int)(tabHovered.x * 255);
                
                ImGui.pushStyleColor(ImGuiCol.Button, activeColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
            }
            
            if (ImGui.button("角度", -1, 0)) {
                if (!isAngleSelected) {
                    currentMode = AnnotationTool.AnnotationMode.ANGLE;
                    updateToolConfig(CONFIG_KEY_MODE, "angle");
                }
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("标注三点形成的角度（度数）");
            }
            
            if (isAngleSelected) {
                ImGui.popStyleColor(3);
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 半径模式按钮
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("");
            
            ImGui.tableNextColumn();
            boolean isRadiusSelected = currentMode == AnnotationTool.AnnotationMode.RADIUS;
            if (isRadiusSelected) {
                ImVec4 tabActive = ImGui.getStyle().getColor(ImGuiCol.TabActive);
                ImVec4 tabHovered = ImGui.getStyle().getColor(ImGuiCol.TabHovered);
                
                int activeColor = (int)(tabActive.w * 255) << 24 | 
                                (int)(tabActive.z * 255) << 16 | 
                                (int)(tabActive.y * 255) << 8 | 
                                (int)(tabActive.x * 255);
                
                int hoveredColor = (int)(tabHovered.w * 255) << 24 | 
                                 (int)(tabHovered.z * 255) << 16 | 
                                 (int)(tabHovered.y * 255) << 8 | 
                                 (int)(tabHovered.x * 255);
                
                ImGui.pushStyleColor(ImGuiCol.Button, activeColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
            }
            
            if (ImGui.button("半径", -1, 0)) {
                if (!isRadiusSelected) {
                    currentMode = AnnotationTool.AnnotationMode.RADIUS;
                    updateToolConfig(CONFIG_KEY_MODE, "radius");
                }
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("标注圆的半径（以方块为单位）");
            }
            
            if (isRadiusSelected) {
                ImGui.popStyleColor(3);
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // 面积模式按钮
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("");
            
            ImGui.tableNextColumn();
            boolean isAreaSelected = currentMode == AnnotationTool.AnnotationMode.AREA;
            if (isAreaSelected) {
                ImVec4 tabActive = ImGui.getStyle().getColor(ImGuiCol.TabActive);
                ImVec4 tabHovered = ImGui.getStyle().getColor(ImGuiCol.TabHovered);
                
                int activeColor = (int)(tabActive.w * 255) << 24 | 
                                (int)(tabActive.z * 255) << 16 | 
                                (int)(tabActive.y * 255) << 8 | 
                                (int)(tabActive.x * 255);
                
                int hoveredColor = (int)(tabHovered.w * 255) << 24 | 
                                 (int)(tabHovered.z * 255) << 16 | 
                                 (int)(tabHovered.y * 255) << 8 | 
                                 (int)(tabHovered.x * 255);
                
                ImGui.pushStyleColor(ImGuiCol.Button, activeColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, hoveredColor);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, activeColor);
            }
            
            if (ImGui.button("面积", -1, 0)) {
                if (!isAreaSelected) {
                    currentMode = AnnotationTool.AnnotationMode.AREA;
                    updateToolConfig(CONFIG_KEY_MODE, "area");
                }
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("标注区域内的方块数量");
            }
            
            if (isAreaSelected) {
                ImGui.popStyleColor(3);
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    @Override
    public void initialize() {
        currentMode = AnnotationTool.AnnotationMode.DISTANCE;
        updateToolConfig(CONFIG_KEY_MODE, "distance");
    }
    
    @Override
    public void cleanup() {
        // 无需清理资源
    }
}
