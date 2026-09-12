package com.plot.plugin.powerline.path;

import com.plot.api.world.ICoordinateService;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.model.PowerLineFootprint;

import java.util.List;
import java.util.Objects;

/** 画布参考路径与已认领线路之间的同步。 */
public final class PowerLineSourceSync {

    private PowerLineSourceSync() {
    }

    public static Shape findShape(List<Shape> shapes, String shapeId) {
        if (shapes == null || shapeId == null || shapeId.isBlank()) {
            return null;
        }
        for (Shape shape : shapes) {
            if (shape != null && shapeId.equals(shape.getId())) {
                return shape;
            }
        }
        return null;
    }

    public static boolean hasLinkedSource(PowerLineFootprint footprint) {
        return footprint != null
            && footprint.hasSourcePath()
            && footprint.getSourceShapeId() != null;
    }

    public static boolean isSourceMissing(PowerLineFootprint footprint, List<Shape> canvasShapes) {
        if (!hasLinkedSource(footprint)) {
            return false;
        }
        return findShape(canvasShapes, footprint.getSourceShapeId()) == null;
    }

    public static boolean isSourceStale(PowerLineFootprint footprint, Shape liveShape) {
        if (!hasLinkedSource(footprint) || liveShape == null) {
            return false;
        }
        PowerLineSourceDescriptor stored = footprint.getSourceDescriptor();
        if (stored == null || stored.shapeId() == null || !stored.shapeId().equals(liveShape.getId())) {
            return false;
        }
        PowerLineSourceDescriptor liveDescriptor = PowerLineSourceDescriptor.capture(liveShape);
        return liveDescriptor.fingerprint() != stored.fingerprint();
    }

    public static void relayout(PowerLineFootprint footprint, Shape liveShape, ICoordinateService coordinates) {
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(liveShape, "liveShape");
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        footprint.setSourceDescriptor(PowerLineSourceDescriptor.capture(liveShape));
        PowerLinePathLayout.layoutAndSync(footprint, coords);
    }
}
