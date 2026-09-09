package com.plot.plugin.powerline.design.structure;

import com.plot.core.material.MaterialMix;

import java.util.Objects;
import java.util.UUID;

/** 塔体装饰模块（与导线挂点独立）。 */
public class TowerDecoration {
    private String id;
    private TowerDecorationKind kind = TowerDecorationKind.BEACON;
    /** 装饰基准高度（相对塔腿地面，与 {@link TowerArm#getBaseHeight()} 同坐标系）。 */
    private double baseHeight = 24.0;
    private double lateralOffset;
    private double longitudinalOffset;
    /** 天线桅杆高度或平台半宽（按类型解释）。 */
    private double size = 4.0;
    private MaterialMix material;
    private boolean enabled = true;

    public TowerDecoration() {
        this.id = UUID.randomUUID().toString();
    }

    public TowerDecoration(String id, TowerDecorationKind kind, double baseHeight) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.kind = kind != null ? kind : TowerDecorationKind.BEACON;
        setBaseHeight(baseHeight);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
    }

    public TowerDecorationKind getKind() {
        return kind != null ? kind : TowerDecorationKind.BEACON;
    }

    public void setKind(TowerDecorationKind kind) {
        this.kind = kind != null ? kind : TowerDecorationKind.BEACON;
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

    public double getLateralOffset() {
        return lateralOffset;
    }

    public void setLateralOffset(double lateralOffset) {
        if (!Double.isFinite(lateralOffset)) {
            throw new IllegalArgumentException("lateralOffset must be finite");
        }
        this.lateralOffset = lateralOffset;
    }

    public double getLongitudinalOffset() {
        return longitudinalOffset;
    }

    public void setLongitudinalOffset(double longitudinalOffset) {
        if (!Double.isFinite(longitudinalOffset)) {
            throw new IllegalArgumentException("longitudinalOffset must be finite");
        }
        this.longitudinalOffset = longitudinalOffset;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        if (!Double.isFinite(size)) {
            throw new IllegalArgumentException("size must be finite");
        }
        this.size = Math.max(0.5, size);
    }

    public MaterialMix getMaterial() {
        return material;
    }

    public void setMaterial(MaterialMix material) {
        this.material = material != null ? material.copy() : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TowerDecoration copy() {
        TowerDecoration copy = new TowerDecoration(id, kind, baseHeight);
        copy.lateralOffset = lateralOffset;
        copy.longitudinalOffset = longitudinalOffset;
        copy.size = size;
        copy.material = material != null ? material.copy() : null;
        copy.enabled = enabled;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TowerDecoration other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && getKind() == other.getKind()
            && Double.compare(baseHeight, other.baseHeight) == 0
            && Double.compare(lateralOffset, other.lateralOffset) == 0
            && Double.compare(longitudinalOffset, other.longitudinalOffset) == 0
            && Double.compare(size, other.size) == 0
            && enabled == other.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, baseHeight, lateralOffset, longitudinalOffset, size, enabled);
    }
}
