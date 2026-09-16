package com.plot.plugin.pattern.model;

import java.util.List;
import java.util.Objects;

/**
 * 图案边框 v1：实线边缘（外轮廓 / 孔洞），不含样式、圆角或多材质。
 */
public class PatternBorderConfig {
    private String borderMaterial = "minecraft:stone_bricks";
    private double borderWidth = 1.0;
    private boolean innerBorder = false;
    private boolean outerBorder = true;
    private boolean enabled = false;

    public String getBorderMaterial() {
        return borderMaterial != null && !borderMaterial.isBlank()
            ? borderMaterial
            : "minecraft:stone_bricks";
    }

    public void setBorderMaterial(String borderMaterial) {
        this.borderMaterial = borderMaterial != null && !borderMaterial.isBlank()
            ? borderMaterial.trim()
            : "minecraft:stone_bricks";
    }

    /** 仅用于旧版 JSON 迁移，取列表首项 */
    @Deprecated
    public void setBorderMaterials(List<String> borderMaterials) {
        if (borderMaterials != null && !borderMaterials.isEmpty()) {
            setBorderMaterial(borderMaterials.getFirst());
        }
    }

    public String getPrimaryBorderMaterial() {
        return getBorderMaterial();
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PatternBorderConfig copy() {
        PatternBorderConfig copy = new PatternBorderConfig();
        copy.borderMaterial = this.borderMaterial;
        copy.borderWidth = this.borderWidth;
        copy.innerBorder = this.innerBorder;
        copy.outerBorder = this.outerBorder;
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
               enabled == that.enabled &&
               Objects.equals(getBorderMaterial(), that.getBorderMaterial());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBorderMaterial(), borderWidth, innerBorder, outerBorder, enabled);
    }
}
