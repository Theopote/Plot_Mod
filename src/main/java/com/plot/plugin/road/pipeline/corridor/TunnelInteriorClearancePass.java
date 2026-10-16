package com.plot.plugin.road.pipeline.corridor;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * Clears native terrain inside tunnel cavity volumes without writing structure-only
 * clearance cells into {@link RoadCorridorPlan} (which would strip corridor batter).
 */
public final class TunnelInteriorClearancePass {
    private static final double EPSILON = 1e-6;

    private TunnelInteriorClearancePass() {
    }

    public static void apply(
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            CrossSectionBuildContext crossSections,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            TerrainSampler terrain,
            double unitsPerBlock,
            ResolvedTunnelStyle tunnelStyle,
            RoadCorridorPlanBuilder.PlanHost host,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            RoadEdgeBuildMetrics metrics) {
        if (solids == null || segments == null || heightInfos == null || constructionTypes == null
                || crossSections == null || terrain == null || tunnelStyle == null
                || host == null || columnResolver == null) {
            return;
        }
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        boolean chainForward = crossSections.samplingOriented().forward();
        double geometryLocalBase = 0.0;
        int cleared = 0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            RoadConstructionType segmentType =
                RoadConstructionClassifier.constructionTypeAt(constructionTypes, i);
            if (segmentType != RoadConstructionType.TUNNEL
                    && segmentType != RoadConstructionType.TUNNEL_PORTAL) {
                geometryLocalBase += segments.get(i).distance;
                continue;
            }
            boolean portalFrame = segmentType == RoadConstructionType.TUNNEL_PORTAL;
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                Vec2d center = segment.start.lerp(segment.end, t);
                double geometryLocal = geometryLocalBase + segment.distance * t;
                double worldStation = geometryLocal / scale;
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    buildProfile,
                    info,
                    geometryLocal,
                    t,
                    worldStation);
                targetY = host.snapEndpointElevation(center, targetY);
                double chainage = crossSections.chainageAtGeometryLocal(geometryLocal);
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
                if (envelopeWidth <= 0) {
                    continue;
                }
                cleared += clearCrossSection(
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    terrain,
                    columnResolver,
                    scale,
                    portalFrame);
            }
            geometryLocalBase += segment.distance;
        }
        if (metrics != null) {
            metrics.cutVolume += cleared;
        }
    }

    private static int clearCrossSection(
            RoadSolidModel solids,
            ResolvedTunnelStyle tunnelStyle,
            Vec2d center,
            Vec2d leftNormal,
            int roadEnvelopeWidth,
            int roadY,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double scale,
            boolean portalFrame) {
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            roadEnvelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            tunnelStyle.liningThickness(),
            roadY,
            portalFrame);
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int[] cleared = {0};
        mask.forEach((lateral, y, kind) -> {
            if (kind != TunnelCrossSectionMask.CellKind.AIR) {
                return;
            }
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                return;
            }
            if (!RoadCorridorClearancePass.hasObstruction(terrain, worldX, y, worldZ)) {
                return;
            }
            solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
            cleared[0]++;
        });
        return cleared[0];
    }
}
