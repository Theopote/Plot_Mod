package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves, migrates, and syncs flat-road vertical intent. */
public final class FlatVerticalIntentSupport {

    private static final double EPSILON = 1e-6;

    private FlatVerticalIntentSupport() {
    }

    public static FlatVerticalIntent resolveIntent(RoadNetwork network, Road road) {
        if (road == null) {
            return null;
        }
        FlatVerticalIntent stored = road.getFlatVerticalIntent();
        if (stored != null) {
            return stored;
        }
        if (road.getVerticalMode() != RoadVerticalMode.FLAT) {
            return null;
        }
        FlatVerticalIntent migrated = migrateLegacyFlat(network, road);
        if (migrated != null) {
            road.setFlatVerticalIntent(migrated);
        }
        return migrated;
    }

    public static void syncCompiledAlignment(RoadNetwork network, Road road, double maxGradePercent) {
        if (road == null || road.getVerticalMode() != RoadVerticalMode.FLAT) {
            return;
        }
        FlatVerticalIntent intent = resolveIntent(network, road);
        if (intent == null) {
            road.setVerticalAlignment(null);
            return;
        }
        RoadVerticalAlignment compiled = FlatProfileCompiler.compile(network, road, intent, maxGradePercent);
        road.setVerticalAlignment(compiled);
    }

    public static void setBaseElevation(
            RoadNetwork network,
            Road road,
            double baseElevation,
            double maxGradePercent) {
        FlatVerticalIntent intent = resolveIntent(network, road);
        if (intent == null) {
            return;
        }
        intent.setBaseElevation(baseElevation);
        pruneOverridesMatchingBase(intent);
        syncCompiledAlignment(network, road, maxGradePercent);
    }

    public static void applyJunctionElevation(
            RoadNetwork network,
            Road road,
            String nodeId,
            Double elevation,
            double maxGradePercent) {
        if (elevation == null) {
            return;
        }
        FlatVerticalIntent intent = resolveIntent(network, road);
        if (intent == null) {
            return;
        }
        if (Math.abs(elevation - intent.getBaseElevation()) <= EPSILON) {
            intent.removeOverride(nodeId);
        } else {
            intent.setIntersectionOverride(nodeId, elevation);
        }
        syncCompiledAlignment(network, road, maxGradePercent);
    }

    public static void enableFlat(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (road == null || network == null) {
            return;
        }
        double base = recommendBaseElevation(network, road);
        FlatVerticalIntent intent = new FlatVerticalIntent(base);
        for (Map.Entry<String, Double> entry
                : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
            RoadNode node = network.getNode(entry.getKey());
            if (node != null && node.getManualElevation() != null
                    && Math.abs(node.getManualElevation() - base) > EPSILON) {
                intent.setIntersectionOverride(entry.getKey(), node.getManualElevation());
            }
        }
        road.setFlatVerticalIntent(intent);
        road.setVerticalMode(RoadVerticalMode.FLAT);
        double maxGrade = road.getEffectiveMaxSlope(config);
        syncCompiledAlignment(network, road, maxGrade);
    }

    public static void enableTerrainAdaptive(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config) {
        if (road == null) {
            return;
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            FlatVerticalIntent intent = resolveIntent(network, road);
            if (intent != null && network != null && config != null) {
                RoadVerticalAlignment compiled = FlatProfileCompiler.compile(
                    network, road, intent, road.getEffectiveMaxSlope(config));
                road.setVerticalAlignment(compiled);
                road.setVerticalMode(RoadVerticalMode.MANUAL_PROFILE);
            } else {
                road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
                road.setVerticalAlignment(null);
            }
            road.setFlatVerticalIntent(null);
            return;
        }
        if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
            return;
        }
        road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
    }

    public static double recommendBaseElevation(RoadNetwork network, Road road) {
        if (road == null) {
            return 64.0;
        }
        FlatVerticalIntent stored = road.getFlatVerticalIntent();
        if (stored != null) {
            return stored.getBaseElevation();
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (alignment != null && !alignment.isEmpty()) {
            if (VerticalProfileDesignRules.isFlat(alignment)) {
                return alignment.getPvis().getFirst().getElevation();
            }
            double sum = 0.0;
            int count = 0;
            for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
                sum += pvi.getElevation();
                count++;
            }
            if (count > 0) {
                return sum / count;
            }
        }
        if (network != null && RoadStationing.isStationable(network, road)) {
            for (Map.Entry<String, Double> entry
                    : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
                RoadNode node = network.getNode(entry.getKey());
                if (node != null && node.getManualElevation() != null) {
                    return node.getManualElevation();
                }
            }
        }
        return 64.0;
    }

    private static FlatVerticalIntent migrateLegacyFlat(RoadNetwork network, Road road) {
        double base = recommendBaseElevation(network, road);
        Map<String, Double> overrides = new LinkedHashMap<>();
        if (network != null) {
            for (Map.Entry<String, Double> entry
                    : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
                RoadNode node = network.getNode(entry.getKey());
                if (node != null && node.getManualElevation() != null
                        && Math.abs(node.getManualElevation() - base) > EPSILON) {
                    overrides.put(entry.getKey(), node.getManualElevation());
                }
            }
        }
        return new FlatVerticalIntent(base, overrides);
    }

    private static void pruneOverridesMatchingBase(FlatVerticalIntent intent) {
        for (String nodeId : intent.getIntersectionOverrides().keySet()) {
            Double elevation = intent.getIntersectionOverride(nodeId);
            if (elevation != null
                    && Math.abs(elevation - intent.getBaseElevation()) <= EPSILON) {
                intent.removeOverride(nodeId);
            }
        }
    }
}
