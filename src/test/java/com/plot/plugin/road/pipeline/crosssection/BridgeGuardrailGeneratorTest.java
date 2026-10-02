package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
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
        double edgeOffset = RoadDimensionUtils.halfExtentFromCenter(section.carriagewayWidth);

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
            BridgeGuardrailGenerator.DEFAULT_MATERIAL);

        long guardrailsOnBridgeHalf = solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.GUARDRAIL)
            .filter(primitive -> primitive.planPoint().x >= 10.0)
            .count();
        long guardrailsOnCausewayHalf = solids.primitives().stream()
            .filter(primitive -> primitive.layer() == RoadSolidLayer.GUARDRAIL)
            .filter(primitive -> primitive.planPoint().x < 10.0)
            .count();

        assertTrue(guardrailsOnBridgeHalf > 0);
        assertEquals(0, guardrailsOnCausewayHalf);
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
            "minecraft:dark_oak_fence");

        assertTrue(solids.primitives().stream().allMatch(primitive ->
            primitive.layer() != RoadSolidLayer.GUARDRAIL
                || "minecraft:dark_oak_fence".equals(primitive.materialId())));
    }
}
