package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.core.terrain.EngineeringTerrainService;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.resolve.GenerationSiteResolver;
import com.plot.plugin.building.generation.resolve.MassingGeometryResolver;
import com.plot.plugin.building.generation.resolve.MaterialResolver;
import com.plot.plugin.building.generation.resolve.ResolvedBuildingDefinition;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.site.BuildingSiteAnalysis;
import com.plot.plugin.building.site.BuildingSiteAnalyzer;
import com.plot.plugin.building.site.BuildingSiteColumnSample;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单次建筑生成的上下文：输入、已解析几何与结果容器。
 * <p>
 * 不包含业务生成逻辑，也不负责 Definition/场地/材料解析——见
 * {@link BuildingGenerationContextFactory}。
 */
public final class BuildingGenerationContext {
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
    private final BuildingSiteAnalysis siteAnalysis;
    private final Map<Long, BuildingSiteColumnSample> siteColumnSamples;

    /** 画布格 → 世界柱列缓存（同一栋内避免重复坐标变换） */
    private final Map<Long, BlockPos> columnCache = new HashMap<>();
    /** 楼层 → 已解析 FloorPlate（Wall/Floor 等 Stage 共享） */
    private final Map<Integer, com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate>
        floorPlateCache = new HashMap<>();

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
            boolean valid,
            BuildingSiteAnalysis siteAnalysis,
            Map<Long, BuildingSiteColumnSample> siteColumnSamples) {
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
        this.siteAnalysis = siteAnalysis != null
            ? siteAnalysis
            : BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION);
        this.siteColumnSamples = siteColumnSamples == null || siteColumnSamples.isEmpty()
            ? Map.of()
            : Map.copyOf(siteColumnSamples);
    }

    /**
     * 由 Factory 组装：{@link ResolvedBuildingDefinition} → Context。
     * {@code resolved == null} 或无效时返回 valid=false 空上下文。
     */
    public static BuildingGenerationContext fromResolved(
            BuildingFootprint footprint,
            BuildingDefinition definition,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result,
            ResolvedBuildingDefinition resolved) {
        Objects.requireNonNull(projectionService, "projectionService");
        BuildingGenerationResult safeResult = result != null ? result : new BuildingGenerationResult();
        if (resolved == null || !resolved.isValid()) {
            MaterialResolver.ResolvedMaterials materials = MaterialResolver.resolve(definition);
            return new BuildingGenerationContext(
                footprint,
                definition,
                world,
                coordinateService,
                projectionService,
                safeResult,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                0,
                materials.foundationFillBlockId(),
                materials.roofBlockId(),
                false,
                BuildingSiteAnalysis.emptyFallback(EngineeringTerrainService.DEFAULT_GROUND_ELEVATION),
                Map.of()
            );
        }

        MassingGeometryResolver.ResolvedMassingGeometry massing = resolved.massing();
        GenerationSiteResolver.ResolvedSiteElevation site = resolved.site();
        MaterialResolver.ResolvedMaterials materials = resolved.materials();
        return new BuildingGenerationContext(
            footprint,
            resolved.definition() != null ? resolved.definition() : definition,
            world,
            coordinateService,
            projectionService,
            safeResult,
            massing.outerPoints(),
            massing.outerPolygon(),
            massing.innerPoints(),
            massing.innerPolygon(),
            massing.footprintCells(),
            site.actualFoundationElevation(),
            materials.foundationFillBlockId(),
            materials.roofBlockId(),
            true,
            resolved.siteAnalysis(),
            resolved.siteColumnSamples()
        );
    }

    public static BuildingGenerationContext create(
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        return BuildingGenerationContextFactory.create(
            footprint, world, coordinateService, projectionService);
    }

    public static BuildingGenerationContext createFromDefinition(
            BuildingDefinition definition,
            BuildingFootprint footprint,
            World world,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService) {
        return BuildingGenerationContextFactory.createFromDefinition(
            definition, footprint, world, coordinateService, projectionService);
    }

    public static BuildingGenerationContext forTesting(
            BuildingDefinition definition,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        return BuildingGenerationContextFactory.forTesting(
            definition, coordinateService, projectionService, result);
    }

    public static BuildingGenerationContext forTesting(
            BuildingFootprint footprint,
            ICoordinateService coordinateService,
            IBlockProjectionService projectionService,
            BuildingGenerationResult result) {
        return BuildingGenerationContextFactory.forTesting(
            footprint, coordinateService, projectionService, result);
    }

    public static List<GridCell> collectFootprintCells(List<Vec2d> points, Polygon polygon) {
        return MassingGeometryResolver.collectFootprintCells(points, polygon);
    }

    public static int sampleTopHeight(World world, BlockPos pos) {
        return GenerationSiteResolver.sampleTopHeight(world, pos);
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

    /** 生成实际使用的地基标高（= {@link #getBaseElevation()}）。 */
    public int getActualFoundationElevation() {
        return baseElevation;
    }

    public BuildingSiteAnalysis getSiteAnalysis() {
        return siteAnalysis;
    }

    public BuildingSiteColumnSample siteColumnSample(int worldX, int worldZ) {
        return siteColumnSamples.get(BuildingSiteAnalyzer.packColumn(worldX, worldZ));
    }

    public Map<Long, BuildingSiteColumnSample> getSiteColumnSamples() {
        return siteColumnSamples;
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

    /**
     * 画布点 → 世界 XZ 柱列；同格多次调用只变换一次。
     */
    public BlockPos canvasToColumn(Vec2d canvasPos) {
        if (canvasPos == null) {
            return BlockPos.ORIGIN;
        }
        long key = packCanvasCell(canvasPos.x, canvasPos.y);
        BlockPos cached = columnCache.get(key);
        if (cached != null) {
            return cached;
        }
        BlockPos column = com.plot.plugin.building.BuildingGeometryUtils.canvasToBlockXZ(
            canvasPos, coordinateService);
        columnCache.put(key, column);
        return column;
    }

    /**
     * 解析指定楼层的 FloorPlate 几何；同楼层多 Stage 复用。
     */
    public com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate
            resolvedFloorPlate(int floorIndex) {
        return floorPlateCache.computeIfAbsent(floorIndex, floor ->
            com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.resolve(
                definition.massing().plateForFloor(floor),
                definition.envelope().wallThickness()));
    }

    private static long packCanvasCell(double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        return (((long) ix) << 32) ^ (iy & 0xffffffffL);
    }

    public record GridCell(Vec2d center) {
    }
}
