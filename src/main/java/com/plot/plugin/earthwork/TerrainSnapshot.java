package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域内地形高度快照：对 footprint 全部格点采样一次，供求解、预览与生成复用。
 */
public final class TerrainSnapshot {
    public record Column(Vec2d center, int worldX, int worldZ, int groundY) {
    }

    private final List<Column> columns;

    private TerrainSnapshot(List<Column> columns) {
        this.columns = List.copyOf(columns);
    }

    public static TerrainSnapshot empty() {
        return new TerrainSnapshot(List.of());
    }

    /** 单元测试用：由已知柱数据构造快照。 */
    static TerrainSnapshot forColumns(List<Column> columns) {
        return new TerrainSnapshot(columns != null ? columns : List.of());
    }

    public static TerrainSnapshot capture(
            World world,
            Polygon polygon,
            List<Vec2d> outerPoints,
            ICoordinateService transformer) {
        if (polygon == null || outerPoints == null || outerPoints.size() < 3) {
            return empty();
        }
        List<Vec2d> footprintCenters = EarthworkGeometryUtils.collectFootprintCellCenters(outerPoints);
        List<Column> columns = new ArrayList<>();
        for (Vec2d center : footprintCenters) {
            if (!polygon.contains(center)) {
                continue;
            }
            BlockPos block = EarthworkGeometryUtils.canvasToBlockXZ(center, transformer);
            int groundY = TerrainSurfaceSampler.sampleAtBlock(world, block.getX(), block.getZ());
            columns.add(new Column(center, block.getX(), block.getZ(), groundY));
        }
        return new TerrainSnapshot(columns);
    }

    public List<Column> columns() {
        return columns;
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    public int columnCount() {
        return columns.size();
    }

    public List<Vec2d> centers() {
        List<Vec2d> centers = new ArrayList<>(columns.size());
        for (Column column : columns) {
            centers.add(column.center());
        }
        return centers;
    }

    public List<Integer> groundHeights() {
        List<Integer> heights = new ArrayList<>(columns.size());
        for (Column column : columns) {
            heights.add(column.groundY());
        }
        return heights;
    }

    public List<GradingSurfaceResolver.HeightSample> heightSamples() {
        List<GradingSurfaceResolver.HeightSample> samples = new ArrayList<>(columns.size());
        for (Column column : columns) {
            samples.add(new GradingSurfaceResolver.HeightSample(
                column.worldX(), column.worldZ(), column.groundY()));
        }
        return samples;
    }

    public List<Column> previewColumns(int previewGridSize) {
        if (previewGridSize <= 1) {
            return columns;
        }
        List<Column> preview = new ArrayList<>();
        for (Column column : columns) {
            if (EarthworkGeometryUtils.matchesPreviewGrid(column.center(), previewGridSize)) {
                preview.add(column);
            }
        }
        return preview.isEmpty() ? columns : preview;
    }

    public List<GradingSurfaceResolver.HeightSample> previewHeightSamples(int previewGridSize) {
        List<GradingSurfaceResolver.HeightSample> samples = new ArrayList<>();
        for (Column column : previewColumns(previewGridSize)) {
            samples.add(new GradingSurfaceResolver.HeightSample(
                column.worldX(), column.worldZ(), column.groundY()));
        }
        return samples;
    }

    public List<Column> columnsView() {
        return Collections.unmodifiableList(columns);
    }
}
