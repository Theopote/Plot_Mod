package com.plot.plugin.road.tunnel;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadConstructionType;
import com.plot.plugin.road.geometry.RoadCorridorWidth;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.pipeline.CrossSectionBuildContext;
import com.plot.plugin.road.pipeline.construction.ConstructionRun;
import com.plot.plugin.road.pipeline.construction.RoadConstructionClassifier;
import com.plot.plugin.road.pipeline.crosssection.BridgeStructureGenerator;
import com.plot.plugin.road.pipeline.geometry.PathSegment;
import com.plot.plugin.road.pipeline.profile.BuildHeightProfile;
import com.plot.plugin.road.pipeline.profile.DesignElevationSource;
import com.plot.plugin.road.pipeline.profile.SegmentHeightInfo;
import com.plot.plugin.road.pipeline.profile.environment.WaterCrossing;
import com.plot.plugin.road.pipeline.construction.WaterCrossingConstructionResolver;
import com.plot.plugin.road.RoadTerrainClearanceUtils;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds portal stations with adequate terrain cover at tunnel run boundaries.
 */
public final class TunnelPortalPlanner {
    private static final double EPSILON = 1e-6;
    public static final int PORTAL_MIN_COVER_BLOCKS = 2;
    private static final int MAX_PORTAL_SEARCH_STEPS = 24;

    private TunnelPortalPlanner() {
    }

    public enum PortalStationStatus {
        FOUND,
        NOT_FOUND
    }

    /**
     * Placed portal station. {@code inwardDirection} is +1 / -1 along world station
     * toward the tunnel interior from the run boundary that produced this portal.
     */
    public record PortalStation(double worldStation, boolean entry, double inwardDirection) {
        public PortalStation(double worldStation, boolean entry) {
            this(worldStation, entry, entry ? 1.0 : -1.0);
        }
    }

    /** One boundary attempt: either a feasible station or an explicit miss. */
    public record PortalBoundaryResult(
            double boundaryStation,
            boolean entry,
            double inwardDirection,
            PortalStationStatus status,
            PortalStation station) {
        public boolean found() {
            return status == PortalStationStatus.FOUND && station != null;
        }
    }

    /** Aggregate of portal boundary attempts for one planPortals call. */
    public record PortalPlanResult(
            List<PortalStation> stations,
            List<PortalBoundaryResult> boundaries,
            int foundCount,
            int notFoundCount) {
        public PortalPlanResult {
            stations = stations != null ? List.copyOf(stations) : List.of();
            boundaries = boundaries != null ? List.copyOf(boundaries) : List.of();
        }

        public static PortalPlanResult empty() {
            return new PortalPlanResult(List.of(), List.of(), 0, 0);
        }
    }

    public static PortalPlanResult planPortals(
            List<ConstructionRun> runs,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double unitsPerBlock,
            ResolvedTunnelStyle style,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            PortalElevationSnapper elevationSnapper) {
        if (runs == null || style == null) {
            return PortalPlanResult.empty();
        }
        double scale = unitsPerBlock > EPSILON ? unitsPerBlock : 1.0;
        Set<String> dedupe = new LinkedHashSet<>();
        List<PortalStation> portals = new ArrayList<>();
        List<PortalBoundaryResult> boundaries = new ArrayList<>();
        int foundCount = 0;
        int notFoundCount = 0;
        for (ConstructionRun run : runs) {
            if (run.type() != RoadConstructionType.TUNNEL || run.length() < EPSILON) {
                continue;
            }
            PortalBoundaryResult entry = findPortalStation(
                run.startStation(), run.endStation(), true, run.length(),
                segments, heightInfos, constructionTypes, crossSections, terrain, scale, style,
                designElevation, buildProfile, profileWaterCrossings, columnResolver, elevationSnapper);
            PortalBoundaryResult exit = findPortalStation(
                run.endStation(), run.startStation(), false, run.length(),
                segments, heightInfos, constructionTypes, crossSections, terrain, scale, style,
                designElevation, buildProfile, profileWaterCrossings, columnResolver, elevationSnapper);
            if (recordBoundary(portals, boundaries, dedupe, entry)) {
                foundCount++;
            } else {
                notFoundCount++;
            }
            if (recordBoundary(portals, boundaries, dedupe, exit)) {
                foundCount++;
            } else {
                notFoundCount++;
            }
        }
        return new PortalPlanResult(portals, boundaries, foundCount, notFoundCount);
    }

    public static PortalStation findPortalNear(List<PortalStation> portals, double worldStation) {
        if (portals == null || portals.isEmpty()) {
            return null;
        }
        for (PortalStation portal : portals) {
            if (Math.abs(portal.worldStation() - worldStation) < 0.75) {
                return portal;
            }
        }
        return null;
    }

