package com.plot.api.graphics;

import java.awt.Color;

/**
 * 填充样式接口
 */
public interface IFillStyle {
    /**
     * 获取填充颜色
     */
    Color getColor();
    
    /**
     * 设置填充颜色
     */
    void setColor(Color color);
    
    /**
     * 获取透明度
     */
    float getOpacity();
    
    /**
     * 设置透明度
     */
    void setOpacity(float opacity);
    
    /**
     * 是否可见
     */
    boolean isVisible();
    
    /**
     * 设置可见性
     */
    void setVisible(boolean visible);
    
    /**
     * 克隆样式
     */
    IFillStyle clone();
} 