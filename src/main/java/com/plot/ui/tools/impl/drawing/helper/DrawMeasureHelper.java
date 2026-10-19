package com.plot.ui.tools.impl.drawing.helper;

import com.plot.api.geometry.Vec2d;
import com.plot.core.graphics.DrawContext;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.utils.PlotI18n;

import java.awt.Color;
import java.util.Locale;

/**
 * 绘制预览阶段的方块距离计算与标签渲染。
 * 与标注工具共享世界坐标转换逻辑；数值精度与单位由 {@link DrawMeasureSettings} 控制。
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

    public static double roundValue(double value) {
        int places = DrawMeasureSettings.getDecimalPlaces();
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    public static String formatValue(double value) {
        int places = DrawMeasureSettings.getDecimalPlaces();
        return String.format(Locale.ROOT, "%." + places + "f", roundValue(value));
    }

    public static String formatBlocks(double value) {
        String number = formatValue(value);
        if (!DrawMeasureSettings.isShowUnit()) {
            return number;
        }
        return translateOrFallback("status.plot.draw.measure.blocks", number, number + " blocks");
    }

    public static String formatDiameter(double value) {
        String number = formatValue(value);
        if (!DrawMeasureSettings.isShowUnit()) {
            return number;
        }
        return translateOrFallback("status.plot.draw.measure.diameter", number, "Diameter " + number + " blocks");
    }

    public static String formatRadius(double value) {
        String number = formatValue(value);
        if (!DrawMeasureSettings.isShowUnit()) {
            return number;
        }
        return translateOrFallback("status.plot.draw.preview.radius_value", number, "Radius: " + number);
    }

    public static String formatTotal(double value) {
        String number = formatValue(value);
        if (!DrawMeasureSettings.isShowUnit()) {
            return number;
        }
        return translateOrFallback("status.plot.draw.measure.total", number, "Total " + number + " blocks");
    }

    public static String formatLabeled(String i18nKey, double value) {
        return translateOrFallback(i18nKey, formatValue(value), formatValue(value));
    }

    private static String translateOrFallback(String key, String number, String fallback) {
        String translated = PlotI18n.status(key, number);
        if (translated == null || translated.contains(key)) {
            return fallback;
        }
        return translated;
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

    public static Color getLabelColor() {
        return DrawMeasureSettings.getLabelColor();
    }

    private static boolean shouldDrawLabels() {
        return DrawMeasureSettings.isEnabled();
    }

    public static void drawTextLabel(DrawContext context, String text, Vec2d position) {
        if (!shouldDrawLabels() || context == null || text == null || text.isBlank() || position == null) {
            return;
        }
        context.drawText(text, position, getLabelColor());
    }

    public static void drawSegmentLabel(DrawContext context, Vec2d p1, Vec2d p2) {
        if (!shouldDrawLabels() || context == null || p1 == null || p2 == null) {
            return;
        }
        if (p1.distance(p2) < 1e-6) {
            return;
        }
        drawTextLabel(context, formatBlocks(distanceBlocks(p1, p2)), midpoint(p1, p2));
    }

    public static void drawSegmentLabelAt(DrawContext context, Vec2d p1, Vec2d p2, Vec2d anchor) {
        if (!shouldDrawLabels() || context == null || p1 == null || p2 == null || anchor == null) {
            return;
        }
        if (p1.distance(p2) < 1e-6) {
            return;
        }
        drawTextLabel(context, formatBlocks(distanceBlocks(p1, p2)), anchor);
    }

    public static void drawDiameterLabel(DrawContext context, Vec2d p1, Vec2d p2) {
        if (!shouldDrawLabels() || context == null || p1 == null || p2 == null) {
            return;
        }
        if (p1.distance(p2) < 1e-6) {
            return;
        }
        drawTextLabel(context, formatDiameter(distanceBlocks(p1, p2)), midpoint(p1, p2));
    }

    public static void drawRadiusLabel(DrawContext context, Vec2d center, Vec2d edge) {
        if (!shouldDrawLabels() || context == null || center == null || edge == null) {
            return;
        }
        if (center.distance(edge) < 1e-6) {
            return;
        }
        drawTextLabel(context, formatRadius(distanceBlocks(center, edge)), midpoint(center, edge));
    }
}
