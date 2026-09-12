package com.plot.plugin.powerline.path;

import com.plot.api.world.ICoordinateService;
import com.plot.core.model.Shape;
import com.plot.plugin.powerline.PowerPoleLayoutUtils;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerPoleSite;

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

    public static SourceSyncStatus resolveStatus(PowerLineFootprint footprint, List<Shape> canvasShapes) {
        if (!hasLinkedSource(footprint)) {
            return SourceSyncStatus.NOT_LINKED;
        }
        return resolveStatus(footprint, findShape(canvasShapes, footprint.getSourceShapeId()));
    }

    public static SourceSyncStatus resolveStatus(PowerLineFootprint footprint, Shape liveShape) {
        if (!hasLinkedSource(footprint)) {
            return SourceSyncStatus.NOT_LINKED;
        }
        if (liveShape == null) {
            return SourceSyncStatus.MISSING;
        }
        if (PowerLinePathAdapters.tryFrom(liveShape) == null) {
            return SourceSyncStatus.UNSUPPORTED;
        }
        try {
            PowerLineSourceDescriptor liveDescriptor = PowerLineSourceDescriptor.capture(liveShape);
            PowerLineSourceDescriptor stored = footprint.getSourceDescriptor();
            if (stored == null
                || stored.shapeId() == null
                || !stored.shapeId().equals(liveShape.getId())) {
                return SourceSyncStatus.OK;
            }
            if (liveDescriptor.fingerprint() != stored.fingerprint()) {
                return SourceSyncStatus.STALE;
            }
            return SourceSyncStatus.OK;
        } catch (IllegalArgumentException ex) {
            return SourceSyncStatus.DEGENERATE;
        }
    }

    public static boolean isSourceMissing(PowerLineFootprint footprint, List<Shape> canvasShapes) {
        return resolveStatus(footprint, canvasShapes) == SourceSyncStatus.MISSING;
    }

    public static boolean isSourceStale(PowerLineFootprint footprint, Shape liveShape) {
        return resolveStatus(footprint, liveShape) == SourceSyncStatus.STALE;
    }

    public static void relayout(PowerLineFootprint footprint, Shape liveShape, ICoordinateService coordinates) {
        relink(footprint, liveShape, coordinates);
    }

    /** 将线路换绑到新的画布参考路径，并重新生成杆塔折线骨架。 */
    public static void relink(PowerLineFootprint footprint, Shape newShape, ICoordinateService coordinates) {
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(newShape, "newShape");
        ICoordinateService coords = Objects.requireNonNull(coordinates, "coordinates");
        PowerLineSourcePath sourcePath = PowerLinePathAdapters.from(newShape);
        PowerLineSourceDescriptor descriptor = PowerLineSourceDescriptor.capture(newShape);
        List<PowerPoleSite> sites = PowerPoleLayoutUtils.computePoleSites(footprint, sourcePath, coords);
        PowerLinePathLayout.commitLayout(footprint, descriptor, sourcePath, sites);
    }
}
