package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;

import java.util.Objects;
import java.util.UUID;

/** 塔身横担结构（与 ConductorAttachment 独立）。 */
public class TowerArm {
    private String id;
    private double baseHeight;
    private TowerArmSide side = TowerArmSide.BOTH;
    private double lateralReach = 3.0;
    private double longitudinalHalfWidth = 0.5;
    private double verticalDrop;
    private BracingPattern bracing = BracingPattern.NONE;
    private MaterialMix material;

    public TowerArm() {
        this.id = UUID.randomUUID().toString();
    }

    public TowerArm(String id, double baseHeight, double lateralReach) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        setBaseHeight(baseHeight);
        setLateralReach(lateralReach);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    public double getBaseHeight() {
        return baseHeight;
    }

    public void setBaseHeight(double baseHeight) {
        if (!Double.isFinite(baseHeight)) {
            throw new IllegalArgumentException("baseHeight must be finite");
        }
        this.baseHeight = Math.max(0.0, baseHeight);
    }

    public TowerArmSide getSide() {
        return side != null ? side : TowerArmSide.BOTH;
    }

    public void setSide(TowerArmSide side) {
        this.side = side;
    }

    public double getLateralReach() {
        return lateralReach;
    }

    public void setLateralReach(double lateralReach) {
        if (!Double.isFinite(lateralReach)) {
            throw new IllegalArgumentException("lateralReach must be finite");
        }
        this.lateralReach = Math.max(0.0, lateralReach);
    }

    public double getLongitudinalHalfWidth() {
        return longitudinalHalfWidth;
    }

    public void setLongitudinalHalfWidth(double longitudinalHalfWidth) {
        if (!Double.isFinite(longitudinalHalfWidth)) {
            throw new IllegalArgumentException("longitudinalHalfWidth must be finite");
        }
        this.longitudinalHalfWidth = Math.max(0.0, longitudinalHalfWidth);
    }

    public double getVerticalDrop() {
        return verticalDrop;
    }

    public void setVerticalDrop(double verticalDrop) {
        if (!Double.isFinite(verticalDrop)) {
            throw new IllegalArgumentException("verticalDrop must be finite");
        }
        this.verticalDrop = Math.max(0.0, verticalDrop);
    }

    public BracingPattern getBracing() {
        return bracing != null ? bracing : BracingPattern.NONE;
    }

    public void setBracing(BracingPattern bracing) {
        this.bracing = bracing;
    }

    public MaterialMix getMaterial() {
        return material;
    }

    public void setMaterial(MaterialMix material) {
        this.material = material;
    }

    public TowerArm copy() {
        TowerArm copy = new TowerArm(id, baseHeight, lateralReach);
        copy.side = side;
        copy.longitudinalHalfWidth = longitudinalHalfWidth;
        copy.verticalDrop = verticalDrop;
        copy.bracing = bracing;
        copy.material = material != null ? material.copy() : null;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TowerArm other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && Double.compare(baseHeight, other.baseHeight) == 0
            && getSide() == other.getSide()
            && Double.compare(lateralReach, other.lateralReach) == 0
            && Double.compare(longitudinalHalfWidth, other.longitudinalHalfWidth) == 0
            && Double.compare(verticalDrop, other.verticalDrop) == 0
            && getBracing() == other.getBracing();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            baseHeight,
            getSide(),
            lateralReach,
            longitudinalHalfWidth,
            verticalDrop,
            getBracing());
    }
}
