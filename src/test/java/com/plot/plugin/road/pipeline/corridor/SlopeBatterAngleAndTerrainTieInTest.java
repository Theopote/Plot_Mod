package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.SlopeFormatUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlopeBatterAngleAndTerrainTieInTest {

    @ParameterizedTest
    @ValueSource(floats = {30f, 45f, 60f})
    void cutSlopeRespectsTargetAngleWithinTolerance(float angleDegrees) {
        float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(angleDegrees);
        RoadCorridorPlan plan = buildCutPlan(ratio, new FlatTerrainSampler(90));
        assertTrue(plan.slopeEntries().size() >= 3, "cut plan must emit slope cells");

        List<SlopeColumnCell> cutCells = new ArrayList<>();
        for (var entry : plan.slopeEntries()) {
            if (entry.getValue().hasCut()) {
                cutCells.add(entry.getValue());
            }
        }
        assertTrue(cutCells.size() >= 2);
        assertAngleTolerance(cutCells, ratio, false);
        assertEquals(0, plan.unresolvedSlopeConstructionCount());
    }

    @ParameterizedTest
    @ValueSource(floats = {30f, 45f, 60f})
    void fillSlopeRespectsTargetAngleWithinTolerance(float angleDegrees) {
        float ratio = SlopeFormatUtils.angleDegreesToBatterRatio(angleDegrees);
        RoadCorridorPlan plan = buildFillPlan(ratio, new FlatTerrainSampler(50));
        assertTrue(plan.slopeEntries().size() >= 3, "fill plan must emit slope cells");

        List<SlopeColumnCell> fillCells = new ArrayList<>();
        for (var entry : plan.slopeEntries()) {
            if (entry.getValue().hasFill()) {
                fillCells.add(entry.getValue());
            }
        }
        assertTrue(fillCells.size() >= 2);
        assertAngleTolerance(fillCells, ratio, true);
        assertEquals(0, plan.unresolvedSlopeConstructionCount());
    }

    @org.junit.jupiter.api.Test
    void shallowCutCompletesWithoutRetainingWallDiagnostic() {
        RoadCorridorPlan plan = buildCutPlan(1.0f, new FlatTerrainSampler(70));
        assertTrue(plan.slopeEntries().size() > 0);
        assertEquals(0, plan.needsRetainingWallCount());
        assertEquals(0, plan.unresolvedSlopeConstructionCount());
    }

    @org.junit.jupiter.api.Test
    void deepCutWithinWidthCapKeepsPartialSlopeAndRetainingWallDiagnostic() {
        RoadCorridorPlan plan = buildCutPlan(1.0f, new FlatTerrainSampler(120));
        assertTrue(plan.slopeBatterWidthCapCount() > 0);
        assertTrue(plan.needsRetainingWallCount() > 0);
        assertEquals(0, plan.unresolvedSlopeConstructionCount());
        assertNotNull(plan.slopeEntries().iterator().next().getValue());
        assertTrue(plan.slopeEntries().size() >= RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS / 2,
            "width-capped cut must still emit partial batter columns");
    }

    private static void assertAngleTolerance(List<SlopeColumnCell> cells, float ratio, boolean fill) {
        List<SlopeColumnCell> sorted = cells.stream()
            .sorted((a, b) -> {
                double ax = a.planPoint().x;
                double ay = a.planPoint().y;
                double bx = b.planPoint().x;
                double by = b.planPoint().y;
                int cmp = Double.compare(ax + ay, bx + by);
                if (cmp != 0) {
                    return cmp;
                }
                return Double.compare(ax, bx);
            })
            .toList();
        for (int i = 1; i < sorted.size(); i++) {
            SlopeColumnCell prev = sorted.get(i - 1);
            SlopeColumnCell curr = sorted.get(i);
            double horizontal = prev.planPoint().distance(curr.planPoint());
            if (horizontal < 0.5) {
                continue;
            }
            int prevY = fill ? prev.fillSurfaceY() : prev.cutSurfaceY();
            int currY = fill ? curr.fillSurfaceY() : curr.cutSurfaceY();
            int deltaY = Math.abs(currY - prevY);
            if (deltaY == 0) {
                continue;
            }
            int maxDeltaY = Math.max(1, (int) Math.ceil(horizontal / Math.max(0.5f, ratio)));
            assertTrue(deltaY <= maxDeltaY,
                "slope step delta " + deltaY + " exceeds angle tolerance for horizontal " + horizontal);
        }
    }

    private static RoadCorridorPlan buildCutPlan(float cutRatio, TerrainSampler terrain) {
        return buildPlan(cutRatio, cutRatio, 64, terrain);
    }

    private static RoadCorridorPlan buildFillPlan(float fillRatio, TerrainSampler terrain) {
        return buildPlan(fillRatio, fillRatio, 64, terrain);
    }

    private static RoadCorridorPlan buildPlan(
            float cutRatio,
            float fillRatio,
            int roadY,
            TerrainSampler terrain) {
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setCutSlopeRatio(cutRatio);
        config.setFillSlopeRatio(fillRatio);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        PathSegment segment = new PathSegment(new Vec2d(0, 0), new Vec2d(40, 0));
        return RoadCorridorPlanBuilder.build(
            List.of(segment),
            List.of(new SegmentHeightInfo(segment, roadY, roadY, roadY, roadY, 0.0)),
            CrossSectionBuildContext.fixed(crossSection),
            List.of(RoadConstructionType.ROAD),
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            terrain,
            1.0,
            "angle-test",
            CorridorTestFixtures.planHost(config));
    }
}
