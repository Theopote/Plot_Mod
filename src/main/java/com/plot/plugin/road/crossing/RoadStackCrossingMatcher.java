package com.plot.plugin.road.crossing;

import com.plot.plugin.road.RoadGeometryUtils;

import java.util.Collection;
import java.util.Set;

final class RoadStackCrossingMatcher {
    static final double GEOMETRY_CHANGE_EPSILON = 0.05;

    private RoadStackCrossingMatcher() {
    }

    static RoadStackCrossing matchExisting(
            RoadStackCrossing detected,
            Collection<RoadStackCrossing> existing,
            Set<String> excludedIds) {
        if (detected == null || existing == null) {
            return null;
        }
        RoadStackCrossing keyMatch = null;
        RoadStackCrossing positionMatch = null;
        for (RoadStackCrossing candidate : existing) {
            if (candidate == null || excludedIds.contains(candidate.id())) {
                continue;
            }
            if (!detected.roadId().equals(candidate.roadId())) {
                continue;
            }
            if (detected.stableKey().equals(candidate.stableKey())) {
                keyMatch = candidate;
            }
            if (detected.position() != null
                    && candidate.position() != null
                    && RoadGeometryUtils.pointsNear(
                        detected.position(), candidate.position(), GEOMETRY_CHANGE_EPSILON)) {
                positionMatch = candidate;
            }
        }
        return keyMatch != null ? keyMatch : positionMatch;
    }
}
