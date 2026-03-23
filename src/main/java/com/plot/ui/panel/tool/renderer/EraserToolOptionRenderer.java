package com.plot.ui.panel.tool.renderer;

import com.plot.PlotMod;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
import com.plot.ui.tools.impl.modify.EraserTool;
import imgui.ImGui;

/**
 * 橡皮擦工具选项渲染器
 */
public class EraserToolOptionRenderer extends AbstractToolOptionRenderer {
    private static final String CONFIG_KEY_ERASER_RADIUS = "eraser_radius";
    
    // 橡皮擦半径范围
    private static final float MIN_ERASER_RADIUS = 1.0f;
    private static final float MAX_ERASER_RADIUS = 20.0f;
    private static final float DEFAULT_ERASER_RADIUS = 5.0f;

    private final float[] eraserRadius = {DEFAULT_ERASER_RADIUS};  // 橡皮擦半径

    public EraserToolOptionRenderer() {
        super("eraser");
        initialize();
    }

    @Override
    public float render() {
        float height = 0;
        ImGui.pushID("eraser_options");
        
        try {
            // 获取当前工具实例
            EraserTool eraserTool = getCurrentEraserTool();
            if (eraserTool != null) {
                // 更新当前值
                eraserRadius[0] = eraserTool.getEraserRadius();
            }
            
            // 橡皮擦大小滑动条
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("橡皮擦大小");
            
            ImGui.tableNextColumn();
            ImGui.pushItemWidth(-1);
            if (ImGui.sliderFloat("##eraser_radius", eraserRadius, MIN_ERASER_RADIUS, MAX_ERASER_RADIUS, "%.1f")) {
                // 更新工具配置
                updateToolConfig(CONFIG_KEY_ERASER_RADIUS, String.valueOf(eraserRadius[0]));
                
                // 直接更新工具实例
                if (eraserTool != null) {
                    eraserTool.setEraserRadius(eraserRadius[0]);
                    PlotMod.LOGGER.info("橡皮擦半径已更新为: {}", eraserRadius[0]);
                }
            }
            ImGui.popItemWidth();
            height += ImGui.getFrameHeightWithSpacing();
            
            // 添加说明文本
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.tableNextColumn();
            ImGui.textWrapped("调整橡皮擦大小，数值越大擦除范围越大");
            height += ImGui.getTextLineHeightWithSpacing() * 2;
            
        } finally {
            ImGui.popID();
        }
        
        return height;
    }

    @Override
    public void initialize() {
        // 从当前工具中获取半径值
        EraserTool eraserTool = getCurrentEraserTool();
        if (eraserTool != null) {
            eraserRadius[0] = eraserTool.getEraserRadius();
            PlotMod.LOGGER.info("初始化橡皮擦半径: {}", eraserRadius[0]);
        } else {
            eraserRadius[0] = DEFAULT_ERASER_RADIUS;
            PlotMod.LOGGER.warn("无法获取橡皮擦工具实例，使用默认半径: {}", DEFAULT_ERASER_RADIUS);
        }
    }
    
    /**
     * 获取当前橡皮擦工具实例
     * @return 橡皮擦工具实例，如果当前工具不是橡皮擦则返回null
     */
    private EraserTool getCurrentEraserTool() {
        AppState appState = AppState.getInstance();
        if (appState != null) {
            BaseTool currentTool = appState.getCurrentTool();
            if (currentTool instanceof EraserTool) {
                return (EraserTool) currentTool;
            }
        }
        return null;
    }

    @Override
    public void cleanup() {
        // 无需清理资源
    }
} 