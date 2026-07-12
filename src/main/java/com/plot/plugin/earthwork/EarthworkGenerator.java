package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.earthwork.model.GradingRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 土方整平生成器
 */
public class EarthworkGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/EarthworkGenerator");

    private final CoordinateTransformer coordinateTransformer;

    public EarthworkGenerator(CoordinateTransformer coordinateTransformer) {
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
        public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
        public final Map<BlockPos, ChangeType> changeTypes = new LinkedHashMap<>();
        public final List<GridSample> gridSamples = new ArrayList<>();
        public long cutVolume;
        public long fillVolume;
        public int resolvedElevation;
        public int blockCount;
        public final List<String> warnings = new ArrayList<>();
    }

    public EarthworkGenerationResult generate(GradingRegion region, World world) {
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

        Polygon polygon = EarthworkGeometryUtils.toPolygon(outerPoints);
        List<Vec2d> allCenters = EarthworkGeometryUtils.collectFootprintCellCenters(outerPoints);
        List<Vec2d> sampleCenters = EarthworkGeometryUtils.collectSampleCenters(outerPoints, region.getGridSize());

        List<Integer> sampleHeights = new ArrayList<>();
        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        for (Vec2d center : sampleCenters) {
            BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(center, coordinateTransformer);
            sampleHeights.add(getTopHeight(world, column));
        }

        int targetElevation = resolveTargetElevation(region, sampleHeights);
        result.resolvedElevation = targetElevation;

        String fillBlockId = EarthworkGeometryUtils.resolveFillBlockId(region.getFillMaterial());
        String cutBlockId = EarthworkGeometryUtils.resolveCutBlockId(region.getCutExposeMaterial());

        for (Vec2d center : allCenters) {
            if (!polygon.contains(center)) {
                continue;
            }
            BlockPos column = EarthworkGeometryUtils.canvasToBlockXZ(center, coordinateTransformer);
            int groundY = getTopHeight(world, column);

            ChangeType sampleType = ChangeType.FILL;
            if (groundY > targetElevation) {
                sampleType = ChangeType.CUT;
            } else if (groundY == targetElevation) {
                sampleType = null;
            }

            if (matchesSampleGrid(center, region.getGridSize()) && sampleType != null) {
                result.gridSamples.add(new GridSample(center, groundY, sampleType));
            }

            if (groundY > targetElevation) {
                for (int y = targetElevation + 1; y <= groundY; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    recordBlock(result, pos, cutBlockId, ChangeType.CUT, projectionHandler);
                }
                result.cutVolume += groundY - targetElevation;
            } else if (groundY < targetElevation) {
                for (int y = groundY + 1; y <= targetElevation; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    recordBlock(result, pos, fillBlockId, ChangeType.FILL, projectionHandler);
                }
                result.fillVolume += targetElevation - groundY;
            }
        }

        region.setLastCutVolume(result.cutVolume);
        region.setLastFillVolume(result.fillVolume);
        region.setLastResolvedElevation(result.resolvedElevation);
        result.blockCount = result.placementRecords.size();
        return result;
    }

    private int resolveTargetElevation(GradingRegion region, List<Integer> sampleHeights) {
        if (region.isAutoBalance()) {
            return EarthworkBalanceUtils.findBalancedElevation(sampleHeights, region.getFillFactor());
        }
        if (region.getManualTargetElevation() != null) {
            return region.getManualTargetElevation();
        }
        return EarthworkBalanceUtils.findBalancedElevation(sampleHeights, region.getFillFactor());
    }

    private boolean matchesSampleGrid(Vec2d center, int gridSize) {
        if (gridSize <= 1) {
            return true;
        }
        int blockX = (int) Math.floor(center.x);
        int blockZ = (int) Math.floor(center.y);
        return blockX % gridSize == 0 && blockZ % gridSize == 0;
    }

    private void recordBlock(
            EarthworkGenerationResult result,
            BlockPos pos,
            String newBlockId,
            ChangeType changeType,
            BlockProjectionHandler projectionHandler) {
        if (!result.placementRecords.containsKey(pos)) {
            String previous = projectionHandler.getBlockIdAt(pos);
            result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
            result.changeTypes.put(pos, changeType);
        }
    }

    private int getTopHeight(World world, BlockPos pos) {
        try {
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, pos);
            return topPos != null ? topPos.getY() : pos.getY();
        } catch (Exception e) {
            LOGGER.warn("获取地形高度失败 ({}, {}): {}", pos.getX(), pos.getZ(), e.getMessage());
            return 64;
        }
    }
}
