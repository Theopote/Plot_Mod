package com.plot.plugin.powerline.engineering.validation;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.engineering.EngineeringRuleProfileResolver;
import com.plot.plugin.powerline.engineering.analysis.LineEngineeringReport;
import com.plot.plugin.powerline.engineering.analysis.PowerLineEngineeringAnalyzer;
import com.plot.plugin.powerline.geometry.PowerLineGeometryModel;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.road.terrain.TerrainSampler;

/**
 * 视觉 / 常识性线路检查入口。
 * <p>
 * 保留 {@link LineEngineeringReport} 数据结构；逐步替代面向工程规范的 profile 分析。
 */
public final class PowerLineValidator {
    private PowerLineValidator() {
    }

    public static LineEngineeringReport validate(
            PowerLineGeometryModel geometry,
            TerrainSampler terrain,
            PowerLineFootprint footprint) {
        if (footprint == null) {
            return PowerLineEngineeringAnalyzer.analyze(geometry, terrain, defaultProfile(), null);
        }
        EngineeringRuleProfile profile = new EngineeringRuleProfileResolver()
            .find(footprint.effectiveEngineeringProfileId());
        ValidationLimits limits = ValidationLimits.fromFootprint(footprint, profile);
        LineEngineeringReport report = PowerLineEngineeringAnalyzer.analyze(
            geometry, terrain, profile, limits);
        report.setProfileName(null);
        return report;
    }

    private static EngineeringRuleProfile defaultProfile() {
        return new EngineeringRuleProfileResolver()
            .find(EngineeringRuleProfile.GENERIC_PLANNING_ID);
    }
}
