package com.plot.plugin.powerline.engineering;

import com.plot.plugin.powerline.model.TowerRole;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** 杆塔规划能力元数据（非结构计算结果）。 */
public class TowerEngineeringMetadata {
    private double nominalHeight = 18.0;
    private double preferredSpan = 20.0;
    private double maxRecommendedSpan = 40.0;
    private double maxRecommendedDeflectionAngle = 5.0;
    private Set<TowerRole> supportedRoles = EnumSet.of(TowerRole.SUSPENSION);
    private int strengthClass = 1;

    public double getNominalHeight() {
        return nominalHeight;
    }

    public void setNominalHeight(double nominalHeight) {
        this.nominalHeight = Math.max(1.0, nominalHeight);
    }

    public double getPreferredSpan() {
        return preferredSpan;
    }

    public void setPreferredSpan(double preferredSpan) {
        this.preferredSpan = Math.max(1.0, preferredSpan);
    }

    public double getMaxRecommendedSpan() {
        return maxRecommendedSpan;
    }

    public void setMaxRecommendedSpan(double maxRecommendedSpan) {
        this.maxRecommendedSpan = Math.max(1.0, maxRecommendedSpan);
    }

    public double getMaxRecommendedDeflectionAngle() {
        return maxRecommendedDeflectionAngle;
    }

    public void setMaxRecommendedDeflectionAngle(double maxRecommendedDeflectionAngle) {
        this.maxRecommendedDeflectionAngle = Math.max(0.0, Math.min(180.0, maxRecommendedDeflectionAngle));
    }

    public Set<TowerRole> getSupportedRoles() {
        return supportedRoles != null ? EnumSet.copyOf(supportedRoles) : EnumSet.noneOf(TowerRole.class);
    }

    public void setSupportedRoles(Set<TowerRole> supportedRoles) {
        this.supportedRoles = supportedRoles != null && !supportedRoles.isEmpty()
            ? EnumSet.copyOf(supportedRoles)
            : EnumSet.of(TowerRole.SUSPENSION);
    }

    public boolean supportsRole(TowerRole role) {
        return supportedRoles != null && supportedRoles.contains(role);
    }

    public int getStrengthClass() {
        return strengthClass;
    }

    public void setStrengthClass(int strengthClass) {
        this.strengthClass = Math.max(1, strengthClass);
    }

    public TowerEngineeringMetadata copy() {
        TowerEngineeringMetadata copy = new TowerEngineeringMetadata();
        copy.nominalHeight = nominalHeight;
        copy.preferredSpan = preferredSpan;
        copy.maxRecommendedSpan = maxRecommendedSpan;
        copy.maxRecommendedDeflectionAngle = maxRecommendedDeflectionAngle;
        copy.supportedRoles = getSupportedRoles();
        copy.strengthClass = strengthClass;
        return copy;
    }

    public static TowerEngineeringMetadata defaultsForRole(TowerRole role) {
        TowerEngineeringMetadata metadata = new TowerEngineeringMetadata();
        metadata.setSupportedRoles(EnumSet.of(role != null ? role : TowerRole.SUSPENSION));
        switch (role != null ? role : TowerRole.SUSPENSION) {
            case ANGLE -> {
                metadata.setMaxRecommendedDeflectionAngle(60.0);
                metadata.setStrengthClass(2);
            }
            case DEAD_END, TERMINAL -> {
                metadata.setMaxRecommendedDeflectionAngle(90.0);
                metadata.setStrengthClass(3);
            }
            default -> metadata.setMaxRecommendedDeflectionAngle(5.0);
        }
        return metadata;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TowerEngineeringMetadata other)) {
            return false;
        }
        return Double.compare(nominalHeight, other.nominalHeight) == 0
            && Double.compare(maxRecommendedSpan, other.maxRecommendedSpan) == 0
            && Double.compare(maxRecommendedDeflectionAngle, other.maxRecommendedDeflectionAngle) == 0
            && strengthClass == other.strengthClass
            && Objects.equals(supportedRoles, other.supportedRoles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nominalHeight, maxRecommendedSpan, maxRecommendedDeflectionAngle, supportedRoles, strengthClass);
    }
}
