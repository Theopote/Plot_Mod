package com.plot.plugin.road.pipeline.crosssection;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.RoadDimensionUtils;
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
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossingStrategy;
import com.plot.plugin.road.solid.RoadSolidLayer;
import com.plot.plugin.road.solid.RoadSolidModel;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates bridge substructures: interior piers with caps and abutments at crossing boundaries.
 */
public final class BridgeStructureGenerator {

    private static final double EPSILON = 1e-6;

    private BridgeStructureGenerator() {
    }

    enum StructureKind {
        INTERIOR_PIER,
        ABUTMENT
    }

    record StructureStation(double worldStation, double geometryDistance, StructureKind kind) {
    }

    record StationLocation(
            int segmentIndex,
            double t,
            double geometryLocal,
            double segmentStartGeometry) {
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
            TerrainSampler terrain,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings) {
        if (constructionTypes.stream().noneMatch(type -> type == RoadConstructionType.BRIDGE)) {
            return;
        }
        String structureBlockId = host.resolveBlockId("material.plot.stone");
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        List<StructureStation> planned = planStructureStations(
            constructionTypes,
            segments,
            heightInfos,
            crossSections,
            unitsPerBlock,
            profileWaterCrossings);
        for (StructureStation station : planned) {
            StationLocation location = locateStationOnPath(segments, scale, station.geometryDistance());
            if (location == null) {
                continue;
            }
            placeStructure(
                host,
                solids,
                segments,
                heightInfos,
                crossSections,
                terrain,
                unitsPerBlock,
                designElevation,
                buildProfile,
                structureBlockId,
                location,
                station.kind());
        }
    }

