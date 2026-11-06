package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.LocalDaylightCut;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: LocalDaylightCut → corridor plan slope auth → solid emit for a
 * one-sided short dirt knob (centerline open).
 */
class LocalDaylightCutE2ETest {

    @Test
    void oneSidedShortOutcropGetsSlopeAuthAndCutEmit() {
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)),
            new PathSegment(new Vec2d(10, 0), new Vec2d(13, 0)),
            new PathSegment(new Vec2d(13, 0), new Vec2d(23, 0)));
        List<SegmentHeightInfo> heights = List.of(
            height(segments.get(0), 64),
            height(segments.get(1), 64),
            height(segments.get(2), 64));
        List<Double> distances = segments.stream().map(s -> s.distance).toList();
        List<RoadConstructionType> types = new ArrayList<>(List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.ROCK_SHELTER_LEFT,
            RoadConstructionType.ROAD));

        TerrainSampler terrain = oneSidedKnob(68);
        var config = CorridorTestFixtures.baseConfig();
        int envelope = RoadCorridorWidth.gradingEnvelopeWidthBlocks(
            ResolvedCrossSection.fromConfig(config));
        Set<Integer> daylight = LocalDaylightCut.apply(
            types, distances, heights, terrain, envelope,
            ResolvedTunnelStyle.defaults(), resolver(), 1.0);
        assertEquals(RoadConstructionType.CUT, types.get(1));
        assertTrue(daylight.contains(1));

        var host = CorridorTestFixtures.planHost(config);
        RoadCorridorPlan plan = RoadCorridorPlanBuilder.build(
            segments,
            heights,
            CrossSectionBuildContext.fixed(ResolvedCrossSection.fromConfig(config)),
            types,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            terrain,
            1.0,
            "daylight-e2e",
            null,
            host,
            List.of(),
            daylight);

        // Outer batter on the +Y (left) knob side: at least one column must carry auth
        // from daylight-authorized slope samples (ROAD neighbors may also stamp cuts).
        int authorizedOuterCuts = 0;
        int outerCuts = 0;
        for (var entry : plan.slopeEntries()) {
            ColumnKey key = entry.getKey();
            SlopeColumnCell slope = entry.getValue();
            if (slope == null || !slope.hasCut()) {
                continue;
            }
            if (key.worldZ() > 0 && key.worldX() >= 10 && key.worldX() <= 13) {
                outerCuts++;
                if (plan.allowsLocalDaylightCut(key)) {
                    authorizedOuterCuts++;
                }
            }
        }
        assertTrue(outerCuts > 0, "expected cut slopes on the outcrop shoulder");
        assertTrue(authorizedOuterCuts > 0,
            "daylight-authorized slope samples must mark at least one outer cut column");

        // Neighbor open-road columns must not receive daylight carve auth via span bleed.
        for (var entry : plan.clearanceEntries()) {
            ColumnKey key = entry.getKey();
            if (key.worldX() < 5 || key.worldX() > 18) {
                assertFalse(plan.allowsLocalDaylightCut(key),
                    "daylight auth must not spread onto distant open-road columns: " + key);
            }
        }

        RoadSolidModel solids = new RoadSolidModel();
        RoadCorridorSolidEmitter.emit(
            solids, plan, terrain, host, null, "minecraft:cobblestone");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                "minecraft:air".equals(p.materialId())
                    && p.elevation() > 64
                    && Math.round(p.planPoint().y) > 0),
            "authorized outcrop side must excavate above design Y");
        assertTrue(solids.primitives().stream().anyMatch(p ->
                p.layer() == RoadSolidLayer.SUBGRADE
                    || (p.materialId() != null && p.materialId().contains("cobble"))
                    || (p.materialId() != null && p.materialId().contains("dirt"))
                    || (p.materialId() != null && p.materialId().contains("stone"))),
            "slope / support materials should appear after cut");
    }

    private static SegmentHeightInfo height(PathSegment segment, int y) {
        return new SegmentHeightInfo(segment, y, y, y, y, 0.0);
    }

    private static TerrainSampler oneSidedKnob(int knobTopY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                if (planPoint.y > 0.5 && planPoint.x >= 10 && planPoint.x <= 13) {
                    return knobTopY;
                }
                return 64;
            }

            @Override
            public int sampleColumnTopY(Vec2d planPoint) {
                return sampleSurfaceY(planPoint);
            }

            @Override
            public boolean isSolidBlock(int worldX, int y, int worldZ) {
                return y <= sampleSurfaceY(new Vec2d(worldX, worldZ));
            }

            @Override
            public boolean isConstructionVolumeObstruction(int worldX, int y, int worldZ) {
                return isSolidBlock(worldX, y, worldZ);
            }
        };
    }

    private static RoadTerrainClearanceUtils.BlockColumnResolver resolver() {
        return new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d planPoint) {
                return (int) Math.round(planPoint.x);
            }

            @Override
            public int worldZ(Vec2d planPoint) {
                return (int) Math.round(planPoint.y);
            }
        };
    }
}
