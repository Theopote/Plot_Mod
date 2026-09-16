package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.pattern.image.BlockColorMatcher;
import com.plot.plugin.pattern.image.ImagePatternRaster;
import com.plot.plugin.pattern.image.ImagePatternResolver;
import com.plot.plugin.pattern.image.PatternImageStore;
import com.plot.plugin.pattern.model.ImagePatternConfig;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.PatternSource;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 铺装图案生成器：Pattern Space 采样 → 地形投影 → 表层替换。
 */
public class PatternGenerator {
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;
    private final Path pluginDataDir;

    public PatternGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler,
            Path pluginDataDir) {
        this.coordinateTransformer = Objects.requireNonNull(coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = Objects.requireNonNull(projectionHandler, "projectionHandler");
        this.pluginDataDir = pluginDataDir;
    }

    public PatternGenerationResult generate(PatternFootprint footprint, World world) {
        PatternGenerationResult result = new PatternGenerationResult();
        if (footprint == null || world == null) {
            return result;
        }
        List<Vec2d> outerPoints = footprint.getOuterPoints();
        if (outerPoints.size() < 3) {
            return result;
        }

        if (footprint.getSource() == PatternSource.IMAGE) {
            generateImagePattern(footprint, world, outerPoints, result);
        } else {
            generateProceduralPattern(footprint, world, outerPoints, result);
        }
        return result;
    }

    private void generateProceduralPattern(
            PatternFootprint footprint,
            World world,
            List<Vec2d> outerPoints,
            PatternGenerationResult result) {
        ProceduralPatternConfig pattern = footprint.getPattern();
        Vec2d regionCentroid = footprint.computeCentroid();
        EngineeringTerrainService terrain = EngineeringTerrainService.of(world);
        List<Vec2d> cellCenters = PolygonRegionUtils.collectFootprintCellCenters(outerPoints);

        for (Vec2d canvasCenter : cellCenters) {
            int materialIndex = ProceduralPatternResolver.resolveMaterialIndex(
                pattern,
                canvasCenter.x,
                canvasCenter.y,
                regionCentroid,
                footprint.getId());
            List<String> materials = pattern.getMaterials();
            String newBlockId = materials.get(Math.min(materialIndex, materials.size() - 1));
            placeSurfaceBlock(canvasCenter, terrain, result, newBlockId);
        }
    }

    private void generateImagePattern(
            PatternFootprint footprint,
            World world,
            List<Vec2d> outerPoints,
            PatternGenerationResult result) {
        ImagePatternConfig config = footprint.getImagePattern();
        Optional<ImagePatternRaster> rasterOptional = PatternImageStore.loadRaster(pluginDataDir, config);
        if (rasterOptional.isEmpty()) {
            return;
        }

        ImagePatternRaster raster = rasterOptional.get();
        BlockColorMatcher matcher = new BlockColorMatcher(config.getPaletteBlocks());
        PolygonRegionUtils.RectBounds bounds = PolygonRegionUtils.computeBounds(outerPoints);
        EngineeringTerrainService terrain = EngineeringTerrainService.of(world);
        List<Vec2d> cellCenters = PolygonRegionUtils.collectFootprintCellCenters(outerPoints);

        for (Vec2d canvasCenter : cellCenters) {
            String newBlockId = ImagePatternResolver.resolveBlockId(
                config, raster, matcher, canvasCenter, bounds);
            if (newBlockId == null) {
                continue;
            }
            placeSurfaceBlock(canvasCenter, terrain, result, newBlockId);
        }
    }

    private void placeSurfaceBlock(
            Vec2d canvasCenter,
            EngineeringTerrainService terrain,
            PatternGenerationResult result,
            String newBlockId) {
        BlockPos column = PatternGeometryUtils.canvasToBlockXZ(canvasCenter, coordinateTransformer);
        int surfaceY = terrain.sampleGroundSurface(column.getX(), column.getZ());
        BlockPos pos = new BlockPos(column.getX(), surfaceY, column.getZ());
        recordBlock(result, pos, newBlockId);
    }

    private void recordBlock(PatternGenerationResult result, BlockPos pos, String newBlockId) {
        if (result == null || pos == null || newBlockId == null) {
            return;
        }
        BlockRecord existing = result.placementRecords.get(pos);
        if (existing != null) {
            result.placementRecords.put(pos, new BlockRecord(pos, existing.previousBlockId, newBlockId));
            return;
        }
        String previous = projectionHandler.getBlockIdAt(pos);
        result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
    }
}
