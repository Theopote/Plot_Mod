package com.plot.plugin.powerline.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 预览/地形适配阶段的派生布局，不写入持久化 footprint。
 * <p>
 * TerrainFit 等自动修正只修改此类；用户显式编辑仍落在 {@link PowerLineFootprint}。
 */
public final class PowerLineDerivedLayout {
    private final List<PoleLayoutConstraint> autoLayoutConstraints = new ArrayList<>();
    private final List<PoleOverride> autoPoleOverrides = new ArrayList<>();
    private Double poleHeightOverride;

    public List<PoleLayoutConstraint> autoLayoutConstraints() {
        return List.copyOf(autoLayoutConstraints);
    }

    public List<PoleOverride> autoPoleOverrides() {
        return List.copyOf(autoPoleOverrides);
    }

    public Double poleHeightOverride() {
        return poleHeightOverride;
    }

    public void addAutoLayoutConstraint(PoleLayoutConstraint constraint) {
        if (constraint != null) {
            autoLayoutConstraints.add(constraint.copy());
        }
    }

    public void addAutoPoleOverride(PoleOverride override) {
        if (override != null) {
            autoPoleOverrides.add(override.copy());
        }
    }

    public void clearAutoLayoutConstraints() {
        autoLayoutConstraints.clear();
    }

    public void removeAutoLayoutConstraint(int index) {
        if (index >= 0 && index < autoLayoutConstraints.size()) {
            autoLayoutConstraints.remove(index);
        }
    }

    public void clearAutoPoleOverrides() {
        autoPoleOverrides.clear();
    }

    public void setPoleHeightOverride(double poleHeight) {
        this.poleHeightOverride = poleHeight;
    }

    public void clearPoleHeightOverride() {
        this.poleHeightOverride = null;
    }

    public void clear() {
        autoLayoutConstraints.clear();
        autoPoleOverrides.clear();
        poleHeightOverride = null;
    }

    public boolean isEmpty() {
        return autoLayoutConstraints.isEmpty()
            && autoPoleOverrides.isEmpty()
            && poleHeightOverride == null;
    }
}
