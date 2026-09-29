package com.plot.plugin.road.profile;

import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.solid.RoadGenerationResult;

/**
 * 纵断面图横轴：预览采样 {@link RoadGenerationResult#profileDistances} 为沿路径里程（米/block），
 * 而 PVI / 设计线 / 交叉点标记使用边内几何局部距离。二者通过 canvas→block 比例换算。
 */
public final class ProfileChartCoordinates {

    private ProfileChartCoordinates() {
    }

    public static double geometryToProfileScale(RoadEdge edge, RoadGenerationResult result) {
        if (edge == null || result == null || !result.hasProfileData()) {
            return 1.0;
        }
        double edgeLength = edge.getLength();
        double profileSpan = result.profileDistances.getLast();
        if (edgeLength < 1e-9 || profileSpan < 1e-9) {
            return 1.0;
        }
        return profileSpan / edgeLength;
    }

    public static double geometryLocalToProfileDistance(
            RoadEdge edge,
            RoadGenerationResult result,
            double geometryLocal) {
        return geometryLocal * geometryToProfileScale(edge, result);
    }

    public static double profileDistanceToGeometryLocal(
            RoadEdge edge,
            RoadGenerationResult result,
            double profileDistance) {
        double scale = geometryToProfileScale(edge, result);
        if (scale < 1e-9) {
            return profileDistance;
        }
        return profileDistance / scale;
    }
}
