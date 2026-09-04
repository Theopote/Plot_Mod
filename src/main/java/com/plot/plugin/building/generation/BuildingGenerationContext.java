package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.BuildingFoundationUtils;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单次建筑生成的上下文：输入、解析几何与结果容器。不包含业务生成逻辑。
 */
public final class BuildingGenerationContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/BuildingGenerationContext");

    private final BuildingFootprint footprint;
    private final BuildingDefinition definition;
    private final World world;
    private final ICoordinateService coordinateService;
    private final IBlockProjectionService projectionService;
    private final BuildingGenerationResult result;

    private final List<Vec2d> outerPoints;
    private final Polygon outerPolygon;
    private final List<Vec2d> innerPoints;
    private final Polygon innerPolygon;
    private final List<GridCell> footprintCells;
    private final int baseElevation;
    private final String foundationFillBlockId;
    private final String roofBlockId;
    private final boolean valid;

    private BuildingGenerationContext(
            BuildingFootprint footprint,
            BuildingDefinition definition,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result,
            List<Vec2d> outerPoints,
            Polygon outerPolygon,
            List<Vec2d> innerPoints,
            Polygon innerPolygon,
            List<GridCell> footprintCells,
            int baseElevation,
            String foundationFillBlockId,
            String roofBlockId,
            boolean valid) {
        this.footprint = footprint;
        this.definition = definition;
        this.world = world;
        this.coordinateService = coordinateService;
        this.projectionService = projectionService;
        this.result = result;
        this.outerPoints = outerPoints;
        this.outerPolygon = outerPolygon;
        this.innerPoints = innerPoints;
        this.innerPolygon = innerPolygon;
        this.footprintCells = footprintCells;
        this.baseElevation = baseElevation;
        this.foundationFillBlockId = foundationFillBlockId;
        this.roofBlockId = roofBlockId;
        this.valid = valid;
    }

    /**
     * 解析轮廓几何、基面标高与材料，构建上下文。无效输入返回 valid=false 的空结果上下文。
     */
    public static BuildingGenerationContext create(
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        Objects.requireNonNull(projectionService, "projectionService");
        BuildingGenerationResult result = new BuildingGenerationResult();

        if (footprint == null || world == null) {
            LOGGER.warn("建筑轮廓或世界为空");
            return empty(footprint, null, world, coordinateService, projectionService, result);
        }

        return createFromDefinition(
            BuildingDefinitionMapper.fromFootprint(footprint),
            footprint,
            world,
            coordinateService,
            projectionService,
            result
        );
    }

    /**
     * 直接从 {@link BuildingDefinition} 构建上下文（Footprint 仅作兼容引用，可为 null）。
     */
    public static BuildingGenerationContext createFromDefinition(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        Objects.requireNonNull(projectionService, "projectionService");
        BuildingGenerationResult result = new BuildingGenerationResult();

        if (definition == null || world == null) {
            LOGGER.warn("建筑定义或世界为空");
            return empty(footprint, definition, world, coordinateService, projectionService, result);
        }

        return createFromDefinition(definition, footprint, world, coordinateService, projectionService, result);
    }

    private static BuildingGenerationContext createFromDefinition(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        List<Vec2d> outerPoints = BuildingGeometryUtils.copyPoints(definition.footprint().outerPoints());
        if (outerPoints.size() < 3) {
            LOGGER.warn("建筑轮廓点数不足");
            return empty(footprint, definition, world, coordinateService, projectionService, result);
        }

        int wallThickness = definition.envelope().wallThickness();
        Polygon outerPolygon = BuildingGeometryUtils.toPolygon(outerPoints);
        List<Vec2d> innerPoints = BuildingGeometryUtils.offsetInward(outerPoints, wallThickness);
        Polygon innerPolygon = innerPoints.size() >= 3
            ? BuildingGeometryUtils.toPolygon(innerPoints)
            : null;
        if (innerPolygon == null) {
            result.warnings.add("plugin.building.warn.inner_offset_failed");
            LOGGER.warn("内轮廓偏移失败（墙过厚或足迹过小），将不生成内部楼板");
        }

        List<GridCell> footprintCells = collectFootprintCells(outerPoints, outerPolygon);

        List<Integer> groundHeights = new ArrayList<>();
        for (GridCell cell : footprintCells) {
            BlockPos column = BuildingGeometryUtils.canvasToBlockXZ(cell.center(), coordinateService);
            groundHeights.add(sampleTopHeight(world, column));
        }
        int baseElevation = BuildingFoundationUtils.computeBaseElevation(
            groundHeights, definition.foundation().manualBaseElevation());

        String foundationFill = BuildingGeometryUtils.resolveBlockId(definition.foundation().fillMaterial());
        String roofBlock = BuildingGeometryUtils.resolveBlockId(definition.roof().material());

        return new BuildingGenerationContext(
            footprint,
            definition,
            world,
            coordinateService,
            projectionService,
            result,
            Collections.unmodifiableList(outerPoints),
            outerPolygon,
            Collections.unmodifiableList(innerPoints),
            innerPolygon,
            Collections.unmodifiableList(footprintCells),
            baseElevation,
            foundationFill,
            roofBlock,
            true
        );
    }

    /**
     * 测试专用：直接从 {@link BuildingDefinition} 构建上下文。
     */
    public static BuildingGenerationContext forTesting(
            BuildingDefinition definition,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        Objects.requireNonNull(definition, "definition");
        BuildingFootprint footprint = new BuildingFootprint(
            definition.footprint().id(),
            BuildingGeometryUtils.copyPoints(definition.footprint().outerPoints()),
            definition.footprint().rectangular());
        BuildingDefinitionMapper.applyMassingEnvelopeFacadeRoofFoundation(definition, footprint);
        return forTesting(footprint, coordinateService, projectionService, result);
    }

    /**
     * 测试专用：构造 valid=true 的最小上下文，不依赖真实 Minecraft World。
     */
    public static BuildingGenerationContext forTesting(
            BuildingFootprint footprint,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(coordinateService, "coordinateService");
        Objects.requireNonNull(projectionService, "projectionService");
        Objects.requireNonNull(result, "result");

        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        List<Vec2d> outerPoints = BuildingGeometryUtils.copyPoints(definition.footprint().outerPoints());
        Polygon outerPolygon = outerPoints.size() >= 3
            ? BuildingGeometryUtils.toPolygon(outerPoints)
            : null;
        List<Vec2d> innerPoints = outerPoints.size() >= 3
            ? BuildingGeometryUtils.offsetInward(outerPoints, definition.envelope().wallThickness())
            : List.of();
        Polygon innerPolygon = innerPoints.size() >= 3
            ? BuildingGeometryUtils.toPolygon(innerPoints)
            : null;
        if (innerPolygon == null && outerPoints.size() >= 3) {
            result.warnings.add("plugin.building.warn.inner_offset_failed");
        }

        List<GridCell> footprintCells = outerPolygon != null
            ? collectFootprintCells(outerPoints, outerPolygon)
            : List.of();
        int baseElevation = BuildingFoundationUtils.computeBaseElevation(
            List.of(), definition.foundation().manualBaseElevation());
        String foundationFill = BuildingGeometryUtils.resolveBlockId(definition.foundation().fillMaterial());
        String roofBlock = BuildingGeometryUtils.resolveBlockId(definition.roof().material());

        return new BuildingGenerationContext(
            footprint,
            definition,
            null,
            coordinateService,
            projectionService,
            result,
            Collections.unmodifiableList(outerPoints),
            outerPolygon,
            Collections.unmodifiableList(innerPoints),
            innerPolygon,
            Collections.unmodifiableList(footprintCells),
            baseElevation,
            foundationFill,
            roofBlock,
            outerPoints.size() >= 3
        );
    }

    private static BuildingGenerationContext empty(
            BuildingFootprint footprint,
            BuildingDefinition definition,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        return new BuildingGenerationContext(
            footprint,
            definition,
            world,
            coordinateService,
            projectionService,
            result,
            List.of(),
            null,
            List.of(),
            null,
            List.of(),
            0,
            "minecraft:dirt",
            "minecraft:oak_planks",
            false
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

    public static int sampleTopHeight(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return EngineeringTerrainService.DEFAULT_GROUND_ELEVATION;
        }
        return EngineeringTerrainService.of(world).sampleGroundSurface(pos.getX(), pos.getZ());
    }

    public boolean isValid() {
        return valid;
    }

    /** @deprecated 优先使用 {@link #getDefinition()} */
    @Deprecated
    public BuildingFootprint getFootprint() {
        return footprint;
    }

    public BuildingDefinition getDefinition() {
        return definition;
    }

    public World getWorld() {
        return world;
    }

    public ICoordinateService getCoordinateService() {
        return coordinateService;
    }

    public IBlockProjectionService getProjectionService() {
        return projectionService;
    }

    public BuildingGenerationResult getResult() {
        return result;
    }

    public List<Vec2d> getOuterPoints() {
        return outerPoints;
    }

    public Polygon getOuterPolygon() {
        return outerPolygon;
    }

    public List<Vec2d> getInnerPoints() {
        return innerPoints;
    }

    public Polygon getInnerPolygon() {
        return innerPolygon;
    }

    public List<GridCell> getFootprintCells() {
        return footprintCells;
    }

    public int getBaseElevation() {
        return baseElevation;
    }

    public String getFoundationFillBlockId() {
        return foundationFillBlockId;
    }

    public String getRoofBlockId() {
        return roofBlockId;
    }

    public int getTopFloorY() {
        if (definition == null) {
            return baseElevation;
        }
        return baseElevation + definition.massing().totalHeight();
    }

    public record GridCell(Vec2d center) {
    }
}
