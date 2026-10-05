package com.plot.plugin.road.terrain;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
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

    public static RoadTerrainStyle fromStored(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
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
