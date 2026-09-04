package com.plot.plugin.building.generation.resolve;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext.GridCell;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析基础 footprint 的外/内轮廓与格网单元（不含逐层 FloorPlate 几何）。
 */
public final class MassingGeometryResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/MassingGeometryResolver");

    private MassingGeometryResolver() {
    }

    public record ResolvedMassingGeometry(
            List<Vec2d> outerPoints,
            Polygon outerPolygon,
            List<Vec2d> innerPoints,
            Polygon innerPolygon,
            List<GridCell> footprintCells,
            boolean valid) {
    }

    /**
     * @param result 可为 null；非 null 时写入 inner-offset / coverage-gap 警告
     */
    public static ResolvedMassingGeometry resolve(BuildingDefinition definition, BuildingGenerationResult result) {
        if (definition == null) {
            return invalid();
        }
        List<Vec2d> outerPoints = BuildingGeometryUtils.copyPoints(definition.footprint().outerPoints());
        if (outerPoints.size() < 3) {
            LOGGER.warn("建筑轮廓点数不足");
            return invalid();
        }

        int wallThickness = definition.envelope().wallThickness();
        Polygon outerPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        List<Vec2d> innerPoints = BuildingGeometryUtils.offsetInward(outerPoints, wallThickness);
        Polygon innerPolygon = innerPoints.size() >= 3
            ? BuildingGeometryUtils.toPolygon(innerPoints)
            : null;
        if (innerPolygon == null) {
            if (result != null) {
                InnerOffsetDegradation.noteInnerOffsetFailure(result);
            }
            LOGGER.warn("内轮廓偏移失败（墙过厚或足迹过小），将不生成内部楼板；墙体按实心体量生成");
        }
        if (definition.massing().hasCoverageGaps()) {
            if (result != null) {
                result.warnings.add("plugin.building.warn.floor_plate_coverage_gap");
            }
            LOGGER.warn("FloorPlate 存在未覆盖楼层 {}，已用基础轮廓填充",
                definition.massing().coverageGapFloors());
        }

        List<GridCell> footprintCells = collectFootprintCells(outerPoints, outerPolygon);
        return new ResolvedMassingGeometry(
            Collections.unmodifiableList(outerPoints),
            outerPolygon,
            Collections.unmodifiableList(innerPoints),
            innerPolygon,
            Collections.unmodifiableList(footprintCells),
            true
        );
    }

    public static List<GridCell> collectFootprintCells(List<Vec2d> points, Polygon polygon) {
        List<GridCell> cells = new ArrayList<>();
        for (Vec2d center : BuildingGeometryUtils.collectFootprintCellCenters(points)) {
            if (polygon.contains(center)) {
                cells.add(new GridCell(center));
            }
        }
        return cells;
    }

    private static ResolvedMassingGeometry invalid() {
        return new ResolvedMassingGeometry(List.of(), null, List.of(), null, List.of(), false);
    }
}
