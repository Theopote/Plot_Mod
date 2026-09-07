package com.plot.plugin.powerline.design.structure;

import java.util.Objects;
import java.util.UUID;

/** 塔身竖向截面 station（pole-local 坐标）。 */
public class TowerStation {
    private String id;
    private double height;
    private double halfWidth;
    private double halfDepth;

    public TowerStation() {
        this.id = UUID.randomUUID().toString();
    }

    public TowerStation(String id, double height, double halfWidth, double halfDepth) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        setHeight(height);
        setHalfWidth(halfWidth);
        setHalfDepth(halfDepth);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        if (!Double.isFinite(height)) {
            throw new IllegalArgumentException("height must be finite");
        }
        this.height = Math.max(0.0, height);
    }

    public double getHalfWidth() {
        return halfWidth;
    }

    public void setHalfWidth(double halfWidth) {
        if (!Double.isFinite(halfWidth)) {
            throw new IllegalArgumentException("halfWidth must be finite");
        }
        this.halfWidth = Math.max(0.0, halfWidth);
    }

    public double getHalfDepth() {
        return halfDepth;
    }

    public void setHalfDepth(double halfDepth) {
        if (!Double.isFinite(halfDepth)) {
            throw new IllegalArgumentException("halfDepth must be finite");
        }
        this.halfDepth = Math.max(0.0, halfDepth);
    }

    public TowerStation copy() {
        return new TowerStation(id, height, halfWidth, halfDepth);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TowerStation other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && Double.compare(height, other.height) == 0
            && Double.compare(halfWidth, other.halfWidth) == 0
            && Double.compare(halfDepth, other.halfDepth) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, height, halfWidth, halfDepth);
    }
}
