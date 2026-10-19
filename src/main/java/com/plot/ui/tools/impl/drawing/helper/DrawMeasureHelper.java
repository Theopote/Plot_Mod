package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.graphics.DrawContext;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.utils.PlotI18n;

import java.awt.Color;
import java.util.Locale;

/**
 * 绘制预览阶段的方块距离计算与标签渲染。
 * 与标注工具共享世界坐标转换逻辑，数值统一保留两位小数。
 */
public final class DrawMeasureHelper {

    private static volatile CoordinateTransformer transformer;

    private DrawMeasureHelper() {
    }

    private static CoordinateTransformer transformer() {
        CoordinateTransformer cached = transformer;
        if (cached != null) {
            return cached;
        }
        try {
            cached = CoordinateTransformer.getInstance();
            transformer = cached;
            return cached;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static double canvasDistance(Vec2d p1, Vec2d p2) {
        return p1.distance(p2);
    }

    public static double distanceBlocks(Vec2d p1, Vec2d p2) {
        if (p1 == null || p2 == null) {
            return 0.0;
        }
        CoordinateTransformer coordinateTransformer = transformer();
        if (coordinateTransformer == null) {
            return canvasDistance(p1, p2);
        }
        try {
            Vec2d world1 = coordinateTransformer.canvasToMinecraftWorld(p1);
            Vec2d world2 = coordinateTransformer.canvasToMinecraftWorld(p2);
            if (world1 == null || world2 == null) {
                return canvasDistance(p1, p2);
            }
            double dx = world2.x - world1.x;
            double dz = world2.y - world1.y;
            return Math.sqrt(dx * dx + dz * dz);
        } catch (Throwable ignored) {
            return canvasDistance(p1, p2);
        }
    }

    public static double radiusBlocks(Vec2d center, double radius) {
        if (center == null || radius <= 0.0) {
            return Math.max(0.0, radius);
        }
        Vec2d circlePoint = new Vec2d(center.x + radius, center.y);
        return distanceBlocks(center, circlePoint);
    }

    public static String formatBlocks(double value) {
        String key = "status.plot.draw.measure.blocks";
        String translated = PlotI18n.status(key, value);
        if (translated == null || translated.contains(key)) {
            return String.format(Locale.ROOT, "%.2f blocks", value);
        }
        return translated;
    }

    public static String formatDiameter(double value) {
        return PlotI18n.status("status.plot.draw.measure.diameter", value);
    }

    public static String formatRadius(double value) {
        return PlotI18n.status("status.plot.draw.preview.radius_value", value);
    }

    public static String formatTotal(double value) {
        return PlotI18n.status("status.plot.draw.measure.total", value);
    }

    public static double polylineLengthBlocks(java.util.List<Vec2d> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            total += distanceBlocks(points.get(i - 1), points.get(i));
        }
        return total;
    }

    public static Vec2d midpoint(Vec2d p1, Vec2d p2) {
        return new Vec2d((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0);
    }

    public static void drawSegmentLabel(DrawContext context, Vec2d p1, Vec2d p2, Color color) {
        if (context == null || p1 == null || p2 == null || color == null) {
            return;
        }
        if (p1.distance(p2) < 1e-6) {
            return;
        }
        context.drawText(formatBlocks(distanceBlocks(p1, p2)), midpoint(p1, p2), color);
    }

    public static void drawSegmentLabelAt(DrawContext context, Vec2d p1, Vec2d p2, Vec2d anchor, Color color) {
        if (context == null || p1 == null || p2 == null || anchor == null || color == null) {
            return;
        }
        if (p1.distance(p2) < 1e-6) {
            return;
        }
        context.drawText(formatBlocks(distanceBlocks(p1, p2)), anchor, color);
    }

    public static void drawDiameterLabel(DrawContext context, Vec2d p1, Vec2d p2, Color color) {
        if (context == null || p1 == null || p2 == null || color == null) {
            return;
        }
        if (p1.distance(p2) < 1e-6) {
            return;
        }
        context.drawText(formatDiameter(distanceBlocks(p1, p2)), midpoint(p1, p2), color);
    }

    public static void drawRadiusLabel(DrawContext context, Vec2d center, Vec2d edge, Color color) {
        if (context == null || center == null || edge == null || color == null) {
            return;
        }
        if (center.distance(edge) < 1e-6) {
            return;
        }
        context.drawText(formatRadius(distanceBlocks(center, edge)), midpoint(center, edge), color);
    }
}
