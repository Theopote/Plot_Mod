package com.plot.plugin.powerline;

import com.plot.plugin.powerline.engineering.EngineeringRuleProfile;
import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 导线垂度参数解析（线路 footprint + 工程 profile）。 */
public final class PowerLineSagPolicy {
    private PowerLineSagPolicy() {
    }

    /**
     * 生成/分析时使用的最大下垂深度（格）。
     * 线路显式设为 {@code <= 0} 时回退到工程 profile；双方均未限制时返回 {@code 0}（无上限）。
     */
    public static double resolveMaxSagDepth(PowerLineFootprint footprint, EngineeringRuleProfile profile) {
        if (footprint != null && footprint.getMaxSagDepth() > 0.0) {
            return footprint.getMaxSagDepth();
        }
        if (profile != null && profile.getSag().getMaxSagDepth() > 0.0) {
            return profile.getSag().getMaxSagDepth();
        }
        return 0.0;
    }

    public static double resolveMaxSagDepth(PowerLineFootprint footprint) {
        return resolveMaxSagDepth(footprint, null);
    }
}
