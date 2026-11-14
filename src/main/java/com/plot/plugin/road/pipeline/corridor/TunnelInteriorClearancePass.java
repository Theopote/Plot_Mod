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
import com.plot.plugin.road.pipeline.crosssection.TunnelStructureGenerator;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
import com.plot.plugin.road.tunnel.TunnelStyle;
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
            // 与 TunnelStructureGenerator 对齐：对角线/急弯加密，锐折顶点邻域跳过，避免清障与衬砌覆盖不一致。
            boolean diagonal = Math.abs(leftNormal.x) > EPSILON && Math.abs(leftNormal.y) > EPSILON;
            boolean acuteIncoming = i > 0
                && TunnelStructureGenerator.isAcutePolylineTurn(segments.get(i - 1), segment);
            boolean acuteOutgoing = i + 1 < segments.size()
                && TunnelStructureGenerator.isAcutePolylineTurn(segment, segments.get(i + 1));
            PathSegment previous = i > 0 ? segments.get(i - 1) : null;
            double skipRadius = AcuteCornerSector.skipRadius(scale);
            boolean shortLeg = segment.distance + 1e-9 < 2.0 * skipRadius;
            if (acuteIncoming && previous != null) {
                cleared += clearAcuteCornerSector(
                    solids, tunnelStyle, previous, segment, chainForward,
                    geometryLocalBase, info, crossSections, designElevation, buildProfile,
                    terrain, columnResolver, scale, portalFrame);
            }
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale
                * (diagonal || acuteIncoming || acuteOutgoing ? 3 : 1)));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                double along = segment.distance * t;
                boolean nearIncoming = acuteIncoming && along < skipRadius;
                boolean nearOutgoing = acuteOutgoing && segment.distance - along < skipRadius;
                boolean midForced = shortLeg && (acuteIncoming || acuteOutgoing) && j == samples / 2;
                if ((nearIncoming || nearOutgoing) && !midForced) {
                    continue;
                }
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

    private static int clearAcuteCornerSector(
            RoadSolidModel solids,
            ResolvedTunnelStyle tunnelStyle,
            PathSegment incoming,
            PathSegment outgoing,
            boolean chainForward,
            double geometryLocalAtVertex,
            SegmentHeightInfo outgoingInfo,
            CrossSectionBuildContext crossSections,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double scale,
            boolean portalFrame) {
        Vec2d center = outgoing.start;
        Vec2d fromNormal = PathSegmentGeometry.chainLeftNormal(incoming, chainForward);
        Vec2d toNormal = PathSegmentGeometry.chainLeftNormal(outgoing, chainForward);
        double worldStation = geometryLocalAtVertex / scale;
        int targetY = DesignElevationSource.resolveTargetElevation(
            designElevation, buildProfile, outgoingInfo, geometryLocalAtVertex, 0.0, worldStation);
        double chainage = crossSections.chainageAtGeometryLocal(geometryLocalAtVertex);
        ResolvedCrossSection crossSection = crossSections.resolve(chainage);
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        if (envelopeWidth <= 0) {
            return 0;
        }
        // Clearance may fan more freely (air-only); cover the triangular gore.
        int[] cleared = {0};
        AcuteCornerSector.forEachTunnelNormal(fromNormal, toNormal, normal ->
            cleared[0] += clearCrossSection(
                solids,
                tunnelStyle,
                center,
                normal,
                envelopeWidth,
                targetY,
                terrain,
                columnResolver,
                scale,
                portalFrame));
        return cleared[0];
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
        int lining = portalFrame
            ? Math.min(TunnelStyle.MAX_LINING_THICKNESS, tunnelStyle.liningThickness() + 1)
            : tunnelStyle.liningThickness();
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            roadEnvelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            lining,
            roadY,
            portalFrame);
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        String liningMaterialId = tunnelStyle.liningMaterial();
        if (liningMaterialId == null || liningMaterialId.isBlank()) {
            liningMaterialId = "minecraft:stone_bricks";
        }
        // Phase A — 连续衬砌壳（含沙子壳格）。只掏 AIR 会让沙衬悬空塌到路面。
        final String shellId = liningMaterialId;
        int[] cleared = {0};
        mask.forEach((lateral, y, kind) -> {
            if (kind != TunnelCrossSectionMask.CellKind.LINING) {
                return;
            }
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (!terrain.isChunkLoaded(worldX, worldZ)) {
                return;
            }
            boolean gravityShell = terrain.isGravityAffectedBlock(worldX, y, worldZ);
            if (!TunnelStructureGenerator.shouldPlaceLining(terrain, worldX, y, worldZ, point)
                    && !gravityShell) {
                return;
            }
            if (y >= terrain.sampleSurfaceY(point) && !gravityShell) {
                return;
            }
            solids.add(
                point,
                y,
                RoadSolidLayer.TUNNEL,
                shellId,
                RoadSolidMaterialPriority.STRUCTURAL);
            cleared[0]++;
        });
        // Phase B — stabilize gravel/sand above the roof before cavity air removes support.
        TunnelRoofGapSealer.sealAboveRoofShell(
            solids,
            mask,
            tunnelStyle.shape(),
            roadY,
            tunnelStyle.clearHeight(),
            roadEnvelopeWidth,
            tunnelStyle.sideClearance(),
            lining,
            center,
            normal,
            scale,
            columnResolver,
            terrain,
            liningMaterialId,
            "minecraft:stone");
        // Phase C — excavate cavity only.
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
            solids.add(
                point,
                y,
                RoadSolidLayer.TUNNEL,
                "minecraft:air",
                RoadSolidMaterialPriority.AIR,
                ClearancePurpose.TUNNEL_INTERIOR);
            cleared[0]++;
        });
        return cleared[0];
    }
}
