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
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelPortalPlanner;
import com.plot.plugin.road.tunnel.TunnelProfile;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.core.terrain.TerrainSampler;

import java.util.List;

/**
 * Generates tunnel cavities, lining, lighting, accent rings, and portal frames.
 */
public final class TunnelStructureGenerator {
    private static final double EPSILON = 1e-6;
    private static final int PORTAL_FRAME_DEPTH_BLOCKS = 2;
    private static final double[] CELL_INSETS = {0.0, -0.25, 0.25};

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
                || constructionTypes.stream().noneMatch(type -> type.isTunnelFamily() || type.isRockShelter())) {
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
            // Half-block station spacing covers diagonal/curved voxel footprints.
            boolean diagonal = Math.abs(leftNormal.x) > EPSILON && Math.abs(leftNormal.y) > EPSILON;
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale * (diagonal ? 2 : 1)));
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
                if (sampleType.isRockShelter()) {
                    // Classifier sides refer to the geometric segment's forward normal.
                    Vec2d rockNormal = PathSegmentGeometry.chainLeftNormal(segment, true);
                    total = total.add(placeRockShelter(solids, tunnelStyle, center, rockNormal,
                        envelopeWidth, targetY, host.resolveBlockId(tunnelStyle.liningMaterial()),
                        terrain, columnResolver, unitsPerBlock, sampleType == RoadConstructionType.ROCK_SHELTER_LEFT));
                    continue;
                }
                if (!sampleType.isTunnelFamily()) continue;
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

    /** Open valley side, rock-side wall and a roof only where rock actually covers it. */
    static RoadRoadbedGradingUtils.GradingVolumes placeRockShelter(
            RoadSolidModel solids, ResolvedTunnelStyle style, Vec2d center, Vec2d leftNormal,
            int width, int roadY, String material, TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columns, double unitsPerBlock, boolean rockOnLeft) {
        double scale = unitsPerBlock > 1e-9 ? unitsPerBlock : 1.0;
        Vec2d normal = leftNormal.normalize();
        int min = com.plot.plugin.road.RoadDimensionUtils.minLateralOffset(width) - style.sideClearance();
        int max = com.plot.plugin.road.RoadDimensionUtils.maxLateralOffset(width) + style.sideClearance();
        int wall = rockOnLeft ? max + 1 : min - 1;
        int roof = roadY + style.clearHeight() + 1;
        int cut = 0;
        for (int lateral = min - style.liningThickness(); lateral <= max + style.liningThickness(); lateral++) {
            boolean wallColumn = rockOnLeft ? lateral >= wall : lateral <= wall;
            for (double inset : CELL_INSETS) {
                Vec2d point = center.add(normal.multiply((lateral + inset) * scale));
                int x = columns.worldX(point), z = columns.worldZ(point);
                if (!terrain.isChunkLoaded(x, z)) continue;
                if (wallColumn) {
                    for (int y = roadY; y < roof + style.liningThickness(); y++) {
                        solids.add(point, y, RoadSolidLayer.TUNNEL, material, RoadSolidMaterialPriority.STRUCTURAL);
                    }
                    continue;
                }
                // A thin structural slab supports the exposed part without filling the valley.
                solids.add(point, roadY - 1, RoadSolidLayer.TUNNEL, material, RoadSolidMaterialPriority.STRUCTURAL);
                for (int y = roadY + 1; y < roof; y++) {
                    if (hasTerrainObstruction(terrain, x, y, z)) cut++;
                    solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
                }
                boolean mountainHalf = rockOnLeft ? lateral >= 0 : lateral <= 0;
                if (mountainHalf && terrain.sampleSurfaceY(point) >= roof) {
                    for (int y = roof; y < roof + style.liningThickness(); y++) {
                        solids.add(point, y, RoadSolidLayer.TUNNEL, material, RoadSolidMaterialPriority.STRUCTURAL);
                    }
                }
            }
        }
        return new RoadRoadbedGradingUtils.GradingVolumes(cut, 0);
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
            if (kind != TunnelCrossSectionMask.CellKind.AIR
                    && kind != TunnelCrossSectionMask.CellKind.LINING) {
                return;
            }
            // Cover the width of each mask cell, not just its center. At 45 degrees
            // integer lateral samples alone leave a checkerboard of native terrain.
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            for (double inset : CELL_INSETS) {
                Vec2d point = center.add(normal.multiply((lateral + inset) * scale));
                int worldX = columnResolver.worldX(point);
                int worldZ = columnResolver.worldZ(point);
                if (worldX == lastX && worldZ == lastZ) continue;
                lastX = worldX;
                lastZ = worldZ;
                if (!terrain.isChunkLoaded(worldX, worldZ)) {
                    continue;
                }
                if (kind == TunnelCrossSectionMask.CellKind.AIR) {
                    // 洞室：清障为空气。不得对 LINING 格先写 air，否则 shouldPlaceLining 拒绝时会留下顶板破洞，
                    // 上方沙砾等重力方块会落到路面。
                    if (hasTerrainObstruction(terrain, worldX, y, worldZ)) {
                        cutHolder[0]++;
                    }
                    solids.add(point, y, RoadSolidLayer.TUNNEL, "minecraft:air", RoadSolidMaterialPriority.AIR);
                } else if (shouldPlaceLining(terrain, worldX, y, worldZ, point)) {
                    if (hasTerrainObstruction(terrain, worldX, y, worldZ)
                            || terrain.isSolidBlock(worldX, y, worldZ)) {
                        cutHolder[0]++;
                    }
                    solids.add(point, y, RoadSolidLayer.TUNNEL, liningMaterialId, RoadSolidMaterialPriority.STRUCTURAL);
                }
            }
        });
        int lining = portalFrame
            ? Math.min(TunnelStyle.MAX_LINING_THICKNESS, tunnelStyle.liningThickness() + 1)
            : tunnelStyle.liningThickness();
        // 整段隧道都封洞顶与上方空隙；仅洞口封缝时，洞身顶板镂空会导致沙砾落入路面。
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
        solids.add(point, y, RoadSolidLayer.TUNNEL, lightMaterial, RoadSolidMaterialPriority.DECORATIVE);
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

    private static boolean hasTerrainObstruction(TerrainSampler terrain, int worldX, int y, int worldZ) {
        return terrain.isConstructionVolumeObstruction(worldX, y, worldZ)
            || terrain.isSolidBlock(worldX, y, worldZ)
            || terrain.isRoadClearableDecoration(worldX, y, worldZ);
    }

    /**
     * Lining seals the structural shell below the engineering surface.
     * Air pockets in the shell still get lining so gravity blocks (gravel/sand) cannot fall through.
     * Open air at/above the surface and clearable decoration (trees) stay without lining.
     */
    static boolean shouldPlaceLining(
            TerrainSampler terrain,
            int worldX,
            int y,
            int worldZ,
            Vec2d planPoint) {
        if (terrain == null || planPoint == null) {
            return false;
        }
        if (terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
            return false;
        }
        int surfaceY = terrain.sampleSurfaceY(planPoint);
        return y < surfaceY;
    }
}
