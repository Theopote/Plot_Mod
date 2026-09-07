package com.plot.plugin.earthwork.terrain;
import com.plot.plugin.earthwork.design.GradingSurfaceResolver;
import com.plot.plugin.earthwork.model.EarthworkSiteBoundaryUtils;
import com.plot.plugin.earthwork.geometry.EarthworkGeometryUtils;
import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.terrain.EngineeringTerrainSampler;
import com.plot.core.terrain.EngineeringTerrainService;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 现状地形快照：footprint 全部格点的工程地面标高、地表方块与采样元数据。
 * <p>
 * 作为土方方案的计算基准，支持预览后检测「世界地形是否已变化」。
 */
public final class TerrainSnapshot {
    public record Column(
            Vec2d center,
            int worldX,
            int worldZ,
            int groundY,
            String surfaceBlockId,
            boolean chunkLoaded) {
        public Column(Vec2d center, int worldX, int worldZ, int groundY) {
            this(center, worldX, worldZ, groundY, "", true);
        }
    }

    public record Metadata(
            long capturedAtEpochMs,
            String worldKey,
            long outlineFingerprint,
            long contentFingerprint,
            int columnCount,
            double captureMinX,
            double captureMinY,
            double captureMaxX,
            double captureMaxY) {
        public Instant capturedAt() {
            return Instant.ofEpochMilli(capturedAtEpochMs);
        }

        public boolean hasCaptureBounds() {
            return captureMaxX > captureMinX && captureMaxY > captureMinY;
        }

        public EarthworkSiteBoundaryUtils.CaptureBounds captureBounds() {
            return hasCaptureBounds()
                ? new EarthworkSiteBoundaryUtils.CaptureBounds(
                    captureMinX, captureMinY, captureMaxX, captureMaxY)
                : null;
        }
    }

    public record ComparisonResult(boolean matches, int changedColumns, int totalColumns) {
        public boolean terrainChanged() {
            return !matches;
        }
    }

    private final Metadata metadata;
    private final List<Column> columns;

    private TerrainSnapshot(Metadata metadata, List<Column> columns) {
        this.metadata = metadata;
        this.columns = List.copyOf(columns);
    }

    public static TerrainSnapshot empty() {
        return new TerrainSnapshot(
            new Metadata(0L, "", 0L, 0L, 0, 0.0, 0.0, 0.0, 0.0),
            List.of());
    }

    /** 单元测试用：由已知柱数据构造快照。 */
    public static TerrainSnapshot forColumns(List<Column> columns) {
        List<Column> safeColumns = columns != null ? columns : List.of();
        long fingerprint = computeContentFingerprint(safeColumns);
        EarthworkSiteBoundaryUtils.CaptureBounds bounds = boundsFromColumns(safeColumns);
        return new TerrainSnapshot(
            new Metadata(
                System.currentTimeMillis(),
                "test",
                0L,
                fingerprint,
                safeColumns.size(),
                bounds != null ? bounds.minX() : 0.0,
                bounds != null ? bounds.minY() : 0.0,
                bounds != null ? bounds.maxX() : 0.0,
                bounds != null ? bounds.maxY() : 0.0),
            safeColumns);
    }

    public static TerrainSnapshot capture(
            World world,
            Polygon polygon,
            List<Vec2d> outerPoints,
            ICoordinateService transformer) {
        if (polygon == null || outerPoints == null || outerPoints.size() < 3) {
            return empty();
        }
        EngineeringTerrainService terrainService = EngineeringTerrainService.of(world);
        String worldKey = TerrainSnapshotCache.worldKey(world);
        long outlineFingerprint = TerrainSnapshotCache.outlineFingerprint(outerPoints);

        List<Vec2d> footprintCenters = EarthworkGeometryUtils.collectFootprintCellCenters(outerPoints);
        List<Column> columns = new ArrayList<>();
        for (Vec2d center : footprintCenters) {
            if (!polygon.contains(center)) {
                continue;
            }
            BlockPos block = EarthworkGeometryUtils.canvasToBlockXZ(center, transformer);
            int worldX = block.getX();
            int worldZ = block.getZ();
            boolean chunkLoaded = terrainService.isChunkLoaded(worldX, worldZ);
            int groundY = EngineeringTerrainSampler.sampleGroundSurface(world, worldX, worldZ);
            String surfaceBlockId = chunkLoaded
                ? resolveBlockId(world, worldX, groundY, worldZ)
                : "";
            columns.add(new Column(center, worldX, worldZ, groundY, surfaceBlockId, chunkLoaded));
        }

        long contentFingerprint = computeContentFingerprint(columns);
        EarthworkSiteBoundaryUtils.CaptureBounds bounds =
            EarthworkSiteBoundaryUtils.CaptureBounds.fromBoundary(outerPoints);
        Metadata metadata = new Metadata(
            System.currentTimeMillis(),
            worldKey,
            outlineFingerprint,
            contentFingerprint,
            columns.size(),
            bounds != null ? bounds.minX() : 0.0,
            bounds != null ? bounds.minY() : 0.0,
            bounds != null ? bounds.maxX() : 0.0,
            bounds != null ? bounds.maxY() : 0.0);
        return new TerrainSnapshot(metadata, columns);
    }

