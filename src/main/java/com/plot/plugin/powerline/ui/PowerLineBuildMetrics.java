package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PowerLineFootprint;

/** Build Tab 预览指标计算。 */
final class PowerLineBuildMetrics {
    private PowerLineBuildMetrics() {
    }

    static double typicalSpanBlocks(
            PowerLineFootprint line,
            double worldLength,
            int poleCount,
            double maxPoleSpacing) {
        if (poleCount <= 0 || worldLength <= 0.0) {
            return maxPoleSpacing;
        }
        boolean closedLoop = line != null && line.isClosedLoop();
        int spanCount = closedLoop ? poleCount : poleCount - 1;
        if (spanCount <= 0) {
            return maxPoleSpacing;
        }
        return worldLength / spanCount;
    }
}
