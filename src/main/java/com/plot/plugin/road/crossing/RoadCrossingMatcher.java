package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Crossing 稳定匹配：分阶段批量匹配，避免近邻抢占精确身份。 */
public final class RoadCrossingMatcher {
    /** 在同道路对内寻找可继承的旧 Crossing 时的空间匹配容差。 */
    static final double MATCH_POSITION_TOLERANCE = 0.5;
    /** 判定 Crossing 几何是否发生实质性变化时的坐标/桩号容差。 */
    static final double GEOMETRY_CHANGE_EPSILON = 1e-6;
    private static final double AMBIGUITY_TOLERANCE = 0.1;

    private RoadCrossingMatcher() {
    }

    public static String roadPairKey(String roadAId, String roadBId) {
        if (roadAId == null || roadBId == null) {
            return "";
        }
        if (roadAId.compareTo(roadBId) <= 0) {
            return roadAId + "|" + roadBId;
        }
        return roadBId + "|" + roadAId;
    }

    /**
     * Batch staged match: exact stableKey first (globally), then unique position assists
     * within each road pair. Weak matches never preempt another detection's exact target.
     */
    public static List<CrossingMatchAssignment> matchAll(
            List<RoadCrossing> detected,
            Collection<RoadCrossing> existingCrossings) {
        if (detected == null || detected.isEmpty()) {
            return List.of();
        }
        List<RoadCrossing> existing = existingCrossings != null
            ? List.copyOf(existingCrossings)
            : List.of();
        Map<String, RoadCrossing> byStableKey = new HashMap<>();
        Map<String, List<RoadCrossing>> byPair = new HashMap<>();
        for (RoadCrossing crossing : existing) {
            if (crossing == null) {
                continue;
            }
            byStableKey.putIfAbsent(crossing.stableKey(), crossing);
            byPair.computeIfAbsent(
                    roadPairKey(crossing.roadAId(), crossing.roadBId()),
                    ignored -> new ArrayList<>())
                .add(crossing);
        }

        CrossingMatchAssignment[] assignments = new CrossingMatchAssignment[detected.size()];
        Set<String> claimedIds = new HashSet<>();

        // Stage 1: exact stableKey.
        for (int i = 0; i < detected.size(); i++) {
            RoadCrossing detectedCrossing = detected.get(i);
            if (detectedCrossing == null) {
                assignments[i] = CrossingMatchAssignment.noMatch(i);
                continue;
            }
            RoadCrossing exact = byStableKey.get(detectedCrossing.stableKey());
            if (exact != null && !claimedIds.contains(exact.id())) {
                claimedIds.add(exact.id());
                assignments[i] = CrossingMatchAssignment.matched(i, exact.id());
            }
        }

        // Stage 2: unique position assist within road pair.
        for (int i = 0; i < detected.size(); i++) {
            if (assignments[i] != null) {
                continue;
            }
            RoadCrossing detectedCrossing = detected.get(i);
            if (detectedCrossing == null) {
                assignments[i] = CrossingMatchAssignment.noMatch(i);
                continue;
            }
            List<RoadCrossing> pair = byPair.getOrDefault(
                roadPairKey(detectedCrossing.roadAId(), detectedCrossing.roadBId()),
                List.of());
            List<RoadCrossing> available = new ArrayList<>();
            for (RoadCrossing candidate : pair) {
                if (!claimedIds.contains(candidate.id())) {
                    available.add(candidate);
                }
            }
            PositionMatchResult positionMatch = matchByPositionDetailed(detectedCrossing, available);
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

    /**
     * Legacy single-object match used by older tests; prefer {@link #matchAll} for reconcile.
     * Returns null for both {@link CrossingMatchOutcome#NO_MATCH} and {@link CrossingMatchOutcome#AMBIGUOUS}.
     */
    public static RoadCrossing matchExisting(
            RoadCrossing detected,
            Collection<RoadCrossing> existingCrossings,
            Set<String> alreadyMatchedIds) {
        if (detected == null || existingCrossings == null || existingCrossings.isEmpty()) {
            return null;
        }
        List<RoadCrossing> filtered = new ArrayList<>();
        for (RoadCrossing existing : existingCrossings) {
            if (existing != null && (alreadyMatchedIds == null || !alreadyMatchedIds.contains(existing.id()))) {
                filtered.add(existing);
            }
        }
        List<CrossingMatchAssignment> assignments = matchAll(List.of(detected), filtered);
        if (assignments.isEmpty() || assignments.getFirst().outcome() != CrossingMatchOutcome.MATCHED) {
            return null;
        }
        String id = assignments.getFirst().existingId();
        for (RoadCrossing existing : filtered) {
            if (existing.id().equals(id)) {
                return existing;
            }
        }
        return null;
    }

    private static PositionMatchResult matchByPositionDetailed(
            RoadCrossing detected,
            List<RoadCrossing> pairCandidates) {
        if (detected.position() == null || pairCandidates.isEmpty()) {
            return PositionMatchResult.noMatch();
        }
        RoadCrossing best = null;
        double bestDistance = Double.MAX_VALUE;
        double secondBestDistance = Double.MAX_VALUE;
        int nearCount = 0;
        for (RoadCrossing candidate : pairCandidates) {
            if (candidate.position() == null) {
                continue;
            }
            double distance = detected.position().distance(candidate.position());
            if (distance > MATCH_POSITION_TOLERANCE) {
                continue;
            }
            nearCount++;
            if (distance < bestDistance) {
                secondBestDistance = bestDistance;
                bestDistance = distance;
                best = candidate;
            } else if (distance < secondBestDistance) {
                secondBestDistance = distance;
            }
        }
        if (best == null) {
            return PositionMatchResult.noMatch();
        }
        if (nearCount > 1
                && secondBestDistance <= MATCH_POSITION_TOLERANCE
                && Math.abs(secondBestDistance - bestDistance) <= AMBIGUITY_TOLERANCE) {
            return PositionMatchResult.ambiguous();
        }
        return PositionMatchResult.matched(best.id());
    }

    public static boolean positionsEquivalent(Vec2d a, Vec2d b) {
        return a != null && b != null && RoadGeometryUtils.pointsNear(a, b, MATCH_POSITION_TOLERANCE);
    }

    private record PositionMatchResult(CrossingMatchOutcome outcome, String existingId) {
        static PositionMatchResult matched(String id) {
            return new PositionMatchResult(CrossingMatchOutcome.MATCHED, id);
        }

        static PositionMatchResult noMatch() {
            return new PositionMatchResult(CrossingMatchOutcome.NO_MATCH, null);
        }

        static PositionMatchResult ambiguous() {
            return new PositionMatchResult(CrossingMatchOutcome.AMBIGUOUS, null);
        }
    }
}
