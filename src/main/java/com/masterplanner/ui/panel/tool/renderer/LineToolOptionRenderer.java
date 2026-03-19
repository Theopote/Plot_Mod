package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.drawing.LineTool;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiStyleVar;
import net.minecraft.util.Identifier;

/**
 * 直线工具选项渲染器
 */
public class LineToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_TYPE = "type";
    private static final String CONFIG_KEY_COUNT = "count";
    private static final String CONFIG_KEY_SPACING = "spacing";

    private final int singleLineIconId;
    private final int multiLineIconId;
    
    private String lineToolType = "single";     // 线条工具类型：single/multi
    private final int[] lineCountArray = {2};   // 线条数量
    private final float[] lineSpacingArray = {10.0f};  // 线条间距

    public LineToolOptionRenderer() {
        super("line");
        
        // 加载图标
        this.singleLineIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/single_line.png"));
        this.multiLineIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/multi_line.png"));
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("line_options");
        
        try {
            syncFromToolState();

            // 使用 pushStyleVar 临时设置圆角，避免永久修改共享 ImGui 样式（影响其他模组）
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, BUTTON_CORNER_ROUNDING);
            
            // 线型选择
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("线型");
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 单线按钮
            boolean isSingleSelected = lineToolType.equals("single");
            if (isSingleSelected) {
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
            
            ImGui.pushID("single_line");
            boolean clicked = ImGui.imageButton(singleLineIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            
            if (clicked && !isSingleSelected) {
                lineToolType = "single";
                updateToolConfig(CONFIG_KEY_TYPE, "single");
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("单线模式");
            }
            
            if (isSingleSelected) {
                ImGui.popStyleColor(3);
            }

            // 多线按钮
            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + BUTTON_SIZE + BUTTON_SPACING * 2);
            
            boolean isMultiSelected = lineToolType.equals("multi");
            if (isMultiSelected) {
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
            
            ImGui.pushID("multi_line");
            clicked = ImGui.imageButton(multiLineIconId, BUTTON_SIZE, BUTTON_SIZE);
            ImGui.popID();
            
            if (clicked && !isMultiSelected) {
                lineToolType = "multi";
                updateToolConfig(CONFIG_KEY_TYPE, "multi");
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("多线模式");
            }
            
            if (isMultiSelected) {
                ImGui.popStyleColor(3);
            }

            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;

            // 只在多线模式下显示额外选项
            if (lineToolType.equals("multi")) {
                ImGui.popStyleVar(); // 先弹出按钮圆角
                ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 0f); // 滑动条无圆角
                try {
                // 线条数量滑动条
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.alignTextToFramePadding();
                ImGui.text("线条数量");
                
                ImGui.tableNextColumn();
                ImGui.pushItemWidth(-1);
                if (ImGui.sliderInt("##count", lineCountArray, 2, 20)) {
                    updateToolConfig(CONFIG_KEY_COUNT, String.valueOf(lineCountArray[0]));
                }
                ImGui.popItemWidth();
                height += ImGui.getFrameHeightWithSpacing();
                
                // 线条间距滑动条
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.alignTextToFramePadding();
                ImGui.text("线条间距");
                
                ImGui.tableNextColumn();
                ImGui.pushItemWidth(-1);
                if (ImGui.sliderFloat("##spacing", lineSpacingArray, 5.0f, 100.0f, "%.1f")) {
                    updateToolConfig(CONFIG_KEY_SPACING, String.valueOf(lineSpacingArray[0]));
                }
                ImGui.popItemWidth();
                height += ImGui.getFrameHeightWithSpacing();
                } finally {
                    ImGui.popStyleVar();
                    ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, BUTTON_CORNER_ROUNDING); // 恢复按钮圆角供后续使用
                }
            }
            
            ImGui.popStyleVar();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    private void syncFromToolState() {
        var currentTool = AppState.getInstance().getCurrentTool();
        if (currentTool instanceof LineTool lineTool) {
            lineToolType = lineTool.getDrawingMode();
            lineCountArray[0] = lineTool.getLineCount();
            lineSpacingArray[0] = lineTool.getLineSpacing();
        }
    }

    @Override
    public void initialize() {
        lineToolType = "single";
        lineCountArray[0] = 2;  // 修复：与LineTool默认值保持一致
        lineSpacingArray[0] = 10.0f;
        
        // 修复：初始化时同步配置到工具
        updateToolConfig(CONFIG_KEY_TYPE, "single");
        updateToolConfig(CONFIG_KEY_COUNT, "2");
        updateToolConfig(CONFIG_KEY_SPACING, "10.0");
    }

    @Override
    public void cleanup() {
        // 清理纹理资源
        ImGuiUtils.deleteTexture(singleLineIconId);
        ImGuiUtils.deleteTexture(multiLineIconId);
    }
} 