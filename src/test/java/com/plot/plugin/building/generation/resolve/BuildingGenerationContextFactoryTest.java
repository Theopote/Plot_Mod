package com.plot.plugin.building.generation.resolve;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.spec.BuildingDefinition;
import com.plot.plugin.building.model.spec.BuildingDefinitionMapper;
import com.plot.plugin.building.model.spec.FloorPlateSpec;
import com.plot.plugin.building.model.spec.MassingSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGenerationContextFactoryTest {

    private static final List<Vec2d> RECT = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 6),
        new Vec2d(0, 6)
    );

    @Test
    void resolveForTestingProducesValidResolvedDefinition() {
        BuildingFootprint footprint = new BuildingFootprint(RECT, true);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWallThickness(1);
        BuildingDefinition definition = BuildingDefinitionResolver.fromFootprint(footprint);
        BuildingGenerationResult result = new BuildingGenerationResult();

        ResolvedBuildingDefinition resolved =
            BuildingGenerationContextFactory.resolveForTesting(definition, result);

        assertTrue(resolved.isValid());
        assertEquals(4, resolved.massing().outerPoints().size());
        assertNotNull(resolved.massing().outerPolygon());
        assertNotNull(resolved.massing().innerPolygon());
        assertFalse(resolved.massing().footprintCells().isEmpty());
        assertNotNull(resolved.materials().foundationFillBlockId());
        assertNotNull(resolved.materials().roofBlockId());
    }

    @Test
    void forTestingContextDelegatesToFactory() {
        BuildingFootprint footprint = new BuildingFootprint(RECT, true);
        footprint.setFloors(1);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);

        assertTrue(context.isValid());
        assertEquals(result, context.getResult());
        assertFalse(context.getFootprintCells().isEmpty());
        assertEquals(context.getDefinition().massing().baseOuterPoints().size(),
            context.getOuterPoints().size());
    }

    @Test
    void coverageGapWarningComesFromMassingResolver() {
        BuildingDefinition definition = new BuildingDefinition(
            new com.plot.plugin.building.model.spec.FootprintSpec("id", "t", RECT, true),
            MassingSpec.create(5, 3, RECT, List.of(
                FloorPlateSpec.of(0, 1, RECT),
                FloorPlateSpec.of(3, 4, RECT)
            )),
            new com.plot.plugin.building.model.spec.EnvelopeSpec(1, null, null),
            new com.plot.plugin.building.model.spec.FacadeSpec(null, List.of(), List.of()),
            new com.plot.plugin.building.model.spec.RoofSpec(
                BuildingFootprint.RoofType.FLAT, 1, null),
            new com.plot.plugin.building.model.spec.FoundationSpec(null, null),
            com.plot.plugin.building.model.spec.AccessorySpec.none()
        );
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContextFactory.resolveForTesting(definition, result);
        assertTrue(result.warnings.contains("plugin.building.warn.floor_plate_coverage_gap"));
    }

    @Test
    void insufficientPointsYieldsInvalidContext() {
        BuildingFootprint footprint = new BuildingFootprint(
            List.of(new Vec2d(0, 0), new Vec2d(1, 0)), true);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, stubCoordinates(), stubProjection(), result);
        assertFalse(context.isValid());
    }

    @Test
    void definitionResolverRoundTripsThroughMapper() {
        BuildingFootprint footprint = new BuildingFootprint(RECT, true);
        footprint.setFloors(3);
        BuildingDefinition definition = BuildingDefinitionResolver.fromFootprint(footprint);
        assertEquals(3, definition.massing().floors());
        BuildingFootprint again = BuildingDefinitionResolver.footprintForTesting(definition);
        assertEquals(3, BuildingDefinitionMapper.fromFootprint(again).massing().floors());
    }

    private static com.plot.api.world.ICoordinateService stubCoordinates() {
        return new com.plot.api.world.ICoordinateService() {
            @Override
            public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                return canvasPos;
            }

            @Override
            public com.plot.api.world.WorldViewBounds getMinecraftWorldViewBounds() {
                return new com.plot.api.world.WorldViewBounds(-512, 512, -512, 512);
            }
        };
    }

    private static com.plot.api.world.IBlockProjectionService stubProjection() {
        return new com.plot.api.world.IBlockProjectionService() {
            @Override
            public com.plot.api.world.PlacementReadiness checkWorldModificationReadiness() {
                return com.plot.api.world.PlacementReadiness.ok();
            }

            @Override
            public String getBlockIdAt(net.minecraft.util.math.BlockPos pos) {
                return "minecraft:air";
            }

            @Override
            public boolean setBlockAt(net.minecraft.util.math.BlockPos pos, String blockId) {
                return false;
            }
        };
    }
}
