package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * 视觉 / 常识性线路检查入口。
 * <p>
 * 保留 {@link PowerLineValidationReport} 数据结构；检查项见 {@link LineValidationCheck} 实现类。
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

    public static PowerLineValidationReport validate(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            PowerLineFootprint footprint) {
        return validate(geometry, terrain, footprint, ValidationLimits.fromFootprint(footprint));
    }

    public static PowerLineValidationReport validate(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            PowerLineFootprint footprint,
            ValidationLimits limits) {
        PowerLineValidationReport report = new PowerLineValidationReport();
        if (geometry == null || limits == null) {
            return report;
        }
        report.setProfileId(VALIDATION_PROFILE_ID);
        report.setProfileName(null);
        LineValidationContext context = new LineValidationContext(
            geometry, terrain, footprint, limits);
        for (LineValidationCheck check : CHECKS) {
            check.apply(context, report);
        }
        return report;
    }
}
