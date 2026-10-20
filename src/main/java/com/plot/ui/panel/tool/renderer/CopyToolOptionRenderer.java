package com.plot.ui.panel.tool.renderer;

import imgui.ImGui;

/**
 * 复制工具属性面板渲染器（无专属配置项，使用方法由 ToolOptionsPanel 统一渲染）。
 */
public class CopyToolOptionRenderer implements ToolOptionRenderer {

    @Override
    public float render() {
        float startY = ImGui.getCursorPosY();
        return ImGui.getCursorPosY() - startY;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void cleanup() {
    }
}
