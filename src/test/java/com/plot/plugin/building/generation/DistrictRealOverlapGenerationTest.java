package com.plot.plugin.building.generation;

import com.plot.core.command.BlockRecord;
import com.plot.core.material.MaterialMix;
import com.plot.plugin.building.benchmark.DistrictMassingFixtures;
import com.plot.plugin.building.golden.GoldenBuildingTestFixtures;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Pipeline 片区重叠：两栋同几何体块冲突时后生成者覆盖（later-wins）。
 */
class DistrictRealOverlapGenerationTest {

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
    void realPipelineOverlapLaterBuildingWins() {
        BuildingFootprint first = DistrictMassingFixtures.massingFootprint(0, 2);
        first.setWallMaterial(MaterialMix.single("minecraft:stone_bricks"));
        BuildingFootprint second = DistrictMassingFixtures.sameGeometry(first, "overlap-second");
        second.setWallMaterial(MaterialMix.single("minecraft:bricks"));

        BuildingGenerationResult firstOnly = generateOne(first);
        BuildingGenerationResult secondOnly = generateOne(second);
        assertFalse(firstOnly.placementRecords.isEmpty());
        assertFalse(secondOnly.placementRecords.isEmpty());

        Set<BlockPos> shared = new HashSet<>(firstOnly.placementRecords.keySet());
        shared.retainAll(secondOnly.placementRecords.keySet());
        assertFalse(shared.isEmpty(), "identical footprints should share voxel placements");

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(first, second),
            DistrictRealOverlapGenerationTest::generateOne);

        assertEquals(2, district.buildingsGenerated());
        assertTrue(district.hasBuildingOverlap());
        assertTrue(district.conflictingBlockCount() >= 1);

        BlockPos sample = shared.iterator().next();
        BlockRecord merged = district.mergedPlacementRecords().get(sample);
        BlockRecord secondRecord = secondOnly.placementRecords.get(sample);
        assertEquals(secondRecord.newBlockId, merged.newBlockId);
    }
}
