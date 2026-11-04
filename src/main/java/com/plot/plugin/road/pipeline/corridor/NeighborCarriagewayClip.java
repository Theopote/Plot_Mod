package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.GeometryUtils;
import com.plot.plugin.road.pipeline.geometry.PathSegment;

import java.util.List;

/**
 * 尖角/回头弯：外侧条带与隧道衬砌按「当前断面」偏移时，可能落入邻段行车道包络。
 * 用点到折线各段中心线的距离判定是否占用了行车道。
 */
public final class NeighborCarriagewayClip {
    private NeighborCarriagewayClip() {
    }

    /**
     * @param carriageHalf 行车道半宽（世界/画布同一单位，与点坐标一致）
     */
    public static boolean insideAnyCarriageway(
            Vec2d point,
            List<PathSegment> segments,
            double carriageHalf) {
        if (point == null || segments == null || segments.isEmpty() || carriageHalf <= 0.0) {
            return false;
        }
        double limit = carriageHalf - 1e-9;
        for (PathSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            if (GeometryUtils.pointToSegmentDistance(point, segment.start, segment.end) < limit) {
                return true;
            }
        }
        return false;
    }

    /** 仅检查邻接段（隧道采样环上更便宜）。 */
    public static boolean insideNeighborCarriageway(
            Vec2d point,
            PathSegment previous,
            PathSegment next,
            double carriageHalf) {
        if (point == null || carriageHalf <= 0.0) {
            return false;
        }
        double limit = carriageHalf - 1e-9;
        if (previous != null
                && GeometryUtils.pointToSegmentDistance(point, previous.start, previous.end) < limit) {
            return true;
        }
        if (next != null
                && GeometryUtils.pointToSegmentDistance(point, next.start, next.end) < limit) {
            return true;
        }
        return false;
    }
}
