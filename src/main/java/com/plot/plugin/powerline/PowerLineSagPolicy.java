package com.plot.plugin.powerline;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** 导线垂度参数解析（线路 footprint）。 */
public final class PowerLineSagPolicy {
    private PowerLineSagPolicy() {
    }

    /**
     * 生成/分析时使用的最大下垂深度（格）。
     * 线路显式设为 {@code <= 0} 表示无上限。
     */
    public static double resolveMaxSagDepth(PowerLineFootprint footprint) {
        if (footprint == null) {
            return PowerLineSagUtils.DEFAULT_MAX_SAG_DEPTH;
        }
        if (footprint.getMaxSagDepth() > 0.0) {
            return footprint.getMaxSagDepth();
        }
        return 0.0;
    }
}
