package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.massing.FloorPlateGeometryResolver;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPlateSpecTest {

    private static final List<Vec2d> BASE = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 8),
        new Vec2d(0, 8)
    );

    @Test
    void massingWithoutCustomPlatesCreatesSingleDefaultPlate() {
        MassingSpec massing = MassingSpec.create(4, 3, BASE, List.of());
        assertEquals(1, massing.floorPlates().size());
        assertEquals(0, massing.floorPlates().getFirst().floorStart());
        assertEquals(3, massing.floorPlates().getFirst().floorEnd());
        assertFalse(massing.hasCustomFloorPlates());
    }

    @Test
    void plateForFloorReturnsMatchingSetbackPlate() {
        List<Vec2d> upper = List.of(
            new Vec2d(2, 2),
            new Vec2d(6, 2),
            new Vec2d(6, 6),
            new Vec2d(2, 6)
        );
        MassingSpec massing = MassingSpec.create(4, 3, BASE, List.of(
            FloorPlateSpec.of(0, 1, BASE),
            FloorPlateSpec.of(2, 3, upper)
        ));
        assertTrue(massing.hasCustomFloorPlates());
        assertEquals(4, massing.plateForFloor(0).outerPoints().size());
        assertEquals(4, massing.plateForFloor(2).outerPoints().size());
        assertEquals(2, massing.plateForFloor(2).outerPoints().getFirst().x, 1e-6);
        assertEquals(2, massing.topOccupiedPlate().outerPoints().getFirst().x, 1e-6);
    }

    @Test
    void insetFromCreatesSmallerPlate() {
        FloorPlateSpec inset = FloorPlateSpec.insetFrom(2, 3, BASE, 1);
        assertTrue(FloorPlateGeometryResolver.resolve(inset, 1).outerPolygon().contains(new Vec2d(4, 4)));
        assertFalse(FloorPlateGeometryResolver.resolve(inset, 1).outerPolygon().contains(new Vec2d(0.5, 0.5)));
    }

    @Test
    void setbackMassingGeneratesDifferentWallBlocksThanUniformExtrusion() {
        List<Vec2d> upper = List.of(
            new Vec2d(2, 2),
            new Vec2d(6, 2),
            new Vec2d(6, 6),
            new Vec2d(2, 6)
        );
        BuildingDefinition uniform = definitionWithPlates(4, List.of(FloorPlateSpec.of(0, 3, BASE)));
        BuildingDefinition setback = definitionWithPlates(4, List.of(
            FloorPlateSpec.of(0, 1, BASE),
            FloorPlateSpec.of(2, 3, upper)
        ));

        int uniformWalls = countWallBlocks(uniform);
        int setbackWalls = countWallBlocks(setback);
        assertNotEquals(uniformWalls, setbackWalls,
            "floor plates must change wall generation: uniform=" + uniformWalls + " setback=" + setbackWalls);
        assertTrue(setbackWalls < uniformWalls,
            "inset upper floors shrink perimeter: uniform=" + uniformWalls + " setback=" + setbackWalls);
    }

    @Test
    void floorPlatesRoundTripThroughProjectJson() {
        BuildingFootprint footprint = new BuildingFootprint(BASE, true);
        footprint.setFloors(4);
        footprint.setFloorHeight(3);
        footprint.setFloorPlates(List.of(
            FloorPlateSpec.of(0, 1, BASE),
            FloorPlateSpec.insetFrom(2, 3, BASE, 1)
        ));

        BuildingProject project = new BuildingProject();
        project.addBuilding(footprint);
        BuildingFootprint restored = BuildingProject.fromJson(project.toJson()).getBuilding(footprint.getId());

        assertEquals(2, restored.getFloorPlates().size());
        assertEquals(0, restored.getFloorPlates().getFirst().floorStart());
        assertEquals(2, restored.getFloorPlates().get(1).floorStart());
    }

    private static BuildingDefinition definitionWithPlates(int floors, List<FloorPlateSpec> plates) {
        FootprintSpec footprint = new FootprintSpec("test", "Test", BASE, true);
        MassingSpec massing = MassingSpec.create(floors, 3, BASE, plates);
        return new BuildingDefinition(
            footprint,
            massing,
            new EnvelopeSpec(1, null, null),
            new FacadeSpec(new WindowPatternSpec(0, 1, 2, 1), List.of()),
            new RoofSpec(BuildingFootprint.RoofType.FLAT, 1, null),
            new FoundationSpec(null, 64)
        );
    }

    private static int countWallBlocks(BuildingDefinition definition) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            definition, stubCoordinates(), stubProjection(), result);

        new BuildingGenerationPipeline(List.of(new com.plot.plugin.building.generation.stage.WallGenerationStage()))
            .generate(context);
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
