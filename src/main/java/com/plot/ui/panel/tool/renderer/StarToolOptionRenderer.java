package com.plot.ui.panel.tool.renderer;

import imgui.ImGui;
import com.plot.ui.tools.impl.drawing.StarTool;

/**
 * 星形工具选项渲染器
 */
public class StarToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_POINTS = "points";
    private static final String CONFIG_KEY_INNER_TWIST = "innerTwist";
    private static final String CONFIG_KEY_OUTER_TWIST = "outerTwist";

    private final int[] starPointsArray = {5};  // 星形顶点数量
    private final float[] innerTwistArray = {0.0f}; // 内扭转角度
    private final float[] outerTwistArray = {0.0f}; // 外扭转角度

    public StarToolOptionRenderer() {
        super("star");
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("star_options");
        
        try {
            // 在渲染前，从当前工具同步状态（如果可能）
            syncWithCurrentTool();
            
            // 顶点数量滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("顶点数量");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderInt("##points", starPointsArray, 3, 20)) {
                updateToolConfig(CONFIG_KEY_POINTS, String.valueOf(starPointsArray[0]));
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 内扭转滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("内扭转");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            // 使用StarTool中定义的常量
            if (ImGui.sliderFloat("##inner_twist", innerTwistArray, (float)StarTool.MIN_TWIST_DEGREES, (float)StarTool.MAX_TWIST_DEGREES, "%.1f°")) {
                updateToolConfig(CONFIG_KEY_INNER_TWIST, String.valueOf(innerTwistArray[0]));
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 外扭转滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("外扭转");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            // 使用StarTool中定义的常量
            if (ImGui.sliderFloat("##outer_twist", outerTwistArray, (float)StarTool.MIN_TWIST_DEGREES, (float)StarTool.MAX_TWIST_DEGREES, "%.1f°")) {
                updateToolConfig(CONFIG_KEY_OUTER_TWIST, String.valueOf(outerTwistArray[0]));
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    /**
     * 从当前工具同步状态
     * 注意：这是一个示例实现，实际需要根据项目架构来获取当前工具
     */
    private void syncWithCurrentTool() {
        // TODO: 实现从工具管理器获取当前工具的逻辑
        // 示例代码：
        // ITool currentTool = toolManager.getActiveTool();
        // if (currentTool instanceof StarTool starTool) {
        //     starPointsArray[0] = starTool.getStarPoints();
        //     innerTwistArray[0] = (float) starTool.getInnerTwist();
        //     outerTwistArray[0] = (float) starTool.getOuterTwist();
        // }
    }

    @Override
    public void initialize() {
        starPointsArray[0] = 5;
        innerTwistArray[0] = 0.0f;
        outerTwistArray[0] = 0.0f;
    }

    @Override
    public void cleanup() {
        // 无需清理资源
    }
} 