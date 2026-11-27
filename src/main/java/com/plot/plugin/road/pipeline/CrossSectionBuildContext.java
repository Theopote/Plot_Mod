package com.plot.plugin.road.pipeline;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.lamp.RoadLampPlacement;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.StationCrossSection;
import com.plot.plugin.road.model.section.VariableCrossSectionResolver;
import com.plot.plugin.road.pipeline.facility.StationFacilityJunctionTrim;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.List;

/**
 * 单条边生成时的横断面解析上下文：无变量断面时返回固定值，否则按桩号阶梯解析。
 */
public final class CrossSectionBuildContext {

    private final ResolvedCrossSection fallback;
    private final RoadNetwork network;
    private final Road road;
    private final OrientedRoadSegment oriented;
    private final List<OrientedRoadSegment> lightingOrientations;
    private final boolean setbackStart;
    private final boolean setbackEnd;
    private final RoadSystemConfig config;

    private CrossSectionBuildContext(
            ResolvedCrossSection fallback,
            RoadNetwork network,
            Road road,
            List<OrientedRoadSegment> orientations,
            RoadSystemConfig config,
            boolean setbackStart,
            boolean setbackEnd) {
        this.fallback = fallback;
        this.network = network;
        this.road = road;
        this.oriented = orientations.isEmpty() ? null : orientations.getFirst();
        this.lightingOrientations = orientations;
        this.setbackStart = setbackStart;
        this.setbackEnd = setbackEnd;
        this.config = config;
    }

    public static CrossSectionBuildContext fixed(ResolvedCrossSection section) {
        return new CrossSectionBuildContext(section, null, null, List.of(), null, true, true);
    }

    public static CrossSectionBuildContext forEdge(
            RoadNetwork network,
            RoadEdge edge,
            RoadSystemConfig config) {
        ResolvedCrossSection start = VariableCrossSectionResolver.resolveForEdge(network, edge, config);
        if (network == null || edge == null || config == null) {
            return fixed(start);
        }
        String roadId = edge.getRoadId();
        Road road = roadId != null ? network.getRoad(roadId) : null;
        if (road == null) {
            return fixed(start);
        }
        var lampPlacement = road.getCrossSection().getStreetFurniture().getRoadLampPlacement();
        // Bind slices when station-domain matters: VCS, lamps, or LOOP (dual-slice / seam).
        boolean needsStations = lampPlacement != null
            || VariableCrossSectionResolver.hasVariableSections(road)
            || road.getTopologyMode() == com.plot.plugin.road.model.RoadTopologyMode.LOOP;
        List<OrientedRoadSegment> orientations = needsStations && RoadStationing.isStationable(network, road)
            ? RoadStationing.orientedSegmentsForEdge(network, road, edge.getId())
            : List.of();
        return new CrossSectionBuildContext(start, network, road, orientations, config,
            lampPlacement != null && StationFacilityJunctionTrim.shouldTrimLightingAtNode(network, road, edge.getStartNodeId()),
            lampPlacement != null && StationFacilityJunctionTrim.shouldTrimLightingAtNode(network, road, edge.getEndNodeId()));
    }

    public boolean isVariable() {
        // Do not require oriented binding — missing slices must not mask an existing VCS.
        return network != null
            && road != null
            && config != null
            && VariableCrossSectionResolver.hasVariableSections(road);
    }

    public ResolvedCrossSection fallback() {
        return fallback;
    }

    public OrientedRoadSegment orientedSegment() {
        return isVariable() ? oriented : null;
    }

    public List<OrientedRoadSegment> lightingOrientations() {
        return lightingOrientations;
    }

    public RoadLampPlacement lightingPlacement() {
        return road != null ? road.getCrossSection().getStreetFurniture().getRoadLampPlacement()
            : fallback.roadLampPlacement;
    }

    public boolean setbackStart() {
        return setbackStart;
    }

    public boolean setbackEnd() {
        return setbackEnd;
    }

    public double segmentStartStation() {
        OrientedRoadSegment bound = primaryBoundOrientation();
        return bound != null ? bound.startStation() : 0.0;
    }

    public OrientedRoadSegment samplingOriented() {
        OrientedRoadSegment bound = primaryBoundOrientation();
        if (bound != null) {
            return bound;
        }
        return new OrientedRoadSegment(null, true, null, null, 0.0, Double.POSITIVE_INFINITY);
    }

