package com.plot.plugin.building.generation.component;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingCanvasScale;
import com.plot.plugin.building.generation.facade.FacadeEdgeResolver;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver;
import com.plot.plugin.building.generation.opening.WindowLayoutResolver.PlannedWindow;
import com.plot.plugin.building.model.spec.BalconySpec;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.FacadeEdgeScope;
import com.plot.plugin.building.model.spec.FacadeSpec;
import com.plot.plugin.building.model.spec.MassingSpec;

import java.util.List;

/** 按阵列窗户在窗下生成等宽阳台。 */
public final class WindowBalconyGenerator {
    private WindowBalconyGenerator() {
    }

    public static void generate(BuildingGenerationContext context, FacadeSpec facade) {
        if (context == null || facade == null || facade.windowBalconyDepth() <= 0) {
            return;
        }
        if (!facade.defaultWindowPattern().enabled()) {
            return;
        }

        BuildingDefinition definition = context.getDefinition();
        MassingSpec massing = definition.massing();
        List<Vec2d> basePoints = massing.baseOuterPoints();
        FacadeEdgeScope scope = facade.edgeScope();
        BuildingCanvasScale canvasScale = context.getCanvasScale();
        int depth = facade.windowBalconyDepth();
        String slabMaterial = facade.resolvedBalconySlabMaterial();

        for (int floor = 0; floor < massing.floors(); floor++) {
            ResolvedFloorPlate plate = context.resolvedFloorPlate(floor);
            List<Vec2d> outerPoints = plate.outerPoints();
            List<PlannedWindow> windows = WindowLayoutResolver.layout(
                outerPoints,
                plate.outerPolygon(),
                plate.innerPolygon(),
                plate.outerCells(),
                facade,
                basePoints,
                scope,
                canvasScale,
                massing.floorHeight());

            for (PlannedWindow window : windows) {
                if (window.columnCenters() == null || window.columnCenters().isEmpty()) {
                    continue;
                }
                int plateSegment = FacadeEdgeResolver.resolveSegmentIndex(
                    scope, window.segmentIndex(), basePoints, outerPoints);
                Vec2d anchor = window.columnCenters().getFirst()
                    .lerp(window.columnCenters().getLast(), 0.5);
                double positionRatio = positionRatioOnSegment(outerPoints, plateSegment, anchor);
                BalconyGenerator.generate(context, new BalconySpec(
                    plateSegment,
                    positionRatio,
                    floor,
                    window.columnCenters().size(),
                    depth,
                    slabMaterial,
                    null));
            }
        }
    }

    private static double positionRatioOnSegment(
            List<Vec2d> outerPoints,
            int segmentIndex,
            Vec2d point) {
        if (outerPoints == null || outerPoints.size() < 3 || point == null) {
            return 0.5;
        }
        int n = outerPoints.size();
        int index = Math.floorMod(segmentIndex, n);
        Vec2d start = outerPoints.get(index);
        Vec2d end = outerPoints.get((index + 1) % n);
        Vec2d ab = end.subtract(start);
        double segLenSq = ab.dot(ab);
        if (segLenSq < 1e-9) {
            return 0.5;
        }
        double t = point.subtract(start).dot(ab) / segLenSq;
        return Math.max(0.0, Math.min(1.0, t));
    }
}
