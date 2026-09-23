package com.plot.plugin.building.generation.opening;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationContext;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.opening.OpeningVerticalLayout;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowSpandrelIntegrationTest {

    @Test
    void southFaceKeepsWallBlocksBetweenWindows() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(10, 6, 1, 3, 1);
        footprint.setWindowSpacing(4);
        footprint.setWindowWidth(1);

        BuildingGenerationResult result = generate(footprint);
        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        String windowId = BuildingGeometryUtils.resolveBlockId(footprint.getWindowMaterial());
        int floorSlabY = result.placementRecords.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        int windowY = OpeningVerticalLayout.windowStartY(floorSlabY, footprint.getWindowSillHeight());
        int southZ = result.placementRecords.keySet().stream().mapToInt(BlockPos::getZ).min().orElse(0);

        List<Integer> southWindowXs = result.placementRecords.entrySet().stream()
            .filter(e -> windowId.equals(e.getValue().newBlockId))
            .map(e -> e.getKey())
            .filter(p -> p.getY() == windowY && p.getZ() == southZ)
            .map(BlockPos::getX)
            .sorted()
            .toList();

        assertTrue(southWindowXs.size() >= 2, "expected multiple south windows");
        for (int i = 0; i < southWindowXs.size() - 1; i++) {
            int left = southWindowXs.get(i);
            int right = southWindowXs.get(i + 1);
            boolean hasSpandrel = false;
            for (int x = left + 1; x < right; x++) {
                BlockPos wallPos = new BlockPos(x, windowY, southZ);
                var record = result.placementRecords.get(wallPos);
                if (record != null && wallId.equals(record.newBlockId)) {
                    hasSpandrel = true;
                    break;
                }
            }
            assertTrue(hasSpandrel,
                "missing spandrel between windows at x=" + left + " and x=" + right);
        }
    }

    private static BuildingGenerationResult generate(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BuildingGenerationContext context = BuildingGenerationContextFactory.forTesting(
            footprint, IdentityCoordinateService.INSTANCE, NOOP, result);
        new BuildingGenerationPipeline(List.of(
            new WallGenerationStage(),
            new OpeningGenerationStage()
        )).generate(context);
        return result;
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
