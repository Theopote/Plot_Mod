package com.plot.ui.panel.tool.renderer;

import com.plot.utils.ImGuiUtils;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import com.plot.ui.tools.impl.modify.MirrorTool;
import com.plot.ui.tools.impl.modify.strategy.MirrorMode;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
// import imgui.type.ImBoolean;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 镜像工具选项渲染器
 * 
 * <p>提供镜像工具的配置选项，包括：</p>
 * <ul>
 *   <li>镜像模式选择（镜像/复制镜像）</li>
 *   <li>正交约束开关</li>
 *   <li>使用说明和快捷键提示</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 1.1 - 修复资源管理和配置同步
 */
public class MirrorToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorToolOptionRenderer.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_MODE = "mode";
    // 移除：正交与图形吸附的单独开关（正交用Shift，全局吸附设置在统一面板中）
    // private static final String CONFIG_KEY_SNAP_DISTANCE = "snapDistance"; // 移除使用
    
    // 模式常量
    private static final String MODE_AXIS_SYMMETRY = "AXIS_SYMMETRY";
    private static final String MODE_CENTRAL_SYMMETRY = "CENTRAL_SYMMETRY";
    
    // 当前配置状态
    private String currentMode = MODE_AXIS_SYMMETRY;
    // 删除对应复选框状态
    // private float snapDistance = 20.0f; // 移除使用
    
    // 图标ID
    private final int mirrorIconId;
    private final int copyMirrorIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;

    public MirrorToolOptionRenderer() {
        super("mirror");
        // 加载图标
        this.mirrorIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/axis_mirror.png"));
        this.copyMirrorIconId = ImGuiUtils.getTextureId(
            Identifier.of("plot", "textures/gui/tooloptionspanel/center_mirror.png"));
        this.resourcesInitialized = true;
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("mirror_options");
        
        try {
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.toolbarControlRounding);
            ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
            
            // === 镜像模式选择 ===
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("对称模式");
            
            ImGui.tableNextColumn();
            float firstButtonX = ImGui.getCursorPosX();
            
            // 轴对称按钮
            boolean isMirrorSelected = MODE_AXIS_SYMMETRY.equals(currentMode);
            pushButtonStyle(currentTheme, isMirrorSelected);
            ImGui.pushID("mirror_mode");
            if (ImGui.imageButton(mirrorIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isMirrorSelected) {
                    currentMode = MODE_AXIS_SYMMETRY;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode);
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("轴对称：关于一条轴线做对称（两点定义轴）");
            }
            ImGui.popStyleColor(4);
            
            ImGui.sameLine();
            ImGui.setCursorPosX(firstButtonX + (BUTTON_SIZE + BUTTON_SPACING * 2));
            
            // 中心对称按钮
            boolean isCopyMirrorSelected = MODE_CENTRAL_SYMMETRY.equals(currentMode);
            pushButtonStyle(currentTheme, isCopyMirrorSelected);
            ImGui.pushID("copy_mirror_mode");
            if (ImGui.imageButton(copyMirrorIconId, BUTTON_SIZE, BUTTON_SIZE)) {
                if (!isCopyMirrorSelected) {
                    currentMode = MODE_CENTRAL_SYMMETRY;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode);
                }
            }
            ImGui.popID();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("中心对称：关于一个中心点做对称（等价于绕该点旋转180°）");
            }
            ImGui.popStyleColor(4);
            ImGui.popStyleVar();
            
            height += BUTTON_SIZE + ImGui.getStyle().getFramePadding().y * 2;
            
            // 已移除：正交约束与图形吸附复选框（正交使用Shift）
            
            // 移除与图形吸附开关关联的“吸附距离”设置
            
            ImGui.popStyleVar();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 设置按钮的样式
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
        // 从工具获取当前配置
        try {
            // 通过ToolManager获取当前工具
            com.plot.core.tool.ToolManager toolManager = com.plot.core.tool.ToolManager.getInstance();
            com.plot.api.tool.ITool currentTool = toolManager.getActiveTool();
            
            if (currentTool instanceof MirrorTool mirrorTool) {
                // 同步镜像模式
                MirrorMode mode = mirrorTool.getMirrorMode();
                currentMode = mode != null ? mode.name() : MODE_AXIS_SYMMETRY;
                
                // 简化同步：仅保留模式
                LOGGER.debug("镜像工具配置已同步: mode={}", currentMode);
            }
        } catch (Exception e) {
            LOGGER.warn("同步镜像工具配置失败: {}", e.getMessage());
            // 使用默认值
        }
    }
    
    @Override
    public void cleanup() {
        // 释放纹理资源
        if (resourcesInitialized) {
            try {
                ImGuiUtils.deleteTexture(mirrorIconId);
                ImGuiUtils.deleteTexture(copyMirrorIconId);
                resourcesInitialized = false;
                LOGGER.debug("镜像工具选项渲染器资源已释放");
            } catch (Exception e) {
                LOGGER.warn("释放镜像工具选项渲染器资源失败: {}", e.getMessage());
            }
        }
    }
} 