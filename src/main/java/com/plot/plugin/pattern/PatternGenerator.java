package com.plot.plugin.pattern;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.PolygonRegionUtils;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.pattern.model.PatternFootprint;
import com.plot.plugin.pattern.model.ProceduralPatternConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

/**
 * 铺装图案生成器：在区域内地表高度替换表层方块。
 */
public class PatternGenerator {
    private final ICoordinateService coordinateTransformer;
    private final IBlockProjectionService projectionHandler;

    public PatternGenerator(
            ICoordinateService coordinateTransformer,
            IBlockProjectionService projectionHandler) {
        this.coordinateTransformer = Objects.requireNonNull(coordinateTransformer, "coordinateTransformer");
        this.projectionHandler = Objects.requireNonNull(projectionHandler, "projectionHandler");
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

        ProceduralPatternConfig pattern = footprint.getPattern();
        Vec2d regionCentroid = footprint.computeCentroid();
        EngineeringTerrainService terrain = EngineeringTerrainService.of(world);
        List<Vec2d> cellCenters = PolygonRegionUtils.collectFootprintCellCenters(outerPoints);

        for (Vec2d center : cellCenters) {
            BlockPos column = PatternGeometryUtils.canvasToBlockXZ(center, coordinateTransformer);
            int surfaceY = terrain.sampleGroundSurface(column.getX(), column.getZ());
            BlockPos pos = new BlockPos(column.getX(), surfaceY, column.getZ());
            int materialIndex = ProceduralPatternResolver.resolveMaterialIndex(
                pattern,
                column.getX(),
                column.getZ(),
                regionCentroid,
                footprint.getId());
            List<String> materials = pattern.getMaterials();
            String newBlockId = materials.get(Math.min(materialIndex, materials.size() - 1));
            recordBlock(result, pos, newBlockId);
        }

        result.blockCount = result.placementRecords.size();
        return result;
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
