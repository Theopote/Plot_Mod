package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.golden.GoldenBuildingTestFixtures;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictMassingGeneratorTest {

    private static BuildingFootprint building(String id, double xOffset) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(xOffset, 0),
            new Vec2d(xOffset + 8, 0),
            new Vec2d(xOffset + 8, 6),
            new Vec2d(xOffset, 6)
        ), true);
        footprint.setName(id);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        footprint.setWindowSpacing(0);
        footprint.setRoofType(BuildingFootprint.RoofType.FLAT);
        return footprint;
    }

    private static BuildingGenerationResult generateOne(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        return BuildingGenerationPipeline.createDefault().generate(context);
    }

    @Test
    void generatesMultipleBuildingsAndMergesBlocks() {
        BuildingFootprint a = building("a", 0);
        BuildingFootprint b = building("b", 20);

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(a, b),
            DistrictMassingGeneratorTest::generateOne);

        assertEquals(2, district.buildingsGenerated());
        assertEquals(0, district.buildingsSkipped());
        assertTrue(district.totalBlocks() > 0);
        assertTrue(district.hasPlacements());

        BuildingGenerationResult merged = district.toMergedResult();
        assertEquals(district.totalBlocks(), merged.blockCount);
        assertEquals(district.mergedPlacementRecords().size(), merged.placementRecords.size());
    }

    @Test
    void failSoftSkipsEmptyAndErrorWithoutAbortingOthers() {
        BuildingFootprint ok = building("ok", 0);
        BuildingFootprint empty = building("empty", 40);
        BuildingFootprint boom = building("boom", 80);

        AtomicInteger calls = new AtomicInteger();
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(ok, empty, boom),
            footprint -> {
                calls.incrementAndGet();
                if ("empty".equals(footprint.getId())) {
                    return new BuildingGenerationResult();
                }
                if ("boom".equals(footprint.getId())) {
                    throw new IllegalStateException("boom");
                }
                return generateOne(footprint);
            });

        assertEquals(3, calls.get());
        assertEquals(1, district.buildingsGenerated());
        assertEquals(2, district.buildingsSkipped());
        assertEquals(3, district.buildingsAttempted());
        assertTrue(district.hasPlacements());

        List<DistrictGenerationResult.BuildingOutcome> skipped = district.skippedOutcomes();
        assertEquals(2, skipped.size());
        assertEquals(DistrictGenerationResult.SkipReason.EMPTY, skipped.get(0).skipReason());
        assertEquals(DistrictGenerationResult.SkipReason.ERROR, skipped.get(1).skipReason());
        assertTrue(district.toMergedResult().warnings.contains("plugin.building.warn.district_partial"));
    }

    @Test
    void overlappingBlocksLaterBuildingWins() {
        BuildingFootprint first = building("first", 0);
        BuildingFootprint second = building("second", 0);

        BlockPos shared = new BlockPos(1, 2, 3);
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(first, second),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                String block = "first".equals(footprint.getId())
                    ? "minecraft:stone"
                    : "minecraft:bricks";
                result.placementRecords.put(shared, new BlockRecord(shared, "minecraft:air", block));
                result.blockCount = 1;
                return result;
            });

        assertEquals(2, district.buildingsGenerated());
        assertEquals(1, district.totalBlocks());
        assertEquals("minecraft:bricks", district.mergedPlacementRecords().get(shared).newBlockId);
    }

    @Test
    void emptyInputReturnsEmptyDistrict() {
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(),
            footprint -> {
                throw new AssertionError("should not run");
            });
        assertEquals(0, district.buildingsAttempted());
        assertFalse(district.hasPlacements());
    }
}
