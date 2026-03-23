package com.plot.ui.panel.tool.renderer;

import com.plot.ui.tools.impl.modify.TransformTool;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 变换工具选项渲染器（优化版）
 * 
 * <p>参考其他工具选项面板的设计，提供简洁统一的界面：</p>
 * <ul>
 *   <li>使用主题颜色系统</li>
 *   <li>统一的按钮样式</li>
 *   <li>简洁的布局</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 2.0 - 优化版
 */
public class TransformToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformToolOptionRenderer.class);
    
    // 配置键常量
    private static final String CONFIG_KEY_ROTATION = "rotation";
    
    // 状态缓存
    private boolean rotationEnabled = true;
    private final ImBoolean rotationEnabledUi = new ImBoolean(true);
    
    /**
     * 构造函数（兼容工厂方法）
     */
    public TransformToolOptionRenderer() {
        super("transform");
        LOGGER.debug("变换工具选项渲染器已初始化");
    }
    
    /**
     * 构造函数（兼容旧版本，保留以支持向后兼容）
     * @deprecated 现在使用无参构造函数，工具从AppState获取
     */
    @Deprecated
    public TransformToolOptionRenderer(TransformTool transformTool) {
        super("transform");
        LOGGER.debug("变换工具选项渲染器已初始化（兼容模式）");
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("transform_options");
        
        try {
            // 获取当前工具
            TransformTool currentTool = getCurrentTool();
            if (currentTool == null) {
                ImGui.tableNextRow();
                ImGui.tableNextColumn();
                ImGui.text("请选择变换工具");
                return ImGui.getFrameHeightWithSpacing();
            }
            
            // 同步工具状态
            syncToolState(currentTool);
            
            // 获取当前主题
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            
            // === 功能设置 ===
            height += renderFeatureSettings(currentTool, currentTheme);
            
        } catch (Exception e) {
            LOGGER.error("渲染变换工具选项时发生错误: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染功能设置
     */
    private float renderFeatureSettings(TransformTool tool, UITheme.ThemeColors theme) {
        float height = 0;
        
        // 旋转功能开关
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("旋转功能");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(-1);
        
        ImGui.pushStyleColor(ImGuiCol.FrameBg, theme.inputBackground);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, theme.inputBackgroundHovered);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, theme.inputBackgroundActive);
        ImGui.pushStyleColor(ImGuiCol.CheckMark, theme.accent);
        ImGui.pushStyleColor(ImGuiCol.Border, theme.buttonBorder);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 4.0f);

        rotationEnabledUi.set(rotationEnabled);
        if (ImGui.checkbox("##transform_rotation_enabled", rotationEnabledUi)) {
            rotationEnabled = rotationEnabledUi.get();
            tool.setRotationEnabled(rotationEnabled);
            updateToolConfig(CONFIG_KEY_ROTATION, String.valueOf(rotationEnabled));
            LOGGER.debug("旋转功能已{}", rotationEnabled ? "启用" : "禁用");
        }
        
        ImGui.popStyleVar(2);
        ImGui.popStyleColor(5);
        ImGui.popItemWidth();
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("启用后，可以在角点外侧拖拽进行旋转");
        }
        
        height += ImGui.getFrameHeightWithSpacing();
        
        return height;
    }

    /**
     * 获取当前工具实例
     */
    private TransformTool getCurrentTool() {
        try {
            com.plot.core.state.AppState appState = com.plot.core.state.AppState.getInstance();
            if (appState == null) {
                return null;
            }
            
            com.plot.api.tool.ITool currentTool = appState.getCurrentTool();
            if (currentTool instanceof TransformTool) {
                return (TransformTool) currentTool;
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.warn("获取变换工具失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 同步工具状态到UI
     */
    private void syncToolState(TransformTool tool) {
        try {
            rotationEnabled = tool.isRotationEnabled();
            rotationEnabledUi.set(rotationEnabled);
        } catch (Exception e) {
            LOGGER.warn("同步工具状态失败: {}", e.getMessage());
        }
    }
    
    @Override
    public void initialize() {
        LOGGER.debug("初始化变换工具选项");
    }
    
    @Override
    public void cleanup() {
        LOGGER.debug("变换工具选项渲染器资源已清理");
    }
}
