package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.construction.WaterCrossingConstructionResolver;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;

import java.util.List;

/**
 * Places simple bridge guardrails along bridge deck edges (Minecraft fence style).
 */
public final class BridgeGuardrailGenerator {

    static final String DEFAULT_MATERIAL = ResolvedBridgeStructure.DEFAULT_GUARDRAIL_MATERIAL;

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
            List<WaterCrossing> profileWaterCrossings,
            String guardrailMaterial) {
        generate(
            host,
            solids,
            constructionTypes,
            segments,
            heightInfos,
            crossSections,
            unitsPerBlock,
            designElevation,
            buildProfile,
            profileWaterCrossings,
            guardrailMaterial,
            RoadParameterLimits.DEFAULT_BRIDGE_GUARDRAIL_HEIGHT_BLOCKS);
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
            List<WaterCrossing> profileWaterCrossings,
            String guardrailMaterial,
            int guardrailHeightBlocks) {
        if (constructionTypes.stream().noneMatch(RoadConstructionType::isBridgeFamily)) {
            return;
        }
        String blockId = host.resolveBlockId(
            guardrailMaterial == null || guardrailMaterial.isBlank()
                ? DEFAULT_MATERIAL
                : guardrailMaterial);
        int height = RoadParameterLimits.clampBridgeGuardrailHeightBlocks(guardrailHeightBlocks);
        boolean chainForward = crossSections.samplingOriented().forward();
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            if (!RoadConstructionClassifier.constructionTypeAt(constructionTypes, i).isBridgeFamily()) {
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
                double worldStation = geometryLocal / scale;
                if (!WaterCrossingConstructionResolver.isBridgeDeckStation(
                        profileWaterCrossings, worldStation)) {
                    continue;
                }
                var resolvedY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    buildProfile,
                    info,
                    geometryLocal,
                    t,
                    worldStation);
                if (resolvedY.isEmpty()) {
                    continue;
                }
                int deckY = host.snapEndpointElevation(center, resolvedY.getAsInt());
                double chainage = crossSections.chainageAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                placeGuardrails(
                    solids,
                    center,
                    normal,
                    deckY,
                    crossSection,
                    blockId,
                    scale,
                    height);
            }
            geometryLocalBase += segment.distance;
        }
    }

    private static void placeGuardrails(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int deckY,
            ResolvedCrossSection crossSection,
            String blockId,
            double scale,
            int heightBlocks) {
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        double edgeOffset = RoadCorridorWidth.bridgeDeckHalfWidthBlocks(crossSection) * scale;
        Vec2d left = center.add(normal.multiply(edgeOffset));
        Vec2d right = center.subtract(normal.multiply(edgeOffset));
        for (int level = 1; level <= heightBlocks; level++) {
            int elevation = deckY + level;
            solids.add(left, elevation, RoadSolidLayer.GUARDRAIL, blockId);
            solids.add(right, elevation, RoadSolidLayer.GUARDRAIL, blockId);
        }
    }
}
