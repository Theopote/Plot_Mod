package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 从分区轮廓计算场地红线（施工边界）。
 */
public final class EarthworkSiteBoundaryUtils {

    private EarthworkSiteBoundaryUtils() {
    }

    /**
     * 取所有分区顶点的轴对齐包围盒矩形（4 点，逆时针）。
     */
    public static List<Vec2d> axisAlignedBoundingRectangle(Collection<GradingZone> zones) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean hasPoint = false;

        if (zones != null) {
            for (GradingZone zone : zones) {
                if (zone == null) {
                    continue;
                }
                for (Vec2d point : zone.getOuterPoints()) {
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                    hasPoint = true;
                }
            }
        }

        if (!hasPoint) {
            return List.of();
        }
        return List.of(
            new Vec2d(minX, minY),
            new Vec2d(maxX, minY),
            new Vec2d(maxX, maxY),
            new Vec2d(minX, maxY)
        );
    }

    /**
     * 取面积最大的分区轮廓作为场地红线（分区互不重叠时的备选）。
     */
    public static List<Vec2d> largestZoneBoundary(Collection<GradingZone> zones) {
        GradingZone largest = null;
        double largestArea = 0.0;
        if (zones != null) {
            for (GradingZone zone : zones) {
                if (zone == null) {
                    continue;
                }
                double area = zone.computeArea();
                if (area > largestArea) {
                    largestArea = area;
                    largest = zone;
                }
            }
        }
        if (largest == null) {
            return List.of();
        }
        return new ArrayList<>(largest.getOuterPoints());
    }

    public static List<Vec2d> resolveSiteBoundary(Collection<GradingZone> zones) {
        List<Vec2d> bounding = axisAlignedBoundingRectangle(zones);
        if (bounding.size() >= 3) {
            return bounding;
        }
        return largestZoneBoundary(zones);
    }

    public static double computeBoundaryArea(List<Vec2d> boundary) {
        return Math.abs(GradingRegion.signedArea(boundary));
    }

    /**
     * 将轴对齐包围盒向外扩展指定格距，用于边坡放坡带采样。
     */
    public static List<Vec2d> expandAxisAlignedBoundary(List<Vec2d> boundary, int marginBlocks) {
        if (boundary == null || boundary.size() < 3 || marginBlocks <= 0) {
            return boundary != null ? new ArrayList<>(boundary) : List.of();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Vec2d point : boundary) {
            if (point == null) {
                continue;
            }
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        double margin = marginBlocks;
        return List.of(
            new Vec2d(minX - margin, minY - margin),
            new Vec2d(maxX + margin, minY - margin),
            new Vec2d(maxX + margin, maxY + margin),
            new Vec2d(minX - margin, maxY + margin)
        );
    }

    public static int resolveEdgeSlopeMarginBlocks(Collection<GradingZone> zones) {
        int margin = 0;
        if (zones == null) {
            return margin;
        }
        for (GradingZone zone : zones) {
            if (zone == null || !zone.isEnabled()) {
                continue;
            }
            ZoneEdgeSettings settings = zone.getEdgeSettings();
            if (settings != null && settings.hasActiveTreatment()) {
                margin = Math.max(margin, settings.getMaximumReachBlocks());
            }
        }
        return margin;
    }
}
