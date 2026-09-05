package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictOverlapAnalyzerTest {

    private static BuildingFootprint building(String id, double x, double y, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(x, y),
            new Vec2d(x + size, y),
            new Vec2d(x + size, y + size),
            new Vec2d(x, y + size)
        ), true);
        footprint.setName(id);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWindowSpacing(0);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        return footprint;
    }

    @Test
    void detectsFootprintOverlapPairs() {
        BuildingFootprint a = building("a", 0, 0, 10);
        BuildingFootprint b = building("b", 5, 5, 10); // overlaps a
        BuildingFootprint c = building("c", 30, 0, 8); // separate

        List<DistrictOverlapAnalyzer.OverlapPair> pairs =
            DistrictOverlapAnalyzer.findFootprintOverlapPairs(List.of(a, b, c));

        assertEquals(1, pairs.size());
        assertEquals("a", pairs.getFirst().buildingIdA());
        assertEquals("b", pairs.getFirst().buildingIdB());
    }

    @Test
    void districtGenerateReportsOverlapWarningAndConflictBlocks() {
        BuildingFootprint a = building("a", 0, 0, 8);
        BuildingFootprint b = building("b", 2, 2, 8);

        BlockPos shared = new BlockPos(3, 1, 3);
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(a, b),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                String block = "a".equals(footprint.getId())
                    ? "minecraft:stone"
                    : "minecraft:bricks";
                result.placementRecords.put(shared, new BlockRecord(shared, "minecraft:air", block));
                // also unique blocks so neither is empty
                BlockPos unique = new BlockPos(
                    "a".equals(footprint.getId()) ? 0 : 9,
                    1,
                    0);
                result.placementRecords.put(unique, new BlockRecord(unique, "minecraft:air", block));
                result.blockCount = 2;
                return result;
            });

        assertEquals(2, district.buildingsGenerated());
        assertTrue(district.hasBuildingOverlap());
        assertTrue(district.conflictingBlockCount() >= 1);
        assertFalse(district.overlappingBuildingPairs().isEmpty());
        assertTrue(district.warnings().contains("plugin.building.warn.district_overlap"));
        assertEquals("minecraft:bricks", district.mergedPlacementRecords().get(shared).newBlockId);
    }

    @Test
    void separatedBuildingsHaveNoOverlap() {
        BuildingFootprint a = building("a", 0, 0, 6);
        BuildingFootprint b = building("b", 20, 0, 6);

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(a, b),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                int x = "a".equals(footprint.getId()) ? 1 : 21;
                BlockPos pos = new BlockPos(x, 1, 1);
                result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
                return result;
            });

        assertFalse(district.hasBuildingOverlap());
        assertEquals(0, district.conflictingBlockCount());
        assertTrue(district.overlappingBuildingPairs().isEmpty());
    }
}
