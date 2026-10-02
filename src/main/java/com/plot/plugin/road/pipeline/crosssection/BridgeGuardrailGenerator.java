package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;

import java.util.List;

/**
 * Places simple bridge guardrails along bridge deck edges (Minecraft fence style).
 */
public final class BridgeGuardrailGenerator {

    static final String DEFAULT_MATERIAL = "minecraft:oak_fence";

    private BridgeGuardrailGenerator() {
    }

    interface Host {
        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);
    }

    static void generate(
            Host host,
            RoadSolidModel solids,
            List<RoadConstructionType> constructionTypes,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            String guardrailMaterial) {
        if (constructionTypes.stream().noneMatch(type -> type == RoadConstructionType.BRIDGE)) {
            return;
        }
        String blockId = host.resolveBlockId(
            guardrailMaterial == null || guardrailMaterial.isBlank()
                ? DEFAULT_MATERIAL
                : guardrailMaterial);
        boolean chainForward = crossSections.samplingOriented().forward();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            if (RoadConstructionClassifier.constructionTypeAt(constructionTypes, i) != RoadConstructionType.BRIDGE) {
                geometryLocalBase += segments.get(i).distance;
                continue;
            }
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d normal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                int deckY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    buildProfile,
                    info,
                    geometryLocal,
                    t,
                    geometryLocal / scale);
                deckY = host.snapEndpointElevation(center, deckY);
                double chainage = crossSections.chainageAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                placeGuardrails(
                    solids,
                    center,
                    normal,
                    deckY,
                    crossSection.carriagewayWidth,
                    blockId,
                    scale);
            }
            geometryLocalBase += segment.distance;
        }
    }

    private static void placeGuardrails(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int deckY,
            int carriagewayWidth,
            String blockId,
            double scale) {
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        double edgeOffset = RoadDimensionUtils.halfExtentFromCenter(Math.max(1, carriagewayWidth)) * scale;
        int elevation = deckY + 1;
        solids.add(center.add(normal.multiply(edgeOffset)), elevation, RoadSolidLayer.GUARDRAIL, blockId);
        solids.add(center.subtract(normal.multiply(edgeOffset)), elevation, RoadSolidLayer.GUARDRAIL, blockId);
    }
}
