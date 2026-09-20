package com.plot.plugin.powerline.design.parametric;

/**
 * 参数化塔在加高时同步加宽，避免落地塔体相对预览/卡片过瘦。
 */
public final class TowerStabilityParameters {
    /** 高大格构塔允许的最大高宽比（height / 全宽）。 */
    public static final double MAX_HEIGHT_TO_BASE_WIDTH = 4.5;

    private TowerStabilityParameters() {
    }

    public static double minimumBaseWidth(double height, double roleBaseWidth, double roleHeight) {
        if (!Double.isFinite(height) || height <= 0.0) {
            return roleBaseWidth;
        }
        double fromSlenderness = height / MAX_HEIGHT_TO_BASE_WIDTH;
        if (!Double.isFinite(roleHeight) || roleHeight <= 0.0) {
            return fromSlenderness;
        }
        double fromRoleScale = roleBaseWidth * (height / roleHeight);
        return Math.max(fromSlenderness, fromRoleScale);
    }

    public static TowerParameterSet enforceStableBaseWidth(
            TowerParameterSet parameters,
            TowerParameterSet roleBaseline) {
        if (parameters == null || roleBaseline == null) {
            return parameters;
        }
        double minWidth = minimumBaseWidth(
            parameters.height(),
            roleBaseline.baseWidth(),
            roleBaseline.height());
        if (parameters.baseWidth() >= minWidth - 1e-6) {
            return parameters;
        }
        return new TowerParameterSet(
            parameters.height(),
            minWidth,
            Math.max(parameters.armSpan(), minWidth),
            parameters.depthScale(),
            parameters.waistRatio(),
            parameters.armLevelScales(),
            parameters.density());
    }
}
