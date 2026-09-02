package com.plot.plugin.earthwork;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.geometry.shapes.Polygon;
import com.plot.api.world.ICoordinateService;
import com.plot.plugin.earthwork.model.EarthMaterialProperties;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingZone;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        public SiteEarthworkReport siteVolumeReport = SiteEarthworkReport.empty();
        public DesignTerrainGrid designTerrainGrid;
        public int resolvedElevation;
        public int resolvedElevationMin;
        public int resolvedElevationMax;
        public boolean slopedSurface;
        public boolean siteGeneration;
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

        TerrainSnapshot terrain = captureExistingTerrain(region, world, outerPoints, terrainSnapshot);
        if (terrain.isEmpty()) {
            LOGGER.warn("整平区域无有效 footprint 格点");
            return result;
        }
        result.existingTerrainSnapshot = terrain;
        result.calculationCellCount = terrain.columnCount();

        GradingSurfaceResolver.ResolvedSurface surface = solveDesignSurface(region, terrain);
        GradingPlane plane = surface.plane();
        result.resolvedElevation = plane.isFlat()
            ? surface.elevationMin()
            : (surface.elevationMin() + surface.elevationMax()) / 2;
        result.resolvedElevationMin = surface.elevationMin();
        result.resolvedElevationMax = surface.elevationMax();
        result.slopedSurface = !plane.isFlat();

        computeEarthworkFromPlane(region, world, terrain, plane, result, region.getPreviewGridSize());

        region.setLastVolumeReport(result.volumeReport);
        region.setLastResolvedElevation(result.resolvedElevation);
        region.setLastResolvedElevationMin(result.resolvedElevationMin);
        region.setLastResolvedElevationMax(result.resolvedElevationMax);
        return result;
    }

    public EarthworkGenerationResult generateSite(EarthworkSite site, World world) {
        return generateSite(site, world, null, null, BuildingFootprintLookup.NONE);
    }

    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion) {
        return generateSite(site, world, terrainSnapshot, previewRegion, BuildingFootprintLookup.NONE);
    }

    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup) {
        return generateSite(site, world, terrainSnapshot, previewRegion, buildingLookup, RoadSurfaceLookup.NONE);
    }

    public EarthworkGenerationResult generateSite(
            EarthworkSite site,
            World world,
            TerrainSnapshot terrainSnapshot,
            GradingRegion previewRegion,
            BuildingFootprintLookup buildingLookup,
            RoadSurfaceLookup roadLookup) {
        EarthworkGenerationResult result = new EarthworkGenerationResult();
        if (site == null || world == null) {
            LOGGER.warn("场地或世界为空");
            return result;
        }

        if (site.delegatesToLegacyGenerator()) {
            GradingZone zone = site.getLegacyDelegateZone();
            EarthworkGenerationResult delegated = generate(zone.getRegion(), world, terrainSnapshot);
            copyResult(result, delegated);
            site.setLastReport(result.siteVolumeReport.totals());
            return result;
        }

        List<Vec2d> siteBoundary = site.getSiteBoundary();
        if (siteBoundary.size() < 3) {
            LOGGER.warn("场地红线点数不足");
            return result;
        }

        result.siteGeneration = true;
        TerrainSnapshot terrain = captureSiteTerrain(site, world, siteBoundary, terrainSnapshot);
        if (terrain.isEmpty()) {
            LOGGER.warn("场地无有效 footprint 格点");
            return result;
        }
        result.existingTerrainSnapshot = terrain;
        result.calculationCellCount = terrain.columnCount();

        DesignTerrainComposer.ComposeResult composed = DesignTerrainComposer.compose(
            site, terrain, coordinateTransformer, buildingLookup, roadLookup);
        result.designTerrainGrid = composed.grid();
        result.resolvedElevationMin = composed.grid().minTargetY();
        result.resolvedElevationMax = composed.grid().maxTargetY();
        result.resolvedElevation = (result.resolvedElevationMin + result.resolvedElevationMax) / 2;
        result.slopedSurface = result.resolvedElevationMin != result.resolvedElevationMax;

        int previewGridSize = previewRegion != null
            ? previewRegion.getPreviewGridSize()
            : GradingRegion.DEFAULT_PREVIEW_GRID_SIZE;
        computeEarthworkFromDesignGrid(site, world, composed.grid(), result, previewGridSize);
        RetainingWallGenerator.generate(site, world, coordinateTransformer, result);

        site.setLastReport(result.siteVolumeReport.totals());
        for (GradingZone zone : site.getGradingZones().values()) {
            EarthworkVolumeReport zoneReport = result.siteVolumeReport.zoneReport(zone.getId());
            zone.getRegion().setLastVolumeReport(zoneReport);
            zone.getRegion().setLastResolvedElevation(result.resolvedElevation);
            zone.getRegion().setLastResolvedElevationMin(result.resolvedElevationMin);
            zone.getRegion().setLastResolvedElevationMax(result.resolvedElevationMax);
        }
        return result;
    }

    private static void copyResult(EarthworkGenerationResult target, EarthworkGenerationResult source) {
        target.existingTerrainSnapshot = source.existingTerrainSnapshot;
        target.placementRecords.putAll(source.placementRecords);
        target.changeTypes.putAll(source.changeTypes);
        target.gridSamples.addAll(source.gridSamples);
        target.volumeReport = source.volumeReport;
        target.siteVolumeReport = new SiteEarthworkReport(source.volumeReport, Map.of());
        target.resolvedElevation = source.resolvedElevation;
        target.resolvedElevationMin = source.resolvedElevationMin;
        target.resolvedElevationMax = source.resolvedElevationMax;
        target.slopedSurface = source.slopedSurface;
        target.calculationCellCount = source.calculationCellCount;
        target.warnings.addAll(source.warnings);
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

    private TerrainSnapshot captureSiteTerrain(
            EarthworkSite site,
            World world,
            List<Vec2d> siteBoundary,
            TerrainSnapshot terrainSnapshot) {
        if (terrainSnapshot != null && !terrainSnapshot.isEmpty()) {
            return terrainSnapshot;
        }
        Polygon polygon = EarthworkGeometryUtils.toPolygon(siteBoundary);
        return TerrainSnapshot.capture(world, polygon, siteBoundary, coordinateTransformer);
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

    private void computeEarthworkFromPlane(
            GradingRegion region,
            World world,
            TerrainSnapshot terrain,
            GradingPlane plane,
            EarthworkGenerationResult result,
            int previewGridSize) {
        SiteEarthworkReport.VolumeMetrics totals = new SiteEarthworkReport.VolumeMetrics();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            applyColumnEarthwork(
                region,
                world,
                column,
                plane.evaluateAt(column.worldX(), column.worldZ()),
                previewGridSize,
                result,
                totals,
                null);
        }
        result.volumeReport = totals.toReport(region.getMaterialProperties());
        result.siteVolumeReport = new SiteEarthworkReport(result.volumeReport, Map.of());
    }

    private void computeEarthworkFromDesignGrid(
            EarthworkSite site,
            World world,
            DesignTerrainGrid grid,
            EarthworkGenerationResult result,
            int previewGridSize) {
        Map<String, GradingZone> zonesById = site.getGradingZones();
        Map<String, SiteEarthworkReport.VolumeMetrics> zoneMetrics = new HashMap<>();
        SiteEarthworkReport.VolumeMetrics totals = new SiteEarthworkReport.VolumeMetrics();
        EarthMaterialProperties siteMaterial = site.getMaterialModel();

        for (DesignTerrainCell cell : grid.cells().values()) {
            if (!cell.participatesInEarthwork()) {
                continue;
            }
            GradingZone zone = zonesById.get(cell.zoneId());
            if (zone == null) {
                continue;
            }
            GradingRegion region = zone.getRegion();
            SiteEarthworkReport.VolumeMetrics zoneVolume = zoneMetrics.computeIfAbsent(
                zone.getId(),
                ignored -> new SiteEarthworkReport.VolumeMetrics());

            ColumnChange change = applyColumnEarthwork(
                region,
                world,
                toColumn(cell),
                cell.targetY(),
                previewGridSize,
                result,
                totals,
                zoneVolume);
            if (change == null) {
                continue;
            }
        }

        result.volumeReport = totals.toReport(siteMaterial);
        result.siteVolumeReport = SiteEarthworkReport.fromMetrics(totals, zoneMetrics, siteMaterial);
    }

    private static TerrainSnapshot.Column toColumn(DesignTerrainCell cell) {
        return new TerrainSnapshot.Column(
            cell.center(),
            cell.worldX(),
            cell.worldZ(),
            cell.existingGroundY());
    }

    private ColumnChange applyColumnEarthwork(
            GradingRegion region,
            World world,
            TerrainSnapshot.Column column,
            int targetElevation,
            int previewGridSize,
            EarthworkGenerationResult result,
            SiteEarthworkReport.VolumeMetrics totals,
            SiteEarthworkReport.VolumeMetrics zoneMetrics) {
        int groundY = column.groundY();
        ChangeType sampleType = ChangeType.FILL;
        if (groundY > targetElevation) {
            sampleType = ChangeType.CUT;
        } else if (groundY == targetElevation) {
            sampleType = null;
        }

        if (EarthworkGeometryUtils.matchesPreviewGrid(column.center(), previewGridSize)
            && sampleType != null) {
            result.gridSamples.add(new GridSample(column.center(), groundY, sampleType));
        }

        String fillBlockId = EarthworkGeometryUtils.resolveFillBlockId(region.getFillMaterial());
        String cutSurfaceBlockId = EarthworkGeometryUtils.resolveCutSurfaceBlockId(region.getCutExposeMaterial());

        long cutVolume = 0L;
        long fillVolume = 0L;
        long cutChanged = 0L;
        long fillChanged = 0L;

        if (groundY > targetElevation) {
            cutVolume = groundY - targetElevation;
            for (int y = targetElevation + 1; y <= groundY; y++) {
                BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                if (recordBlock(result, world, pos, EarthworkGeometryUtils.EXCAVATION_BLOCK_ID, ChangeType.CUT)) {
                    cutChanged++;
                }
            }
            if (cutSurfaceBlockId != null) {
                BlockPos surfacePos = new BlockPos(column.worldX(), targetElevation, column.worldZ());
                if (recordBlock(result, world, surfacePos, cutSurfaceBlockId, ChangeType.CUT)) {
                    cutChanged++;
                }
            }
        } else if (groundY < targetElevation) {
            fillVolume = targetElevation - groundY;
            for (int y = groundY + 1; y <= targetElevation; y++) {
                BlockPos pos = new BlockPos(column.worldX(), y, column.worldZ());
                if (recordBlock(result, world, pos, fillBlockId, ChangeType.FILL)) {
                    fillChanged++;
                }
            }
        }

        if (cutVolume > 0L) {
            totals.addCut(cutVolume, cutChanged);
            if (zoneMetrics != null) {
                zoneMetrics.addCut(cutVolume, cutChanged);
            }
        }
        if (fillVolume > 0L) {
            totals.addFill(fillVolume, fillChanged);
            if (zoneMetrics != null) {
                zoneMetrics.addFill(fillVolume, fillChanged);
            }
        }

        if (cutVolume == 0L && fillVolume == 0L) {
            return null;
        }
        return new ColumnChange(cutVolume, fillVolume);
    }

    private record ColumnChange(long cutVolume, long fillVolume) {
    }

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
        return blockId.trim().toLowerCase(java.util.Locale.ROOT);
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
