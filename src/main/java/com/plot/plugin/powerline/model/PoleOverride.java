package com.plot.plugin.powerline.model;

import java.util.Objects;

/** 按路径里程持久化的单杆覆盖（不依赖杆塔序号）。 */
public class PoleOverride {
    private double pathDistance;
    private TowerRole roleOverride;
    private String poleDesignOverrideId;

    public PoleOverride() {
    }

    public PoleOverride(double pathDistance) {
        this.pathDistance = Math.max(0.0, pathDistance);
    }

    public double getPathDistance() {
        return pathDistance;
    }

    public void setPathDistance(double pathDistance) {
        this.pathDistance = Math.max(0.0, pathDistance);
    }

    public TowerRole getRoleOverride() {
        return roleOverride;
    }

    public void setRoleOverride(TowerRole roleOverride) {
        this.roleOverride = roleOverride;
    }

    public String getPoleDesignOverrideId() {
        return poleDesignOverrideId;
    }

    public void setPoleDesignOverrideId(String poleDesignOverrideId) {
        this.poleDesignOverrideId = poleDesignOverrideId != null && poleDesignOverrideId.isBlank()
            ? null
            : poleDesignOverrideId;
    }

    public PoleOverride copy() {
        PoleOverride copy = new PoleOverride(pathDistance);
        copy.roleOverride = roleOverride;
        copy.poleDesignOverrideId = poleDesignOverrideId;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PoleOverride other)) {
            return false;
        }
        return Double.compare(pathDistance, other.pathDistance) == 0
            && roleOverride == other.roleOverride
            && Objects.equals(poleDesignOverrideId, other.poleDesignOverrideId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathDistance, roleOverride, poleDesignOverrideId);
    }
}
