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
import com.plot.plugin.road.pipeline.corridor.AcuteCornerSector;
import com.plot.plugin.road.pipeline.corridor.ClearancePurpose;
import com.plot.plugin.road.pipeline.corridor.NeighborCarriagewayClip;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidMaterialPriority;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.plugin.road.solid.RoadSolidPrimitive;
import com.plot.plugin.road.tunnel.ResolvedTunnelStyle;
import com.plot.plugin.road.tunnel.TunnelColumnConstruction;
import com.plot.plugin.road.tunnel.TunnelCrossSectionMask;
import com.plot.plugin.road.tunnel.TunnelLightMount;
import com.plot.plugin.road.tunnel.TunnelLightPlacementResolver;
import com.plot.plugin.road.tunnel.TunnelLightingMode;
import com.plot.plugin.road.tunnel.TunnelPortalPlanner;
import com.plot.plugin.road.tunnel.TunnelRoofGapSealer;
import com.plot.plugin.road.tunnel.TunnelStyle;
import com.plot.core.terrain.TerrainSampler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        TunnelPortalPlanner.PortalPlanResult portalPlan = planPortalStations(
            detection, segments, heightInfos, constructionTypes, crossSections, terrain, unitsPerBlock,
            tunnelStyle, designElevation, buildProfile, profileWaterCrossings, columnResolver, host);
        List<TunnelPortalPlanner.PortalStation> portals = portalPlan.stations();
        if (metrics != null) {
            metrics.portalFoundCount += portalPlan.foundCount();
            metrics.portalNotFoundCount += portalPlan.notFoundCount();
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
            // Half-block station spacing covers diagonal/curved voxel footprints.
            boolean diagonal = Math.abs(leftNormal.x) > EPSILON && Math.abs(leftNormal.y) > EPSILON;
            boolean acuteIncoming = i > 0 && isAcutePolylineTurn(segments.get(i - 1), segment);
            boolean acuteOutgoing = i + 1 < segments.size() && isAcutePolylineTurn(segment, segments.get(i + 1));
            PathSegment previous = i > 0 ? segments.get(i - 1) : null;
            PathSegment next = i + 1 < segments.size() ? segments.get(i + 1) : null;
            double skipRadius = AcuteCornerSector.skipRadius(scale);
            boolean shortLeg = segment.distance + 1e-9 < 2.0 * skipRadius;
            // Shared acute vertex: fixed-center normal fan replaces skipped near-vertex stamps.
            if (acuteIncoming && previous != null && type.isTunnelFamily()) {
                total = total.add(placeAcuteCornerSector(
                    host, solids, tunnelStyle, previous, segment, chainForward,
                    geometryLocalBase, info, crossSections, designElevation, buildProfile,
                    portals, terrain, columnResolver, unitsPerBlock, scale));
            }
            int samples = Math.max(2, (int) Math.ceil(segment.distance / scale
                * (diagonal || acuteIncoming || acuteOutgoing ? 3 : 1)));
            for (int j = 0; j <= samples; j++) {
                double t = (double) j / samples;
                // 锐折顶点邻域整段跳过：仅跳 j==0 仍会把近顶点断面衬砌扫进对侧车行道
                double along = segment.distance * t;
                boolean nearIncoming = acuteIncoming && along < skipRadius;
                boolean nearOutgoing = acuteOutgoing && segment.distance - along < skipRadius;
                // Short legs would be wiped by both skip radii; keep the mid stamp.
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
                double carriageHalf = Math.max(0.5, crossSection.carriagewayHalfWidth()) * scale;
                RoadConstructionType sampleType = effectiveConstructionType(
                    type,
                    profileWaterCrossings,
                    worldStation);
                if (sampleType.isRockShelter()) {
                    // Classifier sides refer to the geometric segment's forward normal.
                    Vec2d rockNormal = PathSegmentGeometry.chainLeftNormal(segment, true);
                    total = total.add(placeRockShelter(solids, tunnelStyle, center, rockNormal,
                        envelopeWidth, targetY, host.resolveBlockId(tunnelStyle.liningMaterial()),
                        terrain, columnResolver, unitsPerBlock, sampleType == RoadConstructionType.ROCK_SHELTER_LEFT,
                        segment, previous, next, carriageHalf, segments));
                    continue;
                }
                if (!sampleType.isTunnelFamily()) continue;
                boolean portalFrame = TunnelPortalPlanner.findPortalNear(portals, worldStation) != null;
                double structureStation = structureStationAt(
                    tunnelRunContaining(detection != null ? detection.runs() : null, worldStation),
                    worldStation);
                boolean accentRing = tunnelStyle.hasEffectiveAccentRings()
                    && hitsStructureSpacing(structureStation, tunnelStyle.accentSpacing());
                String liningMaterial = accentRing
                    ? tunnelStyle.accentMaterial()
                    : tunnelStyle.liningMaterial();
                int liningPriority = accentRing
                    ? RoadSolidMaterialPriority.ACCENT_RING
                    : RoadSolidMaterialPriority.STRUCTURAL;
                total = total.add(placeCrossSection(
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    host.resolveBlockId(liningMaterial),
                    liningPriority,
                    terrain,
                    columnResolver,
                    unitsPerBlock,
                    portalFrame,
                    segment,
                    previous,
                    next,
                    carriageHalf,
                    segments));
                placeLighting(
                    host,
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    structureStation,
                    terrain,
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
        return placeRockShelter(
            solids, style, center, leftNormal, width, roadY, material, terrain, columns,
            unitsPerBlock, rockOnLeft, null, null, 0.0);
    }

    static RoadRoadbedGradingUtils.GradingVolumes placeRockShelter(
            RoadSolidModel solids, ResolvedTunnelStyle style, Vec2d center, Vec2d leftNormal,
            int width, int roadY, String material, TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columns, double unitsPerBlock, boolean rockOnLeft,
            PathSegment previous, PathSegment next, double carriageHalf) {
        return placeRockShelter(
            solids, style, center, leftNormal, width, roadY, material, terrain, columns,
            unitsPerBlock, rockOnLeft, null, previous, next, carriageHalf, null);
    }

    static RoadRoadbedGradingUtils.GradingVolumes placeRockShelter(
            RoadSolidModel solids, ResolvedTunnelStyle style, Vec2d center, Vec2d leftNormal,
            int width, int roadY, String material, TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columns, double unitsPerBlock, boolean rockOnLeft,
            PathSegment owner, PathSegment previous, PathSegment next, double carriageHalf,
            List<PathSegment> polyline) {
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
                if (wallColumn && liningIntrudesCarriageway(
                        point, owner, previous, next, polyline, carriageHalf, scale)) {
                    continue;
                }
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
        return placeCrossSection(
            solids, tunnelStyle, center, leftNormal, roadEnvelopeWidth, roadY, liningMaterialId,
            RoadSolidMaterialPriority.STRUCTURAL,
            terrain, columnResolver, canvasUnitsPerBlock, portalFrame,
            null, null, null, 0.0, null);
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
            boolean portalFrame,
            PathSegment owner,
            PathSegment previous,
            PathSegment next,
            double carriageHalf,
            List<PathSegment> polyline) {
        return placeCrossSection(
            solids, tunnelStyle, center, leftNormal, roadEnvelopeWidth, roadY, liningMaterialId,
            RoadSolidMaterialPriority.STRUCTURAL,
            terrain, columnResolver, canvasUnitsPerBlock, portalFrame,
            owner, previous, next, carriageHalf, polyline);
    }

    static RoadRoadbedGradingUtils.GradingVolumes placeCrossSection(
            RoadSolidModel solids,
            ResolvedTunnelStyle tunnelStyle,
            Vec2d center,
            Vec2d leftNormal,
            int roadEnvelopeWidth,
            int roadY,
            String liningMaterialId,
            int liningMaterialPriority,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock,
            boolean portalFrame,
            PathSegment owner,
            PathSegment previous,
            PathSegment next,
            double carriageHalf,
            List<PathSegment> polyline) {
        if (solids == null || center == null || leftNormal == null || roadEnvelopeWidth <= 0
                || terrain == null || columnResolver == null
                || liningMaterialId == null || liningMaterialId.isBlank()
                || tunnelStyle == null) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        int resolvedPriority = liningMaterialPriority > 0
            ? liningMaterialPriority
            : RoadSolidMaterialPriority.STRUCTURAL;
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
        // Phase A — permanent shell first (lining only). Writing cavity air before the roof
        // shell lets gravity blocks (gravel/sand) update and fall onto the finished road.
        mask.forEach((lateral, y, kind) -> {
            if (kind != TunnelCrossSectionMask.CellKind.LINING) {
                return;
            }
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
                if (Math.abs(lateral) * scale + 1e-9 >= carriageHalf
                        && liningIntrudesCarriageway(
                            point, owner, previous, next, polyline, carriageHalf, scale)) {
                    // Defer intrusion→air to Phase C so seal still sees a coherent roof plan.
                    continue;
                }
                if (shouldPlaceLining(terrain, worldX, y, worldZ, point)) {
                    if (hasTerrainObstruction(terrain, worldX, y, worldZ)
                            || terrain.isSolidBlock(worldX, y, worldZ)) {
                        cutHolder[0]++;
                    }
                    solids.add(point, y, RoadSolidLayer.TUNNEL, liningMaterialId, resolvedPriority);
                }
            }
        });
        int lining = portalFrame
            ? Math.min(TunnelStyle.MAX_LINING_THICKNESS, tunnelStyle.liningThickness() + 1)
            : tunnelStyle.liningThickness();
        // Phase B — stabilize overburden / seal roof gaps before any cavity excavation.
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
        // Phase C — excavate cavity and clip intruding / decorative shell cells to air.
        mask.forEach((lateral, y, kind) -> {
            if (kind != TunnelCrossSectionMask.CellKind.AIR
                    && kind != TunnelCrossSectionMask.CellKind.LINING) {
                return;
            }
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
                    // 与走廊清障共用 TunnelColumnConstruction：浅覆土禁止封闭洞腔开挖。
                    if (!TunnelColumnConstruction.allowsClosedCavity(
                            terrain, point, mask.outerTopY(lateral))) {
                        continue;
                    }
                    if (hasTerrainObstruction(terrain, worldX, y, worldZ)) {
                        cutHolder[0]++;
                    }
                    solids.add(
                        point, y, RoadSolidLayer.TUNNEL, "minecraft:air",
                        RoadSolidMaterialPriority.AIR, ClearancePurpose.TUNNEL_INTERIOR);
                } else if (Math.abs(lateral) * scale + 1e-9 >= carriageHalf
                        && liningIntrudesCarriageway(
                            point, owner, previous, next, polyline, carriageHalf, scale)) {
                    if (hasTerrainObstruction(terrain, worldX, y, worldZ)) {
                        cutHolder[0]++;
                    }
                    solids.add(
                        point, y, RoadSolidLayer.TUNNEL, "minecraft:air",
                        RoadSolidMaterialPriority.AIR, ClearancePurpose.TUNNEL_INTERIOR);
                } else if (!shouldPlaceLining(terrain, worldX, y, worldZ, point)
                        && terrain.isRoadClearableDecoration(worldX, y, worldZ)) {
                    // Only clearable decoration — not engineering solid — when lining is refused.
                    // Punching air through near-surface shell solids recreates roof holes / gravel falls.
                    solids.add(
                        point, y, RoadSolidLayer.TUNNEL, "minecraft:air",
                        RoadSolidMaterialPriority.AIR, ClearancePurpose.TUNNEL_INTERIOR);
                    cutHolder[0]++;
                }
            }
        });
        return new RoadRoadbedGradingUtils.GradingVolumes(cutHolder[0], 0);
    }

    /**
     * @see TunnelColumnConstruction#allowsClosedCavity
     */
    public static boolean columnAllowsClosedCavity(
            TerrainSampler terrain,
            Vec2d planPoint,
            int designedRoofTopY) {
        return TunnelColumnConstruction.allowsClosedCavity(terrain, planPoint, designedRoofTopY);
    }

    /**
     * Places lighting using tunnel-run-local structure station (0 at run start), not road chainage.
     * Lights require a mountable lining face in terrain ({@link #shouldPlaceLining}); open cuts
     * without a roof/wall shell get no floating lanterns.
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
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double canvasUnitsPerBlock) {
        if (tunnelStyle.lightSpacing() <= 0) {
            return;
        }
        if (!tunnelStyle.hasCustomLightLayout()
                && tunnelStyle.lightingMode() == TunnelLightingMode.NONE) {
            return;
        }
        if (!hitsStructureSpacing(structureStation, tunnelStyle.lightSpacing())) {
            return;
        }
        if (terrain == null || columnResolver == null) {
            return;
        }
        String lightMaterial = host.resolveBlockId(tunnelStyle.lightMaterial());
        if (lightMaterial == null || lightMaterial.isBlank()) {
            return;
        }
        List<TunnelLightPlacementResolver.LightPosition> lights =
            TunnelLightPlacementResolver.resolve(tunnelStyle, roadEnvelopeWidth, roadY);
        if (lights.isEmpty()) {
            return;
        }
        double scale = canvasUnitsPerBlock > 1e-9 ? canvasUnitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > 1e-12
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        TunnelCrossSectionMask mask = TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            roadEnvelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            tunnelStyle.liningThickness(),
            roadY,
            false);
        TunnelLightMount mount = tunnelStyle.lightMount() != null
            ? tunnelStyle.lightMount()
            : TunnelLightMount.PROTRUDING;
        // 优先依据本站已写入的衬砌体素判断安装面，避免只看原始地形造成漏灯。
        Set<Long> liningVoxels = indexTunnelLiningVoxels(solids, columnResolver);
        for (var light : lights) {
            if (!lightHasLiningMount(
                    mask, light, mount, terrain, center, normal, scale, columnResolver, liningVoxels)) {
                continue;
            }
            placeLightBlock(
                solids, center, normal, light.lateral(), light.y(), scale, lightMaterial);
        }
    }

    static boolean lightHasLiningMount(
            TunnelCrossSectionMask mask,
            TunnelLightPlacementResolver.LightPosition light,
            TunnelLightMount mount,
            TerrainSampler terrain,
            Vec2d center,
            Vec2d normal,
            double scale,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            Set<Long> liningVoxels) {
        if (mask == null || light == null || terrain == null || center == null || columnResolver == null) {
            return false;
        }
        TunnelLightMount resolved = mount != null ? mount : TunnelLightMount.PROTRUDING;
        if (resolved == TunnelLightMount.EMBEDDED) {
            return liningWouldExist(
                mask, light.lateral(), light.y(), terrain, center, normal, scale, columnResolver, liningVoxels);
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            int nl = light.lateral() + dir[0];
            int ny = light.y() + dir[1];
            if (liningWouldExist(
                    mask, nl, ny, terrain, center, normal, scale, columnResolver, liningVoxels)) {
                return true;
            }
        }
        return false;
    }

    private static boolean liningWouldExist(
            TunnelCrossSectionMask mask,
            int lateral,
            int y,
            TerrainSampler terrain,
            Vec2d center,
            Vec2d normal,
            double scale,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            Set<Long> liningVoxels) {
        if (mask.kindAt(lateral, y) != TunnelCrossSectionMask.CellKind.LINING) {
            return false;
        }
        Vec2d point = center.add(normal.multiply(lateral * scale));
        int worldX = columnResolver.worldX(point);
        int worldZ = columnResolver.worldZ(point);
        if (liningVoxels != null && liningVoxels.contains(packVoxelKey(worldX, y, worldZ))) {
            return true;
        }
        return shouldPlaceLining(terrain, worldX, y, worldZ, point);
    }

    /** 已写入的隧道衬砌/装饰环体素（不含空气与灯具）。 */
    static Set<Long> indexTunnelLiningVoxels(
            RoadSolidModel solids,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver) {
        Set<Long> keys = new HashSet<>();
        if (solids == null || columnResolver == null) {
            return keys;
        }
        for (RoadSolidPrimitive primitive : solids.primitives()) {
            if (primitive == null || primitive.layer() != RoadSolidLayer.TUNNEL) {
                continue;
            }
            String materialId = primitive.materialId();
            if (materialId == null || materialId.isBlank() || "minecraft:air".equals(materialId)) {
                continue;
            }
            if (primitive.materialPriority() >= RoadSolidMaterialPriority.DECORATIVE) {
                continue;
            }
            int worldX = columnResolver.worldX(primitive.planPoint());
            int worldZ = columnResolver.worldZ(primitive.planPoint());
            keys.add(packVoxelKey(worldX, primitive.elevation(), worldZ));
        }
        return keys;
    }

    private static long packVoxelKey(int worldX, int y, int worldZ) {
        return (((long) worldX & 0x3FFFFFL) << 42)
            | (((long) (y + 512) & 0x3FFL) << 32)
            | ((long) worldZ & 0xFFFFFFFFL);
    }

    private static void placeLightBlock(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d normal,
            int lateral,
            int y,
            double scale,
            String lightMaterial) {
        Vec2d point = center.add(normal.multiply(lateral * scale));
        solids.add(point, y, RoadSolidLayer.TUNNEL, lightMaterial, RoadSolidMaterialPriority.DECORATIVE);
    }

    private static TunnelPortalPlanner.PortalPlanResult planPortalStations(
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
            return TunnelPortalPlanner.PortalPlanResult.empty();
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
            double direction = portal.inwardDirection() != 0.0
                ? portal.inwardDirection()
                : (portal.entry() ? 1.0 : -1.0);
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
                int si = depthLocation.segmentIndex();
                PathSegment owner = segments.get(si);
                PathSegment previous = si > 0 ? segments.get(si - 1) : null;
                PathSegment next = si + 1 < segments.size() ? segments.get(si + 1) : null;
                double carriageHalf = Math.max(0.5, crossSection.carriagewayHalfWidth()) * scale;
                // 洞门帧只用本段/邻段裁剪：整条 polyline 会把直线洞口外壳误裁成空气。
                total = total.add(placeCrossSection(
                    solids,
                    tunnelStyle,
                    center,
                    leftNormal,
                    envelopeWidth,
                    targetY,
                    host.resolveBlockId(tunnelStyle.liningMaterial()),
                    RoadSolidMaterialPriority.STRUCTURAL,
                    terrain,
                    columnResolver,
                    scale,
                    true,
                    owner,
                    previous,
                    next,
                    carriageHalf,
                    null));
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
     * Fixed-center lining/cavity fan at an acute shared vertex. Along-path samples near the
     * vertex remain skipped so dual axis-aligned sections do not sweep into the opposite
     * carriageway; this sector covers the triangular gore those skips leave behind.
     */
    private static RoadRoadbedGradingUtils.GradingVolumes placeAcuteCornerSector(
            Host host,
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
            List<TunnelPortalPlanner.PortalStation> portals,
            TerrainSampler terrain,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            double unitsPerBlock,
            double scale) {
        Vec2d center = outgoing.start;
        Vec2d fromNormal = PathSegmentGeometry.chainLeftNormal(incoming, chainForward);
        Vec2d toNormal = PathSegmentGeometry.chainLeftNormal(outgoing, chainForward);
        double worldStation = geometryLocalAtVertex / scale;
        int resolvedY = DesignElevationSource.resolveTargetElevation(
            designElevation, buildProfile, outgoingInfo, geometryLocalAtVertex, 0.0, worldStation);
        final int targetY = host.snapEndpointElevation(center, resolvedY);
        double chainage = crossSections.chainageAtGeometryLocal(geometryLocalAtVertex);
        ResolvedCrossSection crossSection = crossSections.resolve(chainage);
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        if (envelopeWidth <= 0) {
            return RoadRoadbedGradingUtils.GradingVolumes.ZERO;
        }
        double carriageHalf = Math.max(0.5, crossSection.carriagewayHalfWidth()) * scale;
        boolean portalFrame = TunnelPortalPlanner.findPortalNear(portals, worldStation) != null;
        String liningId = host.resolveBlockId(tunnelStyle.liningMaterial());
        List<PathSegment> polyline = List.of(incoming, outgoing);
        // Seal-only fan matches clearance gore normals so gravel above intermediate
        // orientations is stabilized without stacking conflicting lining shells.
        int lining = portalFrame
            ? Math.min(TunnelStyle.MAX_LINING_THICKNESS, tunnelStyle.liningThickness() + 1)
            : tunnelStyle.liningThickness();
        TunnelCrossSectionMask sealMask = TunnelCrossSectionMask.build(
            tunnelStyle.shape(),
            envelopeWidth,
            tunnelStyle.sideClearance(),
            tunnelStyle.clearHeight(),
            lining,
            targetY,
            portalFrame);
        AcuteCornerSector.forEachTunnelNormal(fromNormal, toNormal, normal ->
            TunnelRoofGapSealer.sealAboveRoofShell(
                solids,
                sealMask,
                tunnelStyle.shape(),
                targetY,
                tunnelStyle.clearHeight(),
                envelopeWidth,
                tunnelStyle.sideClearance(),
                lining,
                center,
                normal,
                scale,
                columnResolver,
                terrain,
                liningId,
                "minecraft:stone"));
        // Restore one axis-aligned section at the shared vertex (outgoing normal).
        // Bisector/fan lining stamps create diagonal shells that fail 6-connected checks.
        return placeCrossSection(
            solids,
            tunnelStyle,
            center,
            toNormal,
            envelopeWidth,
            targetY,
            liningId,
            RoadSolidMaterialPriority.STRUCTURAL,
            terrain,
            columnResolver,
            unitsPerBlock,
            portalFrame,
            outgoing,
            incoming,
            null,
            carriageHalf,
            polyline);
    }

    /** 折线转向 |θ|≥90° ⇒ 内角为锐角；共点双断面易堵洞。 */
    public static boolean isAcutePolylineTurn(PathSegment incoming, PathSegment outgoing) {
        if (incoming == null || outgoing == null) {
            return false;
        }
        Vec2d inDir = incoming.end.subtract(incoming.start);
        Vec2d outDir = outgoing.end.subtract(outgoing.start);
        if (inDir.lengthSquared() < EPSILON || outDir.lengthSquared() < EPSILON) {
            return false;
        }
        Vec2d a = inDir.normalize();
        Vec2d b = outDir.normalize();
        double turn = Math.atan2(a.cross(b), a.dot(b));
        return Math.abs(turn) >= Math.PI * 0.5 - 1e-6;
    }

    /**
     * 侧壁衬砌是否侵入行车道。
     * <ul>
     *   <li>本段 / polyline / 共线邻段：严格 {@code carriageHalf}（不加体素余量），避免把设计壳层
     *       （约 {@code |lateral| ≈ carriageHalf + sideClearance}）误裁成空气</li>
     *   <li>急弯邻段：才加 {@link NeighborCarriagewayClip#VOXEL_MARGIN}，挡住回头弯对侧串壁</li>
     *   <li>调用方须保证仅对 {@code |lateral|≥carriageHalf} 的侧壁格调用（顶板 lateral≈0 不调用）</li>
     * </ul>
     */
    static boolean liningIntrudesCarriageway(
            Vec2d point,
            PathSegment owner,
            PathSegment previous,
            PathSegment next,
            List<PathSegment> polyline,
            double carriageHalf,
            double scale) {
        if (point == null || carriageHalf <= 0.0) {
            return false;
        }
        if (polyline != null && !polyline.isEmpty()) {
            for (PathSegment segment : polyline) {
                if (segment == null) {
                    continue;
                }
                if (com.plot.core.geometry.GeometryUtils.pointToSegmentDistance(
                        point, segment.start, segment.end) + 1e-9 < carriageHalf) {
                    return true;
                }
            }
        } else if (owner != null
                && com.plot.core.geometry.GeometryUtils.pointToSegmentDistance(
                    point, owner.start, owner.end) + 1e-9 < carriageHalf) {
            return true;
        }
        double acuteMargin = NeighborCarriagewayClip.VOXEL_MARGIN * Math.max(scale, 1e-9);
        if (previous != null && owner != null && isAcutePolylineTurn(previous, owner)
                && NeighborCarriagewayClip.insideNeighborCarriageway(
                    point, previous, null, carriageHalf, acuteMargin)) {
            return true;
        }
        if (next != null && owner != null && isAcutePolylineTurn(owner, next)
                && NeighborCarriagewayClip.insideNeighborCarriageway(
                    point, null, next, carriageHalf, acuteMargin)) {
            return true;
        }
        // 共线邻段不加 margin：否则 |lateral|≈carriageHalf+sideClearance 的设计壳会被误裁。
        return NeighborCarriagewayClip.insideNeighborCarriageway(
            point, previous, next, carriageHalf, 0.0);
    }

    /**
     * Lining seals the structural shell strictly below the engineering surface.
     * Air pockets below the surface still get lining; gravity blocks at/above the
     * natural surface are never replaced (portal / shallow daylight must not grow
     * a brick pile on top of the ground).
     */
    public static boolean shouldPlaceLining(
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
