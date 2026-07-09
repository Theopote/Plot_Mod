package com.plot.ui.panel.tool.renderer;

import com.plot.utils.PlotI18n;
// imports simplified: ScaleStrategy and theme utilities not required by simplified renderer
import com.plot.ui.theme.ThemeManager;
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
 * @author Plot Team
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
            // 缩放面板当前无额外控件，使用方法由ToolOptionsPanel统一显示
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
        if (ImGui.collapsingHeader(PlotI18n.tr("option.plot.usage_instructions"), ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped(PlotI18n.tr("hint.plot.scale.usage_title"));
            ImGui.spacing();
            
            ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step1"));
            ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step2"));
            
            // 使用说明（简化）：缩放中心由交互时确定，默认交互流程如下
            ImGui.textColored(ThemeManager.getInstance().getCurrentTheme().warningText, PlotI18n.tr("hint.plot.scale.workflow"));
            ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step3"));
            ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step4"));
            ImGui.bulletText(PlotI18n.tr("hint.plot.scale.step5"));

        }
    }

    @Override
    public void cleanup() {
        // 无需释放图标资源（未加载）
    }
} 