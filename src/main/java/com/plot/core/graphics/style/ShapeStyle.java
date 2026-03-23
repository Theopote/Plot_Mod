package com.plot.core.graphics.style;

import java.awt.Color;
import java.util.Objects;

import com.plot.api.graphics.IShapeStyle;
import com.plot.api.graphics.ILineStyle;
import com.plot.api.graphics.IFillStyle;

/**
 * 形状样式类，包含线条和填充样式
 */
public class ShapeStyle implements IShapeStyle, Cloneable {
    private LineStyle lineStyle;
    private FillStyle fillStyle;
    private Color color;
    private float opacity;
    private boolean visible = true;  // 添加可见性属性
    
    // 修复：添加明确的标志位来跟踪是否应该跟随图层样式
    // 这替代了不可靠的启发式猜测方法
    private boolean followsLayerStyle = true;  // 默认跟随图层样式
    
    private java.awt.Color lineColor = java.awt.Color.BLACK;
    private float lineWidth = 1.0f;
    
    // 预定义样式
    public static final ShapeStyle DEFAULT = new ShapeStyle(
        new LineStyle(LineStyle.LineType.SOLID, 1.0f).withColor(Color.BLACK),
        new FillStyle(Color.WHITE, 0.5f)
    );
    
    public static final ShapeStyle PREVIEW = new ShapeStyle(
        new LineStyle(LineStyle.LineType.SOLID, 1.0f).withColor(new Color(0, 120, 215)),
        new FillStyle(new Color(0, 120, 215, 128), 0.3f)
    );
    
    public static final ShapeStyle SELECTED = new ShapeStyle(
        new LineStyle(LineStyle.LineType.SOLID, 2.5f).withColor(new Color(255, 215, 0)), // 亮黄色
        new FillStyle(new Color(255, 215, 0, 80), 0.3f) // 半透明亮黄色填充
    );
    
    public static final ShapeStyle HIGHLIGHTED = new ShapeStyle(
        new LineStyle(LineStyle.LineType.SOLID, 2.0f).withColor(new Color(255, 140, 0)), // 橙色
        new FillStyle(new Color(255, 140, 0, 64), 0.2f)
    );
    
    public ShapeStyle() {
        this.lineStyle = new LineStyle();
        this.fillStyle = new FillStyle();
        this.color = Color.BLACK;
        this.opacity = 1.0f;
    }
    
    public ShapeStyle(LineStyle lineStyle, FillStyle fillStyle) {
        this.lineStyle = lineStyle;
        this.fillStyle = fillStyle;
        this.color = Color.BLACK;
        this.opacity = 1.0f;
    }
    
    @Override
    public ILineStyle getLineStyle() {
        return lineStyle;
    }
    
    @Override
    public void setLineStyle(ILineStyle lineStyle) {
        if (lineStyle instanceof LineStyle) {
            this.lineStyle = (LineStyle) lineStyle;
            // 修复：用户手动设置线条样式时，标记为不再跟随图层样式
            this.followsLayerStyle = false;
        }
    }
    
    /**
     * 内部方法：设置线条样式但不改变图层跟随状态
     * 修复：专用于图层样式更新，避免触发followsLayerStyle标志
     */
    public void internalSetLineStyle(ILineStyle lineStyle) {
        if (lineStyle instanceof LineStyle) {
            this.lineStyle = (LineStyle) lineStyle;
        }
    }
    
    @Override
    public IFillStyle getFillStyle() {
        return fillStyle;
    }
    
    @Override
    public void setFillStyle(IFillStyle fillStyle) {
        if (fillStyle instanceof FillStyle) {
            this.fillStyle = (FillStyle) fillStyle;
        }
    }
    
    /**
     * @deprecated Use {@link #setLineStyle(ILineStyle)} instead
     */
    @Deprecated
    public void setLineStyle(LineStyle lineStyle) {
        this.lineStyle = lineStyle;
    }
    
    /**
     * @deprecated Use {@link #setFillStyle(IFillStyle)} instead
     */
    @Deprecated
    public void setFillStyle(FillStyle fillStyle) {
        this.fillStyle = fillStyle;
    }
    
    public Color getColor() {
        return lineStyle != null ? lineStyle.getColor() : color;
    }
    
