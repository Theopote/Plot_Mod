package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeGuardrailGeneratorTest {

    private static final BridgeGuardrailGenerator.Host TEST_HOST = new BridgeGuardrailGenerator.Host() {
        @Override
        public String resolveBlockId(String material) {
            return material;
        }

        @Override
        public int snapEndpointElevation(Vec2d center, int targetY) {
            return targetY;
        }
    };

    @Test
    void placesGuardrailsAlongBridgeDeckEdges() {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 50, 50, 64, 64, 64, 64, 64, 64, 10.0));
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-guardrail"));
        double edgeOffset = RoadCorridorWidth.bridgeDeckHalfWidthBlocks(section);

        BridgeGuardrailGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            BridgeGuardrailGenerator.DEFAULT_MATERIAL);

        assertTrue(solids.count(RoadSolidLayer.GUARDRAIL) > 0);
        assertTrue(solids.primitives().stream().anyMatch(primitive ->
            primitive.layer() == RoadSolidLayer.GUARDRAIL
                && primitive.elevation() == 65
                && Math.abs(primitive.planPoint().y - edgeOffset) < 0.25
                && "minecraft:oak_fence".equals(primitive.materialId())));
        assertTrue(solids.primitives().stream().anyMatch(primitive ->
            primitive.layer() == RoadSolidLayer.GUARDRAIL
                && primitive.elevation() == 65
                && Math.abs(primitive.planPoint().y + edgeOffset) < 0.25
                && "minecraft:oak_fence".equals(primitive.materialId())));
    }

    @Test
    void guardrailSitsOutsideSidewalkWhenSidewalkEnabled() {
        assertGuardrailUsesBridgeDeckEdge(config -> {
            config.setIncludeShoulder(false);
            config.setIncludeSidewalk(true);
            config.setSidewalkWidth(2);
        });
    }

    @Test
    void guardrailSitsOutsideBikeLaneWhenBikeLaneEnabled() {
        assertGuardrailUsesBridgeDeckEdge(config -> {
            config.setIncludeShoulder(false);
            config.setIncludeBikeLane(true);
            config.setBikeLaneWidth(2);
        });
    }

    @Test
    void guardrailSitsOutsideShoulderWhenShoulderEnabled() {
        assertGuardrailUsesBridgeDeckEdge(config -> {
            config.setIncludeShoulder(true);
            config.setShoulderWidth(1);
        });
    }

    @Test
    void skipsNonBridgeSegments() {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(
            new PathSegment(new Vec2d(0, 0), new Vec2d(10, 0)),
            new PathSegment(new Vec2d(10, 0), new Vec2d(20, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(segments.get(0), 50, 50, 64, 64, 64, 64, 64, 64, 10.0),
            new SegmentHeightInfo(segments.get(1), 50, 50, 64, 64, 64, 64, 64, 64, 10.0));
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-guardrail"));

        BridgeGuardrailGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.FILL, RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            BridgeGuardrailGenerator.DEFAULT_MATERIAL);

        long guardrailsOnBridgeHalf = solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.GUARDRAIL)
            .filter(primitive -> primitive.planPoint().x >= 10.0)
            .count();
        long guardrailsOnFillHalf = solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.GUARDRAIL)
            .filter(primitive -> primitive.planPoint().x < 10.0)
            .count();

        assertTrue(guardrailsOnBridgeHalf > 0);
        assertEquals(0, guardrailsOnFillHalf);
    }

    @Test
    void guardrailStaysWithinCrossingZoneOnPartialBridgeSegment() {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(20, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 50, 50, 64, 64, 64, 64, 64, 64, 20.0));
        WaterCrossing bridge = new WaterCrossing(
            0.0, 4.0, 8.0, 12.0,
            5.0, 7.0, 4.0, 2.0, 3.0,
            50, 50, 51, WaterCrossingStrategy.BRIDGE);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-guardrail"));

        BridgeGuardrailGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(bridge),
            BridgeGuardrailGenerator.DEFAULT_MATERIAL);

        assertTrue(solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.GUARDRAIL)
            .allMatch(primitive ->
                primitive.planPoint().x >= 3.5 && primitive.planPoint().x <= 8.5));
        assertEquals(0, solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.GUARDRAIL)
            .filter(primitive -> primitive.planPoint().x < 3.0 || primitive.planPoint().x > 9.0)
            .count());
    }

    @Test
    void resolvesCustomMaterial() {
        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(4, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 50, 50, 64, 64, 64, 64, 64, 64, 4.0));

        BridgeGuardrailGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(ResolvedCrossSection.fromConfig(new RoadSystemConfig("bridge-guardrail"))),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            "minecraft:dark_oak_fence");

        assertTrue(solids.primitives().stream().allMatch(primitive ->
            primitive.layer() != RoadSolidLayer.GUARDRAIL
                || "minecraft:dark_oak_fence".equals(primitive.materialId())));
    }

    private static void assertGuardrailUsesBridgeDeckEdge(java.util.function.Consumer<RoadSystemConfig> configure) {
        RoadSystemConfig config = new RoadSystemConfig("bridge-guardrail");
        config.setRoadWidth(5);
        config.setIncludeDrainage(true);
        configure.accept(config);
        ResolvedCrossSection section = ResolvedCrossSection.fromConfig(config);
        double deckEdge = RoadCorridorWidth.bridgeDeckHalfWidthBlocks(section);
        double carriagewayEdge = section.carriagewayHalfWidth();

        RoadSolidModel solids = new RoadSolidModel();
        List<PathSegment> segments = List.of(new PathSegment(new Vec2d(0, 0), new Vec2d(6, 0)));
        List<SegmentHeightInfo> heightInfos = List.of(
            new SegmentHeightInfo(
                segments.getFirst(), 50, 50, 64, 64, 64, 64, 64, 64, 6.0));

        BridgeGuardrailGenerator.generate(
            TEST_HOST,
            solids,
            List.of(RoadConstructionType.BRIDGE),
            segments,
            heightInfos,
            CrossSectionBuildContext.fixed(section),
            1.0,
            DesignElevationSource.inactive(),
            BuildHeightProfile.inactive(),
            List.of(),
            BridgeGuardrailGenerator.DEFAULT_MATERIAL);

        assertTrue(deckEdge > carriagewayEdge + 0.25,
            "outer band should push guardrail beyond carriageway edge");
        assertTrue(solids.primitives().stream().anyMatch(primitive ->
            primitive.layer() == RoadSolidLayer.GUARDRAIL
                && Math.abs(primitive.planPoint().y - deckEdge) < 0.25));
        assertTrue(solids.primitives().stream().noneMatch(primitive ->
            primitive.layer() == RoadSolidLayer.GUARDRAIL
                && Math.abs(Math.abs(primitive.planPoint().y) - carriagewayEdge) < 0.15));
    }
}
