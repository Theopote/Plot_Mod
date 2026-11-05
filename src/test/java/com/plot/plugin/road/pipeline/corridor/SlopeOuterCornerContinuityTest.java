package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.core.terrain.FlatTerrainSampler;
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
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 急折外侧：射线算高 + 二维环带补齐三角空隙；覆盖按世界柱逐格校验。
 */
class SlopeOuterCornerContinuityTest {

    @Test
    void sharpLTurnOuterWedgeHasSlopeAndClearance() {
        // 东行再北折：左转，外侧楔形在折点东南方
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(40, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(40, 0), new Vec2d(40, 40)));

        // 覆土 8 < 默认深埋门槛，仍属普通路堑，应有连续外侧边坡
        RoadCorridorPlan plan = buildCutPlan(path, 64, 72);
        assertTrue(plan.slopeEntries().size() > 20, "cut L-turn should emit many slope columns");

        // 折点 (40,0) 外侧 SE：二维环带应逐格盖住楔形（覆土 8 → 边坡水平约 10 格内）
        List<Vec2d> probes = List.of(
            new Vec2d(45, -4),
            new Vec2d(47, -6),
            new Vec2d(44, -7),
            new Vec2d(48, -5),
            new Vec2d(46, -8));
        for (Vec2d probe : probes) {
            assertTrue(hasNearbySlope(plan, probe, 1),
                "outer corner wedge missing slope near " + probe);
            assertTrue(hasNearbyClearance(plan, probe, 1),
                "outer corner wedge missing clearance near " + probe);
        }
    }

    @Test
    void sharpLTurnOuterWedgeColumnsAreCoveredCellByCell() {
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(40, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(40, 0), new Vec2d(40, 40)));
        RoadCorridorPlan plan = buildCutPlan(path, 64, 72);

        // 在折点外侧扇区内，距路肩约 4～8 格的弧带逐格要求有边坡
        for (int ox = 4; ox <= 8; ox++) {
            for (int oz = -8; oz <= -4; oz++) {
                double dist = Math.hypot(ox, oz);
                if (dist < 4.5 || dist > 8.5) {
                    continue;
                }
                // 仅东南象限楔形（x>0,z<0）
                ColumnKey key = new ColumnKey(40 + ox, oz);
                assertNotNull(plan.slopeCell(key),
                    "90° outer wedge must cover column " + key);
            }
        }
    }

    @Test
    void sharp135TurnOuterWedgeHasContinuousSlope() {
        // 东行再西北折 ~135° 外角
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(40, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(40, 0), new Vec2d(12, 28)));
        RoadCorridorPlan plan = buildCutPlan(path, 64, 72);
        assertTrue(plan.slopeEntries().size() > 20, "135° cut should emit slope columns");

        List<Vec2d> probes = List.of(
            new Vec2d(46, -3),
            new Vec2d(48, -5),
            new Vec2d(45, -6),
            new Vec2d(47, -7));
        for (Vec2d probe : probes) {
            assertTrue(hasNearbySlope(plan, probe, 1),
                "135° outer wedge missing slope near " + probe);
        }
    }

    @Test
    void sharpLTurnBandCellsDoNotEmitSpuriousRetainingWalls() {
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(40, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(40, 0), new Vec2d(40, 40)));
        RoadCorridorPlan plan = buildCutPlan(path, 64, 72);

        // 覆土仅 8，开敞挖方应接地完成；环带补格不得把中间坡面标成挡墙末端。
        assertTrue(plan.needsRetainingWallCount() < plan.slopeEntries().size() / 4,
            "band fill must not mass-mark retaining tips; retaining="
                + plan.needsRetainingWallCount() + " slopes=" + plan.slopeEntries().size());

        RoadSolidModel solids = new RoadSolidModel();
        var host = CorridorTestFixtures.planHost(CorridorTestFixtures.baseConfig());
        RoadCorridorSolidEmitter.emit(
            solids, plan, new FlatTerrainSampler(72), host, null, "minecraft:stone");
        long retaining = solids.primitives().stream()
            .filter(p -> p.layer() == RoadSolidLayer.RETAINING_WALL)
            .count();
        assertTrue(retaining == 0,
            "ordinary open cut L-turn must not emit retaining walls from band mid-cells; got "
                + retaining);
    }

    @Test
    void outerBandDoesNotExceedMaxBatterWidth() {
        List<Vec2d> path = new ArrayList<>();
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(0, 0), new Vec2d(40, 0)));
        path.addAll(RoadVoxelRasterizer.sampleSpanPoints(new Vec2d(40, 0), new Vec2d(40, 40)));
        RoadCorridorPlan plan = buildCutPlan(path, 64, 72);

        int maxRun = RoadCorridorWidth.MAX_SLOPE_BATTER_RUN_BLOCKS;
        // 路肩约在 x=40±半宽；取折点外侧远点，距中心超过 maxRun+半宽 应无边坡
        int far = maxRun + 12;
        assertNull(plan.slopeCell(new ColumnKey(40 + far, -far)),
            "band must not stamp beyond max batter run");
        assertNull(plan.slopeCell(new ColumnKey(40 + far, 0)),
            "east of corner beyond max run must stay empty");
    }

    private static boolean hasNearbySlope(RoadCorridorPlan plan, Vec2d probe, int radius) {
        int px = (int) Math.round(probe.x);
        int pz = (int) Math.round(probe.y);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (plan.slopeCell(new ColumnKey(px + dx, pz + dz)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNearbyClearance(RoadCorridorPlan plan, Vec2d probe, int radius) {
        int px = (int) Math.round(probe.x);
        int pz = (int) Math.round(probe.y);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (plan.clearanceCell(new ColumnKey(px + dx, pz + dz)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RoadCorridorPlan buildCutPlan(List<Vec2d> path, int roadY, int terrainY) {
        List<PathSegment> segments = new ArrayList<>();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        List<RoadConstructionType> types = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathSegment segment = new PathSegment(path.get(i - 1), path.get(i));
            segments.add(segment);
            heightInfos.add(new SegmentHeightInfo(segment, roadY, roadY, roadY, roadY, 0.0));
            types.add(RoadConstructionType.ROAD);
        }
        RoadSystemConfig config = CorridorTestFixtures.baseConfig();
        config.setCutSlopeRatio(1.0f);
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        return RoadCorridorPlanBuilder.build(
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(crossSection),
            types,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            new FlatTerrainSampler(terrainY),
            1.0,
            "outer-corner",
            JunctionCorridorBuildContext.NONE,
            CorridorTestFixtures.planHost(config));
    }
}
