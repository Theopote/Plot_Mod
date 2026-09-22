package com.plot.plugin.building.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.core.model.Shape;
import com.plot.plugin.building.BuildingBlockCountCache;
import com.plot.plugin.building.BuildingFootprintSelectionAnalysis;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.ui.PluginUiColors;
import imgui.ImColor;

import java.util.ArrayList;
import java.util.List;

/** 将画布拾取分析结果转为面板预览图条目。 */
public final class BuildingFootprintPreviewItems {
    private BuildingFootprintPreviewItems() {
    }

    public static List<BuildingOverviewRenderer.FootprintMapItem> fromCanvasAnalysis(
            BuildingFootprintSelectionAnalysis canvas) {
        List<Shape> shapes = previewShapes(canvas);
        List<BuildingOverviewRenderer.FootprintMapItem> items = new ArrayList<>(shapes.size());
        for (int i = 0; i < shapes.size(); i++) {
            Shape shape = shapes.get(i);
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            if (points.size() < 3) {
                continue;
            }
            items.add(new BuildingOverviewRenderer.FootprintMapItem(
                List.copyOf(points),
                true,
                colorFor(shape, canvas),
                i));
        }
        return items;
    }

    private static int colorFor(Shape shape, BuildingFootprintSelectionAnalysis canvas) {
        if (containsShape(canvas.invalid(), shape)) {
            return PluginUiColors.WARNING;
        }
        if (containsShape(canvas.alreadyAdopted(), shape)) {
            return ImColor.rgba(140, 170, 200, 220);
        }
        return PluginUiColors.ACCENT_BLUE;
    }

    private static boolean containsShape(List<Shape> shapes, Shape shape) {
        if (shape == null || shapes == null) {
            return false;
        }
        for (Shape candidate : shapes) {
            if (candidate != null && candidate.getId().equals(shape.getId())) {
                return true;
            }
        }
        return false;
    }

    public static Shape shapeAt(List<Shape> previewShapes, int hitIndex) {
        if (previewShapes == null || hitIndex < 0 || hitIndex >= previewShapes.size()) {
            return null;
        }
        return previewShapes.get(hitIndex);
    }

    public static List<Shape> previewShapes(BuildingFootprintSelectionAnalysis canvas) {
        List<Shape> shapes = new ArrayList<>(canvas.adoptable());
        shapes.addAll(canvas.alreadyAdopted());
        shapes.addAll(canvas.invalid());
        return shapes;
    }

    public static int totalBlockCount(List<Shape> shapes, WorldProjectionSnapshot projection) {
        int count = 0;
        for (Shape shape : shapes) {
            List<Vec2d> points = BuildingGeometryUtils.extractFootprintPoints(shape);
            count += BuildingBlockCountCache.blockCount(points, projection);
        }
        return count;
    }
}
