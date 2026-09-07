package com.plot.plugin.powerline.design;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/**
 * 杆塔单层定义。
 */
public class PoleLayer {
    public enum Shape {
        COLUMN,
        /** 横担层；多层时最上方（层栈中最后一个）横担为导线悬挂层。 */
        CROSSARM,
        CAP
    }

    private Shape shape = Shape.COLUMN;
    private int height = 1;
    private int crossarmLength = 3;
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
            this.height = 1;
        }
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        if (shape == Shape.CAP) {
            this.height = 1;
            return;
        }
        this.height = Math.max(1, Math.min(32, height));
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
        return copy;
    }
}
