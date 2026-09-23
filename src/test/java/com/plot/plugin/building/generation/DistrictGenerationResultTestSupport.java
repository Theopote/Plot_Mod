package com.plot.plugin.building.generation;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.List;

/** 测试用 DistrictGenerationResult 构造辅助（同包可访问 package-private API）。 */
public final class DistrictGenerationResultTestSupport {
    private DistrictGenerationResultTestSupport() {
    }

    public static BuildingFootprint building(String id, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(0, 0),
            new Vec2d(size, 0),
            new Vec2d(size, size),
            new Vec2d(0, size)
        ), true);
        footprint.setName(id);
        return footprint;
    }

    public static BuildingGenerationResult resultWithWarnings(String... warnings) {
        BuildingGenerationResult result = new BuildingGenerationResult();
        result.blockCount = 1;
        for (String warning : warnings) {
            result.warnings.add(warning);
        }
        return result;
    }

    public static DistrictGenerationResult districtWithSkippedAndWarning() {
        DistrictGenerationResult district = new DistrictGenerationResult();
        district.addSuccess(building("ok", 10), resultWithWarnings("plugin.building.warn.water_site"));
        district.addSkipped(building("bad", 10), DistrictGenerationResult.SkipReason.INVALID, "detail");
        district.addSuccess(building("overlap", 10), resultWithWarnings());
        district.finalizeOverlaps();
        return district;
    }

    public static DistrictGenerationResult districtWithOverlapPair(String idA, String idB) {
        DistrictGenerationResult district = new DistrictGenerationResult();
        district.addSuccess(building(idA, 10), resultWithWarnings());
        district.addSuccess(building(idB, 10), resultWithWarnings());
        district.finalizeOverlaps();
        return district;
    }

    public static BuildingFootprint offsetBuilding(String id, double x, double z, double size) {
        BuildingFootprint footprint = new BuildingFootprint(id, List.of(
            new Vec2d(x, z),
            new Vec2d(x + size, z),
            new Vec2d(x + size, z + size),
            new Vec2d(x, z + size)
        ), true);
        footprint.setName(id);
        return footprint;
    }

    public static DistrictGenerationResult districtWithClosePair(String idA, String idB, double gap) {
        DistrictGenerationResult district = new DistrictGenerationResult();
        district.addSuccess(offsetBuilding(idA, 0, 0, 10), resultWithWarnings());
        district.addSuccess(offsetBuilding(idB, 10 + gap, 0, 10), resultWithWarnings());
        district.finalizeOverlaps();
        return district;
    }
}
