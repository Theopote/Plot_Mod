package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictSiteSummaryTest {

    private static BuildingFootprint building(String id, double ox) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(ox, 0),
            new Vec2d(ox + 4, 0),
            new Vec2d(ox + 4, 4),
            new Vec2d(ox, 4)
        ), true);
        footprint.setName(id);
        footprint.setFloors(2);
        footprint.setFloorHeight(3);
        return footprint;
    }

    private static BuildingGenerationResult withWarnings(String... keys) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos((int) (Math.random() * 1000), 64, 0);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));
        result.blockCount = 1;
        for (String key : keys) {
            result.warnings.add(key);
        }
        return result;
    }

    @Test
    void districtSiteSummaryCountsPerBuildingAndStaysFailSoft() {
        BuildingFootprint flat = building("A", 0);
        BuildingFootprint water = building("B", 20);
        BuildingFootprint steep = building("C", 40);
        BuildingFootprint structure = building("D", 60);
        BuildingFootprint partial = building("E", 80);
        BuildingFootprint heavy = building("F", 100);

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(flat, water, steep, structure, partial, heavy),
            footprint -> switch (footprint.getId()) {
                case "A" -> withWarnings();
                case "B" -> withWarnings("plugin.building.warn.water_site");
                case "C" -> withWarnings("plugin.building.warn.steep_site");
                case "D" -> withWarnings("plugin.building.warn.structure_conflict");
                case "E" -> withWarnings("plugin.building.warn.partial_water_site");
                case "F" -> withWarnings("plugin.building.warn.heavy_earthwork");
                default -> throw new IllegalStateException(footprint.getId());
            });

        assertEquals(6, district.buildingsAttempted());
        assertEquals(6, district.buildingsGenerated());
        assertEquals(0, district.buildingsSkipped());
        assertEquals(1, district.waterSiteCount());
        assertEquals(1, district.partialWaterSiteCount());
        assertEquals(1, district.steepSiteCount());
        assertEquals(1, district.structureConflictBuildingCount());
        assertEquals(1, district.heavyEarthworkSiteCount());
        assertTrue(district.hasSiteConditionSummary());

        DistrictBuildReport report = DistrictBuildReport.from(
            district,
            new com.plot.core.command.commands.BuildingGenerateCommand.ExecutionResult(6, 0, 6, false));
        assertEquals(1, report.waterSiteCount());
        assertEquals(1, report.partialWaterSiteCount());
        assertEquals(1, report.steepSiteCount());
        assertEquals(1, report.structureConflictBuildingCount());
        assertEquals(1, report.heavyEarthworkSiteCount());
    }
}
