package com.masterplanner.core.graphics.style;

import java.awt.Color;
import com.masterplanner.api.graphics.IFillStyle;

/**
 * 填充样式类
 */
public class FillStyle implements IFillStyle {
    private Color color;
    private float opacity;
    private boolean visible = true;
    
    public FillStyle() {
        this(Color.WHITE, 1.0f);
    }
    
    public FillStyle(Color color, float opacity) {
        this.color = color;
        this.opacity = opacity;
    }
    
    @Override
    public Color getColor() {
        return color;
    }
    
    @Override
    public void setColor(Color color) {
        this.color = color;
    }
    
    /**
     * 设置颜色并返回当前实例（链式调用）
     */
    public FillStyle withColor(Color color) {
        setColor(color);
        return this;
    }
    
    @Override
    public float getOpacity() {
        return opacity;
    }
    
    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }
    
    @Override
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    @Override
    public IFillStyle clone() {
        FillStyle clone = new FillStyle();
        clone.color = new Color(color.getRGB(), true);
        clone.opacity = this.opacity;
        clone.visible = this.visible;
        return clone;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FillStyle that = (FillStyle) o;
        return Float.compare(that.opacity, opacity) == 0 &&
               visible == that.visible &&
               color.equals(that.color);
    }
    
    @Override
    public int hashCode() {
        int result = color.hashCode();
        result = 31 * result + Float.hashCode(opacity);
        result = 31 * result + Boolean.hashCode(visible);
        return result;
    }
}