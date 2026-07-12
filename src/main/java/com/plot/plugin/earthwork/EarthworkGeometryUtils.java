package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.EllipseShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 土方几何工具
 */
public final class EarthworkGeometryUtils {
    private EarthworkGeometryUtils() {
    }

    public static List<Shape> findAdoptableRegions(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return List.of();
        }
        List<Shape> regions = new ArrayList<>();
        for (Shape shape : shapes) {
            if (isAdoptableRegion(shape)) {
                regions.add(shape);
            }
        }
        return regions;
    }

    public static boolean isAdoptableRegion(Shape shape) {
        if (shape == null) {
            return false;
        }
        if (shape instanceof Polygon polygon) {
            return polygon.getPoints().size() >= 3;
        }
        return shape instanceof RectangleShape
            || shape instanceof CircleShape
            || shape instanceof EllipseShape;
    }

    public static List<Vec2d> extractRegionPoints(Shape shape) {
        if (shape == null) {
            return List.of();
        }
        List<Vec2d> points = shape.getPoints();
        if (points == null || points.size() < 3) {
            return List.of();
        }
        return BuildingGeometryUtils.copyPoints(points);
    }

    public static Polygon toPolygon(List<Vec2d> points) {
        return BuildingGeometryUtils.toPolygon(points);
    }

    public static List<Vec2d> collectFootprintCellCenters(List<Vec2d> points) {
        return BuildingGeometryUtils.collectFootprintCellCenters(points);
    }

    public static List<Vec2d> collectSampleCenters(List<Vec2d> points, int gridSize) {
        List<Vec2d> allCenters = collectFootprintCellCenters(points);
        if (gridSize <= 1 || allCenters.isEmpty()) {
            return allCenters;
        }

        List<Vec2d> sampled = new ArrayList<>();
        for (Vec2d center : allCenters) {
            int blockX = (int) Math.floor(center.x);
            int blockZ = (int) Math.floor(center.y);
            if (blockX % gridSize == 0 && blockZ % gridSize == 0) {
                sampled.add(center);
            }
        }
        return sampled.isEmpty() ? allCenters : sampled;
    }

    public static BlockPos canvasToBlockXZ(Vec2d canvasPos, CoordinateTransformer transformer) {
        return BuildingGeometryUtils.canvasToBlockXZ(canvasPos, transformer);
    }

    public static String resolveFillBlockId(String material) {
        if (material == null || material.isBlank()) {
            return GradingRegion.DEFAULT_FILL_MATERIAL;
        }
        return material.trim();
    }

    public static String resolveCutBlockId(String material) {
        if (material == null || material.isBlank()) {
            return "minecraft:air";
        }
        return material.trim();
    }

    public static BuildingGeometryUtils.RectBounds computeBounds(List<Vec2d> points) {
        return BuildingGeometryUtils.normalizedRectBounds(points);
    }

    public static Vec2d computeCentroid(List<Vec2d> points) {
        return BuildingGeometryUtils.computeCentroid(points);
    }
}
