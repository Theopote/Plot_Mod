package com.plot.plugin.earthwork;

import com.plot.plugin.earthwork.terrain.TerrainSnapshot;
import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.RegionGeometry;
import com.plot.plugin.earthwork.model.CompositionPolicy;
import com.plot.plugin.earthwork.model.EarthworkSite;
import com.plot.plugin.earthwork.model.GradingRegion;
import com.plot.plugin.earthwork.model.GradingSurfaceMode;
import com.plot.plugin.earthwork.model.GradingZone;
import com.plot.plugin.earthwork.pipeline.EarthworkGenerationResult;
import com.plot.plugin.earthwork.pipeline.EarthworkPipelines;
import com.plot.plugin.earthwork.voxel.EarthworkVoxelizer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 土方集成测试夹具：规则矩形区域、现状快照与合成场地。
 */
public final class EarthworkTestFixtures {

    public static final String STONE = "minecraft:stone";
    public static final String AIR = "minecraft:air";
    public static final String DIRT = "minecraft:dirt";

    private EarthworkTestFixtures() {
    }

    public static List<Vec2d> rectangleOutline(int minX, int maxX, int minZ, int maxZ) {
        return List.of(
            new Vec2d(minX, minZ),
            new Vec2d(maxX + 1.0, minZ),
            new Vec2d(maxX + 1.0, maxZ + 1.0),
            new Vec2d(minX, maxZ + 1.0));
    }

    public static TerrainSnapshot rectangleTerrain(int minX, int maxX, int minZ, int maxZ, int groundY) {
        List<TerrainSnapshot.Column> columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                columns.add(new TerrainSnapshot.Column(new Vec2d(x + 0.5, z + 0.5), x, z, groundY));
            }
        }
        return TerrainSnapshot.forColumns(columns);
    }

    public static TerrainSnapshot rectangleTerrain(
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            java.util.function.BiFunction<Integer, Integer, Integer> groundAt) {
        List<TerrainSnapshot.Column> columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int groundY = groundAt.apply(x, z);
                columns.add(new TerrainSnapshot.Column(new Vec2d(x + 0.5, z + 0.5), x, z, groundY));
            }
        }
        return TerrainSnapshot.forColumns(columns);
    }

    public static int rectangleCellCount(int minX, int maxX, int minZ, int maxZ) {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public static GradingRegion levelPadRegion(
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int targetElevation,
            boolean autoBalance) {
        GradingRegion region = new GradingRegion(rectangleOutline(minX, maxX, minZ, maxZ));
        region.setSurfaceMode(GradingSurfaceMode.LEVEL_PAD);
        region.setAutoBalance(autoBalance);
        region.setManualTargetElevation(targetElevation);
        region.setPreviewGridSize(1);
        region.setFillMaterial(DIRT);
        return region;
    }

    public static EarthworkVoxelizer.BlockSampler solidColumnSampler(TerrainSnapshot terrain, String solidBlockId) {
        Map<BlockPos, String> blocks = new HashMap<>();
        for (TerrainSnapshot.Column column : terrain.columns()) {
            for (int y = 1; y <= column.groundY(); y++) {
                blocks.put(new BlockPos(column.worldX(), y, column.worldZ()), solidBlockId);
            }
        }
        return pos -> blocks.getOrDefault(pos, AIR);
    }

    public static EarthworkGenerationResult generateLegacy(
            GradingRegion region,
            TerrainSnapshot terrain,
            EarthworkVoxelizer.BlockSampler sampler) {
        return EarthworkPipelines.create(null, sampler).legacy().execute(region, null, terrain, null);
    }

    public static EarthworkSite twoZoneSiteForCompose() {
        EarthworkSite site = new EarthworkSite();
        site.setSiteBoundary(rectangleOutline(0, 9, 0, 9));
        site.getCompositionPolicy().setBalanceScope(CompositionPolicy.BALANCE_SCOPE_PER_ZONE);
        return site;
    }

    public static GradingZone donutZone(String id, int outerMax, int holeMin, int holeMax, int targetY) {
        GradingZone zone = new GradingZone(id, RegionGeometry.of(
            rectangleOutline(0, outerMax, 0, outerMax),
            List.of(rectangleOutline(holeMin, holeMax, holeMin, holeMax))));
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(targetY);
        zone.getRegion().setPreviewGridSize(1);
        zone.getRegion().setFillMaterial(DIRT);
        return zone;
    }

    public static GradingZone tinyCompanionZone(String id) {
        GradingZone zone = new GradingZone(id, RegionGeometry.of(rectangleOutline(8, 9, 8, 9)));
        zone.getRegion().setAutoBalance(false);
        zone.getRegion().setManualTargetElevation(64);
        zone.getRegion().setPreviewGridSize(1);
        zone.getRegion().setFillMaterial(DIRT);
        return zone;
    }

    public static boolean isInsideClosedRect(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
