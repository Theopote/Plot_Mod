package com.plot.plugin.building.model.spec;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPlateScheduleTest {

    private static final List<Vec2d> BASE = List.of(
        new Vec2d(0, 0),
        new Vec2d(10, 0),
        new Vec2d(10, 10),
        new Vec2d(0, 10)
    );

    private static final List<Vec2d> UPPER = List.of(
        new Vec2d(2, 2),
        new Vec2d(8, 2),
        new Vec2d(8, 8),
        new Vec2d(2, 8)
    );

    @Test
    void emptyDefinitionsFillAllFloorsWithBase() {
        FloorPlateSchedule schedule = FloorPlateSchedule.resolve(4, BASE, List.of());
        assertEquals(4, schedule.floors());
        for (int floor = 0; floor < 4; floor++) {
            assertEquals(0.0, schedule.plateForFloor(floor).outerPoints().getFirst().x, 1e-6);
        }
    }

    @Test
    void laterDefinitionWinsOnOverlap() {
        FloorPlateSchedule schedule = FloorPlateSchedule.resolve(4, BASE, List.of(
            FloorPlateSpec.of(0, 3, BASE),
            FloorPlateSpec.of(2, 3, UPPER)
        ));
        assertEquals(0.0, schedule.plateForFloor(0).outerPoints().getFirst().x, 1e-6);
        assertEquals(2.0, schedule.plateForFloor(2).outerPoints().getFirst().x, 1e-6);
        assertEquals(2.0, schedule.plateForFloor(3).outerPoints().getFirst().x, 1e-6);
    }

    @Test
    void gapsAreFilledWithBaseNotLastPlate() {
        // Plate A: 0–2, Plate B: 5–7 → floors 3–4 必须是 base，绝不能 silent 用 Plate B
        MassingSpec massing = MassingSpec.create(8, 3, BASE, List.of(
            FloorPlateSpec.of(0, 2, BASE),
            FloorPlateSpec.of(5, 7, UPPER)
        ));

        assertEquals(List.of(3, 4), massing.coverageGapFloors());
        assertTrue(massing.hasCoverageGaps());

        assertEquals(0.0, massing.plateForFloor(0).outerPoints().getFirst().x, 1e-6);
        assertEquals(0.0, massing.plateForFloor(3).outerPoints().getFirst().x, 1e-6);
        assertEquals(0.0, massing.plateForFloor(4).outerPoints().getFirst().x, 1e-6);
        assertEquals(2.0, massing.plateForFloor(5).outerPoints().getFirst().x, 1e-6);
        assertNotEquals(
            massing.plateForFloor(5).outerPoints().getFirst().x,
            massing.plateForFloor(3).outerPoints().getFirst().x,
            1e-6);
    }

    @Test
    void continuousCoverageHasNoGaps() {
        MassingSpec massing = MassingSpec.create(4, 3, BASE, List.of(
            FloorPlateSpec.of(0, 1, BASE),
            FloorPlateSpec.of(2, 3, UPPER)
        ));
        assertFalse(massing.hasCoverageGaps());
        assertTrue(massing.coverageGapFloors().isEmpty());
        assertEquals(2.0, massing.topOccupiedPlate().outerPoints().getFirst().x, 1e-6);
    }

    @Test
    void outOfRangeFloorThrows() {
        FloorPlateSchedule schedule = FloorPlateSchedule.resolve(2, BASE, List.of());
        assertThrows(IndexOutOfBoundsException.class, () -> schedule.plateForFloor(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> schedule.plateForFloor(2));
    }

    @Test
    void scheduleResolvedPlatesLengthEqualsFloors() {
        MassingSpec massing = MassingSpec.create(5, 3, BASE, List.of(
            FloorPlateSpec.of(0, 1, BASE),
            FloorPlateSpec.of(3, 4, UPPER)
        ));
        assertEquals(5, massing.schedule().resolvedPlates().size());
        assertEquals(5, massing.schedule().floors());
    }

    @Test
    void coverageGapEmitsWarningInGenerationContext() {
        BuildingFootprint footprint = new BuildingFootprint(BASE, true);
        footprint.setFloors(8);
        footprint.setFloorHeight(3);
        footprint.setFloorPlates(List.of(
            FloorPlateSpec.of(0, 2, BASE),
            FloorPlateSpec.of(5, 7, UPPER)
        ));

        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext.forTesting(
            footprint,
            new com.plot.api.world.ICoordinateService() {
                @Override
                public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
                    return canvasPos;
                }

                @Override
                public com.plot.api.world.WorldViewBounds getMinecraftWorldViewBounds() {
                    return new com.plot.api.world.WorldViewBounds(-512, 512, -512, 512);
                }
            },
            new com.plot.api.world.IBlockProjectionService() {
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
            },
            result);

        assertTrue(result.warnings.contains("plugin.building.warn.floor_plate_coverage_gap"));
    }
}
