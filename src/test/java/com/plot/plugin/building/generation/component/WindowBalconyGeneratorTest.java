package com.plot.plugin.building.generation.component;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.AccessoryGenerationStage;
import com.plot.plugin.building.generation.stage.FloorGenerationStage;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowBalconyGeneratorTest {

    private static final String BALCONY_SLAB = "minecraft:polished_andesite";

    @Test
    void generatesBalconySlabsUnderWindowsWhenDepthPositive() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(12, 8, 2, 3, 1);
        footprint.setWindowBalconyDepth(2);
        footprint.setBalconySlabMaterial(BALCONY_SLAB);

        BuildingGenerationResult result = generateWithAccessories(footprint);
        assertTrue(countBalconySlabs(result, BALCONY_SLAB) > 0);
    }

    @Test
    void skipsBalconySlabsWhenDepthZero() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(12, 8, 2, 3, 1);
        footprint.setWindowBalconyDepth(0);
        footprint.setBalconySlabMaterial(BALCONY_SLAB);

        BuildingGenerationResult result = generateWithAccessories(footprint);
        assertFalse(hasBlock(result, BALCONY_SLAB));
    }

    @Test
    void skipsBalconySlabsWhenWindowsDisabled() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(12, 8, 2, 3, 1);
        footprint.setWindowsEnabled(false);
        footprint.setWindowBalconyDepth(3);
        footprint.setBalconySlabMaterial(BALCONY_SLAB);

        BuildingGenerationResult result = generateWithAccessories(footprint);
        assertFalse(hasBlock(result, BALCONY_SLAB));
    }

    private static BuildingGenerationResult generateWithAccessories(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, IdentityCoordinateService.INSTANCE, NOOP, result);
        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new FloorGenerationStage(),
            new AccessoryGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);
        return result;
    }

    private static int countBalconySlabs(BuildingGenerationResult result, String blockId) {
        int count = 0;
        for (var record : result.placementRecords.values()) {
            if (blockId.equals(record.newBlockId)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasBlock(BuildingGenerationResult result, String blockId) {
        for (var record : result.placementRecords.values()) {
            if (blockId.equals(record.newBlockId)) {
                return true;
            }
        }
        return false;
    }

    private static final IBlockProjectionService NOOP = new IBlockProjectionService() {
        @Override
        public PlacementReadiness checkWorldModificationReadiness() {
            return PlacementReadiness.ok();
        }

        @Override
        public String getBlockIdAt(BlockPos pos) {
            return "minecraft:air";
        }

        @Override
        public boolean setBlockAt(BlockPos pos, String blockId) {
            return false;
        }
    };
}
