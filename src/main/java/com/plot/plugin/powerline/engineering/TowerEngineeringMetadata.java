package com.plot.plugin.powerline.engineering;

import com.plot.plugin.powerline.model.TowerRole;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** 杆塔规划能力元数据（非结构计算结果）。间距权威来源是 {@code PoleSpacingProfile}，此处不含 preferredSpan。 */
public class TowerEngineeringMetadata {
    private double nominalHeight = 18.0;
    private double maxRecommendedSpan = 40.0;
    private double maxRecommendedDeflectionAngle = 5.0;
    private Set<TowerRole> supportedRoles = EnumSet.of(TowerRole.SUSPENSION);

    public double getNominalHeight() {
        return nominalHeight;
    }

    public void setNominalHeight(double nominalHeight) {
        this.nominalHeight = Math.max(1.0, nominalHeight);
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
        if (supportedRoles == null) {
            this.supportedRoles = EnumSet.of(TowerRole.SUSPENSION);
            return;
        }
        this.supportedRoles = supportedRoles.isEmpty()
            ? EnumSet.noneOf(TowerRole.class)
            : EnumSet.copyOf(supportedRoles);
    }

    public boolean supportsRole(TowerRole role) {
        return supportedRoles != null && supportedRoles.contains(role);
    }

    public TowerEngineeringMetadata copy() {
        TowerEngineeringMetadata copy = new TowerEngineeringMetadata();
        copy.nominalHeight = nominalHeight;
        copy.maxRecommendedSpan = maxRecommendedSpan;
        copy.maxRecommendedDeflectionAngle = maxRecommendedDeflectionAngle;
        copy.supportedRoles = getSupportedRoles();
        return copy;
    }

    public static TowerEngineeringMetadata defaultsForRole(TowerRole role) {
        TowerEngineeringMetadata metadata = new TowerEngineeringMetadata();
        metadata.setSupportedRoles(EnumSet.of(role != null ? role : TowerRole.SUSPENSION));
        switch (role != null ? role : TowerRole.SUSPENSION) {
            case ANGLE -> metadata.setMaxRecommendedDeflectionAngle(60.0);
            case DEAD_END, TERMINAL -> metadata.setMaxRecommendedDeflectionAngle(90.0);
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
            && Objects.equals(supportedRoles, other.supportedRoles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nominalHeight, maxRecommendedSpan, maxRecommendedDeflectionAngle, supportedRoles);
    }
}
