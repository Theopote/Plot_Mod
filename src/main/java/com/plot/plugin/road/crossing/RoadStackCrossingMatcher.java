package com.plot.plugin.road.crossing;

import com.plot.plugin.road.RoadGeometryUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RoadStackCrossingMatcher {
    static final double GEOMETRY_CHANGE_EPSILON = 0.05;
    private static final double STATION_MATCH_TOLERANCE = 0.5;
    private static final double AMBIGUITY_TOLERANCE = 0.1;

    private RoadStackCrossingMatcher() {
    }

    static List<CrossingMatchAssignment> matchAll(
            List<RoadStackCrossing> detected,
            Collection<RoadStackCrossing> existingStacks) {
        if (detected == null || detected.isEmpty()) {
            return List.of();
        }
        List<RoadStackCrossing> existing = existingStacks != null
            ? List.copyOf(existingStacks)
            : List.of();
        Map<String, RoadStackCrossing> byStableKey = new HashMap<>();
        Map<String, List<RoadStackCrossing>> byRoad = new HashMap<>();
        for (RoadStackCrossing stack : existing) {
            if (stack == null) {
                continue;
            }
            byStableKey.putIfAbsent(stack.stableKey(), stack);
            byRoad.computeIfAbsent(stack.roadId(), ignored -> new ArrayList<>()).add(stack);
        }

        CrossingMatchAssignment[] assignments = new CrossingMatchAssignment[detected.size()];
        Set<String> claimedIds = new HashSet<>();

        for (int i = 0; i < detected.size(); i++) {
            RoadStackCrossing detectedStack = detected.get(i);
            if (detectedStack == null) {
                assignments[i] = CrossingMatchAssignment.noMatch(i);
                continue;
            }
            RoadStackCrossing exact = byStableKey.get(detectedStack.stableKey());
            if (exact != null && !claimedIds.contains(exact.id())) {
                claimedIds.add(exact.id());
                assignments[i] = CrossingMatchAssignment.matched(i, exact.id());
            }
        }

        for (int i = 0; i < detected.size(); i++) {
            if (assignments[i] != null) {
                continue;
            }
            RoadStackCrossing detectedStack = detected.get(i);
            if (detectedStack == null) {
                assignments[i] = CrossingMatchAssignment.noMatch(i);
                continue;
            }
            List<RoadStackCrossing> sameRoad = byRoad.getOrDefault(detectedStack.roadId(), List.of());
            List<RoadStackCrossing> available = new ArrayList<>();
            for (RoadStackCrossing candidate : sameRoad) {
                if (!claimedIds.contains(candidate.id())) {
                    available.add(candidate);
                }
            }

            AssistMatchResult stationMatch = matchByStationPairDetailed(detectedStack, available);
            if (stationMatch.outcome() == CrossingMatchOutcome.MATCHED) {
                claimedIds.add(stationMatch.existingId());
                assignments[i] = CrossingMatchAssignment.matched(i, stationMatch.existingId());
                continue;
            }
            if (stationMatch.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                assignments[i] = CrossingMatchAssignment.ambiguous(i);
                continue;
            }

            AssistMatchResult positionMatch = matchByPositionDetailed(detectedStack, available);
            if (positionMatch.outcome() == CrossingMatchOutcome.MATCHED) {
                claimedIds.add(positionMatch.existingId());
                assignments[i] = CrossingMatchAssignment.matched(i, positionMatch.existingId());
            } else if (positionMatch.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                assignments[i] = CrossingMatchAssignment.ambiguous(i);
            } else {
                assignments[i] = CrossingMatchAssignment.noMatch(i);
            }
        }

        return List.of(assignments);
    }

    static RoadStackCrossing matchExisting(
            RoadStackCrossing detected,
            Collection<RoadStackCrossing> existing,
            Set<String> excludedIds) {
        if (detected == null || existing == null) {
            return null;
        }
        List<RoadStackCrossing> filtered = new ArrayList<>();
        for (RoadStackCrossing candidate : existing) {
            if (candidate != null && (excludedIds == null || !excludedIds.contains(candidate.id()))) {
                filtered.add(candidate);
            }
        }
        List<CrossingMatchAssignment> assignments = matchAll(List.of(detected), filtered);
        if (assignments.isEmpty() || assignments.getFirst().outcome() != CrossingMatchOutcome.MATCHED) {
            return null;
        }
        String id = assignments.getFirst().existingId();
        for (RoadStackCrossing candidate : filtered) {
            if (candidate.id().equals(id)) {
                return candidate;
            }
        }
        return null;
    }

    private static AssistMatchResult matchByStationPairDetailed(
            RoadStackCrossing detected,
            List<RoadStackCrossing> candidates) {
        RoadStackCrossing best = null;
        double bestScore = Double.MAX_VALUE;
        double secondBestScore = Double.MAX_VALUE;
        int nearCount = 0;
        for (RoadStackCrossing candidate : candidates) {
            double score = stationPairDistance(detected, candidate);
            if (score > STATION_MATCH_TOLERANCE) {
                continue;
            }
            nearCount++;
            if (score < bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score < secondBestScore) {
                secondBestScore = score;
            }
        }
        if (best == null) {
            return AssistMatchResult.noMatch();
        }
        if (nearCount > 1
                && secondBestScore <= STATION_MATCH_TOLERANCE
                && Math.abs(secondBestScore - bestScore) <= AMBIGUITY_TOLERANCE) {
            return AssistMatchResult.ambiguous();
        }
        return AssistMatchResult.matched(best.id());
    }

    private static AssistMatchResult matchByPositionDetailed(
            RoadStackCrossing detected,
            List<RoadStackCrossing> candidates) {
        if (detected.position() == null) {
            return AssistMatchResult.noMatch();
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
        if (near.isEmpty() || best == null) {
            return AssistMatchResult.noMatch();
        }
        if (near.size() > 1) {
            return AssistMatchResult.ambiguous();
        }
        return AssistMatchResult.matched(best.id());
    }

    private static double stationPairDistance(RoadStackCrossing a, RoadStackCrossing b) {
        double direct = Math.abs(a.stationA() - b.stationA()) + Math.abs(a.stationB() - b.stationB());
        double swapped = Math.abs(a.stationA() - b.stationB()) + Math.abs(a.stationB() - b.stationA());
        return Math.min(direct, swapped) * 0.5;
    }

    private record AssistMatchResult(CrossingMatchOutcome outcome, String existingId) {
        static AssistMatchResult matched(String id) {
            return new AssistMatchResult(CrossingMatchOutcome.MATCHED, id);
        }

        static AssistMatchResult noMatch() {
            return new AssistMatchResult(CrossingMatchOutcome.NO_MATCH, null);
        }

        static AssistMatchResult ambiguous() {
            return new AssistMatchResult(CrossingMatchOutcome.AMBIGUOUS, null);
        }
    }
}
