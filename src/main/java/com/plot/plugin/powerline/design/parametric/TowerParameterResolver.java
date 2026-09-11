package com.plot.plugin.powerline.design.parametric;

import java.util.ArrayList;
import java.util.List;

/**
 * 将用户参数与 Profile 比例关系解析为连续几何（double），不直接修改 {@code TowerStructureDesign}。
 */
public final class TowerParameterResolver {

    private TowerParameterResolver() {
    }

    public static ResolvedTowerParameters resolve(
            TowerParameterProfile profile,
            TowerParameterSet parameters) {
        if (profile == null || parameters == null) {
            throw new IllegalArgumentException("profile and parameters are required");
        }

        List<ConstraintAdjustment> adjustments = new ArrayList<>();

        double height = clamp(profile.heightRange(), parameters.height(), "height", adjustments,
            ConstraintAdjustmentKind.HEIGHT_CLAMPED_TO_PROFILE);
        double baseWidth = clamp(profile.baseWidthRange(), parameters.baseWidth(), "baseWidth", adjustments,
            ConstraintAdjustmentKind.BASE_WIDTH_CLAMPED);
        double armSpan = clamp(profile.armSpanRange(), parameters.armSpan(), "armSpan", adjustments,
            ConstraintAdjustmentKind.ARM_SPAN_CLAMPED);
        double depthScale = clamp(profile.depthScaleRange(), parameters.depthScale(), "depthScale", adjustments,
            ConstraintAdjustmentKind.DEPTH_SCALE_CLAMPED);
        StructureDensity density = parameters.density() != null ? parameters.density() : StructureDensity.MEDIUM;

        double baseHalfWidth = baseWidth / 2.0;
        double baseHalfDepth = (baseWidth * profile.defaultDepthRatio() * depthScale) / 2.0;
        double dominantReach = armSpan / 2.0;

        List<ResolvedTowerStation> stations = new ArrayList<>(profile.stationTemplates().size());
        for (TowerStationTemplate template : profile.stationTemplates()) {
            stations.add(new ResolvedTowerStation(
                template.id(),
                template.role(),
                height * template.heightRatio(),
                baseHalfWidth * template.widthRatio(),
                baseHalfDepth * template.depthRatio()));
        }

        List<ResolvedTowerArm> arms = new ArrayList<>(profile.armTemplates().size());
        for (TowerArmTemplate template : profile.armTemplates()) {
            arms.add(new ResolvedTowerArm(
                template.id(),
                template.role(),
                height * template.heightRatio(),
                dominantReach * template.reachRatio(),
                baseHalfDepth * template.longitudinalHalfWidthRatio(),
                height * template.verticalDropRatio(),
                template.shape(),
                template.bracing()));
        }

        return new ResolvedTowerParameters(
            height,
            baseWidth,
            baseHalfWidth,
            baseHalfDepth,
            armSpan,
            depthScale,
            density,
            stations,
            arms,
            height,
            2.0,
            profile.topWireLift(),
            adjustments);
    }

    private static double clamp(
            ParameterRange range,
            double requested,
            String parameter,
            List<ConstraintAdjustment> adjustments,
            ConstraintAdjustmentKind kind) {
        double resolved = range.clamp(requested);
        if (Double.compare(requested, resolved) != 0) {
            adjustments.add(new ConstraintAdjustment(
                kind,
                parameter,
                requested,
                resolved,
                "clamped to profile range"));
        }
        return resolved;
    }
}
