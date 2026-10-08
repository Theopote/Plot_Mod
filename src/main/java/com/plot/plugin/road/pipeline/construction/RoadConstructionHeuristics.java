package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadConstructionEvaluator;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.terrain.RoadTerrainStyle;

/**
 * Internal construction heuristics keyed by {@link RoadTerrainStyle}.
 * Product UI exposes presets only; numeric thresholds and complexity weights stay here.
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

    public static final double ENVIRONMENT_SAMPLE_SPACING_METERS = 2.0;

    private RoadConstructionHeuristics() {
    }

    public static int bridgeThreshold(RoadTerrainStyle style) {
        return switch (style != null ? style : RoadTerrainStyle.BALANCED) {
            case FOLLOW -> 6;
            case BALANCED -> 3;
            case SMOOTH -> 2;
        };
    }

    public static int tunnelThreshold(RoadTerrainStyle style) {
        return switch (style != null ? style : RoadTerrainStyle.BALANCED) {
            case FOLLOW -> 8;
            case BALANCED -> 4;
            case SMOOTH -> 3;
        };
    }

    public static int bridgeThreshold(RoadSystemConfig config) {
        return bridgeThreshold(config != null ? config.getTerrainStyle() : null);
    }

    public static int tunnelThreshold(RoadSystemConfig config) {
        return tunnelThreshold(config != null ? config.getTerrainStyle() : null);
    }

    public static int bridgeThreshold(Road road, RoadSystemConfig config) {
        return bridgeThreshold(RoadTerrainStyle.effective(road, config));
    }

    public static int tunnelThreshold(Road road, RoadSystemConfig config) {
        return tunnelThreshold(RoadTerrainStyle.effective(road, config));
    }

    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            RoadTerrainStyle style) {
        RoadTerrainStyle effective = style != null ? style : RoadTerrainStyle.BALANCED;
        return new RoadConstructionEvaluator.RoadConstructionScoreConfig(
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

    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            RoadSystemConfig config) {
        return constructionConfig(config != null ? config.getTerrainStyle() : null);
    }

    public static RoadConstructionEvaluator.RoadConstructionScoreConfig constructionConfig(
            Road road,
            RoadSystemConfig config) {
        return constructionConfig(RoadTerrainStyle.effective(road, config));
    }
}
