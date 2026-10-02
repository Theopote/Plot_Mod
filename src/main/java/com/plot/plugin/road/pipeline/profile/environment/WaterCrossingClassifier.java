package com.plot.plugin.road.pipeline.profile.environment;

import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;

import java.util.ArrayList;
import java.util.List;

/** Classifies detected crossings and expands approach / exit transition zones. */
public final class WaterCrossingClassifier {

    private WaterCrossingClassifier() {
    }

    public static List<WaterCrossing> classify(
            List<WaterCrossing> detected,
            WaterCrossingSettings settings,
            TerrainFollowPreset preset,
            double profileLengthMeters) {
        if (detected == null || detected.isEmpty()) {
            return List.of();
        }
        double approachMeters = preset != null
            ? preset.minGradeTransitionMeters()
            : TerrainFollowPreset.STANDARD.minGradeTransitionMeters();
        List<WaterCrossing> classified = new ArrayList<>();
        for (WaterCrossing crossing : detected) {
            WaterCrossingStrategy strategy = resolveStrategy(crossing, settings);
            double approachStart = Math.max(0.0, crossing.crossingStartStation() - approachMeters);
            double exitEnd = Math.min(profileLengthMeters, crossing.crossingEndStation() + approachMeters);
            classified.add(new WaterCrossing(
                approachStart,
                crossing.crossingStartStation(),
                crossing.crossingEndStation(),
                exitEnd,
                crossing.firstWaterSampleStation(),
                crossing.lastWaterSampleStation(),
                crossing.lengthMeters(),
                crossing.averageDepth(),
                crossing.maxDepth(),
                crossing.entryBankTerrainY(),
                crossing.exitBankTerrainY(),
                crossing.waterSurfaceY(),
                strategy));
        }
        return List.copyOf(classified);
    }

    private static WaterCrossingStrategy resolveStrategy(
            WaterCrossing crossing,
            WaterCrossingSettings settings) {
        if (settings.allowUnderwaterRoad()) {
            return WaterCrossingStrategy.TUNNEL_CANDIDATE;
        }
        if (crossing.lengthMeters() + 1e-9 < settings.causewayMaxLengthMeters()
                && crossing.maxDepth() <= settings.causewayMaxDepthBlocks()
                && crossing.lengthMeters() + 1e-9 < settings.bridgePreferredMinLengthMeters()) {
            return WaterCrossingStrategy.CAUSEWAY;
        }
        if (crossing.lengthMeters() + 1e-9 >= settings.longBridgeLengthMeters()) {
            return WaterCrossingStrategy.LONG_BRIDGE;
        }
        return WaterCrossingStrategy.BRIDGE;
    }
}