    public void setColor(Color color) {
        this.color = color;
        // 修复：用户手动设置颜色时，标记为不再跟随图层样式
        this.followsLayerStyle = false;
        
        // 同步更新线条和填充颜色
        if (lineStyle != null) {
            lineStyle.setColor(color);
        }
        if (fillStyle != null) {
            fillStyle.setColor(new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.round(opacity * 255)
            ));
        }
    }
    
    /**
     * 内部方法：设置颜色但不改变图层跟随状态
     * 修复：专用于图层样式更新，避免触发followsLayerStyle标志
     */
    public void internalSetColor(Color color) {
        this.color = color;
        // 同步更新线条和填充颜色
        if (lineStyle != null) {
            lineStyle.setColor(color);
        }
        if (fillStyle != null) {
            fillStyle.setColor(new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.round(opacity * 255)
            ));
        }
    }
    
    public float getOpacity() {
        return opacity;
    }
    
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        // 修复：用户手动设置透明度时，标记为不再跟随图层样式
        this.followsLayerStyle = false;
        
        // 更新填充颜色的透明度
        if (fillStyle != null && color != null) {
            fillStyle.setColor(new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.round(this.opacity * 255)
            ));
        }
    }
    
    /**
     * 内部方法：设置透明度但不改变图层跟随状态
     * 修复：专用于图层样式更新，避免触发followsLayerStyle标志
     */
    public void internalSetOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        // 更新填充颜色的透明度
        if (fillStyle != null && color != null) {
            fillStyle.setColor(new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.round(this.opacity * 255)
            ));
        }
    }
    
    @Override
    public IShapeStyle clone() {
        try {
            ShapeStyle clone = (ShapeStyle) super.clone();
            // 转换为具体类型进行克隆
            clone.lineStyle = lineStyle != null ? (LineStyle) lineStyle.clone() : null;
            clone.fillStyle = fillStyle != null ? (FillStyle) fillStyle.clone() : null;
            clone.color = new Color(color.getRGB(), true);
            clone.visible = this.visible;
            clone.followsLayerStyle = this.followsLayerStyle;  // 修复：包含新的标志位
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShapeStyle that = (ShapeStyle) o;
        return Float.compare(that.opacity, opacity) == 0 &&
               visible == that.visible &&
               followsLayerStyle == that.followsLayerStyle &&  // 修复：包含新的标志位
               (Objects.equals(lineStyle, that.lineStyle)) &&
               (Objects.equals(fillStyle, that.fillStyle)) &&
               (Objects.equals(color, that.color));
    }
    
    @Override
    public int hashCode() {
        int result = lineStyle != null ? lineStyle.hashCode() : 0;
        result = 31 * result + (fillStyle != null ? fillStyle.hashCode() : 0);
        result = 31 * result + (color != null ? color.hashCode() : 0);
        result = 31 * result + Float.hashCode(opacity);
        return result;
    }
    
    // Getters
    public java.awt.Color getLineColor() {
        if (lineStyle != null && lineStyle.getColor() != null) {
            return lineStyle.getColor();
        }
        return lineColor;
    }
    public float getLineWidth() { return lineWidth; }
    
    // Setters
    public void setLineColor(java.awt.Color color) {
        this.lineColor = color != null ? color : java.awt.Color.BLACK;
        if (lineStyle != null) {
            lineStyle.setColor(this.lineColor);
        }
    }
    public void setLineWidth(float width) { this.lineWidth = width; }
    
    // Copy constructor
    public ShapeStyle(ShapeStyle other) {
        this.lineColor = other.lineColor;
        this.lineWidth = other.lineWidth;
    }

    /**
     * 检查形状是否可见
     * @return true 如果形状可见，否则返回 false
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 设置形状的可见性
     * @param visible 可见性
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 检查样式是否跟随图层样式
     * 修复：提供明确的状态查询，替代启发式猜测
     * @return true 如果样式跟随图层，false 如果是用户自定义的
     */
    public boolean doesFollowLayerStyle() {
        return this.followsLayerStyle;
    }
    
    /**
     * 设置是否跟随图层样式
     * @param followsLayerStyle 是否跟随图层样式
     */
    public void setFollowsLayerStyle(boolean followsLayerStyle) {
        this.followsLayerStyle = followsLayerStyle;
    }

    /**
     * 获取线条宽度
     */
    public float getWidth() {
        return lineStyle != null ? lineStyle.getWidth() : lineWidth;
    }

    @Override
    public int getFillColor() {
        return fillStyle != null ? fillStyle.getColor().getRGB() : 0;
    }

    @Override
    public void setFillColor(int color) {
        this.fillStyle = new FillStyle(new Color(color), fillStyle.getOpacity());
    }

    /**
     * 设置填充颜色（接受java.awt.Color参数）
     * @param color 颜色
     */
    public void setFillColor(java.awt.Color color) {
        if (color == null) {
            color = java.awt.Color.WHITE;
        }
        setFillColor(color.getRGB());
    }

    @Override
    public int getStrokeColor() {
        return lineStyle != null ? lineStyle.getColor().getRGB() : 0;
    }

    @Override
    public void setStrokeColor(int color) {
        LineStyle newLineStyle = new LineStyle(lineStyle.getType(), lineWidth);
        newLineStyle.setColor(new Color(color));
        this.lineStyle = newLineStyle;
    }

    /**
     * 设置描边颜色（接受java.awt.Color参数）
     * @param color 颜色
     */
    public void setStrokeColor(java.awt.Color color) {
        if (color == null) {
            color = java.awt.Color.BLACK;
        }
        setStrokeColor(color.getRGB());
    }

    @Override
    public float getStrokeWidth() {
        return lineStyle != null ? lineStyle.getWidth() : lineWidth;
    }

    @Override
    public void setStrokeWidth(float width) {
        this.lineWidth = width;
    }

    /**
     * 设置描边样式
     * @param style 线条样式类型
     */
    public void setStrokeStyle(LineStyle.LineType style) {
        if (this.lineStyle == null) {
            this.lineStyle = new LineStyle(style, this.lineWidth);
        } else if (this.lineStyle instanceof LineStyle) {
            this.lineStyle.setType(style);
        } else {
            // 创建新的LineStyle并保留原有属性
            LineStyle newLineStyle = new LineStyle(style, this.lineStyle.getWidth());
            newLineStyle.setColor(new Color(this.lineStyle.getColor().getRGB()));
            newLineStyle.setVisible(this.lineStyle.isVisible());
            this.lineStyle = newLineStyle;
        }
    }
}
