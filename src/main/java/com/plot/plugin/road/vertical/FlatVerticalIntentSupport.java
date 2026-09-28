package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadUniformElevationUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.core.terrain.TerrainSampler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves, migrates, and syncs flat-road vertical intent. */
public final class FlatVerticalIntentSupport {

    private static final double EPSILON = 1e-6;
    private static final double PROFILE_SAMPLE_SPACING = 5.0;

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
        enableFlatWithBase(network, road, config, recommendBaseElevation(network, road));
    }

    public static void enableFlatWithBase(
            RoadNetwork network,
            Road road,
            RoadSystemConfig config,
            double baseElevation) {
        if (road == null || network == null || !Double.isFinite(baseElevation)) {
            return;
        }
        FlatVerticalIntent intent = new FlatVerticalIntent(baseElevation);
        for (Map.Entry<String, Double> entry
                : VerticalAlignmentJunctionSynchronizer.junctionStations(network, road).entrySet()) {
            RoadNode node = network.getNode(entry.getKey());
            if (node != null && node.getManualElevation() != null
                    && Math.abs(node.getManualElevation() - baseElevation) > EPSILON) {
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
            road.setFlatVerticalIntent(null);
            road.setVerticalAlignment(null);
            road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
            return;
        }
        if (road.getVerticalMode() == RoadVerticalMode.MANUAL_PROFILE) {
            return;
        }
        road.setVerticalMode(RoadVerticalMode.AUTO_SMOOTH);
    }

    public static double recommendBaseElevation(RoadNetwork network, Road road) {
        return recommendBaseElevation(network, road, null, null);
    }

    public static double recommendBaseElevation(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config) {
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
            if (VerticalAlignmentGeometry.isEvaluable(alignment)) {
                double length = network != null && RoadStationing.isStationable(network, road)
                    ? RoadStationing.canonicalLength(network, road)
                    : alignment.endStation();
                Double median = medianSampledProfileElevation(alignment, length);
                if (median != null) {
                    return median;
                }
            }
        }
        if (terrain != null && network != null && config != null
                && RoadStationing.isStationable(network, road)) {
            FlatElevationRecommendation optimized =
                recommendOptimizedElevation(network, road, terrain, config);
            if (optimized.hasRecommendation()) {
                return optimized.best().elevation();
            }
            RoadUniformElevationUtils.FlatRoadRecommendation recommendation =
                RoadUniformElevationUtils.recommendMedianForRoad(network, road, terrain, config);
            if (recommendation.sampleCount() > 0) {
                return recommendation.elevation();
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

    /** Derived flat baseline analysis; not persisted until the user adopts a candidate. */
    public static FlatElevationRecommendation recommendOptimizedElevation(
            RoadNetwork network,
            Road road,
            TerrainSampler terrain,
            RoadSystemConfig config) {
        return FlatElevationOptimizer.evaluate(network, road, terrain, config);
    }

    static Double medianSampledProfileElevation(RoadVerticalAlignment alignment, double roadLength) {
        if (alignment == null || !VerticalAlignmentGeometry.isEvaluable(alignment)
                || !Double.isFinite(roadLength) || roadLength <= EPSILON) {
            return null;
        }
        List<Double> samples = new ArrayList<>();
        for (double station = 0.0; station <= roadLength + EPSILON; station += PROFILE_SAMPLE_SPACING) {
            VerticalAlignmentGeometry.elevationAt(alignment, station).ifPresent(samples::add);
        }
        VerticalAlignmentGeometry.elevationAt(alignment, roadLength).ifPresent(elevation -> {
            if (samples.isEmpty() || Math.abs(samples.getLast() - elevation) > EPSILON) {
                samples.add(elevation);
            }
        });
        if (samples.isEmpty()) {
            return null;
        }
        samples.sort(Double::compare);
        int middle = samples.size() / 2;
        return samples.size() % 2 == 1
            ? samples.get(middle)
            : (samples.get(middle - 1) + samples.get(middle)) / 2.0;
    }

    static double pviCountAverageElevation(RoadVerticalAlignment alignment) {
        double sum = 0.0;
        int count = 0;
        for (PointOfVerticalIntersection pvi : alignment.getPvis()) {
            sum += pvi.getElevation();
            count++;
        }
        return count > 0 ? sum / count : 64.0;
    }

    public record BatchElevationSummary(boolean mixed, double commonElevation, int flatRoadCount) { }

    public static BatchElevationSummary summarizeFlatBaseElevations(
            RoadNetwork network,
            Collection<String> roadIds) {
        if (network == null || roadIds == null || roadIds.isEmpty()) {
            return new BatchElevationSummary(false, 64.0, 0);
        }
        Double common = null;
        int flatCount = 0;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null || road.getVerticalMode() != RoadVerticalMode.FLAT) {
                continue;
            }
            FlatVerticalIntent intent = resolveIntent(network, road);
            if (intent == null) {
                continue;
            }
            flatCount++;
            if (common == null) {
                common = intent.getBaseElevation();
            } else if (Math.abs(common - intent.getBaseElevation()) > EPSILON) {
                return new BatchElevationSummary(true, common, flatCount);
            }
        }
        return new BatchElevationSummary(false, common != null ? common : 64.0, flatCount);
    }

    public static int applyBatchTerrainAdaptive(
            RoadNetwork network,
            Collection<String> roadIds,
            RoadSystemConfig config) {
        if (network == null || roadIds == null) {
            return 0;
        }
        int changed = 0;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null || !RoadStationing.isStationable(network, road)) {
                continue;
            }
            if (RoadVerticalStrategy.fromRoad(road) == RoadVerticalStrategy.TERRAIN_ADAPTIVE) {
                continue;
            }
            enableTerrainAdaptive(network, road, config);
            changed++;
        }
        return changed;
    }

    public static int applyBatchFlatRecommended(
            RoadNetwork network,
            Collection<String> roadIds,
            RoadSystemConfig config) {
        if (network == null || roadIds == null) {
            return 0;
        }
        int changed = 0;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (!canUseFlatStrategy(network, road)) {
                continue;
            }
            enableFlatWithBase(
                network, road, config, recommendBaseElevation(network, road));
            changed++;
        }
        return changed;
    }

    public static int applyBatchFlatUniformBase(
            RoadNetwork network,
            Collection<String> roadIds,
            double baseElevation,
            RoadSystemConfig config) {
        if (network == null || roadIds == null || !Double.isFinite(baseElevation)) {
            return 0;
        }
        int changed = 0;
        for (String roadId : roadIds) {
            Road road = network.getRoad(roadId);
            if (road == null) {
                continue;
            }
            if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
                FlatVerticalIntent intent = resolveIntent(network, road);
                if (intent != null
                        && Math.abs(intent.getBaseElevation() - baseElevation) <= EPSILON) {
                    continue;
                }
                setBaseElevation(
                    network, road, baseElevation, road.getEffectiveMaxSlope(config));
                changed++;
                continue;
            }
            if (!canUseFlatStrategy(network, road)) {
                continue;
            }
            enableFlatWithBase(network, road, config, baseElevation);
            changed++;
        }
        return changed;
    }

    public static boolean canUseFlatStrategy(RoadNetwork network, Road road) {
        if (road == null || network == null || !RoadStationing.isStationable(network, road)) {
            return false;
        }
        double length = RoadStationing.canonicalLength(network, road);
        return VerticalProfileDesignRules.slopeAllowed(length)
            || road.getVerticalMode() == RoadVerticalMode.FLAT;
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
