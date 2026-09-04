package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.ICoordinateService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.WorldViewBounds;
import com.plot.plugin.building.generation.stage.AccessoryGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.AccessoryKind;
import com.plot.plugin.building.model.spec.AccessorySpec;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryGenerationStageTest {

    @Test
    void accessoryKindsStayWithinFreezeSet() {
        assertEquals(AccessoryKind.FROZEN_KIND_COUNT, AccessoryKind.values().length);
        assertEquals(AccessorySpec.FROZEN_KIND_COUNT, AccessoryKind.values().length);
        assertEquals(
            Set.of(AccessoryKind.PARAPET, AccessoryKind.CANOPY, AccessoryKind.BALCONY),
            Arrays.stream(AccessoryKind.values()).collect(Collectors.toSet()),
            "do not add cornice/colonnade/chimney/louver/etc until Accessory model is stable");
    }

    @Test
    void parapetAddsBlocksAboveTopWall() {
        BuildingFootprint footprint = rectangleFootprint();
        footprint.setParapetEnabled(true);
        footprint.setParapetHeight(2);
        footprint.setParapetMaterial("minecraft:stone_brick_wall");

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);

        int before = result.placementRecords.size();
        new AccessoryGenerationStage().generate(context);
        int after = result.placementRecords.size();

        assertTrue(after > before);
        int topWallY = context.getTopFloorY();
        long parapetBlocks = result.placementRecords.keySet().stream()
            .filter(pos -> pos.getY() >= topWallY && pos.getY() < topWallY + 2)
            .count();
        assertTrue(parapetBlocks > 0);
    }

    @Test
    void balconyAddsSlabOutsideWall() {
        BuildingFootprint footprint = rectangleFootprint();
        footprint.addBalcony(new BuildingFootprint.Balcony(1, 0.5, 0, 3, 2, null, null));

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);

        new AccessoryGenerationStage().generate(context);

        assertTrue(result.placementRecords.size() > 0);
        boolean hasOutsideX = result.placementRecords.keySet().stream()
            .anyMatch(pos -> pos.getX() > 10);
        assertTrue(hasOutsideX);
    }

    @Test
    void parapetGeneratesOnNarrowCorridorWhenInnerOffsetFails() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.b07NarrowCorridor().footprint();
        footprint.setParapetEnabled(true);
        footprint.setParapetHeight(1);
        footprint.setParapetMaterial("minecraft:stone_brick_wall");

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);

        new AccessoryGenerationStage().generate(context);

        assertTrue(result.placementRecords.size() > 0, "parapet should generate on solid wall mass");
        int topWallY = context.getTopFloorY();
        assertTrue(result.placementRecords.keySet().stream()
            .anyMatch(pos -> pos.getY() == topWallY));
    }

    @Test
    void balconyGeneratesFromOuterWallOnNarrowCorridor() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.b07NarrowCorridor().footprint();
        footprint.addBalcony(new BuildingFootprint.Balcony(0, 0.5, 0, 2, 1, null, null));

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);

        new AccessoryGenerationStage().generate(context);

        assertTrue(result.placementRecords.size() > 0, "balcony uses outer wall segment, not inner offset");
    }

    @Test
    void disabledAccessoriesProduceNoBlocks() {
        BuildingDefinition definition = BuildingDefinitionMapper.fromFootprint(rectangleFootprint());
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            definition, stubCoordinates(), stubProjection(), result);

        new AccessoryGenerationStage().generate(context);
        assertEquals(0, result.placementRecords.size());
    }

    @Test
    void accessorySpecRoundTripThroughDefinition() {
        BuildingFootprint footprint = rectangleFootprint();
        footprint.setParapetEnabled(true);
        footprint.setParapetHeight(3);
        footprint.addBalcony(new BuildingFootprint.Balcony(0, 0.5, 0, 2, 1, null, null));

        AccessorySpec accessory = BuildingDefinitionMapper.fromFootprint(footprint).accessory();
        assertTrue(accessory.parapet().enabled());
        assertEquals(3, accessory.parapet().height());
        assertEquals(1, accessory.balconies().size());
    }

    private static BuildingFootprint rectangleFootprint() {
        BuildingFootprint footprint = new BuildingFootprint(List.of(
            new Vec2d(0, 0),
            new Vec2d(10, 0),
            new Vec2d(10, 8),
            new Vec2d(0, 8)
        ), true);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWallThickness(1);
        return footprint;
    }

    private static ICoordinateService stubCoordinates() {
        return new ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public WorldViewBounds getMinecraftWorldViewBounds() {
                return new WorldViewBounds(-512, 512, -512, 512);
            }
        };
    }

    private static IBlockProjectionService stubProjection() {
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
                return true;
            }
        };
    }
}
