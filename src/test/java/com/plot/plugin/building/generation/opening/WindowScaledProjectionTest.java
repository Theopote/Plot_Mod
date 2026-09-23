package com.plot.plugin.building.generation.opening;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.api.world.SnapshotCoordinateService;
import net.minecraft.util.math.BlockPos;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowScaledProjectionTest {

    @Test
    void scaledProjectionStillPlacesWindowsOnNarrowFacade() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(8, 6, 1, 4, 1);
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(3);
        footprint.setWindowPierWidth(2);
        footprint.setWindowHeight(2);
        footprint.setWindowSillHeight(0);

        long scaled = countWindows(footprint, SnapshotCoordinateService.uniformScale(4.0));
        assertTrue(scaled > 0, "8-wide facade should still get windows at 4x projection, got " + scaled);
    }

    @Test
    void scaledProjectionStillPlacesWindowsWithUserParams() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 6, 1, 4, 1);
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(3);
        footprint.setWindowPierWidth(2);
        footprint.setWindowHeight(2);
        footprint.setWindowSillHeight(0);

        long scaled = countWindows(footprint, SnapshotCoordinateService.uniformScale(4.0));
        long identity = countWindows(footprint, IdentityCoordinateService.INSTANCE);

        assertTrue(scaled > 0, "expected windows at 4x projection, got " + scaled);
        assertTrue(identity > 0, "expected windows at identity projection, got " + identity);
    }

    private static long countWindows(BuildingFootprint footprint, com.plot.api.world.ICoordinateService coords) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        var context = BuildingGenerationContextFactory.forTesting(
            footprint, coords, NOOP, result);
        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);
        String windowMat = footprint.getWindowMaterial();
        return result.placementRecords.values().stream()
            .filter(r -> r.newBlockId != null && r.newBlockId.contains(
                windowMat.substring(windowMat.indexOf(':') + 1)))
            .count();
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
