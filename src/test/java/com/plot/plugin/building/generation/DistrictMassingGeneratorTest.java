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
        footprint.setWindowsEnabled(false);
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
    void invalidFootprintsDoNotAbortRemainingBuildings() {
        List<BuildingFootprint> buildings = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i == 2 || i == 5 || i == 8) {
                buildings.add(new BuildingFootprint(
                    "bad-" + i,
                    List.of(new Vec2d(0, 0), new Vec2d(1, 0)),
                    false));
            } else {
                buildings.add(building("ok-" + i, i * 12.0));
            }
        }

        AtomicInteger generatedCalls = new AtomicInteger();
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            buildings,
            footprint -> {
                generatedCalls.incrementAndGet();
                return generateOne(footprint);
            });

        assertEquals(7, district.buildingsGenerated());
        assertEquals(3, district.buildingsSkipped());
        assertEquals(10, district.buildingsAttempted());
        assertEquals(7, generatedCalls.get());
        assertTrue(district.hasPlacements());
        assertEquals(
            DistrictGenerationResult.SkipReason.INVALID,
            district.skippedOutcomes().getFirst().skipReason());
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
    void overlappingBlocksDominantBuildingWinsForEqualHeight() {
        BuildingFootprint first = building("first", 0);
        BuildingFootprint second = building("second", 0);

        BlockPos shared = new BlockPos(1, 2, 3);
        java.util.Set<Long> footprintColumns = java.util.Set.of(
            BuildingGenerationPipeline.packWorldColumn(shared.getX(), shared.getZ()));
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(first, second),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                result.footprintWorldColumns = footprintColumns;
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
    void tallerBuildingWinsOverlappingFootprint() {
        BuildingFootprint shortBuilding = building("short", 0);
        shortBuilding.setFloors(2);
        BuildingFootprint tallBuilding = building("tall", 0);
        tallBuilding.setFloors(6);

        BlockPos shared = new BlockPos(1, 2, 3);
        java.util.Set<Long> footprintColumns = java.util.Set.of(
            BuildingGenerationPipeline.packWorldColumn(shared.getX(), shared.getZ()));
        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(shortBuilding, tallBuilding),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                result.footprintWorldColumns = footprintColumns;
                String block = "tall".equals(footprint.getId())
                    ? "minecraft:stone"
                    : "minecraft:bricks";
                result.placementRecords.put(shared, new BlockRecord(shared, "minecraft:air", block));
                result.blockCount = 1;
                return result;
            });

        assertEquals(2, district.buildingsGenerated());
        assertEquals(1, district.totalBlocks());
        assertEquals("minecraft:stone", district.mergedPlacementRecords().get(shared).newBlockId);
    }

    @Test
    void shorterBuildingSkipsBlocksInsideTallerFootprint() {
        BuildingFootprint shortBuilding = building("short", 0);
        shortBuilding.setFloors(2);
        BuildingFootprint tallBuilding = building("tall", 0);
        tallBuilding.setFloors(6);

        BlockPos overlap = new BlockPos(2, 4, 2);
        BlockPos outside = new BlockPos(9, 4, 2);
        java.util.Set<Long> tallFootprint = java.util.Set.of(
            BuildingGenerationPipeline.packWorldColumn(overlap.getX(), overlap.getZ()));

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(shortBuilding, tallBuilding),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                if ("tall".equals(footprint.getId())) {
                    result.footprintWorldColumns = tallFootprint;
                    result.placementRecords.put(
                        overlap, new BlockRecord(overlap, "minecraft:air", "minecraft:stone"));
                } else {
                    result.footprintWorldColumns = java.util.Set.of(
                        BuildingGenerationPipeline.packWorldColumn(outside.getX(), outside.getZ()),
                        BuildingGenerationPipeline.packWorldColumn(overlap.getX(), overlap.getZ()));
                    result.placementRecords.put(
                        overlap, new BlockRecord(overlap, "minecraft:air", "minecraft:bricks"));
                    result.placementRecords.put(
                        outside, new BlockRecord(outside, "minecraft:air", "minecraft:bricks"));
                }
                result.blockCount = result.placementRecords.size();
                return result;
            });

        assertEquals("minecraft:stone", district.mergedPlacementRecords().get(overlap).newBlockId);
        assertEquals("minecraft:bricks", district.mergedPlacementRecords().get(outside).newBlockId);
        assertEquals(2, district.totalBlocks());
    }

    @Test
    void siteAnalysisFailedSkipsWithoutAbortingOthers() {
        BuildingFootprint ok = building("ok", 0);
        BuildingFootprint skipped = building("site-fail", 20);

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(ok, skipped),
            footprint -> {
                if ("site-fail".equals(footprint.getId())) {
                    BuildingGenerationResult result = new BuildingGenerationResult();
                    result.skippedDueToSiteAnalysis = true;
                    result.warnings.add("plugin.building.warn.site_analysis_unavailable_skip");
                    return result;
                }
                return generateOne(footprint);
            });

        assertEquals(1, district.buildingsGenerated());
        assertEquals(1, district.buildingsSkipped());
        assertEquals(2, district.buildingsAttempted());
        assertEquals(
            DistrictGenerationResult.SkipReason.SITE_ANALYSIS_FAILED,
            district.skippedOutcomes().getFirst().skipReason());
        assertTrue(district.hasPlacements());
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
