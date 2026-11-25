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
        Set<String> duplicateStableKeys = new HashSet<>();
        Map<String, List<RoadStackCrossing>> byRoad = new HashMap<>();
        for (RoadStackCrossing stack : existing) {
            if (stack == null) {
                continue;
            }
            String key = stack.stableKey();
            if (duplicateStableKeys.contains(key)) {
                // already known duplicate
            } else if (byStableKey.containsKey(key)) {
                byStableKey.remove(key);
                duplicateStableKeys.add(key);
            } else {
                byStableKey.put(key, stack);
            }
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
            String key = detectedStack.stableKey();
            if (duplicateStableKeys.contains(key)) {
                assignments[i] = CrossingMatchAssignment.ambiguous(i);
                continue;
            }
            RoadStackCrossing exact = byStableKey.get(key);
            if (exact != null && !claimedIds.contains(exact.id())) {
                claimedIds.add(exact.id());
                assignments[i] = CrossingMatchAssignment.matched(i, exact.id());
            }
        }

        // Stage 2: collect preferred assists first, then resolve uniquely (order-independent).
        List<Integer> remaining = new ArrayList<>();
        AssistMatchResult[] preferred = new AssistMatchResult[detected.size()];
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
            if (stationMatch.outcome() != CrossingMatchOutcome.NO_MATCH) {
                preferred[i] = stationMatch;
            } else {
                preferred[i] = matchByPositionDetailed(detectedStack, available);
            }
            remaining.add(i);
        }

        Map<String, Integer> preferCount = new HashMap<>();
        for (int i : remaining) {
            AssistMatchResult result = preferred[i];
            if (result != null
                    && result.outcome() == CrossingMatchOutcome.MATCHED
                    && result.existingId() != null) {
                preferCount.merge(result.existingId(), 1, Integer::sum);
            }
        }

        for (int i : remaining) {
            AssistMatchResult result = preferred[i];
            if (result == null || result.outcome() == CrossingMatchOutcome.NO_MATCH) {
                assignments[i] = CrossingMatchAssignment.noMatch(i);
            } else if (result.outcome() == CrossingMatchOutcome.AMBIGUOUS) {
                assignments[i] = CrossingMatchAssignment.ambiguous(i);
            } else if (preferCount.getOrDefault(result.existingId(), 0) > 1) {
                assignments[i] = CrossingMatchAssignment.ambiguous(i);
            } else {
                claimedIds.add(result.existingId());
                assignments[i] = CrossingMatchAssignment.matched(i, result.existingId());
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
