package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;

/**
 * 移动工具属性面板渲染器
 */
public class MoveToolOptionRenderer implements ToolOptionRenderer {
    private UITheme theme;

    public MoveToolOptionRenderer() {
        // 默认构造函数
    }

    @Override
    public float render() {
        float startY = ImGui.getCursorPosY();
        // 移动工具无专属配置项，使用方法由ToolOptionsPanel统一渲染
        
        return ImGui.getCursorPosY() - startY;
    }

    @Override
    public void initialize() {
        // 初始化渲染器
    }

    @Override
    public void cleanup() {
        // 清理资源
    }
} 