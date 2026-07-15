package com.plot.plugin.building;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.material.MaterialMixResolver;
import com.plot.infrastructure.coordinate.CoordinateTransformer;
import com.plot.infrastructure.event.block.BlockProjectionHandler;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 建筑生成器
 */
public class BuildingGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingGenerator");

    private final CoordinateTransformer coordinateTransformer;

    public BuildingGenerator(CoordinateTransformer coordinateTransformer) {
        this.coordinateTransformer = coordinateTransformer;
    }

    public static class BuildingGenerationResult {
        public final Map<BlockPos, BlockRecord> placementRecords = new LinkedHashMap<>();
        public int cutVolume;
        public int fillVolume;
        public int blockCount;
        public final List<String> warnings = new ArrayList<>();
        public BuildingFootprint.RoofType effectiveRoofType = BuildingFootprint.RoofType.FLAT;
    }

    public BuildingGenerationResult generate(BuildingFootprint footprint, World world) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        if (footprint == null || world == null) {
            LOGGER.warn("建筑轮廓或世界为空");
            return result;
        }

        List<Vec2d> outerPoints = BuildingGeometryUtils.copyPoints(footprint.getOuterPoints());
        if (outerPoints.size() < 3) {
            LOGGER.warn("建筑轮廓点数不足");
            return result;
        }

        Polygon outerPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        List<Vec2d> innerPoints = BuildingGeometryUtils.offsetInward(outerPoints, footprint.getWallThickness());
        Polygon innerPolygon = innerPoints.size() >= 3
            ? BuildingGeometryUtils.toPolygon(innerPoints)
            : null;

        BlockProjectionHandler projectionHandler = BlockProjectionHandler.getInstance();
        List<GridCell> footprintCells = collectFootprintCells(outerPoints, outerPolygon);

        List<Integer> groundHeights = new ArrayList<>();
        for (GridCell cell : footprintCells) {
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateTransformer);
            groundHeights.add(getTopHeight(world, column));
        }
        int baseElevation = BuildingFoundationUtils.computeBaseElevation(
            groundHeights, footprint.getManualBaseElevation());

        String foundationFill = BuildingGeometryUtils.resolveBlockId(footprint.getFoundationFillMaterial());
        String roofBlock = BuildingGeometryUtils.resolveBlockId(footprint.getRoofMaterial());

        levelFoundation(result, footprintCells, world, baseElevation, foundationFill, projectionHandler);
        generateWalls(result, footprintCells, outerPolygon, innerPolygon, world,
            baseElevation, footprint, projectionHandler);
        generateFloors(result, innerPolygon, world, baseElevation, footprint, projectionHandler);

        BuildingFootprint.RoofType roofType = resolveRoofType(footprint, result);
        result.effectiveRoofType = roofType;

        int topFloorY = baseElevation + footprint.getFloors() * footprint.getFloorHeight();
        if (roofType == BuildingFootprint.RoofType.FLAT) {
            replaceTopFloorMaterial(result, innerPolygon, world, topFloorY, roofBlock, projectionHandler);
        } else {
            replaceTopFloorMaterial(result, innerPolygon, world, topFloorY, roofBlock, projectionHandler);
            BuildingRoofGenerator.generate(
                result, footprint, outerPoints, topFloorY,
                roofBlock, roofType, coordinateTransformer, projectionHandler);
        }

        carveWindows(result, footprint, outerPoints, world, baseElevation, projectionHandler);
        carveDoors(result, footprint, outerPoints, world, baseElevation, projectionHandler);

        result.blockCount = result.placementRecords.size();
        return result;
    }

    private BuildingFootprint.RoofType resolveRoofType(
            BuildingFootprint footprint,
            BuildingGenerationResult result) {
        BuildingFootprint.RoofType requested = footprint.getRoofType();
        if (requested == BuildingFootprint.RoofType.FLAT) {
            return BuildingFootprint.RoofType.FLAT;
        }
        if (BuildingGeometryUtils.isSlopedRoofEligible(footprint.getOuterPoints())) {
            return requested;
        }
        result.warnings.add("plugin.building.warn.roof_downgrade");
        return BuildingFootprint.RoofType.FLAT;
    }

    private void levelFoundation(
            BuildingGenerationResult result,
            List<GridCell> cells,
            World world,
            int baseElevation,
            String fillBlockId,
            BlockProjectionHandler projectionHandler) {
        for (GridCell cell : cells) {
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateTransformer);
            int groundY = getTopHeight(world, column);
            if (groundY > baseElevation) {
                for (int y = baseElevation + 1; y <= groundY; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    recordBlock(result, pos, "minecraft:air", projectionHandler);
                }
                result.cutVolume += groundY - baseElevation;
            } else if (groundY < baseElevation) {
                for (int y = groundY + 1; y <= baseElevation; y++) {
                    BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                    recordBlock(result, pos, fillBlockId, projectionHandler);
                }
                result.fillVolume += baseElevation - groundY;
            }
        }
    }

    private void generateWalls(
            BuildingGenerationResult result,
            List<GridCell> cells,
            Polygon outerPolygon,
            Polygon innerPolygon,
            World world,
            int baseElevation,
            BuildingFootprint footprint,
            BlockProjectionHandler projectionHandler) {
        int topY = baseElevation + footprint.getFloors() * footprint.getFloorHeight();
        for (GridCell cell : cells) {
            Vec2d center = cell.center();
            if (!outerPolygon.contains(center)) {
                continue;
            }
            if (innerPolygon != null && innerPolygon.contains(center)) {
                continue;
            }
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(center, coordinateTransformer);
            for (int y = baseElevation; y < topY; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                String wallBlockId = MaterialMixResolver.resolve(
                    footprint.getWallMaterial(), pos, footprint.getId(),
                    BuildingGeometryUtils::resolveBlockId);
                recordBlock(result, pos, wallBlockId, projectionHandler);
            }
        }
    }

    private void generateFloors(
            BuildingGenerationResult result,
            Polygon innerPolygon,
            World world,
            int baseElevation,
            BuildingFootprint footprint,
            BlockProjectionHandler projectionHandler) {
        if (innerPolygon == null) {
            return;
        }

        List<GridCell> innerCells = collectFootprintCells(
            BuildingGeometryUtils.copyPoints(innerPolygon.getPoints()), innerPolygon);

        for (int floor = 0; floor <= footprint.getFloors(); floor++) {
            int floorY = baseElevation + floor * footprint.getFloorHeight();
            for (GridCell cell : innerCells) {
                if (!innerPolygon.contains(cell.center())) {
                    continue;
                }
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateTransformer);
                BlockPos pos = new BlockPos(column.getX(), floorY, column.getZ());
                String floorBlockId = MaterialMixResolver.resolve(
                    footprint.getFloorMaterial(), pos, footprint.getId(),
                    BuildingGeometryUtils::resolveBlockId);
                recordBlock(result, pos, floorBlockId, projectionHandler);
            }
        }
    }

    private void replaceTopFloorMaterial(
            BuildingGenerationResult result,
            Polygon innerPolygon,
            World world,
            int topFloorY,
            String roofBlockId,
            BlockProjectionHandler projectionHandler) {
        if (innerPolygon == null) {
            return;
        }
        List<GridCell> innerCells = collectFootprintCells(
            BuildingGeometryUtils.copyPoints(innerPolygon.getPoints()), innerPolygon);
        for (GridCell cell : innerCells) {
            if (!innerPolygon.contains(cell.center())) {
                continue;
            }
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateTransformer);
            BlockPos pos = new BlockPos(column.getX(), topFloorY, column.getZ());
            recordBlock(result, pos, roofBlockId, projectionHandler);
        }
    }

    private void carveWindows(
            BuildingGenerationResult result,
            BuildingFootprint footprint,
            List<Vec2d> outerPoints,
            World world,
            int baseElevation,
            BlockProjectionHandler projectionHandler) {
        if (footprint.getWindowSpacing() <= 0) {
            return;
        }

        List<BuildingGeometryUtils.WallSample> samples = BuildingGeometryUtils.sampleAlongWallSegments(
            outerPoints, footprint.getWindowSpacing());

        for (int floor = 0; floor < footprint.getFloors(); floor++) {
            int floorBaseY = baseElevation + floor * footprint.getFloorHeight();
            for (BuildingGeometryUtils.WallSample sample : samples) {
                carveOpening(
                    result,
                    sample.point(),
                    sample.tangent(),
                    sample.inwardNormal(),
                    footprint.getWindowWidth(),
                    footprint.getWindowHeight(),
                    floorBaseY + footprint.getWindowSillHeight(),
                    footprint.getWallThickness(),
                    projectionHandler
                );
            }
        }
    }

    private void carveDoors(
            BuildingGenerationResult result,
            BuildingFootprint footprint,
            List<Vec2d> outerPoints,
            World world,
            int baseElevation,
            BlockProjectionHandler projectionHandler) {
        int segmentCount = outerPoints.size();
        for (BuildingFootprint.DoorOpening door : footprint.getDoors()) {
            if (door.floor < 0 || door.floor >= footprint.getFloors()) {
                continue;
            }
            int segmentIndex = Math.floorMod(door.wallSegmentIndex, segmentCount);
            Vec2d point = BuildingGeometryUtils.pointOnWallSegment(
                outerPoints, segmentIndex, door.positionRatio);
            if (point == null) {
                continue;
            }
            Vec2d start = outerPoints.get(segmentIndex);
            Vec2d end = outerPoints.get((segmentIndex + 1) % segmentCount);
            Vec2d tangent = end.subtract(start).normalize();
            Vec2d inwardNormal = BuildingGeometryUtils.leftNormal(tangent);
            if (BuildingFootprint.signedArea(outerPoints) >= 0) {
                inwardNormal = inwardNormal.multiply(-1);
            }
            int floorBaseY = baseElevation + door.floor * footprint.getFloorHeight();
            carveOpening(
                result,
                point,
                tangent,
                inwardNormal,
                door.width,
                door.height,
                floorBaseY,
                footprint.getWallThickness(),
                projectionHandler
            );
        }
    }

    private void carveOpening(
            BuildingGenerationResult result,
            Vec2d centerPoint,
            Vec2d tangent,
            Vec2d inwardNormal,
            int width,
            int height,
            int startY,
            int wallThickness,
            BlockProjectionHandler projectionHandler) {
        Set<BlockPos> carved = new LinkedHashSet<>();
        for (int w = 0; w < width; w++) {
            double lateral = w - (width - 1) / 2.0;
            for (int depth = 0; depth < wallThickness; depth++) {
                Vec2d sample = centerPoint
                    .add(tangent.multiply(lateral))
                    .add(inwardNormal.multiply(depth + 0.5));
                BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(sample, coordinateTransformer);
                for (int h = 0; h < height; h++) {
                    BlockPos pos = new BlockPos(column.getX(), startY + h, column.getZ());
                    if (carved.add(pos)) {
                        recordBlock(result, pos, "minecraft:air", projectionHandler);
                    }
                }
            }
        }
    }

    private List<GridCell> collectFootprintCells(List<Vec2d> points, Polygon polygon) {
        List<GridCell> cells = new ArrayList<>();
        for (Vec2d center : BuildingGeometryUtils.collectFootprintCellCenters(points)) {
            if (polygon.contains(center)) {
                cells.add(new GridCell(center));
            }
        }
        return cells;
    }

    private void recordBlock(
            BuildingGenerationResult result,
            BlockPos pos,
            String newBlockId,
            BlockProjectionHandler projectionHandler) {
        if (!result.placementRecords.containsKey(pos)) {
            String previous = projectionHandler.getBlockIdAt(pos);
            result.placementRecords.put(pos, new BlockRecord(pos, previous, newBlockId));
        }
    }

    private int getTopHeight(World world, BlockPos pos) {
        try {
            BlockPos topPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, pos);
            if (topPos == null) {
                LOGGER.warn("获取地形高度返回null ({}, {})，使用海平面高度",
                    pos.getX(), pos.getZ());
                return world.getSeaLevel();
            }
            return topPos.getY();
        } catch (Exception e) {
            LOGGER.error("获取地形高度失败 ({}, {}): {}",
                pos.getX(), pos.getZ(), e.getMessage(), e);
            throw new RuntimeException("无法获取地形高度，建筑生成中止", e);
        }
    }

    private record GridCell(Vec2d center) {
    }
}
