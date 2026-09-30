package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Crossing 稳定匹配：同道路对按空间位置对应，避免 station 漂移导致设计参数丢失。 */
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
     * 为检测到的交叉点查找可继承设计参数的已有 Crossing。
     * 优先精确 stableKey；否则在同道路对内按位置最近匹配，歧义时返回 null。
     */
    public static RoadCrossing matchExisting(
            RoadCrossing detected,
            Collection<RoadCrossing> existingCrossings,
            Set<String> alreadyMatchedIds) {
        if (detected == null || existingCrossings == null || existingCrossings.isEmpty()) {
            return null;
        }
        List<RoadCrossing> pairCandidates = new ArrayList<>();
        String pairKey = roadPairKey(detected.roadAId(), detected.roadBId());
        for (RoadCrossing existing : existingCrossings) {
            if (existing == null || alreadyMatchedIds.contains(existing.id())) {
                continue;
            }
            if (roadPairKey(existing.roadAId(), existing.roadBId()).equals(pairKey)) {
                pairCandidates.add(existing);
            }
        }
        if (pairCandidates.isEmpty()) {
            return null;
        }

        for (RoadCrossing candidate : pairCandidates) {
            if (candidate.stableKey().equals(detected.stableKey())) {
                return candidate;
            }
        }

        return matchByPosition(detected, pairCandidates);
    }

    private static RoadCrossing matchByPosition(RoadCrossing detected, List<RoadCrossing> pairCandidates) {
        if (detected.position() == null) {
            return null;
        }
        RoadCrossing best = null;
        double bestDistance = Double.MAX_VALUE;
        double secondBestDistance = Double.MAX_VALUE;
        for (RoadCrossing candidate : pairCandidates) {
            if (candidate.position() == null) {
                continue;
            }
            double distance = detected.position().distance(candidate.position());
            if (distance > MATCH_POSITION_TOLERANCE) {
                continue;
            }
            if (distance < bestDistance) {
                secondBestDistance = bestDistance;
                bestDistance = distance;
                best = candidate;
            } else if (distance < secondBestDistance) {
                secondBestDistance = distance;
            }
        }
        if (best == null) {
            return null;
        }
        if (pairCandidates.size() > 1
                && secondBestDistance <= MATCH_POSITION_TOLERANCE
                && Math.abs(secondBestDistance - bestDistance) <= AMBIGUITY_TOLERANCE) {
            return null;
        }
        return best;
    }

    public static boolean positionsEquivalent(Vec2d a, Vec2d b) {
        return a != null && b != null && RoadGeometryUtils.pointsNear(a, b, MATCH_POSITION_TOLERANCE);
    }
}
