package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import com.masterplanner.ui.tools.impl.modify.ChamferTool;
import com.masterplanner.utils.ImGuiUtils;
import imgui.ImGui;
import imgui.type.ImFloat;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 倒角工具选项渲染器
 * 
 * <p>提供倒角工具的配置选项，包括：</p>
 * <ul>
 *   <li>倒角距离设置</li>
 *   <li>使用说明和快捷键提示</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 2.1 - 使用ChamferTool常量，确保配置一致性
 */
public class ChamferToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamferToolOptionRenderer.class);
    
    // 优化：使用ChamferTool的常量，确保配置键一致性
    private static final String CONFIG_KEY_DISTANCE = ChamferTool.CONFIG_KEY_DISTANCE;
    
    // 当前配置状态
    private final ImFloat distanceValue = new ImFloat((float) ChamferTool.DEFAULT_DISTANCE);
    
    // 优化：使用ChamferTool的常量，确保距离范围一致性
    private static final float MIN_DISTANCE = (float) ChamferTool.MIN_DISTANCE;
    private static final float MAX_DISTANCE = (float) ChamferTool.MAX_DISTANCE;
    private static final float DISTANCE_STEP = 0.5f;
    
    // 图标ID
    private final int chamferIconId;
    
    // 资源管理标志
    private boolean resourcesInitialized;
    
    // 默认图标路径
    private static final String DEFAULT_ICON_PATH = "textures/gui/tooloptionspanel/default.png";

    public ChamferToolOptionRenderer() {
        super("chamfer");
        
        // 安全加载图标，包含错误处理和回退机制
        this.chamferIconId = loadTexture();
        this.resourcesInitialized = true;
        
        LOGGER.debug("ChamferToolOptionRenderer 初始化完成，图标ID: chamfer={}", chamferIconId);
    }
    
    /**
     * 安全加载纹理图标，包含错误处理和回退机制
     */
    private int loadTexture() {
        try {
            return ImGuiUtils.getTextureId(Identifier.of("masterplanner", "textures/gui/tooloptionspanel/chamfer.png"));
        } catch (Exception e) {
            LOGGER.error("加载图标失败: {}, 使用默认图标", "textures/gui/tooloptionspanel/chamfer.png", e);
            try {
                return ImGuiUtils.getTextureId(Identifier.of("masterplanner", DEFAULT_ICON_PATH));
            } catch (Exception fallbackException) {
                LOGGER.error("默认图标也加载失败: {}", DEFAULT_ICON_PATH, fallbackException);
                return -1; // 返回无效纹理ID
            }
        }
    }
    
    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("chamfer_options");
        
        try {
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // === 倒角距离设置 ===
            height += renderDistanceSettings(currentTheme);
            
            // 恢复原始样式
            ImGui.getStyle().setFrameRounding(originalRounding);
            
        } catch (Exception e) {
            LOGGER.error("渲染倒角工具选项时发生错误", e);
            height += renderErrorState();
        } finally {
            ImGui.popID();
        }
        
        return height;
    }
    
    /**
     * 渲染倒角距离设置
     */
    private float renderDistanceSettings(UITheme.ThemeColors theme) {
        float height = 0;
        
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text("倒角距离");
        
        ImGui.tableNextColumn();
        ImGui.pushItemWidth(120.0f);
        
        // 距离滑块
        float[] distanceArray = {distanceValue.get()};
        if (ImGui.dragFloat("##distance", distanceArray, DISTANCE_STEP, MIN_DISTANCE, MAX_DISTANCE, "%.1f")) {
            distanceValue.set(distanceArray[0]);
            updateToolConfig(CONFIG_KEY_DISTANCE, String.valueOf(distanceValue.get()));
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("设置倒角斜面的距离大小");
        }
        
        ImGui.popItemWidth();
        height += ImGui.getFrameHeight() + ImGui.getStyle().getItemSpacingY();
        
        return height;
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
                1. 选择第一条直线
                2. 选择第二条直线
                3. 使用+/-键调整距离
                4. 按Enter确认倒角""";
            
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
                Enter: 确认倒角
                +/-: 调整距离
                ESC: 取消操作""";
            
        ImGui.textColored(theme.mutedText, shortcuts);
        ImGui.popTextWrapPos();
        
        height += ImGui.getFrameHeight() * 3 + ImGui.getStyle().getItemSpacingY();
        
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
        ImGui.textColored(theme.errorText, "渲染倒角工具选项时发生错误");
        
        height += ImGui.getFrameHeight() + ImGui.getStyle().getItemSpacingY();
        
        return height;
    }
    
    @Override
    public void initialize() {
        try {
            // 发送默认配置值
            updateToolConfig(CONFIG_KEY_DISTANCE, String.valueOf(distanceValue.get()));
            
            LOGGER.debug("倒角工具选项渲染器初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("初始化倒角工具选项渲染器失败", e);
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
            
            LOGGER.debug("倒角工具选项渲染器清理完成");
            
        } catch (Exception e) {
            LOGGER.error("清理倒角工具选项渲染器失败", e);
        }
    }
}
