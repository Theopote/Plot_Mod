package com.plot.plugin.pattern.space;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.PolygonRegionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从铺装区域轮廓收集 Pattern Space 采样点。
 */
public final class PatternSampling {
    private PatternSampling() {
    }

    public static List<PatternSample> collectFootprintSamples(List<Vec2d> outerPoints) {
        if (outerPoints == null || outerPoints.size() < 3) {
            return List.of();
        }
        List<Vec2d> cellCenters = PolygonRegionUtils.collectFootprintCellCenters(outerPoints);
        List<PatternSample> samples = new ArrayList<>(cellCenters.size());
        for (Vec2d center : cellCenters) {
            samples.add(PatternSample.fromCanvas(center));
        }
        return samples;
    }
}
