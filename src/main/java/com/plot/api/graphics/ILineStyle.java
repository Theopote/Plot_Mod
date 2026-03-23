package com.plot.api.graphics;

import java.awt.Color;
import com.plot.core.graphics.style.LineStyle.LineType;

/**
 * 线条样式接口
 */
public interface ILineStyle {
    /**
     * 获取线条颜色
     */
    Color getColor();
    
    /**
     * 设置线条颜色
     */
    void setColor(Color color);
    
    /**
     * 获取线条宽度
     */
    float getWidth();
    
    /**
     * 设置线条宽度
     */
    void setWidth(float width);
    
    /**
     * 是否可见
     */
    boolean isVisible();
    
    /**
     * 设置可见性
     */
    void setVisible(boolean visible);
    
    /**
     * 获取线条类型
     */
    LineType getType();
    
    /**
     * 克隆样式
     */
    ILineStyle clone();
} 