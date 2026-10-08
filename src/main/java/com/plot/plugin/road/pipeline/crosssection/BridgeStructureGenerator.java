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
    /** Interior pier spacing in world/block stations (not canvas geometry units). */
    private static final double DEFAULT_PIER_SPACING_BLOCKS = 6.0;
    /** Abutment wall depth along the road axis, in world blocks. */
    private static final int ABUTMENT_THICKNESS_BLOCKS = 2;
    /** Narrow roads get a single pier column; wider roads get edge + center columns. */
    private static final int SINGLE_COLUMN_CARRIAGEWAY_WIDTH = 3;
    static final String PIER_MATERIAL = "material.plot.stone_bricks";
    static final String DECK_SLAB_MATERIAL = "material.plot.stone";

    private BridgeStructureGenerator() {
    }

    enum StructureKind {
        INTERIOR_PIER,
        ABUTMENT
    }

    record StructureStation(
            double worldStation,
            double geometryDistance,
            StructureKind kind,
            int landwardSign,
            boolean heavyPier) {
    }

    public record StationLocation(
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
        String pierBlockId = host.resolveBlockId(PIER_MATERIAL);
        String deckSlabBlockId = host.resolveBlockId(DECK_SLAB_MATERIAL);
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
                pierBlockId,
                deckSlabBlockId,
                location,
                station.kind(),
                station.landwardSign(),
                station.heavyPier());
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
            addAbutmentStation(
                stations, dedupe, crossing.crossingStartStation(), crossSections, scale, -1);
            addAbutmentStation(
                stations, dedupe, crossing.crossingEndStation(), crossSections, scale, 1);
        }
    }

    private static void addAbutmentStation(
            List<StructureStation> stations,
            Set<String> dedupe,
            double worldStation,
            CrossSectionBuildContext crossSections,
            double scale,
            int landwardSign) {
        double geometryDistance = geometryDistanceAtWorldStation(worldStation, crossSections, scale);
        String key = stationKey(worldStation, StructureKind.ABUTMENT) + "@" + landwardSign;
        if (dedupe.add(key)) {
            stations.add(new StructureStation(
                worldStation, geometryDistance, StructureKind.ABUTMENT, landwardSign, false));
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
        if (profileWaterCrossings != null && !profileWaterCrossings.isEmpty()) {
            for (WaterCrossing crossing : profileWaterCrossings) {
                if (crossing.strategy() == WaterCrossingStrategy.BRIDGE
                        || crossing.strategy() == WaterCrossingStrategy.LONG_BRIDGE) {
                    appendPiersForCrossingSpan(
                        stations,
                        dedupe,
                        crossing,
                        crossSections,
                        unitsPerBlock);
                }
            }
            return;
        }
        appendPiersAlongBridgeSegments(
            stations,
            dedupe,
            constructionTypes,
            segments,
            crossSections,
            unitsPerBlock,
            profileWaterCrossings);
    }

    private static void appendPiersForCrossingSpan(
            List<StructureStation> stations,
            Set<String> dedupe,
            WaterCrossing crossing,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        double desiredSpacingBlocks = WaterCrossingConstructionResolver.bridgePillarSpacingBlocks(
            List.of(crossing),
            crossing.crossingStartStation() + 1.0,
            DEFAULT_PIER_SPACING_BLOCKS);
        double spanBlocks = crossing.crossingEndStation() - crossing.crossingStartStation();
        if (spanBlocks <= desiredSpacingBlocks + EPSILON) {
            return;
        }
        int bayCount = Math.max(1, (int) Math.round(spanBlocks / desiredSpacingBlocks));
        double actualSpacing = spanBlocks / bayCount;
        for (int bay = 1; bay < bayCount; bay++) {
            double worldStation = crossing.crossingStartStation() + bay * actualSpacing;
            if (isNearAbutment(stations, worldStation)) {
                continue;
            }
            boolean heavyPier = crossing.strategy() == WaterCrossingStrategy.LONG_BRIDGE;
            addPierStation(stations, dedupe, worldStation, crossSections, scale, heavyPier);
        }
    }

    private static void appendPiersAlongBridgeSegments(
            List<StructureStation> stations,
            Set<String> dedupe,
            List<RoadConstructionType> constructionTypes,
            List<PathSegment> segments,
            CrossSectionBuildContext crossSections,
            double unitsPerBlock,
            List<WaterCrossing> profileWaterCrossings) {
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        double defaultSpacingGeometry = DEFAULT_PIER_SPACING_BLOCKS * scale;
        double accumulated = 0.0;
        double pillarSpacing = defaultSpacingGeometry;
        double pillarChainage = 0.0;
        boolean inBridgeRun = false;
        for (int i = 0; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            if (RoadConstructionClassifier.constructionTypeAt(constructionTypes, i) != RoadConstructionType.BRIDGE) {
                inBridgeRun = false;
                accumulated += segment.distance;
                continue;
            }
            double segmentEnd = accumulated + segment.distance;
            double chainageA = crossSections.chainageAtGeometryLocal(accumulated);
            double chainageB = crossSections.chainageAtGeometryLocal(segmentEnd);
            double minChainage = Math.min(chainageA, chainageB);
            double maxChainage = Math.max(chainageA, chainageB);
            if (!inBridgeRun) {
                pillarSpacing = defaultSpacingGeometry;
                pillarChainage = Math.ceil((minChainage - EPSILON) / pillarSpacing) * pillarSpacing;
                inBridgeRun = true;
            }
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
                double desiredSpacingBlocks = WaterCrossingConstructionResolver.bridgePillarSpacingBlocks(
                    profileWaterCrossings,
                    worldStation,
                    DEFAULT_PIER_SPACING_BLOCKS);
                pillarSpacing = Math.max(scale, desiredSpacingBlocks * scale);
                addPierStation(stations, dedupe, worldStation, crossSections, scale, false);
                pillarChainage += pillarSpacing;
            }
            accumulated = segmentEnd;
        }
    }

    private static void addPierStation(
            List<StructureStation> stations,
            Set<String> dedupe,
            double worldStation,
            CrossSectionBuildContext crossSections,
            double scale,
            boolean heavyPier) {
        double geometryDistance = geometryDistanceAtWorldStation(worldStation, crossSections, scale);
        String key = stationKey(worldStation, StructureKind.INTERIOR_PIER)
            + (heavyPier ? "#heavy" : "");
        if (dedupe.add(key)) {
            stations.add(new StructureStation(
                worldStation, geometryDistance, StructureKind.INTERIOR_PIER, 0, heavyPier));
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
            String pierBlockId,
            String deckSlabBlockId,
            StationLocation location,
            StructureKind kind,
            int landwardSign,
            boolean heavyPier) {
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
                solids,
                segment,
                center,
                leftNormal,
                deckY,
                terrain,
                pierBlockId,
                deckSlabBlockId,
                crossSection,
                unitsPerBlock,
                landwardSign);
        } else {
            placeInteriorPier(
                solids,
                segment,
                center,
                leftNormal,
                deckY,
                terrain,
                pierBlockId,
                deckSlabBlockId,
                crossSection,
                unitsPerBlock,
                heavyPier);
        }
    }

    private static void placeAbutment(
            RoadSolidModel solids,
            PathSegment segment,
            Vec2d center,
            Vec2d leftNormal,
            int deckY,
            TerrainSampler terrain,
            String pierBlockId,
            String deckSlabBlockId,
            ResolvedCrossSection crossSection,
            double unitsPerBlock,
            int landwardSign) {
        int widthBlocks = Math.max(3, RoadCorridorWidth.bridgeDeckWidthBlocks(crossSection));
        Vec2d forward = segmentForward(segment);
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        int stepSign = landwardSign < 0 ? -1 : 1;
        for (int step = 0; step < ABUTMENT_THICKNESS_BLOCKS; step++) {
            Vec2d wallCenter = center.add(forward.multiply(stepSign * step * scale));
            fillVerticalStrip(
                solids, wallCenter, leftNormal, widthBlocks, deckY, terrain, pierBlockId, unitsPerBlock);
            placeDeckSlab(
                solids, wallCenter, leftNormal, widthBlocks, deckY - 1, deckSlabBlockId, unitsPerBlock);
        }
    }

    private static void placeInteriorPier(
            RoadSolidModel solids,
            PathSegment segment,
            Vec2d center,
            Vec2d leftNormal,
            int deckY,
            TerrainSampler terrain,
            String pierBlockId,
            String deckSlabBlockId,
            ResolvedCrossSection crossSection,
            double unitsPerBlock,
            boolean heavyPier) {
        int carriagewayWidth = Math.max(1, crossSection.carriagewayWidth);
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        Vec2d normal = leftNormal.lengthSquared() > EPSILON
            ? leftNormal.normalize()
            : new Vec2d(0, 1);
        Vec2d forward = segmentForward(segment);
        if (carriagewayWidth <= SINGLE_COLUMN_CARRIAGEWAY_WIDTH) {
            placePierColumn(solids, center, deckY, terrain, pierBlockId, heavyPier, forward, scale);
        } else {
            int minOffset = RoadDimensionUtils.minLateralOffset(carriagewayWidth);
            int maxOffset = RoadDimensionUtils.maxLateralOffset(carriagewayWidth);
            placePierColumn(solids, center, deckY, terrain, pierBlockId, heavyPier, forward, scale);
            placePierColumn(
                solids,
                center.add(normal.multiply(minOffset * scale)),
                deckY,
                terrain,
                pierBlockId,
                heavyPier,
                forward,
                scale);
            placePierColumn(
                solids,
                center.add(normal.multiply(maxOffset * scale)),
                deckY,
                terrain,
                pierBlockId,
                heavyPier,
                forward,
                scale);
        }
        int deckWidthBlocks = RoadCorridorWidth.bridgeDeckWidthBlocks(crossSection);
        placeDeckSlab(
            solids, center, leftNormal, deckWidthBlocks, deckY - 1, deckSlabBlockId, unitsPerBlock);
    }

    private static void placeDeckSlab(
            RoadSolidModel solids,
            Vec2d center,
            Vec2d leftNormal,
            int widthBlocks,
            int elevation,
            String blockId,
            double unitsPerBlock) {
        solids.addLateralStrip(
            center,
            leftNormal,
            Math.max(1, widthBlocks),
            elevation,
            RoadSolidLayer.BRIDGE,
            blockId,
            unitsPerBlock);
    }

    private static Vec2d segmentForward(PathSegment segment) {
        Vec2d delta = segment.end.subtract(segment.start);
        return delta.lengthSquared() > EPSILON ? delta.normalize() : new Vec2d(1, 0);
    }

    private static void placePierColumn(
            RoadSolidModel solids,
            Vec2d canvasPos,
            int deckY,
            TerrainSampler terrain,
            String blockId,
            boolean heavyPier,
            Vec2d forward,
            double scale) {
        fillPierColumn(solids, canvasPos, deckY, terrain, blockId);
        if (heavyPier) {
            fillPierColumn(solids, canvasPos.add(forward.multiply(scale)), deckY, terrain, blockId);
        }
    }

    private static void fillPierColumn(
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
            fillPierColumn(solids, planPoint, deckY, terrain, blockId);
        }
    }

    public static StationLocation locateStationOnPath(
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
