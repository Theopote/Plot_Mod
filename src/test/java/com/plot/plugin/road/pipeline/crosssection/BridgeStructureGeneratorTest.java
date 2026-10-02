package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeStructureGeneratorTest {

    private static final BridgeStructureGenerator.Host TEST_HOST = new BridgeStructureGenerator.Host() {
        @Override
        public String resolveBlockId(String material) {
            return "material.plot.stone";
        }

        @Override
        public int snapEndpointElevation(Vec2d center, int targetY) {
            return targetY;
        }
    };

    @Test
    void plansAbutmentsAtCrossingBoundaries() {
        WaterCrossing bridge = new WaterCrossing(
            0.0, 5.0, 25.0, 30.0,
            10.0, 20.0, 20.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(30, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 68, 68, 69, 69, 70, 70, 70, 70, 30.0));
        List<RoadConstructionType> types = List.of(RoadConstructionType.BRIDGE);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-structure"));

        List<BridgeStructureGenerator.StructureStation> stations =
            BridgeStructureGenerator.planStructureStations(
                types,
                segments,
                heightInfos,
                CrossSectionBuildContext.fixed(section),
                1.0,
                List.of(bridge));

        assertTrue(stations.stream().anyMatch(station ->
            station.kind() == BridgeStructureGenerator.StructureKind.ABUTMENT
                && Math.abs(station.worldStation() - 5.0) < 1e-6));
        assertTrue(stations.stream().anyMatch(station ->
            station.kind() == BridgeStructureGenerator.StructureKind.ABUTMENT
                && Math.abs(station.worldStation() - 25.0) < 1e-6));
    }

    @Test
    void generatesWiderAbutmentThanInteriorPier() {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(30, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 50, 50, 69, 69, 64, 64, 64, 64, 30.0));
        WaterCrossing bridge = new WaterCrossing(
            0.0, 5.0, 25.0, 30.0,
            10.0, 20.0, 20.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-structure"));
        TerrainSampler terrain = new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return 50;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= 50;
            }
        };

        BridgeStructureGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            terrain,
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(bridge));

        long abutmentCapWidth = solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.BRIDGE)
            .filter(primitive -> primitive.elevation() == 63)
            .map(primitive -> Math.round(primitive.planPoint().x * 10.0))
            .distinct()
            .count();
        assertTrue(abutmentCapWidth >= 3, "abutment cap should span the road envelope");
    }

    @Test
    void longBridgePierSpacingRemainsUniformAcrossPathSegments() {
        WaterCrossing longBridge = new WaterCrossing(
            0.0, 0.0, 60.0, 66.0,
            0.0, 60.0, 60.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.LONG_BRIDGE);
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(7, 0)),
            new PathSegment(new Vec2d(7, 0), new Vec2d(25, 0)),
            new PathSegment(new Vec2d(25, 0), new Vec2d(60, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segments.get(0), 50, 50, 69, 69, 64, 64, 64, 64, 7.0),
            new SegmentHeightInfo(segments.get(1), 50, 50, 69, 69, 64, 64, 64, 64, 18.0),
            new SegmentHeightInfo(segments.get(2), 50, 50, 69, 69, 64, 64, 64, 64, 35.0));
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-structure"));

        List<BridgeStructureGenerator.StructureStation> stations =
            BridgeStructureGenerator.planStructureStations(
                List.of(
                    RoadConstructionType.BRIDGE,
                    RoadConstructionType.BRIDGE,
                    RoadConstructionType.BRIDGE),
                segments,
                heightInfos,
                CrossSectionBuildContext.fixed(section),
                1.0,
                List.of(longBridge));

        List<Double> pierStations = stations.stream()
            .filter(station -> station.kind() == BridgeStructureGenerator.StructureKind.INTERIOR_PIER)
            .map(BridgeStructureGenerator.StructureStation::worldStation)
            .sorted()
            .collect(Collectors.toList());

        assertEquals(4, pierStations.size(), "long bridge span should produce evenly spaced interior piers");
        for (int i = 1; i < pierStations.size(); i++) {
            double spacing = pierStations.get(i) - pierStations.get(i - 1);
            assertEquals(12.0, spacing, 0.25, "pier spacing must stay uniform across path segment boundaries");
        }
    }

    @Test
    void interiorPierIncludesCapCourse() {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(12, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 61, 61, null, null, 64, 64, 64, 64, 12.0));
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-structure"));

        BridgeStructureGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            new TerrainSampler() {
                @Override
                public int sampleSurfaceY(Vec2d point) {
                    return 61;
                }

                @Override
                public boolean isSolidBlock(int x, int y, int z) {
                    return y <= 61;
                }
            },
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of());

        assertTrue(solids.primitives().stream().anyMatch(
            primitive -> primitive.layer() == RoadSolidLayer.BRIDGE && primitive.elevation() == 63),
            "interior pier should include a deck cap course");
    }
}
