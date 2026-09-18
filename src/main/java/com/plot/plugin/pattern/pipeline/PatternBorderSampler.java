package com.plot.plugin.pattern.pipeline;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.pattern.model.PatternBorderConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.space.PatternSample;

import java.util.List;

/**
 * 根据采样点与外轮廓/孔洞的距离判断是否应铺设边框材质。
 */
public final class PatternBorderSampler {
    private PatternBorderSampler() {
    }

    public static boolean shouldUseBorderMaterial(
            PatternFootprint footprint,
            PatternBorderConfig border,
            PatternSample sample) {
        if (footprint == null || border == null || sample == null || !border.isEnabled()) {
            return false;
        }
        Vec2d point = sample.toCanvas();
        double width = border.getBorderWidth();
        if (border.isOuterBorder()) {
            double distance = PatternPolygonBoundaryDistance.distanceToRingBoundary(
                point, footprint.getOuterPoints());
            if (distance <= width) {
                return true;
            }
        }
        if (border.isInnerBorder()) {
            for (List<Vec2d> hole : footprint.getHoles()) {
                double distance = PatternPolygonBoundaryDistance.distanceToRingBoundary(point, hole);
                if (distance <= width) {
                    return true;
                }
            }
        }
        return false;
    }
}
