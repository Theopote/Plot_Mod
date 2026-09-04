package com.plot.plugin.building.generation.massing;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.model.spec.FloorPlateSpec;

import java.util.Collections;
import java.util.List;

/**
 * 将 {@link FloorPlateSpec} 解析为外/内轮廓与格网单元。
 */
public final class FloorPlateGeometryResolver {
    private FloorPlateGeometryResolver() {
    }

    public record ResolvedFloorPlate(
            FloorPlateSpec plate,
            List<Vec2d> outerPoints,
            Polygon outerPolygon,
            Polygon innerPolygon,
            List<BuildingGenerationContext.GridCell> outerCells) {

        public List<Vec2d> innerPoints() {
            return innerPolygon != null
                ? BuildingGeometryUtils.copyPoints(innerPolygon.getPoints())
                : List.of();
        }

        public boolean hasInteriorSpace() {
            return InnerOffsetDegradation.hasInteriorSpace(innerPolygon);
        }
    }

    public static ResolvedFloorPlate resolve(FloorPlateSpec plate, int wallThickness) {
        List<Vec2d> outerPoints = BuildingGeometryUtils.copyPoints(plate.outerPoints());
        Polygon outerPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        List<Vec2d> innerPoints = BuildingGeometryUtils.offsetInward(outerPoints, wallThickness);
        Polygon innerPolygon = innerPoints.size() >= 3
            ? BuildingGeometryUtils.toPolygon(innerPoints)
            : null;
        List<BuildingGenerationContext.GridCell> outerCells = BuildingGenerationContext.collectFootprintCells(
            outerPoints, outerPolygon);
        return new ResolvedFloorPlate(
            plate,
            Collections.unmodifiableList(outerPoints),
            outerPolygon,
            innerPolygon,
            Collections.unmodifiableList(outerCells)
        );
    }
}
