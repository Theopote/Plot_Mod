package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeEnvelopeClearancePassTest {

    @Test
    void cutSlopeColumnsOutsidePavementGetBatterFloorClearance() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        assertTrue(crossSection.includeSlopeBatter);
        int pavementHalf = Math.abs(com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(
            RoadCorridorWidth.pavementEnvelopeWidthBlocks(crossSection)));

        RoadCorridorPlan plan = cutPlan(config, crossSection, new Vec2d(0, 0), new Vec2d(24, 0));
        int slopeCovered = 0;
        int slopeOutsidePavement = 0;
        for (var entry : plan.slopeEntries()) {
            if (!entry.getValue().hasCut()) {
                continue;
            }
            int z = Math.abs(entry.getKey().worldZ());
            if (z <= pavementHalf) {
                continue;
            }
            slopeOutsidePavement++;
            ClearanceCell clearance = plan.clearanceCell(entry.getKey());
            int cutY = entry.getValue().cutSurfaceY();
            if (clearance != null
                    && clearance.designSurfaceY() == cutY
                    && clearance.clearTopY() > clearance.designSurfaceY()) {
                slopeCovered++;
            } else if (cutY >= 89) {
                slopeCovered++;
            }
        }
        assertTrue(slopeOutsidePavement > 0, "cut batter must occupy columns beyond pavement");
        assertTrue(slopeCovered >= slopeOutsidePavement - 2,
            "slope columns beyond pavement must get batter-floor clearance or already meet ground, covered="
                + slopeCovered + " of " + slopeOutsidePavement);
    }

    @Test
    void urbanWithoutBatterDoesNotWidenOpenClearance() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setIncludeSlopeBatter(false);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        assertFalse(crossSection.includeSlopeBatter);
        assertEquals(
            RoadCorridorWidth.pavementEnvelopeWidthBlocks(crossSection),
            RoadCorridorWidth.earthworkEnvelopeWidthBlocks(crossSection, config));

        RoadCorridorPlan plan = cutPlan(config, crossSection, new Vec2d(0, 0), new Vec2d(16, 0));
        assertTrue(plan.slopeEntries().isEmpty(), "urban preset must not emit batter columns");
        int pavementHalf = Math.abs(com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(
            RoadCorridorWidth.pavementEnvelopeWidthBlocks(crossSection)));
        for (var entry : plan.clearanceEntries()) {
            assertTrue(Math.abs(entry.getKey().worldZ()) <= pavementHalf + 1,
                "OPEN punch must stay on pavement envelope when batter is off");
        }
    }

    @Test
    void clearancePassRemovesLeftoverTerrainAboveCutBatter() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        TerrainSampler highGround = new FlatTerrainSampler(90);
        RoadCorridorPlan plan = cutPlan(config, crossSection, new Vec2d(0, 0), new Vec2d(20, 0), highGround);

        ColumnKey slopeKey = null;
        SlopeColumnCell slope = null;
        int pavementHalf = Math.abs(com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(
            RoadCorridorWidth.pavementEnvelopeWidthBlocks(crossSection)));
        for (var entry : plan.slopeEntries()) {
            if (entry.getValue().hasCut() && Math.abs(entry.getKey().worldZ()) > pavementHalf) {
                slopeKey = entry.getKey();
                slope = entry.getValue();
                break;
            }
        }
        assertTrue(slopeKey != null && slope != null, "expected a cut slope column outside pavement");

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorClearancePass.apply(solids, plan, highGround, CorridorTestFixtures.planHost(config));
        int floorY = slope.cutSurfaceY();
        boolean punched = false;
        for (var primitive : solids.primitives()) {
            if (primitive.layer() == RoadSolidLayer.TUNNEL
                    && "minecraft:air".equals(primitive.materialId())
                    && primitive.elevation() == floorY + 1
                    && Math.round(primitive.planPoint().x) == slopeKey.worldX()
                    && Math.round(primitive.planPoint().y) == slopeKey.worldZ()) {
                punched = true;
                break;
            }
        }
        assertTrue(punched, "leftover ground above the cut batter face must be cleared");
    }

    @Test
    void diagonalCutBatterKeepsFourConnectedColumns() {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        RoadCorridorPlan plan = cutPlan(config, crossSection, new Vec2d(0, 0), new Vec2d(24, 24));
        assertTrue(plan.slopeEntries().size() > 40, "diagonal span must raster many batter columns");
        int isolated = 0;
        for (var entry : plan.slopeEntries()) {
            ColumnKey key = entry.getKey();
            if (!hasFourNeighbor(plan, key)) {
                isolated++;
            }
        }
        assertTrue(isolated <= 4,
            "diagonal batter columns should be 4-connected to slope or pavement, isolated=" + isolated);
    }

    private static boolean hasFourNeighbor(RoadCorridorPlan plan, ColumnKey key) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            ColumnKey neighbor = new ColumnKey(key.worldX() + dir[0], key.worldZ() + dir[1]);
            if (plan.slopeCell(neighbor) != null || plan.surfaceMask().get(neighbor) != null) {
                return true;
            }
        }
        return false;
    }

    private static RoadCorridorPlan cutPlan(
            RoadSystemConfig config,
            ResolvedCrossSection crossSection,
            Vec2d start,
            Vec2d end) {
        return cutPlan(config, crossSection, start, end, new FlatTerrainSampler(90));
    }

    private static RoadCorridorPlan cutPlan(
            RoadSystemConfig config,
            ResolvedCrossSection crossSection,
            Vec2d start,
            Vec2d end,
            TerrainSampler terrain) {
        PathSegment segment = new PathSegment(start, end);
        return RoadCorridorPlanBuilder.build(
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, 90, 90, 64, 64, 0.0)),
            CrossSectionBuildContext.fixed(crossSection),
            List.of(RoadConstructionType.ROAD),
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            terrain,
            1.0,
            "slope-envelope",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));
    }
}