    static List<StructureStation> planStructureStations(
            List<RoadConstructionType> constructionTypes,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            List<WaterCrossing> profileWaterCrossings) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        Set<String> dedupe = new LinkedHashSet<>();
        List<StructureStation> stations = new ArrayList<>();
        appendAbutmentStations(stations, dedupe, profileWaterCrossings, crossSections, scale);
        appendInteriorPierStations(
            stations,
            dedupe,
            constructionTypes,
            segments,
            crossSections,
            unitsPerBlock,
            profileWaterCrossings);
        stations.sort(Comparator.comparingDouble(StructureStation::worldStation));
        return List.copyOf(stations);
    }

    private static void appendAbutmentStations(
            List<StructureStation> stations,
            Set<String> dedupe,
            List<WaterCrossing> profileWaterCrossings,
            CrossSectionBuildContext crossSections,
            double scale) {
        if (profileWaterCrossings == null || profileWaterCrossings.isEmpty()) {
            return;
        }
        for (WaterCrossing crossing : profileWaterCrossings) {
            if (crossing.strategy() != WaterCrossingStrategy.BRIDGE
                    && crossing.strategy() != WaterCrossingStrategy.LONG_BRIDGE) {
                continue;
            }
            addAbutmentStation(stations, dedupe, crossing.crossingStartStation(), crossSections, scale);
            addAbutmentStation(stations, dedupe, crossing.crossingEndStation(), crossSections, scale);
        }
    }

    private static void addAbutmentStation(
            List<StructureStation> stations,
            Set<String> dedupe,
            double worldStation,
            CrossSectionBuildContext crossSections,
            double scale) {
        double geometryDistance = geometryDistanceAtWorldStation(worldStation, crossSections, scale);
        String key = stationKey(worldStation, StructureKind.ABUTMENT);
        if (dedupe.add(key)) {
            stations.add(new StructureStation(worldStation, geometryDistance, StructureKind.ABUTMENT));
        }
    }

    private static void appendInteriorPierStations(
            List<StructureStation> stations,
            Set<String> dedupe,
            List<RoadConstructionType> constructionTypes,
            List<PathSegment> segments,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            List<WaterCrossing> profileWaterCrossings) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        double defaultSpacing = Math.max(unitsPerBlock, 6.0 * unitsPerBlock);
        double accumulated = 0.0;
        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            if (RoadConstructionClassifier.constructionTypeAt(constructionTypes, i) != RoadConstructionType.BRIDGE) {
                accumulated += segment.distance;
                continue;
            }
            double segmentEnd = accumulated + segment.distance;
            double chainageA = crossSections.chainageAtGeometryLocal(accumulated);
            double chainageB = crossSections.chainageAtGeometryLocal(segmentEnd);
            double minChainage = Math.min(chainageA, chainageB);
            double maxChainage = Math.max(chainageA, chainageB);
            double pillarSpacing = defaultSpacing;
            double pillarChainage = Math.ceil((minChainage - EPSILON) / pillarSpacing) * pillarSpacing;
            while (pillarChainage <= maxChainage + EPSILON) {
                double geometryDistance = crossSections.orientedSegment() != null
                    ? crossSections.orientedSegment()
                        .geometryLocalAtRoadStation(pillarChainage)
                        .orElse(accumulated)
                    : pillarChainage;
                double worldStation = geometryDistance / scale;
                if (profileWaterCrossings != null
                        && !profileWaterCrossings.isEmpty()
                        && !WaterCrossingConstructionResolver.isBridgeStructureStation(
                            profileWaterCrossings, worldStation)) {
                    pillarChainage += pillarSpacing;
                    continue;
                }
                if (isNearAbutment(stations, worldStation)) {
                    pillarChainage += pillarSpacing;
                    continue;
                }
                pillarSpacing = Math.max(
                    unitsPerBlock,
                    WaterCrossingConstructionResolver.bridgePillarSpacingBlocks(
                        profileWaterCrossings,
                        worldStation,
                        6.0 * unitsPerBlock));
                String key = stationKey(worldStation, StructureKind.INTERIOR_PIER);
                if (dedupe.add(key)) {
                    stations.add(new StructureStation(worldStation, geometryDistance, StructureKind.INTERIOR_PIER));
                }
                pillarChainage += pillarSpacing;
            }
            accumulated = segmentEnd;
        }
    }

    private static boolean isNearAbutment(List<StructureStation> stations, double worldStation) {
        for (StructureStation station : stations) {
            if (station.kind() == StructureKind.ABUTMENT
                    && Math.abs(station.worldStation() - worldStation) <= 1.0 + EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static void placeStructure(
            Host host,
            RoadSolidModel solids,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            String structureBlockId,
            StationLocation location,
            StructureKind kind) {
        PathSegment segment = segments.get(location.segmentIndex());
        SegmentHeightInfo info = heightInfos.get(location.segmentIndex());
        int deckY = DesignElevationSource.resolveTargetElevation(
            designElevation,
            buildProfile,
            info,
            location.geometryLocal(),
            location.t(),
            location.geometryLocal() / Math.max(unitsPerBlock, EPSILON));
        Vec2d center = segment.start.lerp(segment.end, location.t());
        deckY = host.snapEndpointElevation(center, deckY);
        Vec2d leftNormal = PathSegmentGeometry.chainLeftNormal(
            segment,
            crossSections.samplingOriented().forward());
        double chainage = crossSections.chainageAtGeometryLocal(location.geometryLocal());
        ResolvedCrossSection crossSection = crossSections.resolve(chainage);
        if (kind == StructureKind.ABUTMENT) {
            placeAbutment(
                host, solids, center, leftNormal, deckY, terrain, structureBlockId, crossSection, unitsPerBlock);
        } else {
            placeInteriorPier(
                host, solids, center, leftNormal, deckY, terrain, structureBlockId, crossSection, unitsPerBlock);
        }
    }

    private static void placeAbutment(
            Host host,
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int deckY,
            TerrainSampler terrain,
            String blockId,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        int widthBlocks = Math.max(3, RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection));
        fillVerticalStrip(
            solids, center, leftNormal, widthBlocks, deckY, terrain, blockId, unitsPerBlock);
        solids.addLateralStrip(
            center,
            leftNormal,
            widthBlocks,
            deckY - 1,
            RoadSolidLayer.BRIDGE,
            blockId,
            unitsPerBlock);
    }

    private static void placeInteriorPier(
            Host host,
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int deckY,
            TerrainSampler terrain,
            String blockId,
            ResolvedCrossSection crossSection,
            double unitsPerBlock) {
        double halfExtent = RoadDimensionUtils.halfExtentFromCenter(crossSection.carriagewayWidth) * unitsPerBlock;
        Vec2d left = center.add(leftNormal.multiply(halfExtent));
        Vec2d right = center.subtract(leftNormal.multiply(halfExtent));
        placePierColumn(solids, center, deckY, terrain, blockId);
        placePierColumn(solids, left, deckY, terrain, blockId);
        placePierColumn(solids, right, deckY, terrain, blockId);
        solids.addLateralStrip(
            center,
            leftNormal,
            3,
            deckY - 1,
            RoadSolidLayer.BRIDGE,
            blockId,
            unitsPerBlock);
    }

    private static void placePierColumn(
            RoadSolidModel solids,
            Vec2d canvasPos,
            int deckY,
            TerrainSampler terrain,
            String blockId) {
        int groundY = terrain.sampleSurfaceY(canvasPos);
        for (int y = groundY + 1; y < deckY; y++) {
            solids.add(canvasPos, y, RoadSolidLayer.BRIDGE, blockId);
        }
    }

    private static void fillVerticalStrip(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int deckY,
            TerrainSampler terrain,
            String blockId,
            double unitsPerBlock) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > EPSILON
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        int minOffset = RoadDimensionUtils.minLateralOffset(widthBlocks);
        int maxOffset = RoadDimensionUtils.maxLateralOffset(widthBlocks);
        for (int lateral = minOffset; lateral <= maxOffset; lateral++) {
            Vec2d planPoint = center.add(normal.multiply(lateral * scale));
            placePierColumn(solids, planPoint, deckY, terrain, blockId);
        }
    }

    static StationLocation locateStationOnPath(
            List<PathSegment> segments,
            double unitsPerBlock,
            double geometryDistance) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        double remaining = geometryDistance;
        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            if (remaining > segment.distance + EPSILON) {
                remaining -= segment.distance;
                continue;
            }
            double t = segment.distance > EPSILON
                ? Math.clamp(remaining / segment.distance, 0.0, 1.0)
                : 0.0;
            double segmentStartGeometry = geometryDistance - remaining;
            return new StationLocation(i, t, geometryDistance, segmentStartGeometry);
        }
        if (segments.isEmpty()) {
            return null;
        }
        PathSegment last = segments.getLast();
        return new StationLocation(
            segments.size() - 1,
            1.0,
            geometryDistance,
            geometryDistance - last.distance);
    }

    private static double geometryDistanceAtWorldStation(
            double worldStation,
            CrossSectionBuildContext crossSections,
            double scale) {
        if (crossSections.orientedSegment() != null) {
            return crossSections.orientedSegment()
                .geometryLocalAtRoadStation(worldStation)
                .orElse(worldStation * scale);
        }
        return worldStation * scale;
    }

    private static String stationKey(double worldStation, StructureKind kind) {
        return kind.name() + "@" + Math.round(worldStation * 1000.0);
    }
}
