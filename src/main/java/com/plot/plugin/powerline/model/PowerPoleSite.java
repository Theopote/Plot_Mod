package com.plot.plugin.powerline.model;

import com.plot.api.geometry.Vec2d;

import java.util.Objects;
import java.util.UUID;

/** 生成前的线路杆塔节点（含角色与设计解析元数据）。 */
public class PowerPoleSite {
    private final String id;
    private Vec2d planPosition;
    private TowerRole role = TowerRole.SUSPENSION;
    private String poleDesignOverrideId;
    private boolean roleAutoAssigned = true;
    private double deflectionAngle;
    private double stationing;
    private int pathIndex;

    public PowerPoleSite(Vec2d planPosition) {
        this.id = UUID.randomUUID().toString();
        this.planPosition = planPosition != null ? planPosition.copy() : new Vec2d(0, 0);
    }

    public PowerPoleSite(String id, Vec2d planPosition) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.planPosition = planPosition != null ? planPosition.copy() : new Vec2d(0, 0);
    }

    public String getId() {
        return id;
    }

    public Vec2d getPlanPosition() {
        return planPosition.copy();
    }

    public void setPlanPosition(Vec2d planPosition) {
        this.planPosition = planPosition != null ? planPosition.copy() : new Vec2d(0, 0);
    }

    public TowerRole getRole() {
        return role != null ? role : TowerRole.SUSPENSION;
    }

    public void setRole(TowerRole role) {
        this.role = role != null ? role : TowerRole.SUSPENSION;
    }

    public String getPoleDesignOverrideId() {
        return poleDesignOverrideId;
    }

    public void setPoleDesignOverrideId(String poleDesignOverrideId) {
        this.poleDesignOverrideId = poleDesignOverrideId != null && poleDesignOverrideId.isBlank()
            ? null
            : poleDesignOverrideId;
    }

    public boolean isRoleAutoAssigned() {
        return roleAutoAssigned;
    }

    public void setRoleAutoAssigned(boolean roleAutoAssigned) {
        this.roleAutoAssigned = roleAutoAssigned;
    }

    public double getDeflectionAngle() {
        return deflectionAngle;
    }

    public void setDeflectionAngle(double deflectionAngle) {
        this.deflectionAngle = Math.max(0.0, Math.min(180.0, deflectionAngle));
    }

    public double getStationing() {
        return stationing;
    }

    public void setStationing(double stationing) {
        this.stationing = Math.max(0.0, stationing);
    }

    public int getPathIndex() {
        return pathIndex;
    }

    public void setPathIndex(int pathIndex) {
        this.pathIndex = Math.max(0, pathIndex);
    }

    public PowerPoleSite copy() {
        PowerPoleSite copy = new PowerPoleSite(id, planPosition);
        copy.role = role;
        copy.poleDesignOverrideId = poleDesignOverrideId;
        copy.roleAutoAssigned = roleAutoAssigned;
        copy.deflectionAngle = deflectionAngle;
        copy.stationing = stationing;
        copy.pathIndex = pathIndex;
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PowerPoleSite other)) {
            return false;
        }
        return Objects.equals(id, other.id)
            && Objects.equals(planPosition, other.planPosition)
            && getRole() == other.getRole()
            && Objects.equals(poleDesignOverrideId, other.poleDesignOverrideId)
            && roleAutoAssigned == other.roleAutoAssigned
            && Double.compare(deflectionAngle, other.deflectionAngle) == 0
            && Double.compare(stationing, other.stationing) == 0
            && pathIndex == other.pathIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            planPosition,
            getRole(),
            poleDesignOverrideId,
            roleAutoAssigned,
            deflectionAngle,
            stationing,
            pathIndex);
    }
}
