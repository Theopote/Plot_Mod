package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 土方整平生成器
 */
public class EarthworkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkGenerator");

    private final ICoordinateService coordinateTransformer;

    public EarthworkGenerator(ICoordinateService coordinateTransformer) {
        this.coordinateTransformer = coordinateTransformer;
    }

    public enum ChangeType {
        CUT, FILL
    }

    public static class GridSample {
        public final Vec2d center;
        public final int groundY;
        public final ChangeType changeType;

        public GridSample(Vec2d center, int groundY, ChangeType changeType) {
            this.center = center;
            this.groundY = groundY;
            this.changeType = changeType;
        }
    }

    public static class EarthworkGenerationResult {
        public TerrainSnapshot existingTerrainSnapshot = TerrainSnapshot.empty();
        public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
        public final Map<BlockPos, ChangeType> changeTypes = new LinkedHashMap<>();
        public final List<GridSample> gridSamples = new ArrayList<>();
        public EarthworkVolumeReport volumeReport = EarthworkVolumeReport.empty();
        public int resolvedElevation;
        public int resolvedElevationMin;
        public int resolvedElevationMax;
        public boolean slopedSurface;
        public final List<String> warnings = new ArrayList<>();
        public int calculationCellCount;
    }

    public EarthworkGenerationResult generate(GradingRegion region, World world) {
        return generate(region, world, null);
    }

    public EarthworkGenerationResult generate(
            GradingRegion region,
            World world,
            TerrainSnapshot terrainSnapshot) {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        if (region == null || world == null) {
            LOGGER.warn("整平区域或世界为空");
            return result;
        }

        List<Vec2d> outerPoints = region.getOuterPoints();
        if (outerPoints.size() < 3) {
            LOGGER.warn("整平区域轮廓点数不足");
            return result;
        }

        // STEP 1 — Capture existing terrain
        TerrainSnapshot terrain = captureExistingTerrain(region, world, outerPoints, terrainSnapshot);
        if (terrain.isEmpty()) {
            LOGGER.warn("整平区域无有效 footprint 格点");
            return result;
        }
        result.existingTerrainSnapshot = terrain;
        result.calculationCellCount = terrain.columnCount();

        // STEP 2 — Solve design surface
        GradingSurfaceResolver.ResolvedSurface surface = solveDesignSurface(region, terrain);
        GradingPlane plane = surface.plane();
        result.resolvedElevation = plane.isFlat()
            ? surface.elevationMin()
            : (surface.elevationMin() + surface.elevationMax()) / 2;
        result.resolvedElevationMin = surface.elevationMin();
        result.resolvedElevationMax = surface.elevationMax();
        result.slopedSurface = !plane.isFlat();

        // STEP 3 & 4 — Compute earthwork + generate voxel changes
        computeEarthworkAndVoxelChanges(region, world, terrain, plane, result);

        region.setLastVolumeReport(result.volumeReport);
        region.setLastResolvedElevation(result.resolvedElevation);
        region.setLastResolvedElevationMin(result.resolvedElevationMin);
        region.setLastResolvedElevationMax(result.resolvedElevationMax);
        return result;
    }

    private TerrainSnapshot captureExistingTerrain(
            GradingRegion region,
            World world,
            List<Vec2d> outerPoints,
            TerrainSnapshot terrainSnapshot) {
        if (terrainSnapshot != null && !terrainSnapshot.isEmpty()) {
            return terrainSnapshot;
        }
        Polygon polygon = EarthworkGeometryUtils.toPolygon(outerPoints);
        return TerrainSnapshot.capture(world, polygon, outerPoints, coordinateTransformer);
    }

    private GradingSurfaceResolver.ResolvedSurface solveDesignSurface(
            GradingRegion region,
            TerrainSnapshot terrain) {
        return GradingSurfaceResolver.resolve(
            region,
            terrain.centers(),
            terrain.groundHeights(),
            coordinateTransformer);
    }

    private void computeEarthworkAndVoxelChanges(
            GradingRegion region,
            World world,
            TerrainSnapshot terrain,
            GradingPlane plane,
            EarthworkGenerationResult result) {
        String fillBlockId = EarthworkGeometryUtils.resolveFillBlockId(region.getFillMaterial());
        String cutSurfaceBlockId = EarthworkGeometryUtils.resolveCutSurfaceBlockId(region.getCutExposeMaterial());

        long geometricCutVolume = 0L;
        long geometricFillVolume = 0L;
        long cutChangedBlocks = 0L;
        long fillChangedBlocks = 0L;

        for (TerrainSnapshot.Column column : terrain.columns()) {
            int groundY = column.groundY();
            int targetElevation = plane.evaluateAt(column.worldX(), column.worldZ());

            ChangeType sampleType = ChangeType.FILL;
            if (groundY > targetElevation) {
                sampleType = ChangeType.CUT;
            } else if (groundY == targetElevation) {
                sampleType = null;
            }

            if (EarthworkGeometryUtils.matchesPreviewGrid(column.center(), region.getPreviewGridSize())
                && sampleType != null) {
                result.gridSamples.add(new GridSample(column.center(), groundY, sampleType));
            }

            if (groundY > targetElevation) {
                geometricCutVolume += groundY - targetElevation;
                for (int y = targetElevation + 1; y <= groundY; y++) {
                    BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                    if (recordBlock(result, world, pos, EarthworkGeometryUtils.EXCAVATION_BLOCK_ID, ChangeType.CUT)) {
                        cutChangedBlocks++;
                    }
                }
                if (cutSurfaceBlockId != null) {
                    BlockPos surfacePos = new BlockPos(column.worldX(), targetElevation, column.worldZ());
                    if (recordBlock(result, world, surfacePos, cutSurfaceBlockId, ChangeType.CUT)) {
                        cutChangedBlocks++;
                    }
                }
            } else if (groundY < targetElevation) {
                geometricFillVolume += targetElevation - groundY;
                for (int y = groundY + 1; y <= targetElevation; y++) {
                    BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                    if (recordBlock(result, world, pos, fillBlockId, ChangeType.FILL)) {
                        fillChangedBlocks++;
                    }
                }
            }
        }

        result.volumeReport = EarthworkVolumeReport.fromMetrics(
            geometricCutVolume,
            geometricFillVolume,
            region.getMaterialProperties(),
            cutChangedBlocks,
            fillChangedBlocks);
    }

    /**
     * @return 是否写入了一条需要落地的变更
     */
    private boolean recordBlock(
            EarthworkGenerationResult result,
            World world,
            BlockPos pos,
            String newBlockId,
            ChangeType changeType) {
        if (result.placementRecords.containsKey(pos)) {
            return false;
        }
        String previous = getBlockIdAt(world, pos);
        if (!shouldApplyBlockChange(previous, newBlockId)) {
            return false;
        }
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
        result.changeTypes.put(pos, changeType);
        return true;
    }

    static boolean shouldApplyBlockChange(String previousBlockId, String newBlockId) {
        return !normalizeBlockId(previousBlockId).equals(normalizeBlockId(newBlockId));
    }

    static String normalizeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return Registries.BLOCK.getId(Blocks.AIR).toString();
        }
        return blockId.trim().toLowerCase(Locale.ROOT);
    }

    private String getBlockIdAt(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return Registries.BLOCK.getId(Blocks.AIR).toString();
        }
        try {
            Block block = world.getBlockState(pos).getBlock();
            return Registries.BLOCK.getId(block).toString();
        } catch (Exception e) {
            LOGGER.warn("读取方块失败 {}: {}", pos, e.getMessage());
            return Registries.BLOCK.getId(Blocks.AIR).toString();
        }
    }
}
