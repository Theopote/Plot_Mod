package com.plot.plugin.earthwork.model;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.earthwork.geometry.EarthworkCanvasScale;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 从分区轮廓计算场地红线（施工边界）及地形捕获范围。
 */
public final class EarthworkSiteBoundaryUtils {

    /**
     * 地形捕获用的轴对齐范围（画布坐标）。
     */
    public record CaptureBounds(double minX, double minY, double maxX, double maxY) {
        public boolean isValid() {
            return minX <= maxX && minY <= maxY;
        }

        public boolean contains(CaptureBounds other) {
            if (other == null || !other.isValid() || !isValid()) {
                return false;
            }
            return minX <= other.minX
                && minY <= other.minY
                && maxX >= other.maxX
                && maxY >= other.maxY;
        }

        public static CaptureBounds fromBoundary(List<Vec2d> boundary) {
            if (boundary == null || boundary.size() < 3) {
                return null;
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
            if (!Double.isFinite(minX)) {
                return null;
            }
            return new CaptureBounds(minX, minY, maxX, maxY);
        }
    }

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
        return expandAxisAlignedBoundary(boundary, marginBlocks, EarthworkCanvasScale.identity());
    }

    public static List<Vec2d> expandAxisAlignedBoundary(
            List<Vec2d> boundary,
            int marginBlocks,
            EarthworkCanvasScale canvasScale) {
        if (boundary == null || boundary.size() < 3 || marginBlocks <= 0) {
            return boundary != null ? new ArrayList<>(boundary) : List.of();
        }
        EarthworkCanvasScale scale = canvasScale != null ? canvasScale : EarthworkCanvasScale.identity();
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
        double margin = scale.uniformBlocksToCanvas(marginBlocks, boundary);
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

    public static boolean hasActiveSlopeTreatment(Collection<GradingZone> zones) {
        return resolveEdgeSlopeMarginBlocks(zones) > 0;
    }

    /**
     * 统一的地形捕获边界：场地红线 + 活跃边坡最大 reach。
     * Preview capture、Pipeline capture、Cache fingerprint 均应使用此边界。
     */
    public static List<Vec2d> resolveCaptureBoundary(EarthworkSite site) {
        return resolveCaptureBoundary(site, EarthworkCanvasScale.identity());
    }

    public static List<Vec2d> resolveCaptureBoundary(EarthworkSite site, EarthworkCanvasScale canvasScale) {
        if (site == null) {
            return List.of();
        }
        return resolveCaptureBoundary(
            site.getSiteBoundary(), site.getGradingZones().values(), canvasScale);
    }

    public static List<Vec2d> resolveCaptureBoundary(
            List<Vec2d> siteBoundary,
            Collection<GradingZone> zones) {
        return resolveCaptureBoundary(siteBoundary, zones, EarthworkCanvasScale.identity());
    }

    public static List<Vec2d> resolveCaptureBoundary(
            List<Vec2d> siteBoundary,
            Collection<GradingZone> zones,
            EarthworkCanvasScale canvasScale) {
        if (siteBoundary == null || siteBoundary.size() < 3) {
            return List.of();
        }
        int margin = resolveEdgeSlopeMarginBlocks(zones);
        return expandAxisAlignedBoundary(siteBoundary, margin, canvasScale);
    }

    public static List<Vec2d> resolveCaptureBoundary(
            List<Vec2d> outerPoints,
            ZoneEdgeSettings edgeSettings) {
        return resolveCaptureBoundary(outerPoints, edgeSettings, EarthworkCanvasScale.identity());
    }

    public static List<Vec2d> resolveCaptureBoundary(
            List<Vec2d> outerPoints,
            ZoneEdgeSettings edgeSettings,
            EarthworkCanvasScale canvasScale) {
        if (outerPoints == null || outerPoints.size() < 3) {
            return List.of();
        }
        int margin = edgeSettings != null && edgeSettings.hasActiveTreatment()
            ? edgeSettings.getMaximumReachBlocks()
            : 0;
        return expandAxisAlignedBoundary(outerPoints, margin, canvasScale);
    }
}
