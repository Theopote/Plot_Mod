package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.ConstructionRun;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadGenerationResult;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelContinuityTest {

    record Scenario(String name, List<PathSegment> segments, int roadWidth) {
        @Override
        public String toString() {
            return name + " width=" + roadWidth;
        }
    }

    static Stream<Arguments> tunnelScenarios() {
        List<Arguments> args = new ArrayList<>();
        for (int width : List.of(5, 9, 15)) {
            args.add(Arguments.of(new Scenario("right-angle", rightAnglePath(), width)));
        }
        args.add(Arguments.of(new Scenario("forty-five-degree", fortyFiveDegreePath(), 9)));
        args.add(Arguments.of(new Scenario("s-curve", sCurvePath(), 9)));
        return args.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tunnelScenarios")
    void tunnelGeometryIsContinuous(Scenario scenario) {
        RoadGenerationResult result = generateTunnel(scenario);

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

    private static RoadGenerationResult generateTunnel(Scenario scenario) {
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
            heightInfos.add(new SegmentHeightInfo(segment, 72, 72, 64, 64, 0.0));
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
        style.setShape(TunnelShape.RECTANGULAR);
        style.setClearHeight(5);
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
