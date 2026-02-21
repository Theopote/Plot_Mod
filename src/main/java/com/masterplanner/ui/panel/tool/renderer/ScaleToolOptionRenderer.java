package com.masterplanner.ui.panel.tool.renderer;

// imports simplified: ScaleStrategy and theme utilities not required by simplified renderer
import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImGui;
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

    // 按钮/图标相关已经移除：缩放中心由交互时确定，简化面板

    public ScaleToolOptionRenderer() {
        super("scale");
        // 以前用于显示三个缩放中心的图标，现简化为由交互自动确定中心，故不再加载图标
    }

    @Override
    public void initialize() {
        LOGGER.debug("缩放工具选项渲染器已初始化");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("scale_options");
        try {
            // 缩放面板已简化：不显示“缩放中心”和“缩放方式”控件，仅显示使用说明与快捷键
            renderUsageInstructions();
        } catch (Exception e) {
            LOGGER.error("缩放工具选项渲染器渲染失败: {}", e.getMessage(), e);
        } finally {
            ImGui.popID();
        }
        
        return height;
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
            ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, "交互流程：");
            ImGui.bulletText("1. 使用选择工具选择图形");
            ImGui.bulletText("2. 切换到缩放工具，第一次点击或以选择框中心确定中心点");
            ImGui.bulletText("3. 第二次点击或拖动设置参考点并缩放，点击完成");

        }
    }

    @Override
    public void cleanup() {
        // 无需释放图标资源（未加载）
    }
} 