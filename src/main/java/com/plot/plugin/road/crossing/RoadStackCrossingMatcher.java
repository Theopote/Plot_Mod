package com.plot.plugin.road.crossing;

import com.plot.plugin.road.RoadGeometryUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

final class RoadStackCrossingMatcher {
    static final double GEOMETRY_CHANGE_EPSILON = 0.05;
    private static final double STATION_MATCH_TOLERANCE = 0.5;
    private static final double AMBIGUITY_TOLERANCE = 0.1;

    private RoadStackCrossingMatcher() {
    }

    static RoadStackCrossing matchExisting(
            RoadStackCrossing detected,
            Collection<RoadStackCrossing> existing,
            Set<String> excludedIds) {
        if (detected == null || existing == null) {
            return null;
        }
        List<RoadStackCrossing> sameRoad = new ArrayList<>();
        for (RoadStackCrossing candidate : existing) {
            if (candidate == null || excludedIds.contains(candidate.id())) {
                continue;
            }
            if (!detected.roadId().equals(candidate.roadId())) {
                continue;
            }
            if (detected.stableKey().equals(candidate.stableKey())) {
                return candidate;
            }
            sameRoad.add(candidate);
        }
        if (sameRoad.isEmpty()) {
            return null;
        }

        RoadStackCrossing stationMatch = matchByStationPair(detected, sameRoad);
        if (stationMatch != null) {
            return stationMatch;
        }
        return matchByPosition(detected, sameRoad);
    }

    private static RoadStackCrossing matchByStationPair(
            RoadStackCrossing detected,
            List<RoadStackCrossing> candidates) {
        RoadStackCrossing best = null;
        double bestScore = Double.MAX_VALUE;
        double secondBestScore = Double.MAX_VALUE;
        for (RoadStackCrossing candidate : candidates) {
            double score = stationPairDistance(detected, candidate);
            if (score > STATION_MATCH_TOLERANCE) {
                continue;
            }
            if (score < bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score < secondBestScore) {
                secondBestScore = score;
            }
        }
        if (best == null) {
            return null;
        }
        if (candidates.size() > 1
                && secondBestScore <= STATION_MATCH_TOLERANCE
                && Math.abs(secondBestScore - bestScore) <= AMBIGUITY_TOLERANCE) {
            return null;
        }
        return best;
    }

    private static RoadStackCrossing matchByPosition(
            RoadStackCrossing detected,
            List<RoadStackCrossing> candidates) {
        if (detected.position() == null) {
            return null;
        }
        List<RoadStackCrossing> near = new ArrayList<>();
        RoadStackCrossing best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RoadStackCrossing candidate : candidates) {
            if (candidate.position() == null) {
                continue;
            }
            if (!RoadGeometryUtils.pointsNear(
                    detected.position(), candidate.position(), GEOMETRY_CHANGE_EPSILON)) {
                continue;
            }
            near.add(candidate);
            double distance = detected.position().distance(candidate.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (near.isEmpty()) {
            return null;
        }
        // Multiple relationships can share the same plan position — never pick arbitrarily.
        if (near.size() > 1) {
            return null;
        }
        return best;
    }

    private static double stationPairDistance(RoadStackCrossing a, RoadStackCrossing b) {
        double direct = Math.abs(a.stationA() - b.stationA()) + Math.abs(a.stationB() - b.stationB());
        double swapped = Math.abs(a.stationA() - b.stationB()) + Math.abs(a.stationB() - b.stationA());
        return Math.min(direct, swapped) * 0.5;
    }
}
