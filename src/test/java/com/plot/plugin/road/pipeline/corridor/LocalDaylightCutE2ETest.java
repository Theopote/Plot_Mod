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

    @Test
    void ordinaryCutNeighborDoesNotInheritDaylightAuth() {
        // Daylight-approved short CUT beside a long ordinary deep CUT — auth must not OR
        // across the shared CUT↔CUT span onto the deep neighbor's roadbed/slope columns.
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)),
            new PathSegment(new Vec2d(10, 0), new Vec2d(13, 0)),
            new PathSegment(new Vec2d(13, 0), new Vec2d(28, 0)));
        List<SegmentHeightInfo> heights = List.of(
            height(segments.get(0), 64),
            height(segments.get(1), 64),
            height(segments.get(2), 64));
        List<RoadConstructionType> types = new ArrayList<>(List.of(
            RoadConstructionType.ROAD,
            RoadConstructionType.CUT,
            RoadConstructionType.CUT));
        Set<Integer> daylight = Set.of(1);

        TerrainSampler terrain = daylightKnobBesideDeepCut(68, 90);
        var config = CorridorTestFixtures.baseConfig();
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
            "daylight-cut-neighbor",
            null,
            host,
            List.of(),
            daylight);

        int authorizedInKnob = 0;
        int authorizedInDeep = 0;
        for (ColumnKey key : allPlanColumns(plan)) {
            if (!plan.allowsLocalDaylightCut(key)) {
                continue;
            }
            if (key.worldX() >= 10 && key.worldX() <= 13) {
                authorizedInKnob++;
            }
            // Deep ordinary CUT starts after the daylight span; keep a gap past the split.
            if (key.worldX() >= 16 && key.worldX() <= 28) {
                authorizedInDeep++;
            }
        }
        assertTrue(authorizedInKnob > 0, "daylight CUT span must receive carve auth");
        assertEquals(0, authorizedInDeep,
            "ordinary deep CUT neighbor must not inherit local daylight auth");

        RoadSolidModel solids = new RoadCorridorSolidEmitterHarness(plan, terrain, host).emit();
        // Per-column placement: authorized knob cells excavate; deep neighbor cells stay solid.
        int knobAirColumns = 0;
        int deepAirColumns = 0;
        for (var p : solids.primitives()) {
            if (!"minecraft:air".equals(p.materialId()) || p.elevation() <= 64) {
                continue;
            }
            int x = (int) Math.round(p.planPoint().x);
            int z = (int) Math.round(p.planPoint().y);
            if (x >= 10 && x <= 13 && z > 0) {
                knobAirColumns++;
            }
            if (x >= 16 && x <= 28) {
                deepAirColumns++;
                assertFalse(plan.allowsLocalDaylightCut(new ColumnKey(x, z)),
                    "deep CUT air carve must not carry daylight auth: " + x + "," + z);
            }
        }
        assertTrue(knobAirColumns > 0,
            "approved knob region must emit AIR above design Y");
        assertEquals(0, deepAirColumns,
            "ordinary deep CUT neighbor must keep natural cover (no open-daylight AIR)");
    }

    private static Set<ColumnKey> allPlanColumns(RoadCorridorPlan plan) {
        Set<ColumnKey> keys = new java.util.HashSet<>();
        for (var e : plan.clearanceEntries()) {
            keys.add(e.getKey());
        }
        for (var e : plan.slopeEntries()) {
            keys.add(e.getKey());
        }
        return keys;
    }

    /** Thin wrapper so the neighbor test reads as plan→emit without duplicating args. */
    private record RoadCorridorSolidEmitterHarness(
            RoadCorridorPlan plan,
            TerrainSampler terrain,
            RoadCorridorPlanBuilder.PlanHost host) {
        RoadSolidModel emit() {
            RoadSolidModel solids = new RoadSolidModel();
            RoadCorridorSolidEmitter.emit(
                solids, plan, terrain, host, null, "minecraft:cobblestone");
            return solids;
        }
    }

    private static SegmentHeightInfo height(PathSegment segment, int y) {
        return new SegmentHeightInfo(segment, y, y, y, y, 0.0);
    }

    private static TerrainSampler oneSidedKnob(int knobTopY) {
        return daylightKnobBesideDeepCut(knobTopY, 64);
    }

    /** Short +Y knob on [10,13]; optional deep cover on [14,28] for ordinary CUT neighbor. */
    private static TerrainSampler daylightKnobBesideDeepCut(int knobTopY, int deepTopY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d planPoint) {
                if (planPoint.x >= 10 && planPoint.x <= 13 && planPoint.y > 0.5) {
                    return knobTopY;
                }
                if (deepTopY > 64 && planPoint.x >= 14 && planPoint.x <= 28) {
                    return deepTopY;
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
