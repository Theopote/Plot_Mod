package com.plot.plugin.powerline.engineering.analysis;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.validation.PowerLineValidator;
import com.plot.plugin.powerline.engineering.validation.ValidationLimits;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.road.terrain.TerrainSampler;

/**
 * @deprecated 使用 {@link PowerLineValidator}。保留以兼容旧调用点。
 */
@Deprecated
public final class PowerLineEngineeringAnalyzer {
    private PowerLineEngineeringAnalyzer() {
    }

    public static LineEngineeringReport analyze(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile) {
        ValidationLimits limits = profile != null
            ? ValidationLimits.fromFootprint(null, profile)
            : null;
        return analyze(geometry, terrain, profile, limits);
    }

    public static LineEngineeringReport analyze(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            EngineeringRuleProfile profile,
            ValidationLimits limits) {
        return PowerLineValidator.validate(geometry, terrain, null, profile, limits);
    }
}
