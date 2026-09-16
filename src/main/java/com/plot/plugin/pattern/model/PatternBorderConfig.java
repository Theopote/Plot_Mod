package com.plot.plugin.pattern.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 图案边框配置，用于为图案添加边框或边缘处理
 */
public class PatternBorderConfig {
    public enum BorderStyle {
        NONE,           // 无边框
        SOLID,          // 实线边框
        DASHED,         // 虚线边框
        DOTTED,         // 点线边框
        DOUBLE,         // 双线边框
        OUTLINE         // 轮廓边框
    }

    private BorderStyle style = BorderStyle.NONE;
    private List<String> borderMaterials = new ArrayList<>();
    private double borderWidth = 1.0;
    private boolean innerBorder = false;
    private boolean outerBorder = true;
    private double cornerRadius = 0.0;
    private boolean enabled = false;

    public PatternBorderConfig() {
        // 默认边框材质
        borderMaterials.add("minecraft:stone_bricks");
    }

    public BorderStyle getStyle() {
        return style != null ? style : BorderStyle.NONE;
    }

    public void setStyle(BorderStyle style) {
        this.style = style != null ? style : BorderStyle.NONE;
    }

    public List<String> getBorderMaterials() {
        return new ArrayList<>(borderMaterials);
    }

    public void setBorderMaterials(List<String> borderMaterials) {
        this.borderMaterials = borderMaterials != null ? new ArrayList<>(borderMaterials) : new ArrayList<>();
        if (this.borderMaterials.isEmpty()) {
            this.borderMaterials.add("minecraft:stone_bricks");
        }
    }

    public double getBorderWidth() {
        return Math.max(0.5, Math.min(5.0, borderWidth));
    }

    public void setBorderWidth(double borderWidth) {
        this.borderWidth = Math.max(0.5, Math.min(5.0, borderWidth));
    }

    public boolean isInnerBorder() {
        return innerBorder;
    }

    public void setInnerBorder(boolean innerBorder) {
        this.innerBorder = innerBorder;
    }

    public boolean isOuterBorder() {
        return outerBorder;
    }

    public void setOuterBorder(boolean outerBorder) {
        this.outerBorder = outerBorder;
    }

    public double getCornerRadius() {
        return Math.max(0.0, Math.min(3.0, cornerRadius));
    }

    public void setCornerRadius(double cornerRadius) {
        this.cornerRadius = Math.max(0.0, Math.min(3.0, cornerRadius));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrimaryBorderMaterial() {
        return borderMaterials.isEmpty() ? "minecraft:stone_bricks" : borderMaterials.get(0);
    }

    public PatternBorderConfig copy() {
        PatternBorderConfig copy = new PatternBorderConfig();
        copy.style = this.style;
        copy.borderMaterials = new ArrayList<>(this.borderMaterials);
        copy.borderWidth = this.borderWidth;
        copy.innerBorder = this.innerBorder;
        copy.outerBorder = this.outerBorder;
        copy.cornerRadius = this.cornerRadius;
        copy.enabled = this.enabled;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternBorderConfig that = (PatternBorderConfig) o;
        return Double.compare(that.borderWidth, borderWidth) == 0 &&
               innerBorder == that.innerBorder &&
               outerBorder == that.outerBorder &&
               Double.compare(that.cornerRadius, cornerRadius) == 0 &&
               enabled == that.enabled &&
               style == that.style &&
               Objects.equals(borderMaterials, that.borderMaterials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(style, borderMaterials, borderWidth, innerBorder, outerBorder, cornerRadius, enabled);
    }
}