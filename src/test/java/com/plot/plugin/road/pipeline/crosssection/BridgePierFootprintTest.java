package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.model.section.BridgePierStyle;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.corridor.GradeSeparationBuildContext;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgePierFootprintTest {

    private static final BridgeStructureGenerator.Host HOST = new BridgeStructureGenerator.Host() {
        @Override
        public String resolveBlockId(String material) {
            return material != null && !material.isBlank() ? material : "minecraft:stone";
        }

        @Override
        public int snapEndpointElevation(Vec2d center, int targetY) {
            return targetY;
        }
    };

    @Test
    void singlePierDiameterThreeSpansThreeLateralColumns() {
        Set<Long> laterals = generateInteriorLaterals(BridgePierStyle.SINGLE, 3, 1, 7);
        assertEquals(Set.of(-10L, 0L, 10L), laterals);
    }

    @Test
    void doublePierIsSymmetricAboutCenterline() {
        Set<Long> laterals = generateInteriorLaterals(BridgePierStyle.DOUBLE, 1, 2, 7);
        assertEquals(Set.of(-20L, 20L), laterals);
    }

    private static Set<Long> generateInteriorLaterals(
            BridgePierStyle style,
            int diameter,
            int offset,
            int roadWidth) {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segments.getFirst(), 50, 50, 69, 69, 64, 64, 64, 64, 20.0));
        WaterCrossing bridge = new WaterCrossing(
            0.0, 5.0, 15.0, 20.0,
            8.0, 12.0, 10.0, 10.0, 14.0,
            68, 68, 69, WaterCrossingStrategy.BRIDGE);
        RoadSystemConfig config = new RoadSystemConfig("pier-footprint");
        config.setRoadWidth(roadWidth);
        config.setIncludeShoulder(false);
        config.setIncludeSidewalk(false);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);

        BridgeStructureGenerator.generate(
            HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            flatBed(50),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(bridge),
            GradeSeparationBuildContext.NONE,
            6.0,
            BridgeStructureGenerator.PIER_MATERIAL,
            style,
            diameter,
            offset);

        Set<Long> laterals = solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.BRIDGE)
            .filter(primitive -> primitive.elevation() >= 51 && primitive.elevation() <= 62)
            .filter(primitive -> Math.abs(primitive.planPoint().x - 10.0) < 1.0)
            .map(primitive -> Math.round(primitive.planPoint().y * 10.0))
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        assertTrue(laterals.size() >= 1);
        return laterals;
    }

    private static TerrainSampler flatBed(int bedY) {
        return new TerrainSampler() {
            @Override
            public int sampleSurfaceY(Vec2d point) {
                return bedY;
            }

            @Override
            public boolean isSolidBlock(int x, int y, int z) {
                return y <= bedY;
            }
        };
    }
}
