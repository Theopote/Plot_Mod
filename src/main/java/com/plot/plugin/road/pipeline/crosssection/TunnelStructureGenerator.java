package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadRoadbedGradingUtils;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.RoadEdgeBuildMetrics;
import com.plot.plugin.road.pipeline.construction.ConstructionDetection;
import com.plot.plugin.road.pipeline.construction.ConstructionRun;
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
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelPortalPlanner;
import com.plot.plugin.road.tunnel.TunnelProfile;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * Generates tunnel cavities, lining, lighting, accent rings, and portal frames.
 */
public final class TunnelStructureGenerator {
    private static final double EPSILON = 1e-6;
    private static final int PORTAL_FRAME_DEPTH_BLOCKS = 2;

    private TunnelStructureGenerator() {
    }

    public interface Host {
        String resolveBlockId(String material);

        int snapEndpointElevation(Vec2d center, int targetY);
    }

    public static void generate(
            Host host,
            RoadSolidModel solids,
            RoadEdgeBuildMetrics metrics,
            ResolvedTunnelStyle tunnelStyle,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            List<RoadConstructionType> constructionTypes,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings,
            ConstructionDetection detection,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver) {
        if (tunnelStyle == null
                || constructionTypes.stream().noneMatch(type -> type == RoadConstructionType.TUNNEL)) {
            return;
        }
        List<TunnelPortalPlanner.PortalStation> portals = planPortalStations(
            detection, segments, heightInfos, constructionTypes, crossSections, terrain, unitsPerBlock,
            tunnelStyle, designElevation, buildProfile, profileWaterCrossings, columnResolver, host);

        RoadRoadbedGradingUtils.GradingVolumes total = RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        boolean chainForward = crossSections.samplingOriented().forward();
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(constructionTypes, i);
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
                RoadConstructionType sampleType = effectiveConstructionType(
                    type,
                    profileWaterCrossings,
                    worldStation);
                if (sampleType != RoadConstructionType.TUNNEL) {
                    continue;
                }
                boolean portalFrame = TunnelPortalPlanner.findPortalNear(portals, worldStation) != null;
                double structureStation = structureStationAt(
                    tunnelRunContaining(detection != null ? detection.runs() : null, worldStation),
                    worldStation);
                boolean accentRing = tunnelStyle.accentRings()
                    && !tunnelStyle.accentMaterial().isBlank()
                    && hitsStructureSpacing(structureStation, tunnelStyle.accentSpacing());
                String liningMaterial = accentRing
                    ? tunnelStyle.accentMaterial()
                    : tunnelStyle.liningMaterial();
                total = total.add(placeCrossSection(
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    host.resolveBlockId(liningMaterial),
                    terrain,
                    columnResolver,
                    unitsPerBlock,
                    portalFrame));
                placeLighting(
                    host,
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    structureStation,
                    columnResolver,
                    unitsPerBlock);
            }
            geometryLocalBase += segment.distance;
        }
        total = total.add(placePortals(
            host,
            solids,
            tunnelStyle,
            segments,
            heightInfos,
            crossSections,
            terrain,
            unitsPerBlock,
            designElevation,
            buildProfile,
            columnResolver,
            portals));
        if (metrics != null) {
            metrics.cutVolume += total.cutVolume();
        }
    }

    static RoadRoadbedGradingUtils.GradingVolumes placeCrossSection(
            RoadSolidModel solids,
            ResolvedTunnelStyle tunnelStyle,
            Vec2d center,
            Vec2d leftNormal,
            int roadEnvelopeWidth,
            int roadY,
            String liningMaterialId,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock,
            boolean portalFrame) {
        if (solids == null || center == null || leftNormal == null || roadEnvelopeWidth <= 0
                || terrain == null || columnResolver == null
                || liningMaterialId == null || liningMaterialId.isBlank()
                || tunnelStyle == null) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            roadEnvelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            tunnelStyle.liningThickness(),
            roadY,
            portalFrame);
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        final int[] cutHolder = {0};
        mask.forEach((lateral, y, kind) -> {
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            if (terrain.isSolidBlock(worldX, y, worldZ)) {
                cutHolder[0]++;
            }
            if (kind == TunnelCrossSectionMask.CellKind.AIR) {
                solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air");
            } else if (kind == TunnelCrossSectionMask.CellKind.LINING) {
                solids.add(point, y, RoadSolidLayer.TUNNEL, liningMaterialId);
            }
        });
        return new RoadRoadbedGradingUtils.GradingVolumes(cutHolder[0], 0);
    }

    /**
     * Places lighting using tunnel-run-local structure station (0 at run start), not road chainage.
     */
    static void placeLighting(
            Host host,
            RoadSolidModel solids,
            ResolvedTunnelStyle tunnelStyle,
            Vec2d center,
            Vec2d leftNormal,
            int roadEnvelopeWidth,
            int roadY,
            double structureStation,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock) {
        TunnelLightingMode mode = tunnelStyle.lightingMode();
        if (mode == TunnelLightingMode.NONE || tunnelStyle.lightSpacing() <= 0) {
            return;
        }
        if (!hitsStructureSpacing(structureStation, tunnelStyle.lightSpacing())) {
            return;
        }
        String lightMaterial = host.resolveBlockId(tunnelStyle.lightMaterial());
        if (lightMaterial == null || lightMaterial.isBlank()) {
            return;
        }
        int side = tunnelStyle.sideClearance();
        int cavityWidth = roadEnvelopeWidth + side * 2;
        int cavityMin = com.plot.plugin.road.RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int halfCavityWidth = cavityWidth / 2;
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int wallLightY = roadY + 2;
        if (mode.placesWallLights()) {
            placeLightBlock(solids, center, normal, cavityMin, wallLightY, scale, columnResolver, lightMaterial);
            placeLightBlock(solids, center, normal, cavityMax, wallLightY, scale, columnResolver, lightMaterial);
        }
        if (mode.placesCeilingLights()) {
            int ceilingY = TunnelProfile.ceilingY(
                tunnelStyle.shape(), 0, halfCavityWidth, roadY, tunnelStyle.clearHeight());
            placeLightBlock(solids, center, normal, 0, ceilingY, scale, columnResolver, lightMaterial);
        }
    }

    private static void placeLightBlock(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d normal,
            int lateral,
            int y,
            double scale,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            String lightMaterial) {
        Vec2d point = center.add(normal.multiply(lateral * scale));
        solids.add(point, y, RoadSolidLayer.TUNNEL, lightMaterial);
    }

    private static List<TunnelPortalPlanner.PortalStation> planPortalStations(
            ConstructionDetection detection,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            ResolvedTunnelStyle tunnelStyle,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            Host host) {
        if (detection == null || detection.runs() == null) {
            return List.of();
        }
        return TunnelPortalPlanner.planPortals(
            detection.runs(),
            segments,
            heightInfos,
            constructionTypes,
            crossSections,
            terrain,
            unitsPerBlock,
            tunnelStyle,
            designElevation,
            buildProfile,
            profileWaterCrossings,
            columnResolver,
            host::snapEndpointElevation);
    }

    /**
     * Places thickened portal frames extending into the tunnel interior.
     * Entry frames grow along +station; exit frames grow along -station.
     */
    static RoadRoadbedGradingUtils.GradingVolumes placePortals(
            Host host,
            RoadSolidModel solids,
            ResolvedTunnelStyle tunnelStyle,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            List<TunnelPortalPlanner.PortalStation> portals) {
        if (portals == null || portals.isEmpty()) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        RoadRoadbedGradingUtils.GradingVolumes total = RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        boolean chainForward = crossSections.samplingOriented().forward();
        for (TunnelPortalPlanner.PortalStation portal : portals) {
            BridgeStructureGenerator.StationLocation location =
                BridgeStructureGenerator.locateStationOnPath(segments, scale, portal.worldStation() * scale);
            if (location == null) {
                continue;
            }
            double direction = portal.entry() ? 1.0 : -1.0;
            for (int depth = 0; depth < PORTAL_FRAME_DEPTH_BLOCKS; depth++) {
                double geometryLocal = location.geometryLocal() + direction * depth * scale;
                if (geometryLocal < -EPSILON) {
                    continue;
                }
                BridgeStructureGenerator.StationLocation depthLocation =
                    BridgeStructureGenerator.locateStationOnPath(segments, scale, geometryLocal);
                if (depthLocation == null || depthLocation.segmentIndex() >= segments.size()) {
                    continue;
                }
                PathSegment segment = segments.get(depthLocation.segmentIndex());
                SegmentHeightInfo info = depthLocation.segmentIndex() < heightInfos.size()
                    ? heightInfos.get(depthLocation.segmentIndex())
                    : heightInfos.getLast();
                Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
                Vec2d center = segment.start.lerp(segment.end, depthLocation.t());
                int targetY = DesignElevationSource.resolveTargetElevation(
                    designElevation,
                    buildProfile,
                    info,
                    depthLocation.geometryLocal(),
                    depthLocation.t(),
                    geometryLocal / scale);
                targetY = host.snapEndpointElevation(center, targetY);
                double chainage = crossSections.chainageAtGeometryLocal(depthLocation.geometryLocal());
                ResolvedCrossSection crossSection = crossSections.resolve(chainage);
                int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
                if (envelopeWidth <= 0) {
                    continue;
                }
                total = total.add(placeCrossSection(
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    host.resolveBlockId(tunnelStyle.liningMaterial()),
                    terrain,
                    columnResolver,
                    scale,
                    true));
            }
        }
        return total;
    }

    private static RoadConstructionType effectiveConstructionType(
            RoadConstructionType segmentType,
            List<WaterCrossing> profileWaterCrossings,
            double worldStation) {
        if (WaterCrossingConstructionResolver.isCausewayFillStation(profileWaterCrossings, worldStation)) {
            return RoadConstructionType.FILL;
        }
        return segmentType;
    }

    static ConstructionRun tunnelRunContaining(List<ConstructionRun> runs, double worldStation) {
        if (runs == null || runs.isEmpty()) {
            return null;
        }
        for (ConstructionRun run : runs) {
            if (run.type() != RoadConstructionType.TUNNEL) {
                continue;
            }
            if (worldStation + EPSILON >= run.startStation() && worldStation - EPSILON <= run.endStation()) {
                return run;
            }
        }
        return null;
    }

    static double structureStationAt(ConstructionRun run, double worldStation) {
        if (run == null) {
            return worldStation;
        }
        return Math.max(0.0, worldStation - run.startStation());
    }

    static boolean hitsStructureSpacing(double structureStation, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        return Math.floorMod((int) Math.round(structureStation), spacing) == 0;
    }
}
