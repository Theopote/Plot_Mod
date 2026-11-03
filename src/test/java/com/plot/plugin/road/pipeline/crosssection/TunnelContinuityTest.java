package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadPathStationSampler;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.ConstructionRun;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadVoxelRasterizer;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.core.terrain.TerrainSampler;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bend continuity over final voxels: connected cavity, enclosed shell, and
 * 3D lining links across corners — not “at least one air + one lining”.
 */
class TunnelContinuityTest {

    private static final int ROAD_Y = 64;
    private static final int[][] DIRS6 = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    record Scenario(String name, List<PathSegment> segments, int roadWidth, TunnelShape shape) {
        @Override
        public String toString() {
            return name + " " + shape + " width=" + roadWidth;
        }
    }

    static Stream<Arguments> tunnelScenarios() {
        List<Arguments> args = new ArrayList<>();
        for (int width : List.of(5, 9, 15)) {
            args.add(Arguments.of(new Scenario("right-angle", rightAnglePath(), width, TunnelShape.RECTANGULAR)));
        }
        args.add(Arguments.of(new Scenario("forty-five-degree", fortyFiveDegreePath(), 9, TunnelShape.RECTANGULAR)));
        args.add(Arguments.of(new Scenario("s-curve", sCurvePath(), 9, TunnelShape.RECTANGULAR)));
        return args.stream();
    }

