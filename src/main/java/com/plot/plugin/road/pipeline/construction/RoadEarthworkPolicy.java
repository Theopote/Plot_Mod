package com.plot.plugin.road.pipeline.construction;

import com.plot.plugin.road.RoadConstructionType;

/**
 * Corridor earthwork rules by construction type: open batter vs structure-only spans.
 */
public enum RoadEarthworkPolicy {
    /** Shoulder and continuous batter to natural ground. */
    NORMAL_BATTER,
    /** No corridor batter or open subgrade fill (deck / tunnel cavity only). */
    STRUCTURE_ONLY,
    /** Connection segments; v1 uses same batter rules as {@link #NORMAL_BATTER}. */
    TRANSITION_BATTER;

    public static RoadEarthworkPolicy of(RoadConstructionType type) {
        if (type == null) {
            return NORMAL_BATTER;
        }
        if (type.isStructureInterior()) {
            return STRUCTURE_ONLY;
        }
        if (type.isPortalOrAbutment()) {
            return TRANSITION_BATTER;
        }
        return NORMAL_BATTER;
    }

    public boolean allowsCorridorBatter() {
        return this == NORMAL_BATTER || this == TRANSITION_BATTER;
    }

    public boolean allowsOpenSubgradeFill() {
        return this != STRUCTURE_ONLY;
    }

    /** Whether expandClearanceEnvelope may write open corridor ClearanceCell columns. */
    public boolean allowsOpenCorridorClearance() {
        return this != STRUCTURE_ONLY;
    }
}
