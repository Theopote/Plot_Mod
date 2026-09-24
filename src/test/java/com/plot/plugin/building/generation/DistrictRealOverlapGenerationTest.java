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
 * 真实 Pipeline 片区重叠：同高同几何时按生成优先级决胜（先处理者保留重叠区）。
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
    void realPipelineOverlapSameHeightPriorityBuildingWins() {
        BuildingFootprint first = DistrictMassingFixtures.massingFootprint(0, 2);
        first.setWallMaterial(MaterialMix.single("minecraft:stone_bricks"));
        BuildingFootprint second = DistrictMassingFixtures.sameGeometry(first, "overlap-second");
        second.setWallMaterial(MaterialMix.single("minecraft:bricks"));
        // overlap-second 在同高排序中先于 d-0 处理，重叠区保留其材质

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
        // 低优先级建筑在占优轮廓内被过滤，不产生体素冲突计数
        assertEquals(0, district.conflictingBlockCount());

        BlockPos sample = shared.iterator().next();
        BlockRecord merged = district.mergedPlacementRecords().get(sample);
        BlockRecord secondRecord = secondOnly.placementRecords.get(sample);
        assertEquals(secondRecord.newBlockId, merged.newBlockId);
    }
}
