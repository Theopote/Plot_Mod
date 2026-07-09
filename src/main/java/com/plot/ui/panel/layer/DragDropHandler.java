package com.plot.ui.panel.layer;

import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.utils.PlotI18n;
import com.plot.PlotMod;
import imgui.ImGui;

import java.util.function.Consumer;

public class DragDropHandler {
    // === 常量定义 ===
    private static final float LAYER_ITEM_HEIGHT = 24.0f;
    public static final float DRAG_THRESHOLD = 5.0f;
    
    // === 依赖项 ===
    private final Consumer<String> showWarningDialog;

    // === 状态字段 ===
    private boolean isDragging = false;
    private int dragSourceIndex = -1;
    private float dragStartY = 0;

    private final LayerManager layerManager; // Add this line

    public DragDropHandler(
            LayerManager layerManager,
            Consumer<String> showWarningDialog) {
        this.layerManager = layerManager;
        this.showWarningDialog = showWarningDialog;
    }

    public void handleDragStart(Layer layer, float mouseY) {
        if (!isDragging) {
            dragStartY = mouseY;
            dragSourceIndex = this.layerManager.getLayers().indexOf(layer); // Use this.layerManager
            long dragStartTime = System.currentTimeMillis();
        } else {
            float dragDistance = Math.abs(mouseY - dragStartY);
            if (dragDistance > DRAG_THRESHOLD) {
                isDragging = true;
            }
        }
    }
    
    public void handleDragEnd(Layer layer, float mouseY) {
        if (isDragging) {
            int targetIndex = calculateDropIndex(mouseY);
            if (targetIndex != -1 && targetIndex != dragSourceIndex) {
                handleLayerMove(layer, targetIndex);
            }
        }
        resetDragState();
    }
    
    public void cancelDrag() {
        resetDragState();
    }
    
    public boolean isDragging() {
        return isDragging;
    }
    
    private void handleLayerMove(Layer layer, int targetIndex) {
        try {
            layerManager.moveLayer(layer.getId(), targetIndex);
            PlotMod.LOGGER.debug("图层移动: {} -> 位置 {}",
                layer.getName(), targetIndex);
        } catch (Exception e) {
            PlotMod.LOGGER.error("图层移动失败: {}", e.getMessage(), e);
            showWarningDialog.accept(PlotI18n.tr("layer.plot.move_failed", e.getMessage()));
        }
    }
    
    private int calculateDropIndex(float mouseY) {
        try {
            float windowY = ImGui.getWindowPosY();
            float relativeY = mouseY - windowY;
            float itemTotalHeight = LAYER_ITEM_HEIGHT + 2;
            
            // 计算基础索引
            int index = (int)((relativeY - ImGui.getScrollY()) / itemTotalHeight);
            index = layerManager.getLayers().size() - 1 - index;
            
            // 边界检查
            if (index < 0) {
                index = 0;
            } else if (index > layerManager.getLayers().size()) {
                index = layerManager.getLayers().size();
            }
            
            // 精确定位（上半部分/下半部分）
            float itemY = windowY + (layerManager.getLayers().size() - 1 - index) * itemTotalHeight;
            if (mouseY > itemY + LAYER_ITEM_HEIGHT / 2) {
                index++;
            }
            
            PlotMod.LOGGER.debug("计算拖放目标索引: mouseY={}, index={}",
                mouseY, index);
            
            return index;
        } catch (Exception e) {
            PlotMod.LOGGER.error("计算拖放索引时发生错误: {}", e.getMessage(), e);
            return -1;
        }
    }
    
    private void resetDragState() {
        isDragging = false;
        dragSourceIndex = -1;
        dragStartY = 0;
    }
}
