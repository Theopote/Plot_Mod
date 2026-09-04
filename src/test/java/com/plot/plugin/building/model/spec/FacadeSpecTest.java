package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadeSpecTest {

    private static final List<Vec2d> BASE = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 8),
        new Vec2d(0, 8)
    );

    @Test
    void withoutWallFacadesAllSegmentsUseDefaultPattern() {
        FacadeSpec facade = new FacadeSpec(new WindowPatternSpec(4, 1, 2, 1), List.of(), List.of());
        WindowPatternSpec pattern = facade.windowPatternForSegment(2, 4);
        assertEquals(4, pattern.spacing());
        assertFalse(facade.hasCustomWallFacades());
    }

    @Test
    void wallFacadeOverrideAppliesToMatchingSegment() {
        WindowPatternSpec south = new WindowPatternSpec(2, 1, 2, 1);
        WindowPatternSpec north = new WindowPatternSpec(0, 1, 2, 1);
        FacadeSpec facade = new FacadeSpec(
            new WindowPatternSpec(4, 1, 2, 1),
            List.of(
                WallFacadeSpec.of(0, south),
                WallFacadeSpec.noWindows(2)
            ),
            List.of()
        );
        assertTrue(facade.hasCustomWallFacades());
        assertEquals(2, facade.windowPatternForSegment(0, 4).spacing());
        assertEquals(4, facade.windowPatternForSegment(1, 4).spacing());
        assertFalse(facade.windowPatternForSegment(2, 4).enabled());
        assertEquals(4, facade.windowPatternForSegment(3, 4).spacing());
    }

    @Test
    void laterWallFacadeOverrideWinsForSameSegment() {
        FacadeSpec facade = new FacadeSpec(
            new WindowPatternSpec(4, 1, 2, 1),
            List.of(
                WallFacadeSpec.of(1, new WindowPatternSpec(2, 1, 2, 1)),
                WallFacadeSpec.noWindows(1)
            ),
            List.of()
        );
        assertFalse(facade.windowPatternForSegment(1, 4).enabled());
    }

    @Test
    void disabledSegmentGeneratesFewerWindowBlocksThanUniformFacade() {
        BuildingDefinition uniform = definitionWithFacades(List.of());
        BuildingDefinition mixed = definitionWithFacades(List.of(
            WallFacadeSpec.of(0, new WindowPatternSpec(4, 1, 2, 1)),
            WallFacadeSpec.noWindows(1),
            WallFacadeSpec.of(2, new WindowPatternSpec(4, 1, 2, 1)),
            WallFacadeSpec.of(3, new WindowPatternSpec(4, 1, 2, 1))
        ));

        int uniformOpenings = countWindowBlocks(uniform);
        int mixedOpenings = countWindowBlocks(mixed);
        assertNotEquals(uniformOpenings, mixedOpenings);
        assertTrue(mixedOpenings < uniformOpenings,
            "uniform=" + uniformOpenings + " mixed=" + mixedOpenings);
    }

    @Test
    void wallFacadesRoundTripThroughProjectJson() {
        BuildingFootprint footprint = new BuildingFootprint(BASE, true);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWindowSpacing(4);
        footprint.setWallFacades(List.of(
            WallFacadeSpec.of(0, new WindowPatternSpec(2, 1, 2, 1)),
            WallFacadeSpec.noWindows(2)
        ));

        BuildingProject project = new BuildingProject();
        project.addBuilding(footprint);
        BuildingFootprint restored = BuildingProject.fromJson(project.toJson()).getBuilding(footprint.getId());

        assertEquals(2, restored.getWallFacades().size());
        assertEquals(2, restored.getWallFacades().getFirst().windowPattern().spacing());
        assertFalse(restored.getWallFacades().get(1).windowPattern().enabled());
    }

    private static BuildingDefinition definitionWithFacades(List<WallFacadeSpec> wallFacades) {
        FootprintSpec footprint = new FootprintSpec("test", "Test", BASE, true);
        MassingSpec massing = MassingSpec.create(2, 3, BASE, List.of());
        FacadeSpec facade = new FacadeSpec(
            new WindowPatternSpec(4, 1, 2, 1),
            wallFacades,
            List.of()
        );
        return new BuildingDefinition(
            footprint,
            massing,
            new EnvelopeSpec(1, null, null),
            facade,
            new RoofSpec(BuildingFootprint.RoofType.FLAT, 1, null),
            new FoundationSpec(null, 64),
            AccessorySpec.none()
        );
    }

    private static int countWindowBlocks(BuildingDefinition definition) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            definition, stubCoordinates(), stubProjection(), result);
        new BuildingGenerationPipeline(List.of(new OpeningGenerationStage())).generate(context);
        return result.placementRecords.size();
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