    /** First bound orientation (VCS or uniform LOOP reverse slice), if any. */
    private OrientedRoadSegment primaryBoundOrientation() {
        if (oriented != null) {
            return oriented;
        }
        if (lightingOrientations != null && !lightingOrientations.isEmpty()) {
            return lightingOrientations.getFirst();
        }
        return null;
    }

    /**
     * Canonical station at geometry-local.
     * Uniform / fixed edges without slices use geometry-local; variable multi-slice misses and
     * failed domain converts are fail-closed {@link Double#NaN} (no silent instance substitute).
     */
    public double chainageAtGeometryLocal(double geometryLocalDistance) {
        OrientedRoadSegment slice = sliceContaining(geometryLocalDistance);
        if (slice == null) {
            return isVariable() ? Double.NaN : geometryLocalDistance;
        }
        if (network != null && road != null && slice.edgeId() != null) {
            var canonical = RoadStationing.resolveCanonicalStationAtEdgeLocal(
                network, road, slice.edgeId(), geometryLocalDistance);
            if (canonical.isPresent()) {
                return canonical.getAsDouble();
            }
            // LOOP / variable: never substitute instance (wrong domain). LINEAR uniform may.
            return failClosedOrLinearInstance(slice, geometryLocalDistance);
        }
        double instance = slice.roadStationAtGeometryLocal(geometryLocalDistance);
        if (network != null && road != null) {
            double canonical = RoadStationing.toCanonicalChainage(network, road, instance);
            if (Double.isFinite(canonical)) {
                return canonical;
            }
            return failClosedOrLinearInstance(slice, geometryLocalDistance);
        }
        return instance;
    }

    /** Seam-invariant physical station for lamp phase grids. */
    public double physicalStationAtGeometryLocal(double geometryLocalDistance) {
        OrientedRoadSegment slice = sliceContaining(geometryLocalDistance);
        if (slice == null) {
            return isVariable() ? Double.NaN : geometryLocalDistance;
        }
        if (network == null || road == null) {
            return slice.roadStationAtGeometryLocal(geometryLocalDistance);
        }
        double instance = slice.roadStationAtGeometryLocal(geometryLocalDistance);
        double physical = RoadStationing.rotatedToPhysical(network, road, instance);
        if (Double.isFinite(physical)) {
            return physical;
        }
        return failClosedOrLinearInstance(slice, geometryLocalDistance);
    }

    private double failClosedOrLinearInstance(OrientedRoadSegment slice, double geometryLocalDistance) {
        if (isVariable()
                || (road != null
                    && road.getTopologyMode() == com.plot.plugin.road.model.RoadTopologyMode.LOOP)) {
            return Double.NaN;
        }
        return slice.roadStationAtGeometryLocal(geometryLocalDistance);
    }

    private OrientedRoadSegment sliceContaining(double geometryLocalDistance) {
        if (lightingOrientations != null) {
            for (OrientedRoadSegment slice : lightingOrientations) {
                if (slice.containsGeometryLocal(geometryLocalDistance)) {
                    return slice;
                }
            }
            if (lightingOrientations.size() == 1) {
                return lightingOrientations.getFirst();
            }
        }
        return oriented;
    }

    public ResolvedCrossSection resolve(double chainageMeters) {
        if (!isVariable()) {
            return fallback;
        }
        return VariableCrossSectionResolver.resolve(network, road, chainageMeters, config);
    }

    /**
     * 旧式路灯：道路默认或任一变截面桩号启用了 streetlightSpacing 时为 true。
     * 新版 {@link RoadLampPlacement} 由调用方另行判断。
     */
    public boolean hasAnyLegacyStreetlightSpacing() {
        if (fallback != null
                && fallback.streetlightSpacing != null
                && fallback.streetlightSpacing > 0) {
            return true;
        }
        if (road == null) {
            return false;
        }
        Integer baseSpacing = road.getCrossSection() != null
            ? road.getCrossSection().getStreetFurniture().getStreetlightSpacing()
            : null;
        if (baseSpacing != null && baseSpacing > 0) {
            return true;
        }
        if (road.getVariableCrossSections() == null) {
            return false;
        }
        for (StationCrossSection entry : road.getVariableCrossSections().getStations()) {
            if (entry == null || entry.getCrossSection() == null) {
                continue;
            }
            Integer spacing = entry.getCrossSection().getStreetFurniture().getStreetlightSpacing();
            if (spacing != null && spacing > 0) {
                return true;
            }
        }
        return false;
    }
}
