package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
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
            int columnCount) {
        public Instant capturedAt() {
            return Instant.ofEpochMilli(capturedAtEpochMs);
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
            new Metadata(0L, "", 0L, 0L, 0),
            List.of());
    }

    /** 单元测试用：由已知柱数据构造快照。 */
    static TerrainSnapshot forColumns(List<Column> columns) {
        List<Column> safeColumns = columns != null ? columns : List.of();
        long fingerprint = computeContentFingerprint(safeColumns);
        return new TerrainSnapshot(
            new Metadata(System.currentTimeMillis(), "test", 0L, fingerprint, safeColumns.size()),
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
            int groundY = terrainService.sampleGroundSurface(worldX, worldZ);
            String surfaceBlockId = chunkLoaded
                ? resolveBlockId(world, worldX, groundY, worldZ)
                : "";
            columns.add(new Column(center, worldX, worldZ, groundY, surfaceBlockId, chunkLoaded));
        }

        long contentFingerprint = computeContentFingerprint(columns);
        Metadata metadata = new Metadata(
            System.currentTimeMillis(),
            worldKey,
            outlineFingerprint,
            contentFingerprint,
            columns.size());
        return new TerrainSnapshot(metadata, columns);
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
            int currentGroundY = terrainService.sampleGroundSurface(column.worldX(), column.worldZ());
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
