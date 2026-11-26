package com.plot.plugin.road.crossing;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative copy of two-road and stack crossings from a working network onto live
 * for the given road ids. Used after verified reverse migration — not for ordinary
 * centerline geometry sync.
 */
public final class RoadCrossingRegistrySync {

    private RoadCrossingRegistrySync() {
    }

    public static void copyForRoads(RoadNetwork live, RoadNetwork working, Set<String> roadIds) {
        if (live == null || working == null || roadIds == null || roadIds.isEmpty()) {
            return;
        }
        Set<String> relevantCrossingIds = new HashSet<>();
        for (RoadCrossing crossing : working.getCrossings().values()) {
            if (crossing == null) {
                continue;
            }
            if (roadIds.contains(crossing.roadAId()) || roadIds.contains(crossing.roadBId())) {
                live.registerCrossing(crossing);
                relevantCrossingIds.add(crossing.id());
            }
        }
        for (RoadCrossing crossing : List.copyOf(live.getCrossings().values())) {
            if (crossing == null) {
                continue;
            }
            if ((roadIds.contains(crossing.roadAId()) || roadIds.contains(crossing.roadBId()))
                    && !relevantCrossingIds.contains(crossing.id())
                    && working.getCrossing(crossing.id()) == null) {
                live.removeCrossing(crossing.id());
            }
        }
        Set<String> relevantStacks = new HashSet<>();
        for (RoadStackCrossing stack : working.getStackCrossings().values()) {
            if (stack != null && roadIds.contains(stack.roadId())) {
                live.registerStackCrossing(stack);
                relevantStacks.add(stack.id());
            }
        }
        for (RoadStackCrossing stack : List.copyOf(live.getStackCrossings().values())) {
            if (stack != null
                    && roadIds.contains(stack.roadId())
                    && !relevantStacks.contains(stack.id())
                    && working.getStackCrossing(stack.id()) == null) {
                live.removeStackCrossing(stack.id());
            }
        }
    }

    /** True when working registry for {@code roadId} differs from baseline (open snapshot / live). */
    public static boolean roadCrossingsDiffer(RoadNetwork baseline, RoadNetwork working, String roadId) {
        if (baseline == null || working == null || roadId == null || roadId.isBlank()) {
            return false;
        }
        Set<String> baselineIds = new HashSet<>();
        for (RoadCrossing crossing : baseline.getCrossings().values()) {
            if (crossing != null && (roadId.equals(crossing.roadAId()) || roadId.equals(crossing.roadBId()))) {
                baselineIds.add(crossing.id());
                RoadCrossing other = working.getCrossing(crossing.id());
                if (other == null || !crossingStateEqual(crossing, other)) {
                    return true;
                }
            }
        }
        for (RoadCrossing crossing : working.getCrossings().values()) {
            if (crossing != null
                    && (roadId.equals(crossing.roadAId()) || roadId.equals(crossing.roadBId()))
                    && !baselineIds.contains(crossing.id())) {
                return true;
            }
        }
        Set<String> baselineStacks = new HashSet<>();
        for (RoadStackCrossing stack : baseline.getStackCrossings().values()) {
            if (stack != null && roadId.equals(stack.roadId())) {
                baselineStacks.add(stack.id());
                RoadStackCrossing other = working.getStackCrossing(stack.id());
                if (other == null || !stackStateEqual(stack, other)) {
                    return true;
                }
            }
        }
        for (RoadStackCrossing stack : working.getStackCrossings().values()) {
            if (stack != null && roadId.equals(stack.roadId()) && !baselineStacks.contains(stack.id())) {
                return true;
            }
        }
        return false;
    }

    private static boolean crossingStateEqual(RoadCrossing left, RoadCrossing right) {
        return Objects.equals(left.roadAId(), right.roadAId())
            && Objects.equals(left.roadBId(), right.roadBId())
            && Double.compare(left.stationA(), right.stationA()) == 0
            && Double.compare(left.stationB(), right.stationB()) == 0
            && left.type() == right.type()
            && Objects.equals(left.elevatedRoadId(), right.elevatedRoadId())
            && Objects.equals(left.crossingClearance(), right.crossingClearance())
            && Objects.equals(left.position(), right.position());
    }

    private static boolean stackStateEqual(RoadStackCrossing left, RoadStackCrossing right) {
        return Objects.equals(left.roadId(), right.roadId())
            && Double.compare(left.stationA(), right.stationA()) == 0
            && Double.compare(left.stationB(), right.stationB()) == 0
            && left.type() == right.type()
            && Objects.equals(left.crossingClearance(), right.crossingClearance())
            && Objects.equals(left.lowerStation(), right.lowerStation())
            && Objects.equals(left.upperStation(), right.upperStation())
            && Objects.equals(left.position(), right.position());
    }
}
