package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver.ResolvedFloorPlate;
import com.plot.plugin.building.generation.massing.InnerOffsetDegradation;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootprintContinuityTest {

    @Test
    void footprintCellsMapToDistinctWorldColumnsAtVariousOffsets() {
        for (double offset : List.of(0.0, 0.1, 0.25, 0.33, 0.5, 0.7, 1.0)) {
            assertNoWorldColumnCollisions(10, 8, offset, IdentityCoordinateService.INSTANCE);
        }
    }

    @Test
    void footprintCellsMapToDistinctWorldColumnsAtScaledProjection() {
        for (double offset : List.of(0.0, 0.25, 0.5)) {
            assertNoWorldColumnCollisions(10, 8, offset, SnapshotCoordinateService.uniformScale(4.0));
        }
    }

    @Test
    void exteriorWallMassCellsPlaceBlocksAtVariousOffsets() {
        for (double offset : List.of(0.0, 0.1, 0.25, 0.33, 0.5, 0.7)) {
            assertExteriorWallsPlaced(10, 8, offset, IdentityCoordinateService.INSTANCE);
        }
    }

    @Test
    void exteriorWallMassCellsPlaceBlocksAtScaledProjection() {
        for (double offset : List.of(0.0, 0.25, 0.5)) {
            assertExteriorWallsPlaced(10, 8, offset, SnapshotCoordinateService.uniformScale(4.0));
        }
    }

    private static void assertNoWorldColumnCollisions(
            int width, int depth, double offset, com.plot.api.world.ICoordinateService coords) {
        BuildingFootprint footprint = rectangle(width, depth, offset);
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, new BuildingGenerationResult());

        Map<Long, List<Vec2d>> worldToCanvas = new HashMap<>();
        for (var cell : context.getFootprintCells()) {
            BlockPos col = context.canvasToColumn(cell.center());
            long key = pack(col.getX(), col.getZ());
            worldToCanvas.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell.center());
        }

        for (var entry : worldToCanvas.entrySet()) {
            assertEquals(1, entry.getValue().size(),
                "multiple footprint cells mapped to world column "
                    + unpack(entry.getKey()) + " offset=" + offset + " centers=" + entry.getValue());
        }
    }

    private static void assertExteriorWallsPlaced(
            int width, int depth, double offset, com.plot.api.world.ICoordinateService coords) {
        BuildingFootprint footprint = rectangle(width, depth, offset);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, result);
        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new FloorGenerationStage()
        )).generate(context);

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
            long key = pack(col.getX(), col.getZ());
            if (!wallColumns.contains(key)) {
                missing.add(unpack(key) + " canvas=" + cell.center().x + "," + cell.center().y);
            }
        }

        assertTrue(missing.isEmpty(),
            "missing exterior wall columns offset=" + offset + ": " + missing);
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

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static String unpack(long packed) {
        return ((int) (packed >> 32)) + "," + ((int) packed);
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
