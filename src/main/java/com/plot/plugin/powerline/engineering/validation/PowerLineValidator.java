package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;

import java.util.List;

/**
 * 视觉 / 常识性线路检查入口。
 * <p>
 * 保留 {@link LineEngineeringReport} 数据结构；检查项见 {@link LineValidationCheck} 实现类。
 */
public final class PowerLineValidator {
    private static final String VALIDATION_PROFILE_ID = "validation/common_sense";

    private static final List<LineValidationCheck> CHECKS = List.of(
        new SpacingCheck(),
        new TerrainCollisionCheck(),
        new SagCheck(),
        new CornerCheck(),
        new WireOverlapCheck()
    );

    private PowerLineValidator() {
    }

    public static LineEngineeringReport validate(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            PowerLineFootprint footprint) {
        EngineeringRuleProfile profile = footprint != null
            ? new EngineeringRuleProfileResolver().find(footprint.effectiveEngineeringProfileId())
            : defaultProfile();
        ValidationLimits limits = ValidationLimits.fromFootprint(footprint, profile);
        return validate(geometry, terrain, footprint, profile, limits);
    }

    public static LineEngineeringReport validate(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            PowerLineFootprint footprint,
            EngineeringRuleProfile profile,
            ValidationLimits limits) {
        LineEngineeringReport report = new LineEngineeringReport();
        if (geometry == null || profile == null || limits == null) {
            return report;
        }
        report.setProfileId(VALIDATION_PROFILE_ID);
        report.setProfileName(null);
        LineValidationContext context = new LineValidationContext(
            geometry, terrain, footprint, profile, limits);
        for (LineValidationCheck check : CHECKS) {
            check.apply(context, report);
        }
        return report;
    }

    private static EngineeringRuleProfile defaultProfile() {
        return new EngineeringRuleProfileResolver()
            .find(EngineeringRuleProfile.GENERIC_PLANNING_ID);
    }
}