    static Stream<Arguments> shapedBendScenarios() {
        List<Arguments> args = new ArrayList<>();
        for (TunnelShape shape : List.of(
                TunnelShape.RECTANGULAR,
                TunnelShape.CIRCULAR,
                TunnelShape.HORSESHOE,
                TunnelShape.PENTAGONAL,
                TunnelShape.ARCH)) {
            int clear = shape == TunnelShape.CIRCULAR ? 5 : 6;
            args.add(Arguments.of(new Scenario("right-angle", rightAnglePath(), 9, shape), clear));
            args.add(Arguments.of(new Scenario("s-curve", sCurvePath(), 9, shape), clear));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tunnelScenarios")
    void tunnelGeometryIsContinuous(Scenario scenario) {
        RoadGenerationResult result = generateTunnel(scenario, 5);

        long lights = result.placementRecords.values().stream()
            .filter(record -> "minecraft:sea_lantern".equals(record.newBlockId))
            .count();
        assertTrue(lights > 0, scenario + " must place sea_lantern lights");

        Set<BlockPos> lining = new HashSet<>();
        result.placementRecords.forEach((pos, record) -> {
            if ("minecraft:stone_bricks".equals(record.newBlockId)) {
                lining.add(pos);
            }
        });
        assertFalse(lining.isEmpty(), scenario + " must place tunnel lining");
        assertFalse(hasIsolatedLiningBlock(lining), scenario + " must not contain isolated lining blocks");

        long airBlocks = result.placementRecords.values().stream()
            .filter(record -> "minecraft:air".equals(record.newBlockId))
            .count();
        assertTrue(airBlocks > 0, scenario + " must carve tunnel air volume");
    }

    @ParameterizedTest(name = "{0} clear={1}")
    @MethodSource("shapedBendScenarios")
    void bentStationsKeepEnclosedCavityAndCornerLinks(Scenario scenario, int clearHeight) {
        RoadGenerationResult result = generateTunnel(scenario, clearHeight);
        RoadSystemConfig config = new RoadSystemConfig("tunnel-continuity");
        config.setRoadWidth(scenario.roadWidth());
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        int envelope = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        int halfSpan = Math.max(4, envelope / 2 + 3);
        int minCavity = Math.max(6, envelope * Math.max(2, clearHeight / 2));

        List<PathSegment> segments = scenario.segments();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        for (PathSegment segment : segments) {
            heightInfos.add(new SegmentHeightInfo(segment, 72, 72, ROAD_Y, ROAD_Y, 0.0));
        }

        AtomicInteger stations = new AtomicInteger();
        AtomicReference<Set<BlockPos>> previousAir = new AtomicReference<>();
        AtomicReference<Set<BlockPos>> previousLining = new AtomicReference<>();
        double length = totalLength(segments);
        Set<Integer> cornerSegmentIndexes = cornerSegmentIndexes(segments);

        RoadPathStationSampler.forEach(
            segments,
            heightInfos,
            0.0,
            1.0,
            (center, targetY) -> targetY,
            (center, leftNormal, targetY, chainage, segmentIndex) -> {
                if (chainage < 3.0 || chainage > length - 3.0) {
                    return;
                }
                stations.incrementAndGet();
                Vec2d left = leftNormal.lengthSquared() > 1e-12 ? leftNormal.normalize() : new Vec2d(0, 1);
                Vec2d forward = left.perpendicular().normalize();

                StationSlice slice = collectStationSlice(
                    result, center, left, forward, halfSpan, clearHeight);

                Set<BlockPos> mainCavity = largestComponent3d(slice.air);
                assertTrue(mainCavity.size() >= minCavity,
                    scenario + " station " + chainage
                        + " main cavity cells=" + mainCavity.size()
                        + " allSliceAir=" + slice.air.size() + " min=" + minCavity);
                // Slice edges / corner overlaps can pick up inset-stray voxels; require the
                // primary cavity to dominate station air rather than every stray cell linking.
                boolean nearCorner = cornerSegmentIndexes.contains(segmentIndex);
                int num = nearCorner ? 2 : 3;
                int den = nearCorner ? 3 : 4; // ~67% at corners, 75% on straight
                assertTrue(mainCavity.size() * den >= slice.air.size() * num,
                    scenario + " station " + chainage
                        + " primary cavity ratio main=" + mainCavity.size()
                        + " all=" + slice.air.size());
                StationSlice enclosed = new StationSlice(mainCavity, slice.lining);
                assertTrue(enclosesCavity(enclosed, center, left),
                    scenario + " station " + chainage
                        + " lining must enclose left/right/roof around cavity");

                Set<BlockPos> prevAir = previousAir.get();
                Set<BlockPos> prevLining = previousLining.get();
                if (prevAir != null && cornerSegmentIndexes.contains(segmentIndex)) {
                    assertTrue(sharesNeighborhood(prevAir, mainCavity, 2),
                        scenario + " station " + chainage
                            + " corner cavity must join prior station within 2 blocks");
                    assertTrue(sharesNeighborhood(prevLining, slice.lining, 2),
                        scenario + " station " + chainage
                            + " corner lining must join prior station within 2 blocks");
                }
                previousAir.set(mainCavity);
                previousLining.set(slice.lining);
            });

        assertTrue(stations.get() >= 4, scenario + " stations=" + stations.get());
    }

    private record StationSlice(Set<BlockPos> air, Set<BlockPos> lining) {
    }

    /**
     * Collect TUNNEL air/lining in a short prism around the station so bend
     * continuity is judged on final voxels, not 2D mask projection alone.
     */
    private static StationSlice collectStationSlice(
            RoadGenerationResult result,
            Vec2d center,
            Vec2d left,
            Vec2d forward,
            int halfSpan,
            int clearHeight) {
        Set<BlockPos> air = new HashSet<>();
        Set<BlockPos> lining = new HashSet<>();
        int cx = (int) Math.round(center.x);
        int cz = (int) Math.round(center.y);
        int radius = halfSpan + 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = ROAD_Y + 1; y <= ROAD_Y + clearHeight + 2; y++) {
                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                    if (result.placementLayers.get(pos) != RoadSolidLayer.TUNNEL) {
                        continue;
                    }
                    Vec2d plan = new Vec2d(pos.getX() + 0.5, pos.getZ() + 0.5);
                    Vec2d delta = plan.subtract(center);
                    double along = delta.dot(forward);
                    double lateral = delta.dot(left);
                    if (Math.abs(along) > 1.6 || Math.abs(lateral) > halfSpan + 0.6) {
                        continue;
                    }
                    var record = result.placementRecords.get(pos);
                    if (record == null || record.newBlockId == null) {
                        continue;
                    }
                    if ("minecraft:air".equals(record.newBlockId)) {
                        air.add(pos);
                    } else {
                        lining.add(pos);
                    }
                }
            }
        }
        return new StationSlice(air, lining);
    }

    private static Set<Integer> cornerSegmentIndexes(List<PathSegment> segments) {
        Set<Integer> corners = new HashSet<>();
        for (int i = 1; i < segments.size(); i++) {
            corners.add(i - 1);
            corners.add(i);
        }
        return corners;
    }

    private static boolean enclosesCavity(StationSlice slice, Vec2d center, Vec2d left) {
        if (slice.air.isEmpty() || slice.lining.isEmpty()) {
            return false;
        }
        boolean leftWall = false;
        boolean rightWall = false;
        boolean roof = false;
        int maxAirY = Integer.MIN_VALUE;
        for (BlockPos air : slice.air) {
            maxAirY = Math.max(maxAirY, air.getY());
        }
        for (BlockPos lining : slice.lining) {
            Vec2d plan = new Vec2d(lining.getX() + 0.5, lining.getZ() + 0.5);
            double lateral = plan.subtract(center).dot(left);
            if (lateral < -0.4) {
                leftWall = true;
            }
            if (lateral > 0.4) {
                rightWall = true;
            }
            if (lining.getY() >= maxAirY) {
                roof = true;
            }
        }
        return leftWall && rightWall && roof;
    }

    private static boolean sharesNeighborhood(Set<BlockPos> previous, Set<BlockPos> current, int maxManhattan) {
        if (previous == null || previous.isEmpty() || current == null || current.isEmpty()) {
            return false;
        }
        for (BlockPos a : previous) {
            for (BlockPos b : current) {
                int manhattan = Math.abs(a.getX() - b.getX())
                    + Math.abs(a.getY() - b.getY())
                    + Math.abs(a.getZ() - b.getZ());
                if (manhattan <= maxManhattan) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<BlockPos> largestComponent3d(Set<BlockPos> cells) {
        Set<BlockPos> best = Set.of();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : cells) {
            if (!seen.add(start)) {
                continue;
            }
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> component = new HashSet<>();
            queue.add(start);
            component.add(start);
            while (!queue.isEmpty()) {
                BlockPos cell = queue.removeFirst();
                for (int[] d : DIRS6) {
                    BlockPos next = cell.add(d[0], d[1], d[2]);
                    if (cells.contains(next) && seen.add(next)) {
                        queue.add(next);
                        component.add(next);
                    }
                }
            }
            if (component.size() > best.size()) {
                best = component;
            }
        }
        return best;
    }

    private static double totalLength(List<PathSegment> segments) {
        double total = 0.0;
        for (PathSegment segment : segments) {
            total += segment.distance;
        }
        return total;
    }

    private static boolean hasIsolatedLiningBlock(Set<BlockPos> lining) {
        if (lining.size() <= 2) {
            return false;
        }
        for (BlockPos pos : lining) {
            int neighbors = 0;
            for (BlockPos candidate : lining) {
                if (candidate.equals(pos)) {
                    continue;
                }
                if (Math.abs(candidate.getX() - pos.getX())
                        + Math.abs(candidate.getY() - pos.getY())
                        + Math.abs(candidate.getZ() - pos.getZ()) == 1) {
                    neighbors++;
                }
            }
            if (neighbors == 0) {
                return true;
            }
        }
        return false;
    }

    private static RoadGenerationResult generateTunnel(Scenario scenario, int clearHeight) {
        RoadSolidModel solids = new RoadSolidModel();
        RoadSystemConfig config = new RoadSystemConfig("tunnel-continuity");
        config.setRoadWidth(scenario.roadWidth());
        ResolvedCrossSection crossSection = ResolvedCrossSection.fromConfig(config);
        CrossSectionBuildContext crossSections = CrossSectionBuildContext.fixed(crossSection);

        List<PathSegment> segments = scenario.segments();
        List<SegmentHeightInfo> heightInfos = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        List<RoadConstructionType> constructionTypes = new ArrayList<>();
        double station = 0.0;
        for (PathSegment segment : segments) {
            heightInfos.add(new SegmentHeightInfo(segment, 72, 72, ROAD_Y, ROAD_Y, 0.0));
            constructionTypes.add(RoadConstructionType.TUNNEL);
            distances.add(segment.distance);
            station += segment.distance;
        }

        ConstructionRun run = new ConstructionRun(
            RoadConstructionType.TUNNEL,
            0,
            segments.size(),
            0.0,
            station,
            10,
            10.0);
        ConstructionDetection detection = new ConstructionDetection(
            List.of(),
            List.of(),
            constructionTypes,
            distances,
            List.of(run));

        TunnelStyle style = new TunnelStyle();
        style.setShape(scenario.shape());
        style.setClearHeight(clearHeight);
        style.setSideClearance(1);
        style.setLiningThickness(1);
        style.setLightingMode(TunnelLightingMode.WALL_AND_CEILING);
        style.setLightMaterial("minecraft:sea_lantern");
        style.setLightSpacing(6);
        style.ensureConfigDefaults();
        ResolvedTunnelStyle tunnelStyle = ResolvedTunnelStyle.from(style);

        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 72;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 72;
            }
        };

        RoadTerrainClearanceUtils.BlockColumnResolver columnResolver = new RoadTerrainClearanceUtils.BlockColumnResolver() {
            @Override
            public int worldX(Vec2d point) {
                return (int) Math.round(point.x);
            }

            @Override
            public int worldZ(Vec2d point) {
                return (int) Math.round(point.y);
            }
        };

        TunnelStructureGenerator.generate(
            new TunnelStructureGenerator.Host() {
                @Override
                public String resolveBlockId(String material) {
                    return material;
                }

                @Override
                public int snapEndpointElevation(Vec2d center, int targetY) {
                    return targetY;
                }
            },
            solids,
            null,
            tunnelStyle,
            segments,
            heightInfos,
            crossSections,
            terrain,
            1.0,
            constructionTypes,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            detection,
            columnResolver);

        RoadGenerationResult result = new RoadGenerationResult(station);
        RoadVoxelRasterizer.flushEdgeSolids(
            result,
            solids,
            null,
            com.plot.infrastructure.event.block.BlockProjectionHandler.getInstance());
        return result;
    }

    private static List<PathSegment> rightAnglePath() {
        return List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(24, 0)),
            new PathSegment(new Vec2d(24, 0), new Vec2d(24, 24)));
    }

    private static List<PathSegment> fortyFiveDegreePath() {
        return List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0)),
            new PathSegment(new Vec2d(20, 0), new Vec2d(34, 14)));
    }

    private static List<PathSegment> sCurvePath() {
        return List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(16, 0)),
            new PathSegment(new Vec2d(16, 0), new Vec2d(24, 8)),
            new PathSegment(new Vec2d(24, 8), new Vec2d(32, 8)));
    }
}