    /**
     * @return true when the boundary yielded a newly recorded FOUND station
     */
    private static boolean recordBoundary(
            List<PortalStation> portals,
            List<PortalBoundaryResult> boundaries,
            Set<String> dedupe,
            PortalBoundaryResult result) {
        boundaries.add(result);
        if (!result.found()) {
            return false;
        }
        PortalStation station = result.station();
        String key = String.format("%.3f:%s", station.worldStation(), station.entry());
        if (dedupe.add(key)) {
            portals.add(station);
            return true;
        }
        // Duplicate FOUND still counts as found for metrics of this boundary.
        return true;
    }

    static PortalBoundaryResult findPortalStation(
            double boundaryStation,
            double inwardTargetStation,
            boolean entry,
            double runLength,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double scale,
            ResolvedTunnelStyle style,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            PortalElevationSnapper elevationSnapper) {
        double direction = inwardTargetStation >= boundaryStation ? 1.0 : -1.0;
        double step = Math.max(scale, 1.0);
        int maxSteps = Math.min(
            MAX_PORTAL_SEARCH_STEPS,
            (int) Math.floor(Math.max(0.0, runLength) / (2.0 * step)));
        for (int i = 0; i <= maxSteps; i++) {
            double station = boundaryStation + direction * step * i;
            if (direction > 0 && station > inwardTargetStation + EPSILON) {
                break;
            }
            if (direction < 0 && station < inwardTargetStation - EPSILON) {
                break;
            }
            if (isFeasiblePortalStation(
                station, segments, heightInfos, constructionTypes, crossSections, terrain, scale, style,
                designElevation, buildProfile, profileWaterCrossings, columnResolver, elevationSnapper)) {
                return new PortalBoundaryResult(
                    boundaryStation,
                    entry,
                    direction,
                    PortalStationStatus.FOUND,
                    new PortalStation(station, entry, direction));
            }
        }
        return new PortalBoundaryResult(
            boundaryStation,
            entry,
            direction,
            PortalStationStatus.NOT_FOUND,
            null);
    }

    private static boolean isFeasiblePortalStation(
            double worldStation,
            List<PathSegment> segments,
            List<SegmentHeightInfo> heightInfos,
            List<RoadConstructionType> constructionTypes,
            CrossSectionBuildContext crossSections,
            TerrainSampler terrain,
            double scale,
            ResolvedTunnelStyle style,
            DesignElevationSource designElevation,
            BuildHeightProfile buildProfile,
            List<WaterCrossing> profileWaterCrossings,
            RoadTerrainClearanceUtils.BlockColumnResolver columnResolver,
            PortalElevationSnapper elevationSnapper) {
        BridgeStructureGenerator.StationLocation location =
            BridgeStructureGenerator.locateStationOnPath(segments, scale, worldStation * scale);
        if (location == null || location.segmentIndex() >= segments.size()) {
            return false;
        }
        if (WaterCrossingConstructionResolver.isCausewayFillStation(profileWaterCrossings, worldStation)) {
            return false;
        }
        PathSegment segment = segments.get(location.segmentIndex());
        SegmentHeightInfo info = location.segmentIndex() < heightInfos.size()
            ? heightInfos.get(location.segmentIndex())
            : heightInfos.getLast();
        RoadConstructionType type = RoadConstructionClassifier.constructionTypeAt(
            constructionTypes, location.segmentIndex());
        if (!type.isTunnelFamily()) {
            return false;
        }
        boolean chainForward = crossSections.samplingOriented().forward();
        Vec2d leftNormal = com.plot.plugin.road.pipeline.geometry.PathSegmentGeometry
            .chainLeftNormal(segment, chainForward);
        Vec2d center = segment.start.lerp(segment.end, location.t());
        int targetY = DesignElevationSource.resolveTargetElevation(
            designElevation, buildProfile, info, location.geometryLocal(), location.t(), worldStation);
        if (elevationSnapper != null) {
            targetY = elevationSnapper.snap(center, targetY);
        }
        double chainage = crossSections.chainageAtGeometryLocal(location.geometryLocal());
        ResolvedCrossSection crossSection = crossSections.resolve(chainage);
        int envelopeWidth = RoadCorridorWidth.gradingEnvelopeWidthBlocks(crossSection);
        if (envelopeWidth <= 0) {
            return false;
        }
        TunnelFeasibility feasibility = TunnelFeasibilityChecker.check(
            terrain, center, leftNormal, targetY, envelopeWidth, style, columnResolver, scale);
        return feasibility.valid() && feasibility.minimumCover() >= PORTAL_MIN_COVER_BLOCKS;
    }

    @FunctionalInterface
    public interface PortalElevationSnapper {
        int snap(Vec2d center, int targetY);
    }
}
