package com.plot.api.graphics;

import java.awt.Color;

/**
 * 文本样式接口
 */
public interface ITextStyle {
    /**
     * 获取字体名称
     */
    String getFontName();
    
    /**
     * 设置字体名称
     */
    void setFontName(String name);
    
    /**
     * 获取字体大小
     */
    float getFontSize();
    
    /**
     * 设置字体大小
     */
    void setFontSize(float size);
    
    /**
     * 获取字体颜色
     */
    Color getColor();
    
    /**
     * 设置字体颜色
     */
    void setColor(Color color);
    
    /**
     * 是否加粗
     */
    boolean isBold();
    
    /**
     * 设置加粗
     */
    void setBold(boolean bold);
    
    /**
     * 是否斜体
     */
    boolean isItalic();
    
    /**
     * 设置斜体
     */
    void setItalic(boolean italic);
    
    /**
     * 获取行高
     */
    float getLineHeight();
    
    /**
     * 设置行高
     */
    void setLineHeight(float height);
    
    /**
     * 获取字体颜色
     */
    int getFontColor();
    
    /**
     * 设置字体颜色
     */
    void setFontColor(int color);
    
    /**
     * 获取字体家族
     */
    String getFontFamily();
    
    /**
     * 设置字体家族
     */
    void setFontFamily(String family);
    
    /**
     * 克隆样式
     */
    ITextStyle clone();
} 