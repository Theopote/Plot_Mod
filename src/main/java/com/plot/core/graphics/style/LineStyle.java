package com.plot.core.graphics.style;

import java.awt.Color;
import com.plot.api.graphics.ILineStyle;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 线条样式类
 */
public class LineStyle implements ILineStyle {
    private static final Logger LOGGER = LoggerFactory.getLogger(LineStyle.class);
    
    /**
     * 线型枚举
     * 只包含实线和虚线两种类型
     */
    public enum LineType {
        SOLID,
        DASHED;

        public String getDisplayName() {
            return PlotI18n.lineTypeLabel(this);
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }
    
    private boolean visible;
    private LineType type;
    private Color color = Color.BLACK;
    private float width;
    private float[] dashPattern = null;
    
    public LineStyle() {
        this(LineType.SOLID, 1.0f);
    }
    
    public LineStyle(LineType type, float width) {
        this.type = type;
        this.width = width;
        this.visible = true;
        updateDashPattern();
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
    public Color getColor() {
        return color;
    }
    
    @Override
    public void setColor(Color color) {
        this.color = color;
    }
    
    /**
     * 设置颜色并返回当前实例（链式调用）
     * @param color 颜色
     * @return 当前实例
     */
    public LineStyle withColor(Color color) {
        setColor(color);
        return this;
    }
    
    /**
     * 设置颜色并返回当前实例（链式调用）
     * @param rgb RGB颜色值
     * @return 当前实例
     */
    public LineStyle withColor(int rgb) {
        setColor(new Color(rgb));
        return this;
    }
    
    @Override
    public float getWidth() {
        return width;
    }
    
    @Override
    public void setWidth(float width) {
        if (this.width != width) {
            // 线宽范围限制在0.1~5.0之间
            float newWidth = Math.max(0.1f, Math.min(width, 5.0f));
            this.width = newWidth;
            LOGGER.debug("设置线宽: {} -> {}", width, newWidth);
        }
    }
    
    @Override
    public LineType getType() {
        return type;
    }
    
    public void setType(LineType type) {
        if (this.type != type) {
            LineType oldType = this.type;
            this.type = type;
            updateDashPattern();
            LOGGER.debug("设置线型: {} -> {}", oldType, type);
        }
    }
    
    /**
     * 获取虚线模式
     */
    public float[] getDashPattern() {
        return dashPattern;
    }
    
    /**
     * 获取自定义线型模式
     * @return 自定义线型模式数组，如果不是自定义线型则返回null
     */
    public double[] getCustomPattern() {
        if (dashPattern == null) return null;
        
        // 将float[]转换为double[]
        double[] pattern = new double[dashPattern.length];
        for (int i = 0; i < dashPattern.length; i++) {
            pattern[i] = dashPattern[i];
        }
        return pattern;
    }
    
    /**
     * 更新虚线模式
     * 只处理实线和虚线两种类型
     */
    private void updateDashPattern() {
        switch (type) {
            case SOLID:
                dashPattern = null;
                break;
            case DASHED:
                dashPattern = new float[]{6.0f, 6.0f};
                break;
        }
    }
    
    @Override
    public ILineStyle clone() {
        LineStyle clone = new LineStyle();
        clone.color = new Color(color.getRGB(), true);
        clone.width = this.width;
        clone.visible = this.visible;
        clone.type = this.type;
        if (dashPattern != null) {
            clone.dashPattern = dashPattern.clone();
        }
        return clone;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineStyle that = (LineStyle) o;
        return visible == that.visible &&
               Float.compare(that.width, width) == 0 &&
               color.equals(that.color) &&
               type == that.type;
    }
    
    @Override
    public int hashCode() {
        int result = Boolean.hashCode(visible);
        result = 31 * result + color.hashCode();
        result = 31 * result + Float.hashCode(width);
        result = 31 * result + type.hashCode();
        return result;
    }
}
