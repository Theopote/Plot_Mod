package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.SnapshotCoordinateService;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.MassingGeometryResolver;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.test.world.IdentityCoordinateService;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.BuildingRoofGenerator;
import com.plot.plugin.building.generation.component.WallAttachmentPlacer;
import com.plot.plugin.building.generation.massing.FloorPlateScaleResolver;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import com.plot.plugin.building.ui.BuildingFloorPlateUi;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingWorldScaleTest {

    private static final ICoordinateService FOUR_BLOCKS_PER_CANVAS_UNIT =
        SnapshotCoordinateService.uniformScale(4.0);

    private static final List<Vec2d> SQUARE = List.of(
        new Vec2d(0, 0),
        new Vec2d(20, 0),
        new Vec2d(20, 20),
        new Vec2d(0, 20)
    );

    @Test
    void wallThicknessIsScaleInvariantInWorldBlocks() {
        BuildingFootprint footprint = new BuildingFootprint(SQUARE, true);
        footprint.setWallThickness(2);
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(footprint);
        BuildingGenerationResult result = new BuildingGenerationResult();

        var identity = MassingGeometryResolver.resolve(
            definition, result, BuildingCanvasScale.capture(IdentityCoordinateService.INSTANCE, SQUARE));
        var scaled = MassingGeometryResolver.resolve(
            definition, result, BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE));

        double identityCanvasInset = minCanvasX(identity.innerPoints());
        double scaledCanvasInset = minCanvasX(scaled.innerPoints());
        assertEquals(2.0, identityCanvasInset, 0.05);
        assertEquals(0.5, scaledCanvasInset, 0.05);

        double identityWorldInset = minWorldX(identity.innerPoints(), IdentityCoordinateService.INSTANCE);
        double scaledWorldInset = minWorldX(scaled.innerPoints(), FOUR_BLOCKS_PER_CANVAS_UNIT);
        assertEquals(2.0, identityWorldInset, 0.5);
        assertEquals(2.0, scaledWorldInset, 0.5);
    }

    @Test
    void blocksToCanvasScalesWithProjection() {
        BuildingCanvasScale identity = BuildingCanvasScale.capture(IdentityCoordinateService.INSTANCE, SQUARE);
        BuildingCanvasScale scaled = BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE);

        assertEquals(2.0, identity.uniformBlocksToCanvas(2, SQUARE), 1e-6);
        assertEquals(0.5, scaled.uniformBlocksToCanvas(2, SQUARE), 1e-6);
    }

    @Test
    void setbackInsetIsScaleInvariantInWorldBlocks() {
        BuildingCanvasScale identity = BuildingCanvasScale.capture(IdentityCoordinateService.INSTANCE, SQUARE);
        BuildingCanvasScale scaled = BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE);

        FloorPlateSpec identityUpper = identity.insetFloorPlate(2, 3, SQUARE, 2.0);
        FloorPlateSpec scaledUpper = scaled.insetFloorPlate(2, 3, SQUARE, 2.0);

        double identityWorldInset = minWorldX(identityUpper.outerPoints(), IdentityCoordinateService.INSTANCE);
        double scaledWorldInset = minWorldX(scaledUpper.outerPoints(), FOUR_BLOCKS_PER_CANVAS_UNIT);

        assertEquals(2.0, identityWorldInset, 0.5);
        assertEquals(2.0, scaledWorldInset, 0.5);
    }

    @Test
    void balconyDepthUsesWorldBlocks() {
        List<Vec2d> wall = List.of(new Vec2d(0, 0), new Vec2d(20, 0), new Vec2d(20, 1), new Vec2d(0, 1));
        BuildingGenerationResult result = new BuildingGenerationResult();

        Set<BlockPos> identitySlab = WallAttachmentPlacer.placeHorizontalSlab(
            result,
            wall,
            0,
            0.5,
            64,
            3,
            2,
            "minecraft:oak_planks",
            BuildingCanvasScale.capture(IdentityCoordinateService.INSTANCE, wall),
            IdentityCoordinateService.INSTANCE,
            noopProjection());

        result = new BuildingGenerationResult();
        Set<BlockPos> scaledSlab = WallAttachmentPlacer.placeHorizontalSlab(
            result,
            wall,
            0,
            0.5,
            64,
            3,
            2,
            "minecraft:oak_planks",
            BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, wall),
            FOUR_BLOCKS_PER_CANVAS_UNIT,
            noopProjection());

        assertEquals(6, identitySlab.size());
        assertEquals(6, scaledSlab.size());
        assertEquals(
            maxWorldOffsetFromWall(identitySlab, IdentityCoordinateService.INSTANCE),
            maxWorldOffsetFromWall(scaledSlab, FOUR_BLOCKS_PER_CANVAS_UNIT),
            0.5);
    }

    @Test
    void gableRoofRisePreservesPitchRatioAcrossProjection() {
        BuildingGeometryUtils.RectBounds bounds = new BuildingGeometryUtils.RectBounds(0, 20, 0, 10);
        BuildingCanvasScale identity = com.plot.test.building.BuildingCanvasScales.capture(SQUARE);
        BuildingCanvasScale scaled = BuildingCanvasScale.capture(FOUR_BLOCKS_PER_CANVAS_UNIT, SQUARE);
        Vec2d ridgePoint = new Vec2d(10, 5);
        Vec2d eaveDirection = new Vec2d(0, 1);
        double canvasEaveDistance = 5.0;

        int identityRidge = BuildingRoofGenerator.computeGableRise(10, 5, bounds, true, 2, identity);
        int scaledRidge = BuildingRoofGenerator.computeGableRise(10, 5, bounds, true, 2, scaled);

        assertEquals(2, identityRidge);
        assertEquals(
            (int) Math.floor(identity.canvasToBlocks(canvasEaveDistance, ridgePoint, eaveDirection) / 2.0),
            identityRidge);
        assertEquals(
            (int) Math.floor(scaled.canvasToBlocks(canvasEaveDistance, ridgePoint, eaveDirection) / 2.0),
            scaledRidge);
        assertEquals(identity.canvasToBlocks(canvasEaveDistance, ridgePoint, eaveDirection) * 4.0,
            scaled.canvasToBlocks(canvasEaveDistance, ridgePoint, eaveDirection),
            0.5);
    }

    @Test
    void simpleTowerUiRoundTripUsesBlocksNotCanvasUnits() {
        BuildingFootprint building = new BuildingFootprint(SQUARE, true);
        building.setFloors(4);
        BuildingFloorPlateUi.applySimpleTower(building, 2, 2.0, com.plot.test.building.BuildingCanvasScales.capture(SQUARE));

        BuildingFloorPlateUi.SimpleTowerState state =
            BuildingFloorPlateUi.readState(building, com.plot.test.building.BuildingCanvasScales.capture(SQUARE));
        assertTrue(state.enabled());
        assertEquals(2.0, state.insetDistance(), 1e-6);
    }

    private static double maxWorldOffsetFromWall(Set<BlockPos> slab, ICoordinateService coordinates) {
        double anchorZ = coordinates.canvasToMinecraftWorld(new Vec2d(10, 0)).y;
        return slab.stream()
            .mapToDouble(pos -> Math.abs(pos.getZ() - anchorZ))
            .max()
            .orElse(0.0);
    }

    private static IBlockProjectionService noopProjection() {
        return new IBlockProjectionService() {
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

    private static double minCanvasX(List<Vec2d> points) {
        return points.stream().mapToDouble(p -> p.x).min().orElse(0.0);
    }

    private static double minWorldX(List<Vec2d> points, ICoordinateService coordinates) {
        return points.stream()
            .mapToDouble(p -> coordinates.canvasToMinecraftWorld(p).x)
            .min()
            .orElse(0.0);
    }
}
