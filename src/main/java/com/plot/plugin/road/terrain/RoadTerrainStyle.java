package com.plot.plugin.road.terrain;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.pipeline.construction.RoadConstructionHeuristics;
import com.plot.plugin.road.pipeline.profile.terrain.TerrainFollowPreset;

/**
 * Unified product terrain style: profile smoothing and construction tendency are chosen together.
 */
public enum RoadTerrainStyle {
    /** Close to terrain detail; prefers fill/cut over bridges and tunnels. */
    FOLLOW,
    /** Default balance between terrain following and smoothing. */
    BALANCED,
    /** Smoother profile with more bridges/tunnels on height gaps. */
    SMOOTH;

    public TerrainFollowPreset followPreset() {
        return switch (this) {
            case FOLLOW -> TerrainFollowPreset.TIGHT;
            case BALANCED -> TerrainFollowPreset.STANDARD;
            case SMOOTH -> TerrainFollowPreset.GENTLE;
        };
    }

    public RoadConstructionHeuristics.TerrainAdaptationPreset constructionPreset() {
        return switch (this) {
            case FOLLOW -> RoadConstructionHeuristics.TerrainAdaptationPreset.FOLLOW;
            case BALANCED -> RoadConstructionHeuristics.TerrainAdaptationPreset.BALANCED;
            case SMOOTH -> RoadConstructionHeuristics.TerrainAdaptationPreset.FLATTEN;
        };
    }

    public static RoadTerrainStyle fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fromLegacyFollowPreset(TerrainFollowPreset.fromStored(value));
        }
    }

    public static RoadTerrainStyle fromLegacyFollowPreset(TerrainFollowPreset preset) {
        if (preset == null) {
            return null;
        }
        return switch (preset) {
            case TIGHT -> FOLLOW;
            case STANDARD -> BALANCED;
            case GENTLE -> SMOOTH;
        };
    }

    public static RoadTerrainStyle fromLegacyAdaptation(
            RoadConstructionHeuristics.TerrainAdaptationPreset preset) {
        if (preset == null) {
            return null;
        }
        return switch (preset) {
            case FOLLOW -> FOLLOW;
            case BALANCED -> BALANCED;
            case FLATTEN -> SMOOTH;
        };
    }

    public static RoadTerrainStyle effective(Road road, RoadSystemConfig config) {
        RoadTerrainStyle stored = road != null ? road.getStoredTerrainStyle() : null;
        if (stored != null) {
            return stored;
        }
        if (config != null) {
            return config.getTerrainStyle();
        }
        return BALANCED;
    }
}
