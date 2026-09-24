package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.api.world.WorldProjectionSnapshot;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 世界方块列格网：在任意世界原点偏移下，矩形外墙不得出现 1 格宽的列缺口。
 */
class BuildingWorldColumnGridTest {

    @Test
    void rectangleWallsHaveNoWorldColumnGapsAtAwkwardWorldOrigin() {
        SnapshotCoordinateService coords = awkwardWorldProjection();
        for (double offset : List.of(0.0, 0.11, 0.27, 0.5, 0.63, 0.89)) {
            assertNoExteriorWallColumnGaps(14, 10, offset, coords);
        }
    }

    @Test
    void rectangleWallsHaveNoWorldColumnGapsAtAnisotropicProjection() {
        SnapshotCoordinateService coords = anisotropicProjection(1.0, 3.7);
        for (double offset : List.of(0.0, 0.33, 0.5)) {
            assertNoExteriorWallColumnGaps(16, 12, offset, coords);
        }
    }

    private static void assertNoExteriorWallColumnGaps(
            int width,
            int depth,
            double offset,
            com.plot.api.world.ICoordinateService coords) {
        BuildingFootprint footprint = rectangle(width, depth, offset);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, result);
        new BuildingGenerationPipeline(List.of(new WallGenerationStage())).generate(context);

        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        int floorY = context.getBaseElevation();
        Set<Long> wallColumns = columnsAtMaterialAndY(result, wallId, floorY);

        ResolvedFloorPlate plate = context.resolvedFloorPlate(0);
        List<String> missing = new ArrayList<>();
        for (var cell : plate.outerCells()) {
            if (!InnerOffsetDegradation.isWallMassCell(
                    plate.outerPolygon(), plate.innerPolygon(), cell.center())) {
                continue;
            }
            BlockPos col = context.canvasToColumn(cell.center());
            if (!wallColumns.contains(pack(col.getX(), col.getZ()))) {
                missing.add(col.getX() + "," + col.getZ());
            }
        }
        assertTrue(missing.isEmpty(),
            "missing exterior wall columns offset=" + offset + ": " + missing);

        List<String> gaps = findAdjacentWallColumnGaps(context, plate, wallColumns);
        assertTrue(gaps.isEmpty(),
            "world column gaps offset=" + offset + ": " + gaps);
    }

    private static List<String> findAdjacentWallColumnGaps(
            BuildingGenerationContext context,
            ResolvedFloorPlate plate,
            Set<Long> wallColumns) {
        List<String> gaps = new ArrayList<>();
        int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var cell : plate.outerCells()) {
            if (!InnerOffsetDegradation.isWallMassCell(
                    plate.outerPolygon(), plate.innerPolygon(), cell.center())) {
                continue;
            }
            BlockPos col = context.canvasToColumn(cell.center());
            long packed = pack(col.getX(), col.getZ());
            if (!wallColumns.contains(packed)) {
                continue;
            }
            for (int[] delta : neighbors) {
                int nx = col.getX() + delta[0];
                int nz = col.getZ() + delta[1];
                Vec2d neighborCanvas = context.getCanvasScale().worldBlockCenterToCanvas(nx, nz);
                if (!InnerOffsetDegradation.isWallMassCell(
                        plate.outerPolygon(), plate.innerPolygon(), neighborCanvas)) {
                    continue;
                }
                if (!wallColumns.contains(pack(nx, nz))) {
                    gaps.add(col.getX() + "," + col.getZ() + " -> " + nx + "," + nz);
                }
            }
        }
        return gaps;
    }

    private static BuildingFootprint rectangle(int width, int depth, double offset) {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(offset, offset),
            new Vec2d(offset + width, offset),
            new Vec2d(offset + width, offset + depth),
            new Vec2d(offset, offset + depth)
        ), true);
        GoldenBuildingCaseFactory.applyDefaults(footprint, 2, 3, 1);
        return footprint;
    }

    private static Set<Long> columnsAtMaterialAndY(
            BuildingGenerationResult result, String materialId, int y) {
        Set<Long> columns = new HashSet<>();
        for (var record : result.placementRecords.values()) {
            if (record.pos.getY() == y && materialId.equals(record.newBlockId)) {
                columns.add(pack(record.pos.getX(), record.pos.getZ()));
            }
        }
        return columns;
    }

    private static SnapshotCoordinateService awkwardWorldProjection() {
        WorldViewBounds bounds = new WorldViewBounds(137.0, 937.0, -203.0, 397.0);
        return new SnapshotCoordinateService(new WorldProjectionSnapshot(
            bounds, 256f, 1f, 800f, 600f));
    }

    private static SnapshotCoordinateService anisotropicProjection(double xScale, double zScale) {
        float canvasWidth = 800f;
        float canvasHeight = 600f;
        WorldViewBounds bounds = new WorldViewBounds(
            53.0,
            53.0 + canvasWidth * xScale,
            -17.0,
            -17.0 + canvasHeight * zScale);
        return new SnapshotCoordinateService(new WorldProjectionSnapshot(
            bounds, 256f, 1f, canvasWidth, canvasHeight));
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static final IBlockProjectionService NOOP = new IBlockProjectionService() {
        @Override
        public PlacementReadiness checkWorldModificationReadiness() {
            return PlacementReadiness.ok();
        }

        @Override
        public String getBlockIdAt(BlockPos pos) {
            return "minecraft:air";
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            return false;
        }
    };
}
