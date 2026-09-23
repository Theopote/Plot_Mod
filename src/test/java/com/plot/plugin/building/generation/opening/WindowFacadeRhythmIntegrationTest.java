package com.plot.plugin.building.generation.opening;

import com.plot.api.world.IBlockProjectionService;
import com.plot.api.world.PlacementReadiness;
import com.plot.plugin.building.BuildingGeometryUtils;
import com.plot.plugin.building.generation.BuildingGenerationPipeline;
import com.plot.plugin.building.generation.BuildingGenerationResult;
import com.plot.plugin.building.generation.resolve.BuildingGenerationContextFactory;
import com.plot.plugin.building.generation.stage.OpeningGenerationStage;
import com.plot.plugin.building.generation.stage.WallGenerationStage;
import com.plot.plugin.building.golden.GoldenBuildingCaseFactory;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.test.world.IdentityCoordinateService;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowFacadeRhythmIntegrationTest {

    @Test
    void southWallPreservesPierColumnsInFinalVoxels() {
        BuildingFootprint footprint = GoldenBuildingCaseFactory.rectangle(12, 6, 1, 4, 1);
        footprint.setWindowsEnabled(true);
        footprint.setWindowWidth(2);
        footprint.setWindowPierWidth(2);
        footprint.setWindowSillHeight(0);

        BuildingGenerationResult result = generate(footprint);
        String wallId = GoldenBuildingCaseFactory.GOLDEN_WALL;
        String windowId = BuildingGeometryUtils.resolveBlockId(footprint.getWindowMaterial());

        int floorSlabY = result.placementRecords.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        int windowY = OpeningVerticalLayout.windowStartY(floorSlabY, footprint.getWindowSillHeight());
        int southZ = result.placementRecords.keySet().stream().mapToInt(BlockPos::getZ).min().orElse(0);

        List<Character> row = new ArrayList<>();
        int minX = result.placementRecords.keySet().stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = result.placementRecords.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0);
        for (int x = minX; x <= maxX; x++) {
            BlockPos pos = new BlockPos(x, windowY, southZ);
            var record = result.placementRecords.get(pos);
            if (record != null && windowId.equals(record.newBlockId)) {
                row.add('G');
            } else if (record != null && wallId.equals(record.newBlockId)) {
                row.add('W');
            } else {
                row.add('.');
            }
        }

        assertTrue(row.size() >= 8, "row=" + row);
        assertEquals('W', row.getFirst(), "corner pier at segment start");
        assertEquals('W', row.getLast(), "corner pier at segment end");

        int maxGlassRun = 0;
        int glassRun = 0;
        int minPierBetweenWindows = Integer.MAX_VALUE;
        int pierRun = 0;
        boolean inGlass = false;
        for (char cell : row) {
            if (cell == 'G') {
                glassRun++;
                inGlass = true;
                if (pierRun > 0) {
                    minPierBetweenWindows = Math.min(minPierBetweenWindows, pierRun);
                }
                pierRun = 0;
            } else if (cell == 'W') {
                maxGlassRun = Math.max(maxGlassRun, glassRun);
                glassRun = 0;
                if (inGlass) {
                    pierRun++;
                }
            } else {
                maxGlassRun = Math.max(maxGlassRun, glassRun);
                glassRun = 0;
                pierRun = 0;
                inGlass = false;
            }
        }
        maxGlassRun = Math.max(maxGlassRun, glassRun);

        assertTrue(maxGlassRun <= footprint.getWindowWidth(),
            "glass run too wide: " + maxGlassRun + " row=" + row);
        assertTrue(minPierBetweenWindows >= footprint.getWindowPierWidth(),
            "pier too narrow: " + minPierBetweenWindows + " row=" + row);
    }

    private static BuildingGenerationResult generate(BuildingFootprint footprint) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        var context = BuildingGenerationContextFactory.forTesting(
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
