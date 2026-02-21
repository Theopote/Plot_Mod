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
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置窗口标题
        ImGui.text("移动工具");
        ImGui.separator();

        // 显示工具描述
        ImGui.textWrapped("""
                移动工具用于移动选中的图形。使用步骤：
                1. 首先选择要移动的图形
                2. 点击第一个点作为参考点
                3. 移动鼠标到目标位置
                4. 点击完成移动""");

        // 捕捉功能说明
        ImGui.separator();
        ImGui.textColored(currentTheme.successText, "捕捉功能：");
        ImGui.textWrapped("""
                • 移动起点可以捕捉到图形的端点、中点、圆心等
                • 移动目标点可以捕捉到其他图形的特征点
                • 按住Shift键启用正交约束（水平/垂直移动）
                • 捕捉点会显示绿色（起点）和红色（目标）标记""");

        // 快捷键说明
        ImGui.separator();
        ImGui.textColored(currentTheme.warningText, "快捷键：");
        ImGui.textWrapped("""
                • Shift: 启用正交约束
                • Esc: 取消移动操作
                • 右键: 取消当前移动""");

        // 显示警告信息
        ImGui.separator();
        ImGui.textColored(currentTheme.warningText, "注意：");
        ImGui.textWrapped("如果没有图形被选中，将无法使用移动工具。请先使用选择工具选中要移动的图形。");
        
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