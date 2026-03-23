package com.plot.ui.grid;

import imgui.ImGui;

/**
 * 网格设置数据模型
 */
public class GridSettings {
    private float gridSize = 32.0f;         // 默认网格大小
    private float opacity = 0.4f;           // 默认透明度
    private float lineWidth = 1.0f;         // 默认线宽
    private float[] colorComponents = {
        125f/255f,  // R = 125
        125f/255f,  // G = 125
        125f/255f,  // B = 125
        1.0f        // A = 255
    }; // 默认颜色 (RGB: 125,125,125)

    // Getters and Setters
    public float getGridSize() {
        return gridSize;
    }

    public void setGridSize(float gridSize) {
        this.gridSize = gridSize;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public float getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }

    public float[] getColorComponents() {
        return colorComponents;
    }

    public void setColorComponents(float r, float g, float b) {
        this.colorComponents[0] = r;
        this.colorComponents[1] = g;
        this.colorComponents[2] = b;
    }

    public int getColor() {
        return ImGui.getColorU32(
            colorComponents[0],
            colorComponents[1],
            colorComponents[2],
            colorComponents[3]
        );
    }

    public void setColor(int packedColor) {
        // 将打包的颜色值转换回RGBA分量
        float r = ((packedColor >> 16) & 0xFF) / 255f;
        float g = ((packedColor >> 8) & 0xFF) / 255f;
        float b = (packedColor & 0xFF) / 255f;
        float a = ((packedColor >> 24) & 0xFF) / 255f;
        
        colorComponents[0] = r;
        colorComponents[1] = g;
        colorComponents[2] = b;
        colorComponents[3] = a;
    }

    /**
     * 获取带透明度的颜色值
     */
    public int getColorWithOpacity() {
        return ImGui.getColorU32(
            colorComponents[0],
            colorComponents[1],
            colorComponents[2],
            opacity
        );
    }
    
    @Override
    public String toString() {
        return String.format("GridSettings{大小=%.1f, 透明度=%.2f, 线宽=%.1f, 颜色=[%.2f,%.2f,%.2f,%.2f]}", 
            gridSize, opacity, lineWidth, 
            colorComponents[0], colorComponents[1], colorComponents[2], colorComponents[3]);
    }
} 