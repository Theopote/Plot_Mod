package com.plot.api.graphics;

/**
 * 形状样式接口
 */
public interface IShapeStyle {
    /**
     * 获取线条样式
     */
    ILineStyle getLineStyle();
    
    /**
     * 设置线条样式
     */
    void setLineStyle(ILineStyle lineStyle);
    
    /**
     * 获取填充样式
     */
    IFillStyle getFillStyle();
    
    /**
     * 设置填充样式
     */
    void setFillStyle(IFillStyle fillStyle);
    
    /**
     * 获取填充颜色
     */
    int getFillColor();
    
    /**
     * 设置填充颜色
     */
    void setFillColor(int color);
    
    /**
     * 获取线条颜色
     */
    int getStrokeColor();
    
    /**
     * 设置线条颜色
     */
    void setStrokeColor(int color);
    
    /**
     * 获取线条宽度
     */
    float getStrokeWidth();
    
    /**
     * 设置线条宽度
     */
    void setStrokeWidth(float width);
    
    /**
     * 克隆样式
     */
    IShapeStyle clone();
} 