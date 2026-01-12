package com.masterplanner.ui.panel.tool.renderer;

import imgui.ImGui;

/**
 * 自由绘制工具选项渲染器
 */
public class FreeDrawToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_SMOOTHING = "smoothing";

    private final float[] smoothing = {0.5f};     // 平滑度，默认值0.5

    public FreeDrawToolOptionRenderer() {
        super("freedraw");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("free_draw_options");
        
        try {
            // 平滑度滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("平滑度");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##smoothing", smoothing, 0.0f, 1.0f, "%.2f")) {
                updateToolConfig(CONFIG_KEY_SMOOTHING, String.valueOf(smoothing[0]));
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    @Override
    public void initialize() {
        smoothing[0] = 0.5f;
    }

    @Override
    public void cleanup() {
        // 无需清理资源
    }
} 