    /**
     * 快照是否覆盖请求边界（含放坡带扩展后的 AABB）。
     */
    public boolean covers(List<Vec2d> requestedBoundary) {
        EarthworkSiteBoundaryUtils.CaptureBounds requested =
            EarthworkSiteBoundaryUtils.CaptureBounds.fromBoundary(requestedBoundary);
        if (requested == null) {
            return true;
        }
        EarthworkSiteBoundaryUtils.CaptureBounds capture = resolveCaptureBounds();
        return capture != null && capture.contains(requested);
    }

    private EarthworkSiteBoundaryUtils.CaptureBounds resolveCaptureBounds() {
        EarthworkSiteBoundaryUtils.CaptureBounds fromMetadata = metadata.captureBounds();
        if (fromMetadata != null) {
            return fromMetadata;
        }
        return boundsFromColumns(columns);
    }

    private static EarthworkSiteBoundaryUtils.CaptureBounds boundsFromColumns(List<Column> columns) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Column column : columns) {
            if (column == null) {
                continue;
            }
            // 用 block 索引 [x, x+1) 对齐 region outline / capture boundary 语义
            minX = Math.min(minX, column.worldX());
            minY = Math.min(minY, column.worldZ());
            maxX = Math.max(maxX, column.worldX() + 1.0);
            maxY = Math.max(maxY, column.worldZ() + 1.0);
        }
        if (!Double.isFinite(minX)) {
            return null;
        }
        return new EarthworkSiteBoundaryUtils.CaptureBounds(minX, minY, maxX, maxY);
    }

    public Metadata metadata() {
        return metadata;
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

    public long contentFingerprint() {
        return metadata.contentFingerprint;
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

    /**
     * 与当前世界重新采样结果对比，判断现状地形是否自快照以来发生变化。
     */
    public ComparisonResult compareWithCurrentWorld(World world) {
        if (isEmpty()) {
            return new ComparisonResult(true, 0, 0);
        }
        EngineeringTerrainService terrainService = EngineeringTerrainService.of(world);
        if (!metadata.worldKey.equals(TerrainSnapshotCache.worldKey(world))) {
            return new ComparisonResult(false, columnCount(), columnCount());
        }

        int changed = 0;
        for (Column column : columns) {
            if (!column.chunkLoaded()) {
                continue;
            }
            int currentGroundY = EngineeringTerrainSampler.sampleGroundSurface(
                world, column.worldX(), column.worldZ());
            String currentSurfaceBlockId = resolveBlockId(world, column.worldX(), currentGroundY, column.worldZ());
            if (column.groundY() != currentGroundY
                || !normalizeBlockId(column.surfaceBlockId()).equals(normalizeBlockId(currentSurfaceBlockId))) {
                changed++;
            }
        }
        return new ComparisonResult(changed == 0, changed, columnCount());
    }

    static long computeContentFingerprint(List<Column> columns) {
        long hash = 17L;
        for (Column column : columns) {
            hash = 31L * hash + column.worldX();
            hash = 31L * hash + column.worldZ();
            hash = 31L * hash + column.groundY();
            hash = 31L * hash + Objects.hashCode(normalizeBlockId(column.surfaceBlockId()));
            hash = 31L * hash + Boolean.hashCode(column.chunkLoaded());
        }
        return hash;
    }

    private static String resolveBlockId(World world, int worldX, int groundY, int worldZ) {
        if (world == null) {
            return "";
        }
        try {
            Block block = world.getBlockState(new BlockPos(worldX, groundY, worldZ)).getBlock();
            return Registries.BLOCK.getId(block).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "";
        }
        return blockId.trim().toLowerCase(Locale.ROOT);
    }
}
