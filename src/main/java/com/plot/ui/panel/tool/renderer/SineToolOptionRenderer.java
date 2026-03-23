package com.plot.ui.panel.tool.renderer;

import imgui.ImGui;

/**
 * 正弦曲线工具选项渲染器
 * 只提供相位控制，其他参数通过鼠标交互确定
 */
public class SineToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_PHASE = "phase";

    private final float[] phaseArray = {0.0f}; // 默认相位0

    public SineToolOptionRenderer() {
        super("sine");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("sine_options");
        
        try {
            // 相位滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("相位");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##phase", phaseArray, 0.0f, 360.0f, "%.1f°")) {
                updateToolConfig(CONFIG_KEY_PHASE, String.valueOf(phaseArray[0]));
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();

            // 相位预设按钮
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("");

            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            ImGui.pushID("phase_presets");
            if (ImGui.button("0°")) {
                phaseArray[0] = 0.0f;
                updateToolConfig(CONFIG_KEY_PHASE, String.valueOf(phaseArray[0]));
            }
            ImGui.sameLine();
            if (ImGui.button("90°")) {
                phaseArray[0] = 90.0f;
                updateToolConfig(CONFIG_KEY_PHASE, String.valueOf(phaseArray[0]));
            }
            ImGui.sameLine();
            if (ImGui.button("180°")) {
                phaseArray[0] = 180.0f;
                updateToolConfig(CONFIG_KEY_PHASE, String.valueOf(phaseArray[0]));
            }
            ImGui.sameLine();
            if (ImGui.button("360°")) {
                phaseArray[0] = 360.0f;
                updateToolConfig(CONFIG_KEY_PHASE, String.valueOf(phaseArray[0]));
            }
            ImGui.popID();
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    @Override
    public void initialize() {
        phaseArray[0] = 0.0f;
    }

    @Override
    public void cleanup() {
        // 无需清理资源
    }
} 