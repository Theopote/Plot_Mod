package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.terrain.RoadTerrainStyle;

/**
 * Internal construction and terrain-adaptation heuristics. Product UI exposes presets only;
 * numeric thresholds and complexity weights stay here.
 */
public final class RoadConstructionHeuristics {

    public static final double MIN_CONSIDERATION_HEIGHT = 2.0;
    public static final double MIN_STRUCTURE_RUN = 3.0;

    public static final double CUT_WEIGHT = 1.0;
    public static final double FILL_WEIGHT = 1.0;
    public static final double BRIDGE_PREFERENCE = 15.0;
    public static final double BRIDGE_PREFERENCE_PER_LENGTH = 0.8;
    public static final double TUNNEL_PREFERENCE = 25.0;
    public static final double TUNNEL_PREFERENCE_PER_LENGTH = 1.5;

    public static final int TUNNEL_CLEARANCE_HEIGHT = 5;
    public static final int TUNNEL_SIDE_CLEARANCE = 1;
    public static final int TUNNEL_LINING_THICKNESS = 1;

    public static final double ENVIRONMENT_SAMPLE_SPACING_METERS = 2.0;

    public enum TerrainAdaptationPreset {
        FOLLOW,
        BALANCED,
        FLATTEN
    }

    private RoadConstructionHeuristics() {
    }

    public static TerrainAdaptationPreset presetFromConfig(RoadSystemConfig config) {
        return presetFromStyle(config != null ? config.getTerrainStyle() : null);
    }

    public static TerrainAdaptationPreset presetFromStyle(RoadTerrainStyle style) {
        RoadTerrainStyle effective = style != null ? style : RoadTerrainStyle.BALANCED;
        return effective.constructionPreset();
    }

    public static int bridgeThreshold(TerrainAdaptationPreset preset) {
        return switch (preset != null ? preset : TerrainAdaptationPreset.BALANCED) {
            case FOLLOW -> 6;
            case BALANCED -> 3;
            case FLATTEN -> 2;
        };
    }

    public static int tunnelThreshold(TerrainAdaptationPreset preset) {
        return switch (preset != null ? preset : TerrainAdaptationPreset.BALANCED) {
            case FOLLOW -> 8;
            case BALANCED -> 4;
            case FLATTEN -> 3;
        };
    }

    public static int bridgeThreshold(RoadSystemConfig config) {
        return bridgeThreshold(presetFromConfig(config));
    }

    public static int tunnelThreshold(RoadSystemConfig config) {
        return tunnelThreshold(presetFromConfig(config));
    }

    public static RoadConstructionEvaluator.RoadConstructionCostConfig constructionConfig(
            TerrainAdaptationPreset preset) {
        TerrainAdaptationPreset effective = preset != null ? preset : TerrainAdaptationPreset.BALANCED;
        return new RoadConstructionEvaluator.RoadConstructionCostConfig(
            FILL_WEIGHT,
            BRIDGE_PREFERENCE,
            BRIDGE_PREFERENCE_PER_LENGTH,
            CUT_WEIGHT,
            TUNNEL_PREFERENCE,
            TUNNEL_PREFERENCE_PER_LENGTH,
            MIN_CONSIDERATION_HEIGHT,
            bridgeThreshold(effective),
            tunnelThreshold(effective));
    }

    public static RoadConstructionEvaluator.RoadConstructionCostConfig constructionConfig(RoadSystemConfig config) {
        return constructionConfig(presetFromConfig(config));
    }

    public static RoadConstructionEvaluator.RoadConstructionCostConfig constructionConfig(
            Road road,
            RoadSystemConfig config) {
        return constructionConfig(RoadTerrainStyle.effective(road, config));
    }

    public static RoadConstructionEvaluator.RoadConstructionCostConfig constructionConfig(RoadTerrainStyle style) {
        return constructionConfig(presetFromStyle(style));
    }

    public static int bridgeThreshold(Road road, RoadSystemConfig config) {
        return bridgeThreshold(RoadTerrainStyle.effective(road, config));
    }

    public static int tunnelThreshold(Road road, RoadSystemConfig config) {
        return tunnelThreshold(RoadTerrainStyle.effective(road, config));
    }

    public static int bridgeThreshold(RoadTerrainStyle style) {
        return bridgeThreshold(presetFromStyle(style));
    }

    public static int tunnelThreshold(RoadTerrainStyle style) {
        return tunnelThreshold(presetFromStyle(style));
    }
}
