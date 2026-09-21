package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 杆塔单层定义。
 */
public class PoleLayer {
    public static final int COLUMN_MAX_HEIGHT = 32;
    public static final int CAP_MAX_HEIGHT = 8;

    public enum Shape {
        COLUMN,
        /** 横担层；多层时最上方（层栈中最后一个）横担为导线悬挂层。 */
        CROSSARM,
        CAP;

        /** 未知 / 空白 → null，由调用方跳过该层。 */
        public static Shape parseOrNull(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Shape.valueOf(raw.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private Shape shape = Shape.COLUMN;
    private int height = 1;
    private int crossarmLength = 3;
    private CrossarmSupport crossarmSupport = CrossarmSupport.NONE;
    private int crossarmSupportDepth = 3;
    private MaterialMix crossarmBraceMaterial;
    private MaterialMix material = MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);

    public PoleLayer() {
    }

    public PoleLayer(Shape shape, int height, MaterialMix material) {
        setShape(shape);
        setHeight(height);
        setMaterial(material);
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape != null ? shape : Shape.COLUMN;
        if (this.shape == Shape.CAP) {
            this.height = clampHeight(Shape.CAP, this.height);
        }
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = clampHeight(shape, height);
    }

    public static int maxHeightForShape(Shape shape) {
        return shape == Shape.CAP ? CAP_MAX_HEIGHT : COLUMN_MAX_HEIGHT;
    }

    private static int clampHeight(Shape shape, int height) {
        int max = maxHeightForShape(shape);
        return Math.max(1, Math.min(max, height));
    }

    public int getCrossarmLength() {
        return crossarmLength;
    }

    public void setCrossarmLength(int crossarmLength) {
        int normalized = Math.max(1, crossarmLength);
        if (normalized % 2 == 0) {
            normalized += 1;
        }
        this.crossarmLength = Math.min(15, normalized);
    }

    public CrossarmSupport getCrossarmSupport() {
        return crossarmSupport != null ? crossarmSupport : CrossarmSupport.NONE;
    }

    public void setCrossarmSupport(CrossarmSupport crossarmSupport) {
        this.crossarmSupport = crossarmSupport != null ? crossarmSupport : CrossarmSupport.NONE;
    }

    public int getCrossarmSupportDepth() {
        return crossarmSupportDepth;
    }

    public void setCrossarmSupportDepth(int crossarmSupportDepth) {
        this.crossarmSupportDepth = Math.max(1, Math.min(8, crossarmSupportDepth));
    }

    public MaterialMix getCrossarmBraceMaterial() {
        return crossarmBraceMaterial;
    }

    public void setCrossarmBraceMaterial(MaterialMix crossarmBraceMaterial) {
        this.crossarmBraceMaterial = crossarmBraceMaterial != null ? crossarmBraceMaterial.copy() : null;
    }

    /** 斜撑材质；未指定时回退到横担点缀或实体木板（厚重木构，非栅栏）。 */
    public MaterialMix resolveCrossarmBraceMaterial() {
        if (crossarmBraceMaterial != null
                && crossarmBraceMaterial.getPrimaryMaterial() != null
                && !crossarmBraceMaterial.getPrimaryMaterial().isBlank()) {
            return crossarmBraceMaterial;
        }
        if (material != null && material.hasAccent()) {
            return MaterialMix.single(material.getAccentMaterial());
        }
        return MaterialMix.single("minecraft:spruce_planks");
    }

    public MaterialMix getMaterial() {
        return material;
    }

    public void setMaterial(MaterialMix material) {
        this.material = material != null
            ? material.copy()
            : MaterialMix.single(PowerLineFootprint.DEFAULT_POLE_MATERIAL);
    }

    public PoleLayer copy() {
        PoleLayer copy = new PoleLayer(shape, height, material);
        copy.crossarmLength = crossarmLength;
        copy.crossarmSupport = crossarmSupport;
        copy.crossarmSupportDepth = crossarmSupportDepth;
        copy.crossarmBraceMaterial = crossarmBraceMaterial != null ? crossarmBraceMaterial.copy() : null;
        return copy;
    }
}
