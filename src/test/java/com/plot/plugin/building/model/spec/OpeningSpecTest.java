package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver;
import com.plot.plugin.building.generation.opening.OpeningPlacementResolver.ResolvedOpening;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.model.BuildingProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningSpecTest {

    private static final List<Vec2d> BASE = List.of(
        new Vec2d(0, 0),
        new Vec2d(8, 0),
        new Vec2d(8, 8),
        new Vec2d(0, 8)
    );

    @Test
    void doorOpeningSpecRoundTripsThroughOpeningSpec() {
        DoorOpeningSpec legacy = new DoorOpeningSpec(1, 0.5, 0, 2, 3);
        OpeningSpec opening = legacy.toOpeningSpec();
        assertEquals(OpeningKind.DOOR, opening.kind());
        assertEquals(0, opening.bottomOffset());
        assertEquals(legacy.wallSegmentIndex(), DoorOpeningSpec.from(opening).wallSegmentIndex());
    }

    @Test
    void facadeSpecCollectsDoorsAndArchesFromFootprint() {
        BuildingFootprint footprint = new BuildingFootprint(BASE, true);
        footprint.addDoor(new BuildingFootprint.DoorOpening(0, 0.5, 0, 1, 2));
        footprint.addOpening(OpeningSpec.arch(2, 0.4, 0, 2, 3));

        FacadeSpec facade = FacadeSpec.from(footprint);
        assertEquals(2, facade.openings().size());
        assertEquals(OpeningKind.DOOR, facade.openings().getFirst().kind());
        assertEquals(OpeningKind.ARCH, facade.openings().get(1).kind());
        assertEquals(1, facade.doorOpenings().size());
    }

    @Test
    void explicitWindowUsesBottomOffsetAsSill() {
        OpeningSpec window = OpeningSpec.window(0, 0.5, 0, 1, 2, 1);
        ResolvedOpening resolved = OpeningPlacementResolver.resolve(window, BASE, 64, 4);
        assertNotNull(resolved);
        assertEquals(65, resolved.startY());
        assertEquals(2, resolved.height());
    }

    @Test
    void archOpeningGeneratesCarvedBlocks() {
        BuildingDefinition definition = definitionWithOpenings(List.of(
            OpeningSpec.arch(0, 0.5, 0, 2, 3)
        ));
        int archBlocks = countExplicitOpeningBlocks(definition);
        assertTrue(archBlocks > 0);
    }

    @Test
    void openingsRoundTripThroughProjectJson() {
        BuildingFootprint footprint = new BuildingFootprint(BASE, true);
        footprint.setFloors(2);
        footprint.addDoor(new BuildingFootprint.DoorOpening(1, 0.5, 0, 2, 3));
        footprint.addOpening(OpeningSpec.arch(2, 0.25, 0, 2, 3));

        BuildingProject project = new BuildingProject();
        project.addBuilding(footprint);
        BuildingFootprint restored = BuildingProject.fromJson(project.toJson()).getBuilding(footprint.getId());

        assertEquals(2, restored.getOpenings().size());
        assertEquals(OpeningKind.DOOR, restored.getOpenings().getFirst().kind());
        assertEquals(OpeningKind.ARCH, restored.getOpenings().get(1).kind());
        assertEquals(1, restored.getDoors().size());
    }

    @Test
    void legacyDoorsJsonStillLoadsAsDoorOpenings() {
        String json = """
            {
              "buildings": [{
                "id": "b1",
                "name": "Legacy",
                "outerPoints": [
                  {"x": 0, "y": 0}, {"x": 8, "y": 0},
                  {"x": 8, "y": 8}, {"x": 0, "y": 8}
                ],
                "isRectangular": true,
                "floors": 1,
                "floorHeight": 3,
                "wallThickness": 1,
                "doors": [{
                  "wallSegmentIndex": 1,
                  "positionRatio": 0.5,
                  "floor": 0,
                  "width": 2,
                  "height": 3
                }]
              }]
            }
            """;

        BuildingFootprint footprint = BuildingProject.fromJson(json).getBuilding("b1");
        assertNotNull(footprint);
        assertEquals(1, footprint.getOpenings().size());
        assertEquals(OpeningKind.DOOR, footprint.getOpenings().getFirst().kind());
    }

    private static BuildingDefinition definitionWithOpenings(List<OpeningSpec> openings) {
        FootprintSpec footprint = new FootprintSpec("test", "Test", BASE, true);
        MassingSpec massing = MassingSpec.create(1, 3, BASE, List.of());
        FacadeSpec facade = new FacadeSpec(
            new WindowPatternSpec(0, 1, 2, 1),
            List.of(),
            openings
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

    private static int countExplicitOpeningBlocks(BuildingDefinition definition) {
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
