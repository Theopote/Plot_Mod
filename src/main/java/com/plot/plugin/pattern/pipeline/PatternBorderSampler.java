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
            double distance = distanceToRingBoundary(point, footprint.getOuterPoints());
            if (distance <= width) {
                return true;
            }
        }
        if (border.isInnerBorder()) {
            for (List<Vec2d> hole : footprint.getHoles()) {
                double distance = distanceToRingBoundary(point, hole);
                if (distance <= width) {
                    return true;
                }
            }
        }
        return false;
    }

    static double distanceToRingBoundary(Vec2d point, List<Vec2d> ring) {
        if (point == null || ring == null || ring.size() < 2) {
            return Double.MAX_VALUE;
        }
        double min = Double.MAX_VALUE;
        int count = ring.size();
        for (int i = 0; i < count; i++) {
            Vec2d start = ring.get(i);
            Vec2d end = ring.get((i + 1) % count);
            if (start == null || end == null) {
                continue;
            }
            min = Math.min(min, distancePointToSegment(point, start, end));
        }
        return min;
    }

    private static double distancePointToSegment(Vec2d point, Vec2d start, Vec2d end) {
        double dx = end.x - start.x;
        double dz = end.y - start.y;
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1e-12) {
            return point.distance(start);
        }
        double t = ((point.x - start.x) * dx + (point.y - start.y) * dz) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = start.x + t * dx;
        double closestZ = start.y + t * dz;
        double px = point.x - closestX;
        double pz = point.y - closestZ;
        return Math.sqrt(px * px + pz * pz);
    }
}
