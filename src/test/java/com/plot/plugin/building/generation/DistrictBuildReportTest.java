package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.commands.BuildingGenerateCommand;
import com.plot.plugin.building.model.BuildingFootprint;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictBuildReportTest {

    private static BuildingFootprint building(String id, double size, int floors, int floorHeight) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size)
        ), true);
        footprint.setName(id);
        footprint.setFloors(floors);
        footprint.setFloorHeight(floorHeight);
        return footprint;
    }

    @Test
    void fromDistrictCapturesAreaVolumeAndPlacement() {
        BuildingFootprint a = building("a", 10, 4, 3);
        BuildingFootprint b = building("b", 5, 2, 3);

        BuildingGenerationResult ra = new BuildingGenerationResult();
        BlockPos pa = new BlockPos(0, 0, 0);
        ra.placementRecords.put(pa, new BlockRecord(pa, "minecraft:air", "minecraft:stone"));
        ra.blockCount = 1;

        BuildingGenerationResult rb = new BuildingGenerationResult();
        BlockPos pb = new BlockPos(1, 0, 0);
        rb.placementRecords.put(pb, new BlockRecord(pb, "minecraft:air", "minecraft:bricks"));
        rb.blockCount = 1;

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(a, b),
            footprint -> "a".equals(footprint.getId()) ? ra : rb);

        assertEquals(2, district.buildingsGenerated());
        assertEquals(100.0 + 25.0, district.totalArea(), 1e-6);
        assertEquals(100.0 * 4 * 3 + 25.0 * 2 * 3, district.totalVolume(), 1e-6);

        BuildingGenerateCommand.ExecutionResult placement =
            new BuildingGenerateCommand.ExecutionResult(2, 0, 2, false);
        DistrictBuildReport report = DistrictBuildReport.from(district, placement);

        assertTrue(report.isDistrict());
        assertEquals(2, report.buildingsGenerated());
        assertEquals(0, report.buildingsSkipped());
        assertEquals(125.0, report.totalArea(), 1e-6);
        assertEquals(2, report.placedBlocks());
        assertEquals(2, report.plannedBlocks());
        assertTrue(report.placementFullSuccess());
        assertFalse(report.cancelled());
    }

    @Test
    void fromDistrictKeepsSkipListAfterPartialGeneration() {
        BuildingFootprint ok = building("ok", 8, 3, 3);
        BuildingFootprint bad = building("bad", 8, 3, 3);

        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(2, 2, 2);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(ok, bad),
            footprint -> {
                if ("bad".equals(footprint.getId())) {
                    throw new IllegalStateException("nope");
                }
                return result;
            });

        BuildingGenerateCommand.ExecutionResult placement =
            new BuildingGenerateCommand.ExecutionResult(1, 0, 1, false);
        DistrictBuildReport report = DistrictBuildReport.from(district, placement);

        assertEquals(1, report.buildingsGenerated());
        assertEquals(1, report.buildingsSkipped());
        assertEquals(1, report.skipped().size());
        assertEquals("bad", report.skipped().getFirst().buildingId());
        assertTrue(report.warnings().contains("plugin.building.warn.district_partial"));
    }

    @Test
    void invalidFootprintIsSkippedWithoutCallingGenerator() {
        BuildingFootprint invalid = new BuildingFootprint("invalid", List.of(
            new Vec2d(0, 0),
            new Vec2d(1, 0)
        ), false);
        invalid.setName("invalid");

        BuildingFootprint ok = building("ok", 6, 2, 3);
        BuildingGenerationResult result = new BuildingGenerationResult();
        BlockPos pos = new BlockPos(0, 1, 0);
        result.placementRecords.put(pos, new BlockRecord(pos, "minecraft:air", "minecraft:stone"));

        DistrictGenerationResult district = DistrictMassingGenerator.generate(
            List.of(invalid, ok),
            footprint -> {
                if ("invalid".equals(footprint.getId())) {
                    throw new AssertionError("invalid footprint should not generate");
                }
                return result;
            });

        assertEquals(1, district.buildingsGenerated());
        assertEquals(1, district.buildingsSkipped());
        assertEquals(
            DistrictGenerationResult.SkipReason.INVALID,
            district.skippedOutcomes().getFirst().skipReason());
    }
}
