package com.plot.ui.panel.layer;

import com.plot.core.command.commands.LayerColorEditCommand;
import com.plot.core.command.commands.LayerLineStyleEditCommand;
import com.plot.core.command.commands.LayerPropertyEditCommand;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.layer.Layer;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 图层属性面板编辑的撤销/重做辅助类。
 */
public final class LayerEditHistory {
    private LayerEditHistory() {
    }

    public record ShapeStyleRecord(String shapeId, ShapeStyle style) {
    }

    public record LayerColorState(Color layerColor, List<ShapeStyleRecord> shapeStyles) {
        public static LayerColorState capture(Layer layer) {
            return new LayerColorState(layer.getColor(), captureFollowingShapeStyles(layer));
        }
    }

    public record LayerLineStyleState(LineStyle lineStyle, List<ShapeStyleRecord> shapeStyles) {
        public static LayerLineStyleState capture(Layer layer) {
            LineStyle lineStyle = layer.getLineStyle();
            LineStyle copy = lineStyle != null ? (LineStyle) lineStyle.clone() : new LineStyle();
            return new LayerLineStyleState(copy, captureFollowingShapeStyles(layer));
        }
    }

    public static List<ShapeStyleRecord> captureFollowingShapeStyles(Layer layer) {
        List<ShapeStyleRecord> records = new ArrayList<>();
        for (Shape shape : layer.getShapes()) {
            if (shape == null || !(shape.getStyle() instanceof ShapeStyle shapeStyle)) {
                continue;
            }
            if (!shapeStyle.doesFollowLayerStyle()) {
                continue;
            }
            records.add(new ShapeStyleRecord(shape.getId(), (ShapeStyle) shapeStyle.clone()));
        }
        return records;
    }

    public static void commitColorEdit(Layer layer, LayerColorState before, LayerColorState after) {
        if (layer == null || before == null || after == null) {
            return;
        }
        if (statesEqual(before, after)) {
            return;
        }
        AppState.getInstance().getCommandHistory().execute(
                new LayerColorEditCommand(layer.getId(), before, after));
    }

    public static void commitLineStyleEdit(Layer layer, LayerLineStyleState before, LayerLineStyleState after) {
        if (layer == null || before == null || after == null) {
            return;
        }
        if (statesEqual(before, after)) {
            return;
        }
        AppState.getInstance().getCommandHistory().execute(
                new LayerLineStyleEditCommand(layer.getId(), before, after));
    }

    public static void commitProperty(String layerId, String property, Object before, Object after) {
        if (layerId == null || property == null || valuesEqual(before, after)) {
            return;
        }
        AppState.getInstance().getCommandHistory().execute(
                new LayerPropertyEditCommand(layerId, property, before, after));
    }

    private static boolean statesEqual(LayerColorState before, LayerColorState after) {
        return colorsEqual(before.layerColor(), after.layerColor())
                && shapeStylesEqual(before.shapeStyles(), after.shapeStyles());
    }

    private static boolean statesEqual(LayerLineStyleState before, LayerLineStyleState after) {
        return lineStylesEqual(before.lineStyle(), after.lineStyle())
                && shapeStylesEqual(before.shapeStyles(), after.shapeStyles());
    }

    private static boolean shapeStylesEqual(List<ShapeStyleRecord> before, List<ShapeStyleRecord> after) {
        if (before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            ShapeStyleRecord a = before.get(i);
            ShapeStyleRecord b = after.get(i);
            if (!a.shapeId().equals(b.shapeId())) {
                return false;
            }
            if (!shapeStyleEqual(a.style(), b.style())) {
                return false;
            }
        }
        return true;
    }

    private static boolean shapeStyleEqual(ShapeStyle a, ShapeStyle b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getStrokeColor() == b.getStrokeColor()
                && a.getFillColor() == b.getFillColor()
                && lineStylesEqual(asLineStyle(a.getLineStyle()), asLineStyle(b.getLineStyle()));
    }

    private static LineStyle asLineStyle(com.plot.api.graphics.ILineStyle lineStyle) {
        return lineStyle instanceof LineStyle concrete ? concrete : null;
    }

    private static boolean lineStylesEqual(LineStyle a, LineStyle b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getType() == b.getType()
                && Math.abs(a.getWidth() - b.getWidth()) < 1e-4f
                && colorsEqual(a.getColor(), b.getColor());
    }

    private static boolean colorsEqual(Color a, Color b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getRGB() == b.getRGB();
    }

    private static boolean valuesEqual(Object before, Object after) {
        if (before == after) {
            return true;
        }
        if (before == null || after == null) {
            return false;
        }
        return before.equals(after);
    }
}
