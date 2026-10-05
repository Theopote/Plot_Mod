package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.List;

/** Validates that road vertical control elevations stay within world block Y limits. */
public final class RoadVerticalBoundsValidator {

    private RoadVerticalBoundsValidator() {
    }

    public static List<Violation> findViolations(RoadNetwork network, RoadElevationBounds bounds) {
        List<Violation> violations = new ArrayList<>();
        if (network == null || bounds == null) {
            return violations;
        }
        for (Road road : network.getRoads().values()) {
            RoadVerticalAlignment alignment = road.getVerticalAlignment();
            if (alignment == null) {
                continue;
            }
            for (int i = 0; i < alignment.pviCount(); i++) {
                PointOfVerticalIntersection pvi = alignment.getPvis().get(i);
                double elevation = pvi.getElevation();
                if (!bounds.contains(elevation)) {
                    violations.add(new Violation(
                        road.getId(),
                        i,
                        elevation,
                        bounds.minY(),
                        bounds.maxY()));
                }
            }
        }
        return violations;
    }

    public record Violation(
            String roadId,
            int pviIndex,
            double elevation,
            double minY,
            double maxY) {
    }
}
