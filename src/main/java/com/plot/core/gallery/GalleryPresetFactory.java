package com.plot.core.gallery;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.CircleShape;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.geometry.shapes.RectangleShape;
import com.plot.core.model.Shape;
import com.plot.core.model.serialization.ProjectSnapshot;
import com.plot.core.model.serialization.ShapeSerialization;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置图库预设：经典建筑平面与基础几何图形。
 */
public final class GalleryPresetFactory {
    private GalleryPresetFactory() {
    }

    public static List<GalleryItem> createPresets() {
        List<GalleryItem> presets = new ArrayList<>();
        presets.add(preset(
            "preset_church",
            "gallery.plot.item.church.name",
            "gallery.plot.item.church.desc",
            "BUILDING",
            latinCrossPolygon()));
        presets.add(preset(
            "preset_castle",
            "gallery.plot.item.castle.name",
            "gallery.plot.item.castle.desc",
            "BUILDING",
            castleShapes()));
        presets.add(preset(
            "preset_villa",
            "gallery.plot.item.villa.name",
            "gallery.plot.item.villa.desc",
            "BUILDING",
            villaShapes()));
        presets.add(preset(
            "preset_courtyard",
            "gallery.plot.item.courtyard.name",
            "gallery.plot.item.courtyard.desc",
            "BUILDING",
            courtyardPolygon()));
        presets.add(preset(
            "preset_rectangle",
            "gallery.plot.item.rect_block.name",
            "gallery.plot.item.rect_block.desc",
            "SYMBOL",
            List.of(new RectangleShape(new Vec2d(-8, -6), 16, 12, 0))));
        presets.add(preset(
            "preset_circle",
            "gallery.plot.item.circle_block.name",
            "gallery.plot.item.circle_block.desc",
            "LANDSCAPE",
            List.of(new CircleShape(new Vec2d(0, 0), 8))));
        presets.add(preset(
            "preset_triangle",
            "gallery.plot.item.triangle_block.name",
            "gallery.plot.item.triangle_block.desc",
            "SYMBOL",
            trianglePolygon()));
        return presets;
    }

    private static GalleryItem preset(
            String id,
            String nameKey,
            String descriptionKey,
            String category,
            List<Shape> shapes) {
        List<ProjectSnapshot.ShapeSnapshot> snapshots = new ArrayList<>();
        for (Shape shape : shapes) {
            ProjectSnapshot.ShapeSnapshot snap = new ProjectSnapshot.ShapeSnapshot();
            snap.type = ShapeSerialization.getTypeName(shape);
            snap.data = shape.serialize();
            snapshots.add(snap);
        }
        return GalleryItem.presetItem(id, nameKey, descriptionKey, category, snapshots);
    }

    private static List<Shape> latinCrossPolygon() {
        List<Vec2d> points = List.of(
            new Vec2d(-3, -18),
            new Vec2d(3, -18),
            new Vec2d(3, -3),
            new Vec2d(14, -3),
            new Vec2d(14, 5),
            new Vec2d(3, 5),
            new Vec2d(3, 16),
            new Vec2d(-3, 16),
            new Vec2d(-3, 5),
            new Vec2d(-14, 5),
            new Vec2d(-14, -3),
            new Vec2d(-3, -3));
        return List.of(new Polygon(points));
    }

    private static List<Shape> castleShapes() {
        return List.of(
            new RectangleShape(new Vec2d(-10, -10), 20, 20, 0),
            new RectangleShape(new Vec2d(-12, 8), 5, 5, 0),
            new RectangleShape(new Vec2d(7, 8), 5, 5, 0),
            new RectangleShape(new Vec2d(-12, -13), 5, 5, 0),
            new RectangleShape(new Vec2d(7, -13), 5, 5, 0));
    }

    private static List<Shape> villaShapes() {
        return List.of(
            new RectangleShape(new Vec2d(-12, -8), 24, 16, 0),
            new RectangleShape(new Vec2d(-4, 8), 8, 6, 0),
            new Polygon(List.of(
                new Vec2d(-14, -8),
                new Vec2d(-14, 8),
                new Vec2d(-12, 8),
                new Vec2d(-12, -8))));
    }

    private static List<Shape> courtyardPolygon() {
        List<Vec2d> points = List.of(
            new Vec2d(-14, -14),
            new Vec2d(14, -14),
            new Vec2d(14, 14),
            new Vec2d(5, 14),
            new Vec2d(5, 2),
            new Vec2d(-5, 2),
            new Vec2d(-5, 14),
            new Vec2d(-14, 14));
        return List.of(new Polygon(points));
    }

    private static List<Shape> trianglePolygon() {
        return List.of(new Polygon(List.of(
            new Vec2d(0, 10),
            new Vec2d(-9, -6),
            new Vec2d(9, -6))));
    }
}
