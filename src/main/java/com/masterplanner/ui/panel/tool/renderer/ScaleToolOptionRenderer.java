package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.tools.impl.modify.strategy.ScaleStrategy;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缩放工具选项渲染器 (重新设计版)
 *
 * <p>提供缩放工具的配置选项，包括三个缩放中心模式按钮：</p>
 * <ul>
 *   <li>自定义中心：用户点击确定缩放中心，三点缩放</li>
 *   <li>选择中心：以选择框中心为缩放中心，两点缩放</li>
 *   <li>图形中心：以每个图形中心为缩放中心，两点缩放</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 3.0 - 重新设计版
 */
public class ScaleToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleToolOptionRenderer.class);

    // 配置键常量
    private static final String CONFIG_KEY_MODE = "mode";
    
    // UI状态变量
    private ScaleStrategy.ScaleMode currentMode;
    
    // 按钮/图标相关已经移除：缩放中心由交互时确定，简化面板

    public ScaleToolOptionRenderer() {
        super("scale");
        // 以前用于显示三个缩放中心的图标，现简化为由交互自动确定中心，故不再加载图标
    }

    @Override
    public void initialize() {
        // 初始化默认值
        this.currentMode = ScaleStrategy.ScaleMode.UNIFORM;
        // 仅初始化模式，中心由交互决定
        
        LOGGER.debug("缩放工具选项渲染器已初始化");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("scale_options");
        
        // 样式栈计数器，用于确保正确清理
        int styleColorCount = 0;
        int styleVarCount = 0;
        
        try {
            // 缩放中心由交互决定，面板仅显示提示
            
            UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
            float originalRounding = ImGui.getStyle().getFrameRounding();
            
            // 简化：缩放中心由交互时确定（点击/选择），不在工具选项中提供按钮
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("缩放中心");
            ImGui.tableNextColumn();
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "由交互自动确定（点击或以选择框中心）");
            height += ImGui.getTextLineHeight() + ImGui.getStyle().getItemSpacing().y;
            
            // 缩放方式：仅保留模式切换（等比/非等比）通过下拉快速切换
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("缩放方式");
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            String currentLabel = currentMode == ScaleStrategy.ScaleMode.NON_UNIFORM ? "非统一缩放" : "等比缩放";
            if (ImGui.beginCombo("##scale_mode_combo", currentLabel)) {
                if (ImGui.selectable("等比缩放", currentMode == ScaleStrategy.ScaleMode.UNIFORM)) {
                    currentMode = ScaleStrategy.ScaleMode.UNIFORM;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode.name());
                }
                if (ImGui.selectable("非统一缩放", currentMode == ScaleStrategy.ScaleMode.NON_UNIFORM)) {
                    currentMode = ScaleStrategy.ScaleMode.NON_UNIFORM;
                    updateToolConfig(CONFIG_KEY_MODE, currentMode.name());
                }
                ImGui.endCombo();
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 恢复原始的圆角设置
            ImGui.getStyle().setFrameRounding(originalRounding);
            
            // === 使用说明 ===
            renderUsageInstructions();
            
            // === 快捷键提示 ===
            renderShortcutTips();
            
        } catch (Exception e) {
            LOGGER.error("缩放工具选项渲染器渲染失败: {}", e.getMessage(), e);
            
            // 紧急清理样式栈
            while (styleColorCount > 0) {
                try {
                    ImGui.popStyleColor();
                    styleColorCount--;
                } catch (Exception ex) {
                    LOGGER.warn("清理样式颜色栈失败: {}", ex.getMessage());
                    break;
                }
            }
            
            while (styleVarCount > 0) {
                try {
                    ImGui.popStyleVar();
                    styleVarCount--;
                } catch (Exception ex) {
                    LOGGER.warn("清理样式变量栈失败: {}", ex.getMessage());
                    break;
                }
            }
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
    
    /**
     * 渲染使用说明
     */
    private void renderUsageInstructions() {
        if (ImGui.collapsingHeader("使用说明", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("缩放工具使用步骤：");
            ImGui.spacing();
            
            ImGui.bulletText("1. 使用选择工具选择要缩放的图形");
            ImGui.bulletText("2. 切换到缩放工具");
            
            // 使用说明（简化）：缩放中心由交互时确定，默认交互流程如下
            ImGui.textColored(1.0f, 1.0f, 0.6f, 1.0f, "交互流程：");
            ImGui.bulletText("1. 使用选择工具选择图形");
            ImGui.bulletText("2. 切换到缩放工具，第一次点击或以选择框中心确定中心点");
            ImGui.bulletText("3. 第二次点击或拖动设置参考点并缩放，点击完成");
            
            ImGui.spacing();
            ImGui.textWrapped("缩放方式说明：");
            ImGui.bulletText("等比缩放：X和Y方向使用相同的缩放因子");
            ImGui.bulletText("仅X轴：仅沿X方向缩放");
            ImGui.bulletText("仅Y轴：仅沿Y方向缩放");
            
            ImGui.spacing();
            ImGui.textWrapped("缩放中心说明：缩放中心在交互时确定（点击或以选择框中心）");
        }
    }
    
    /**
     * 渲染快捷键提示
     */
    private void renderShortcutTips() {
        if (ImGui.collapsingHeader("快捷键", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textColored(0.9f, 0.6f, 0.3f, 1.0f, "快捷键提示：");
            ImGui.spacing();
            ImGui.bulletText("Shift：临时启用统一缩放模式");
            ImGui.bulletText("右键 / Esc：取消当前缩放操作");
            
            ImGui.spacing();
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "提示：");
            ImGui.textWrapped("""
                    • 等比缩放适合等比例调整图形大小
                    • 仅X轴/仅Y轴适合沿单一方向调整尺寸
                    • 缩放时会显示实时预览效果""");
        }
    }

    @Override
    public void cleanup() {
        // 无需释放图标资源（未加载）
    }
} 