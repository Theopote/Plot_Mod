package com.plot.plugin.building.golden;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.benchmark.DistrictMassingFixtures;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.generation.DistrictMassingGenerator;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 片区 Massing 场景（D-B10–D-B14）。
 * <p>
 * 与 {@link GoldenBuildingCaseFactory} B01–B12 单体 Golden 编号分离；矩阵见
 * {@code docs/development/BuildingMassingScenarioMatrix.md}。
 */
class BuildingDistrictScenarioTest {

    private static final BuildingGenerationPipeline PIPELINE =
        BuildingGenerationPipeline.createDefault();

    @Test
    void districtTenBuildings() {
        DistrictGenerationResult district = generateDistrict(10, 4);

        DistrictScenarioAssertions.assertAllGenerated(district, 10);
        assertTrue(district.totalBlocks() > 0);
        assertFalse(district.hasBuildingOverlap());
    }

    @Test
    void districtHundredBuildings() {
        DistrictGenerationResult district = generateDistrict(100, 4);

        DistrictScenarioAssertions.assertAllGenerated(district, 100);
        assertTrue(district.totalBlocks() > 0);
    }

    @Test
    void districtOverlapLaterWins() {
        BuildingFootprint first = DistrictMassingFixtures.massingFootprint(0, 4);
        BuildingFootprint second = DistrictMassingFixtures.sameGeometry(first, "d-overlap-1");
        BlockPos shared = new BlockPos(1, 2, 3);

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(first, second),
            footprint -> {
                BuildingGenerationResult result = new BuildingGenerationResult();
                String block = "d-0".equals(footprint.getId())
                    ? "minecraft:stone"
                    : "minecraft:bricks";
                result.placementRecords.put(shared, new BlockRecord(shared, "minecraft:air", block));
                BlockPos unique = new BlockPos(
                    "d-0".equals(footprint.getId()) ? 0 : 9,
                    1,
                    0);
                result.placementRecords.put(unique, new BlockRecord(unique, "minecraft:air", block));
                result.blockCount = 2;
                return result;
            });

        assertEquals(2, district.buildingsGenerated());
        assertTrue(district.hasBuildingOverlap());
        assertTrue(district.conflictingBlockCount() >= 1);
        assertEquals(2, district.overlappingBuildingCount());
        assertEquals("minecraft:bricks", district.mergedPlacementRecords().get(shared).newBlockId);
    }

    @Test
    void districtInvalidMixedFailSoft() {
        List<BuildingFootprint> buildings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i == 2 || i == 5 || i == 8) {
                buildings.add(new BuildingFootprint(
                    "bad-" + i,
                    List.of(new Vec2d(0, 0), new Vec2d(1, 0)),
                    false));
            } else {
                buildings.add(DistrictMassingFixtures.massingFootprint(i, 4));
            }
        }

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            buildings,
            BuildingDistrictScenarioTest::generateOne);

        DistrictScenarioAssertions.assertFailSoftPartial(district, 7, 3, 10);
        assertTrue(district.hasPlacements());
        assertEquals(
            DistrictGenerationResult.SkipReason.INVALID,
            district.skippedOutcomes().getFirst().skipReason());
    }

    @Test
    void districtMixedElevationSources() {
        BuildingFootprint auto = DistrictMassingFixtures.massingFootprint(0, 3);
        BuildingFootprint manualLow = DistrictMassingFixtures.massingFootprint(1, 3);
        manualLow.setManualBaseElevation(64);
        BuildingFootprint manualHigh = DistrictMassingFixtures.massingFootprint(2, 3);
        manualHigh.setManualBaseElevation(80);

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(auto, manualLow, manualHigh),
            BuildingDistrictScenarioTest::generateOne);

        DistrictScenarioAssertions.assertAllGenerated(district, 3);
        assertTrue(district.totalBlocks() > 0);
    }

    private static DistrictGenerationResult generateDistrict(int count, int floors) {
        return DistrictMassingGenerator.generate(
            DistrictMassingFixtures.district(count, floors),
            BuildingDistrictScenarioTest::generateOne);
    }

    private static BuildingGenerationResult generateOne(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContext.forTesting(
            footprint,
            GoldenBuildingTestFixtures.coordinates(),
            GoldenBuildingTestFixtures.projection(),
            result);
        return PIPELINE.generate(context);
    }
}
