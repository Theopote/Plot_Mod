package com.plot.plugin.road.vertical;

import com.plot.core.terrain.TerrainSampler;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.Map;
import java.util.Objects;

/** Fingerprint inputs that affect flat elevation recommendations (path, intent, junctions, config). */
public final class FlatElevationRecommendationSignature {
    private FlatElevationRecommendationSignature() {
    }

    public static int contextSignature(RoadNetwork network, Road road, RoadSystemConfig config) {
        Objects.requireNonNull(road, "road");
        int hash = 17;
        hash = 31 * hash + road.getId().hashCode();
        hash = 31 * hash + road.getSegmentIds().hashCode();
        if (RoadStationing.isStationable(network, road)) {
            hash = 31 * hash + Double.hashCode(RoadStationing.canonicalLength(network, road));
        }
        hash = 31 * hash + Integer.hashCode(road.getEffectiveWidth(config));
        hash = 31 * hash + Float.hashCode(road.getEffectiveMaxSlope(config));
        hash = 31 * hash + intentSignature(FlatVerticalIntentSupport.resolveIntent(network, road));
        hash = 31 * hash + junctionSignature(network, road);
        hash = 31 * hash + costConfigSignature(config);
        return hash;
    }

    public static int terrainFingerprint(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        if (terrain == null || !RoadStationing.isStationable(network, road)) {
            return 0;
        }
        int hash = 1;
        for (FlatElevationOptimizer.TerrainSegmentSample sample
                : FlatElevationOptimizer.sampleRoadTerrainSegments(network, road, terrain, config)) {
            hash = 31 * hash + sample.groundY();
        }
        return hash;
    }

    private static int intentSignature(FlatVerticalIntent intent) {
        if (intent == null) {
            return 0;
        }
        int hash = Double.hashCode(intent.getBaseElevation());
        for (Map.Entry<String, Double> entry : intent.getIntersectionOverrides().entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + Double.hashCode(entry.getValue());
        }
        return hash;
    }

    private static int junctionSignature(RoadNetwork network, Road road) {
        int hash = 1;
        for (Map.Entry<String, Double> entry
                : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
            RoadNode node = network.getNode(entry.getKey());
            if (node == null) {
                continue;
            }
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + Objects.hashCode(node.getManualElevation());
            hash = 31 * hash + Boolean.hashCode(node.isGradeSeparated());
            hash = 31 * hash + Objects.hashCode(node.getElevatedRoadId());
            hash = 31 * hash + Objects.hashCode(node.getCrossingClearance());
        }
        return hash;
    }

    private static int costConfigSignature(RoadSystemConfig config) {
        int hash = 1;
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.FILL_WEIGHT);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.BRIDGE_PREFERENCE);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.BRIDGE_PREFERENCE_PER_LENGTH);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.CUT_WEIGHT);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.TUNNEL_PREFERENCE);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.TUNNEL_PREFERENCE_PER_LENGTH);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.MIN_CONSIDERATION_HEIGHT);
        hash = 31 * hash + RoadConstructionHeuristics.bridgeThreshold(config);
        hash = 31 * hash + RoadConstructionHeuristics.tunnelThreshold(config);
        hash = 31 * hash + Double.hashCode(RoadConstructionHeuristics.MIN_STRUCTURE_RUN);
        hash = 31 * hash + Objects.hashCode(config.getTerrainAdaptation());
        hash = 31 * hash + Double.hashCode(config.getPathSampleDistance());
        return hash;
    }
}
