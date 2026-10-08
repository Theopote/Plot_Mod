package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
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
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelProfile;
import com.plot.plugin.road.tunnel.TunnelShape;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.core.terrain.TerrainSampler;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates tunnel cavities, lining, lighting, accent rings, and simple portals.
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
            TunnelStyle tunnelStyle,
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
        RoadRoadbedGradingUtils.GradingVolumes total = RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        boolean chainForward = crossSections.samplingOriented().forward();
        double geometryLocalBase = 0.0;
        for (int i = 0; i < segments.size() && i < heightInfos.size(); i++) {
            RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(constructionTypes, i);
            PathSegment segment = segments.get(i);
            SegmentHeightInfo info = heightInfos.get(i);
            Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(segment, chainForward);
            Vec2d forward = segment.end.subtract(segment.start);
            if (forward.lengthSquared() > EPSILON) {
                forward = forward.normalize();
            }
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
                boolean accentRing = tunnelStyle.isAccentRings()
                    && !tunnelStyle.getAccentMaterial().isBlank()
                    && tunnelStyle.getAccentSpacing() > 0
                    && Math.floorMod((int) Math.round(chainage), tunnelStyle.getAccentSpacing()) == 0;
                String liningMaterial = accentRing
                    ? tunnelStyle.getAccentMaterial()
                    : tunnelStyle.getLiningMaterial();
                total = total.add(placeCrossSection(
                    host,
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
                    false));
                placeLighting(
                    host,
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    chainage,
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
            constructionTypes,
            designElevation,
            buildProfile,
            profileWaterCrossings,
            detection,
            columnResolver));
        if (metrics != null) {
            metrics.cutVolume += total.cutVolume();
        }
    }

    static RoadRoadbedGradingUtils.GradingVolumes placeCrossSection(
            Host host,
            RoadSolidModel solids,
            TunnelStyle tunnelStyle,
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
        int clearance = tunnelStyle.getClearHeight();
        int side = tunnelStyle.getSideClearance();
        int lining = portalFrame
            ? Math.min(TunnelStyle.MAX_LINING_THICKNESS, tunnelStyle.getLiningThickness() + 1)
            : tunnelStyle.getLiningThickness();
        int cavityWidth = roadEnvelopeWidth + side * 2;
        int outerWidth = cavityWidth + lining * 2;
        int roadMin = RoadDimensionUtils.minLateralOffset(roadEnvelopeWidth);
        int roadMax = RoadDimensionUtils.maxLateralOffset(roadEnvelopeWidth);
        int cavityMin = RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int outerMin = RoadDimensionUtils.minLateralOffset(outerWidth);
        int outerMax = RoadDimensionUtils.maxLateralOffset(outerWidth);
        int halfCavityWidth = cavityWidth / 2;
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        TunnelShape shape = tunnelStyle.getShape();
        int cut = 0;
        int roofTop = roadY + clearance + lining;
        for (int lateral = outerMin; lateral <= outerMax; lateral++) {
            Vec2d point = center.add(normal.multiply(lateral * scale));
            int worldX = columnResolver.worldX(point);
            int worldZ = columnResolver.worldZ(point);
            boolean cavityColumn = lateral >= cavityMin && lateral <= cavityMax;
            boolean roadColumn = lateral >= roadMin && lateral <= roadMax;
            for (int y = roadY - 1; y <= roofTop; y++) {
                boolean cavityAir = cavityColumn
                    && TunnelProfile.isCavityAir(shape, lateral, y, roadY, halfCavityWidth, clearance);
                boolean cavityFloor = cavityColumn && !roadColumn && y == roadY;
                boolean structuralFloor = cavityColumn && y == roadY - 1;
                boolean preserveRoadSurface = roadColumn && y == roadY;
                if (terrain.isSolidBlock(worldX, y, worldZ)) {
                    cut++;
                }
                if (cavityAir) {
                    solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air");
                } else if (!preserveRoadSurface
                        && (cavityFloor || structuralFloor || !cavityColumn || cavityColumn)) {
                    solids.add(point, y, RoadSolidLayer.TUNNEL, liningMaterialId);
                }
            }
        }
        return new RoadRoadbedGradingUtils.GradingVolumes(cut, 0);
    }

    static void placeLighting(
            Host host,
            RoadSolidModel solids,
            TunnelStyle tunnelStyle,
            Vec2d center,
            Vec2d leftNormal,
            int roadEnvelopeWidth,
            int roadY,
            double chainage,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock) {
        TunnelLightingMode mode = tunnelStyle.getLightingMode();
        if (mode == TunnelLightingMode.NONE || tunnelStyle.getLightSpacing() <= 0) {
            return;
        }
        if (Math.floorMod((int) Math.round(chainage), tunnelStyle.getLightSpacing()) != 0) {
            return;
        }
        String lightMaterial = host.resolveBlockId(tunnelStyle.getLightMaterial());
        if (lightMaterial == null || lightMaterial.isBlank()) {
            return;
        }
        int side = tunnelStyle.getSideClearance();
        int cavityWidth = roadEnvelopeWidth + side * 2;
        int cavityMin = RoadDimensionUtils.minLateralOffset(cavityWidth);
        int cavityMax = RoadDimensionUtils.maxLateralOffset(cavityWidth);
        int halfCavityWidth = cavityWidth / 2;
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int wallLightY = roadY + 2;
        if (mode == TunnelLightingMode.WALL_BANDS || mode == TunnelLightingMode.BOTH_SIDES) {
            placeLightBlock(solids, center, normal, cavityMin, wallLightY, scale, columnResolver, lightMaterial);
            placeLightBlock(solids, center, normal, cavityMax, wallLightY, scale, columnResolver, lightMaterial);
        }
        if (mode == TunnelLightingMode.CEILING_BAND) {
            int ceilingY = TunnelProfile.ceilingY(
                tunnelStyle.getShape(), 0, halfCavityWidth, roadY, tunnelStyle.getClearHeight());
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

    private static RoadRoadbedGradingUtils.GradingVolumes placePortals(
            Host host,
            RoadSolidModel solids,
            TunnelStyle tunnelStyle,
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
        if (detection == null || detection.runs() == null) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        Set<String> placed = new LinkedHashSet<>();
        RoadRoadbedGradingUtils.GradingVolumes total = RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        for (ConstructionRun run : detection.runs()) {
            if (run.type() != RoadConstructionType.TUNNEL || run.length() < EPSILON) {
                continue;
            }
            total = total.add(placePortalAtStation(
                host, solids, tunnelStyle, segments, heightInfos, crossSections, terrain, scale,
                constructionTypes, designElevation, buildProfile, profileWaterCrossings,
                columnResolver, run.startStation(), placed));
            total = total.add(placePortalAtStation(
                host, solids, tunnelStyle, segments, heightInfos, crossSections, terrain, scale,
                constructionTypes, designElevation, buildProfile, profileWaterCrossings,
                columnResolver, run.endStation(), placed));
        }
        return total;
    }

    private static RoadRoadbedGradingUtils.GradingVolumes placePortalAtStation(
            Host host,
            RoadSolidModel solids,
            TunnelStyle tunnelStyle,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double scale,
            List<RoadConstructionType> constructionTypes,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double worldStation,
            Set<String> placed) {
        String key = String.format("%.3f", worldStation);
        if (!placed.add(key)) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        BridgeStructureGenerator.StationLocation location =
            BridgeStructureGenerator.locateStationOnPath(segments, scale, worldStation * scale);
        if (location == null) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        RoadRoadbedGradingUtils.GradingVolumes total = RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        boolean chainForward = crossSections.samplingOriented().forward();
        for (int depth = 0; depth < PORTAL_FRAME_DEPTH_BLOCKS; depth++) {
            double geometryLocal = location.geometryLocal() + depth * scale;
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
                host,
                solids,
                tunnelStyle,
                center,
                leftNormal,
                envelopeWidth,
                targetY,
                host.resolveBlockId(tunnelStyle.getLiningMaterial()),
                terrain,
                columnResolver,
                scale,
                true));
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
}
