package com.plot.plugin.building.realistic;

import com.plot.plugin.building.generation.DistrictGenerationResult;
import com.plot.plugin.building.model.BuildingFootprint;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 片区真实场景 gap 报告：按轮廓类别与 skip 原因汇总。
 */
public final class RealisticDistrictGapReport {
    public record CategoryRow(
            RealisticFootprintKind kind,
            int attempted,
            int generated,
            int skipped,
            int roofDowngrades,
            int innerOffsetWarnings) {
    }

    public record Summary(
            int attempted,
            int generated,
            int skipped,
            Map<DistrictGenerationResult.SkipReason, Integer> skipByReason,
            Map<RealisticFootprintKind, CategoryRow> byKind,
            int roofDowngrades,
            int innerOffsetWarnings,
            int overlapPairs,
            int conflictingBlocks,
            int steepSiteWarnings,
            int waterSiteWarnings) {

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("Realistic district gap report\n");
            sb.append("  attempted=").append(attempted)
                .append(" generated=").append(generated)
                .append(" skipped=").append(skipped).append('\n');
            sb.append("  skipByReason=").append(skipByReason).append('\n');
            sb.append("  roofDowngrades=").append(roofDowngrades)
                .append(" innerOffsetWarnings=").append(innerOffsetWarnings).append('\n');
            sb.append("  overlapPairs=").append(overlapPairs)
                .append(" conflictingBlocks=").append(conflictingBlocks).append('\n');
            sb.append("  steepSiteWarnings=").append(steepSiteWarnings)
                .append(" waterSiteWarnings=").append(waterSiteWarnings).append('\n');
            sb.append("  byKind:\n");
            for (CategoryRow row : byKind.values()) {
                sb.append("    ").append(row.kind())
                    .append(" attempted=").append(row.attempted())
                    .append(" generated=").append(row.generated())
                    .append(" skipped=").append(row.skipped())
                    .append(" roofDowngrades=").append(row.roofDowngrades())
                    .append(" innerOffsetWarnings=").append(row.innerOffsetWarnings())
                    .append('\n');
            }
            return sb.toString();
        }
    }

    private RealisticDistrictGapReport() {
    }

    public static Summary analyze(
            DistrictGenerationResult district,
            List<TaggedFootprint> catalog) {
        Map<String, TaggedFootprint> byId = new HashMap<>();
        for (TaggedFootprint tagged : catalog) {
            byId.put(tagged.footprint().getId(), tagged);
        }

        Map<RealisticFootprintKind, CategoryRowBuilder> kindBuilders = new EnumMap<>(RealisticFootprintKind.class);
        Map<DistrictGenerationResult.SkipReason, Integer> skipByReason = new LinkedHashMap<>();
        int roofDowngrades = 0;
        int innerOffsetWarnings = 0;
        int steepSiteWarnings = 0;
        int waterSiteWarnings = 0;

        for (DistrictGenerationResult.BuildingOutcome outcome : district.outcomes()) {
            TaggedFootprint tagged = byId.get(outcome.buildingId());
            RealisticFootprintKind kind = tagged != null
                ? tagged.kind()
                : RealisticFootprintKind.RECT_GRID;
            CategoryRowBuilder builder = kindBuilders.computeIfAbsent(kind, ignored -> new CategoryRowBuilder(kind));
            builder.attempted++;
            if (outcome.success()) {
                builder.generated++;
                if (outcome.result() != null) {
                    if (roofDowngraded(tagged, outcome.result())) {
                        builder.roofDowngrades++;
                        roofDowngrades++;
                    }
                    if (outcome.result().warnings.contains("plugin.building.warn.inner_offset_failed")) {
                        builder.innerOffsetWarnings++;
                        innerOffsetWarnings++;
                    }
                    if (outcome.result().warnings.contains("plugin.building.warn.steep_site")
                            || outcome.result().warnings.contains("plugin.building.warn.severe_steep_site")) {
                        steepSiteWarnings++;
                    }
                    if (outcome.result().warnings.contains("plugin.building.warn.water_site")
                            || outcome.result().warnings.contains("plugin.building.warn.partial_water_site")) {
                        waterSiteWarnings++;
                    }
                }
            } else {
                builder.skipped++;
                if (outcome.skipReason() != null) {
                    skipByReason.merge(outcome.skipReason(), 1, Integer::sum);
                }
            }
        }

        Map<RealisticFootprintKind, CategoryRow> byKind = new EnumMap<>(RealisticFootprintKind.class);
        for (CategoryRowBuilder builder : kindBuilders.values()) {
            byKind.put(builder.kind, builder.toRow());
        }

        return new Summary(
            district.buildingsAttempted(),
            district.buildingsGenerated(),
            district.buildingsSkipped(),
            skipByReason,
            byKind,
            roofDowngrades,
            innerOffsetWarnings,
            district.overlappingBuildingPairs().size(),
            district.conflictingBlockCount(),
            steepSiteWarnings,
            waterSiteWarnings);
    }

    private static boolean roofDowngraded(
            TaggedFootprint tagged,
            com.plot.plugin.building.generation.BuildingGenerationResult result) {
        if (tagged == null || result == null || result.effectiveRoofType == null) {
            return false;
        }
        BuildingFootprint.RoofType requested = tagged.requestedRoof();
        return requested != BuildingFootprint.RoofType.FLAT
            && result.effectiveRoofType == BuildingFootprint.RoofType.FLAT
            && result.warnings.contains("plugin.building.warn.roof_downgrade");
    }

    private static final class CategoryRowBuilder {
        private final RealisticFootprintKind kind;
        private int attempted;
        private int generated;
        private int skipped;
        private int roofDowngrades;
        private int innerOffsetWarnings;

        private CategoryRowBuilder(RealisticFootprintKind kind) {
            this.kind = kind;
        }

        private CategoryRow toRow() {
            return new CategoryRow(kind, attempted, generated, skipped, roofDowngrades, innerOffsetWarnings);
        }
    }
}
