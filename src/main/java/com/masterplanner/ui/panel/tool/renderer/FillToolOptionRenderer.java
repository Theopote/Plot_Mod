package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.utils.ImGuiUtils;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 填充工具选项渲染器
 * 
 * <p>提供填充工具的配置选项，包括：</p>
 * <ul>
 *   <li>填充模式选择（点击填充/边界填充）</li>
 *   <li>透明度调节</li>
 *   <li>连续模式开关</li>
 *   <li>使用说明和快捷键提示</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 填充工具选项渲染器
 */
public class FillToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FillToolOptionRenderer.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_MODE = "mode";
    private static final String CONFIG_KEY_FILL_OPACITY = "fillOpacity";
    private static final String CONFIG_KEY_MULTIPLE_MODE = "multipleMode";
    
    // 模式常量
    private static final String MODE_POINT_FILL = "POINT_FILL";
    private static final String MODE_BOUNDARY_FILL = "BOUNDARY_FILL";
    
    // 当前配置状态
    private String currentMode = MODE_POINT_FILL;
    private final ImFloat fillOpacity = new ImFloat(1.0f);
    private final ImBoolean multipleModeCheckbox = new ImBoolean(false);
    
    // 图标ID
    private final int pointFillIconId;
    private final int boundaryFillIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;

    public FillToolOptionRenderer() {
        super("fill");
        // 加载图标
        this.pointFillIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/point_fill.png"));
        this.boundaryFillIconId = ImGuiUtils.getTextureId(
            Identifier.of("masterplanner", "textures/gui/tooloptionspanel/boundary_fill.png"));
        this.resourcesInitialized = true;
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("fill_options");
        
        try {
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // === 填充模式选择 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("填充模式");
            
            ImGui.getStyle().setFrameRounding(currentTheme.toolbarControlRounding);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 点击填充模式按钮（选中高亮样式与圆/椭圆工具一致）
            boolean isPointFillSelected = MODE_POINT_FILL.equals(currentMode);
            if (isPointFillSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
                ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            }
            ImGui.pushID("point_fill_mode");
            if (ImGui.imageButton(pointFillIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isPointFillSelected) {
                    currentMode = MODE_POINT_FILL;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode);
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("点击填充：直接点击要填充的区域进行填充");
            }
            ImGui.popStyleColor(4);
            
            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2));
            
            // 边界填充模式按钮
            boolean isBoundaryFillSelected = MODE_BOUNDARY_FILL.equals(currentMode);
            if (isBoundaryFillSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonSelected);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonSelectedHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonSelectedActive);
                ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonActiveBorder);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
                ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            }
            ImGui.pushID("boundary_fill_mode");
            if (ImGui.imageButton(boundaryFillIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isBoundaryFillSelected) {
                    currentMode = MODE_BOUNDARY_FILL;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode);
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("边界填充：选择要填充的边界，然后点击填充位置");
            }
            ImGui.popStyleColor(4);
            ImGui.popStyleVar();
            
            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
            // === 透明度设置 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("透明度");
            
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(120.0f);
            float[] opacityArray = {fillOpacity.get()};
            if (ImGui.sliderFloat("##fill_opacity", opacityArray, 0.0f, 1.0f, "%.2f")) {
                fillOpacity.set(opacityArray[0]);
                updateToolConfig(CONFIG_KEY_FILL_OPACITY, String.valueOf(fillOpacity.get()));
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("调整填充的透明度（0.0 = 完全透明，1.0 = 完全不透明）");
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // === 连续模式设置 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("连续模式");
            
            ImGui.tableNextColumn();
            // 设置复选框样式
            ImGui.pushStyleColor(ImGuiCol.FrameBg, currentTheme.controlBackground);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, currentTheme.buttonHovered);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, currentTheme.buttonActive);
            ImGui.pushStyleColor(ImGuiCol.CheckMark, 0.0f, 1.0f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.buttonBorder);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 4.0f);
            ImGui.pushID("multiple_mode_checkbox");
            if (ImGui.checkbox("##multiple_mode", multipleModeCheckbox)) {
                updateToolConfig(CONFIG_KEY_MULTIPLE_MODE, String.valueOf(multipleModeCheckbox.get()));
            }
            ImGui.popID();
            ImGui.popStyleVar(2);
            ImGui.popStyleColor(5);
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用后可以连续进行多次填充操作，无需重新选择工具");
            }
            
            height += ImGui.getFrameHeightWithSpacing();
            
            // === 使用说明 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("使用说明");
            
            ImGui.tableNextColumn();
            ImGui.textWrapped("""
                    • 点击填充：直接点击要填充的区域
                    • 边界填充：先选择边界，再点击填充位置
                    • 快捷键：B键切换模式，M键切换连续模式，ESC取消""");
            
            height += ImGui.getTextLineHeightWithSpacing() * 4;
            
            // 恢复原始的圆角设置
            ImGui.getStyle().setFrameRounding(originalRounding);
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    // 已改为内联样式设置，保留此占位避免后续调用方报错（不再使用）
    @Deprecated
    @SuppressWarnings("unused")
    private void pushButtonStyle(UITheme.ThemeColors theme, boolean isSelected) {}
    
    @Override
    public void initialize() {
        // 默认选中点击填充，初始同步一次配置，确保按钮选中状态渲染正确
        currentMode = MODE_POINT_FILL;
        updateToolConfig(CONFIG_KEY_MODE, currentMode);
    }
    
    @Override
    public void cleanup() {
        // 释放纹理资源
        if (resourcesInitialized) {
            try {
                ImGuiUtils.deleteTexture(pointFillIconId);
                ImGuiUtils.deleteTexture(boundaryFillIconId);
                resourcesInitialized = false;
                LOGGER.debug("填充工具选项渲染器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("释放填充工具选项渲染器资源失败: {}", e.getMessage());
            }
        }
    }
} 