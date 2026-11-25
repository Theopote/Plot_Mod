package com.plot.plugin.road.crossing;

import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Migrates crossing registry ownership after logical road split/merge using known
 * split stations — never Matcher tolerance.
 */
public final class RoadCrossingOwnershipMigrator {
    private static final double EPSILON = 1e-6;

    private RoadCrossingOwnershipMigrator() {
    }

    /**
     * After {@code headRoadId} was split at {@code splitStation}, tail segments live on
     * {@code tailRoadId}. Rewrite two-road and stack crossings accordingly.
     */
    public static void afterRoadSplit(
            RoadNetwork network,
            String headRoadId,
            String tailRoadId,
            double splitStation) {
        if (network == null || headRoadId == null || tailRoadId == null || !Double.isFinite(splitStation)) {
            return;
        }
        migrateTwoRoadAfterSplit(network, headRoadId, tailRoadId, splitStation);
        migrateStackAfterSplit(network, headRoadId, tailRoadId, splitStation);
    }

    /**
     * After {@code removedTailRoadId} was merged into {@code headRoadId}.
     *
     * @param headLengthBefore length of head before merge (used when tail was appended)
     * @param appendTail       true when tail was appended after head; false when prepended
     */
    public static void afterRoadMerge(
            RoadNetwork network,
            String headRoadId,
            String removedTailRoadId,
            double headLengthBefore,
            boolean appendTail) {
        if (network == null || headRoadId == null || removedTailRoadId == null) {
            return;
        }
        migrateTwoRoadAfterMerge(network, headRoadId, removedTailRoadId, headLengthBefore, appendTail);
        migrateStackAfterMerge(network, headRoadId, removedTailRoadId, headLengthBefore, appendTail);
    }

    private static void migrateTwoRoadAfterSplit(
            RoadNetwork network,
            String headRoadId,
            String tailRoadId,
            double splitStation) {
        List<RoadCrossing> crossings = new ArrayList<>(network.getCrossings().values());
        for (RoadCrossing crossing : crossings) {
            if (!crossing.involvesRoad(headRoadId)) {
                continue;
            }
            String roadA = crossing.roadAId();
            String roadB = crossing.roadBId();
            double stationA = crossing.stationA();
            double stationB = crossing.stationB();
            String elevated = crossing.elevatedRoadId();

            if (headRoadId.equals(roadA) && stationA >= splitStation - EPSILON) {
                roadA = tailRoadId;
                stationA = Math.max(0.0, stationA - splitStation);
            }
            if (headRoadId.equals(roadB) && stationB >= splitStation - EPSILON) {
                roadB = tailRoadId;
                stationB = Math.max(0.0, stationB - splitStation);
            }
            if (elevated != null && elevated.equals(headRoadId)) {
                // Elevated pass moved with whichever arm carried that station.
                if (crossing.roadAId().equals(headRoadId) && crossing.stationA() >= splitStation - EPSILON) {
                    elevated = tailRoadId;
                } else if (crossing.roadBId().equals(headRoadId)
                        && crossing.stationB() >= splitStation - EPSILON) {
                    elevated = tailRoadId;
                }
            }

            if (roadA.equals(crossing.roadAId())
                    && roadB.equals(crossing.roadBId())
                    && Math.abs(stationA - crossing.stationA()) <= EPSILON
                    && Math.abs(stationB - crossing.stationB()) <= EPSILON
                    && java.util.Objects.equals(elevated, crossing.elevatedRoadId())) {
                continue;
            }
            network.registerCrossing(new RoadCrossing(
                crossing.id(),
                roadA,
                stationA,
                roadB,
                stationB,
                crossing.position(),
                crossing.type(),
                elevated,
                crossing.crossingClearance(),
                crossing.sharedElevation(),
                crossing.cornerRadius()));
        }
    }

    private static void migrateStackAfterSplit(
            RoadNetwork network,
            String headRoadId,
            String tailRoadId,
            double splitStation) {
        List<RoadStackCrossing> stacks = new ArrayList<>(network.getStackCrossings().values());
        for (RoadStackCrossing stack : stacks) {
            if (!headRoadId.equals(stack.roadId())) {
                continue;
            }
            boolean aOnTail = stack.stationA() >= splitStation - EPSILON;
            boolean bOnTail = stack.stationB() >= splitStation - EPSILON;
            if (aOnTail == bOnTail) {
                String roadId = aOnTail ? tailRoadId : headRoadId;
                double stationA = aOnTail ? Math.max(0.0, stack.stationA() - splitStation) : stack.stationA();
                double stationB = bOnTail ? Math.max(0.0, stack.stationB() - splitStation) : stack.stationB();
                Double lower = aOnTail ? offsetOptional(stack.lowerStation(), splitStation) : stack.lowerStation();
                Double upper = aOnTail ? offsetOptional(stack.upperStation(), splitStation) : stack.upperStation();
                network.registerStackCrossing(new RoadStackCrossing(
                    stack.id(),
                    roadId,
                    stack.position(),
                    stationA,
                    stationB,
                    stack.type(),
                    lower,
                    upper,
                    stack.crossingClearance(),
                    stack.sharedElevation()));
                continue;
            }
            // Passes land on different roads → convert to ordinary two-road crossing.
            String roadA = aOnTail ? tailRoadId : headRoadId;
            String roadB = bOnTail ? tailRoadId : headRoadId;
            double stationA = aOnTail ? Math.max(0.0, stack.stationA() - splitStation) : stack.stationA();
            double stationB = bOnTail ? Math.max(0.0, stack.stationB() - splitStation) : stack.stationB();
            String elevated = null;
            if (stack.isGradeSeparated()) {
                double upper = stack.resolvedUpperStation();
                boolean upperWasA = Math.abs(upper - stack.stationA()) <= Math.abs(upper - stack.stationB());
                elevated = upperWasA ? roadA : roadB;
            }
            network.removeStackCrossing(stack.id());
            network.registerCrossing(new RoadCrossing(
                stack.id(),
                roadA,
                stationA,
                roadB,
                stationB,
                stack.position(),
                stack.type(),
                elevated,
                stack.crossingClearance(),
                stack.sharedElevation(),
                null));
        }
    }

    private static void migrateTwoRoadAfterMerge(
            RoadNetwork network,
            String headRoadId,
            String removedTailRoadId,
            double headLengthBefore,
            boolean appendTail) {
        List<RoadCrossing> crossings = new ArrayList<>(network.getCrossings().values());
        for (RoadCrossing crossing : crossings) {
            if (!crossing.involvesRoad(removedTailRoadId) && !crossing.involvesRoad(headRoadId)) {
                continue;
            }
            String roadA = rewriteMergedRoadId(crossing.roadAId(), headRoadId, removedTailRoadId);
            String roadB = rewriteMergedRoadId(crossing.roadBId(), headRoadId, removedTailRoadId);
            double stationA = rewriteMergedStation(
                crossing.roadAId(), crossing.stationA(), headRoadId, removedTailRoadId, headLengthBefore, appendTail);
            double stationB = rewriteMergedStation(
                crossing.roadBId(), crossing.stationB(), headRoadId, removedTailRoadId, headLengthBefore, appendTail);
            String elevated = crossing.elevatedRoadId() == null
                ? null
                : rewriteMergedRoadId(crossing.elevatedRoadId(), headRoadId, removedTailRoadId);

            // Same road on both arms after merge → restore stack identity when possible.
            if (roadA.equals(roadB)) {
                network.removeCrossing(crossing.id());
                Double lower = null;
                Double upper = null;
                if (crossing.type() == CrossingType.GRADE_SEPARATED) {
                    if (crossing.elevatedRoadId() != null
                            && crossing.elevatedRoadId().equals(crossing.roadAId())) {
                        upper = stationA;
                        lower = stationB;
                    } else if (crossing.elevatedRoadId() != null
                            && crossing.elevatedRoadId().equals(crossing.roadBId())) {
                        upper = stationB;
                        lower = stationA;
                    } else {
                        upper = Math.max(stationA, stationB);
                        lower = Math.min(stationA, stationB);
                    }
                    network.registerStackCrossing(new RoadStackCrossing(
                        crossing.id(),
                        roadA,
                        crossing.position(),
                        stationA,
                        stationB,
                        CrossingType.GRADE_SEPARATED,
                        lower,
                        upper,
                        crossing.crossingClearance(),
                        crossing.sharedElevation()));
                } else {
                    network.registerStackCrossing(new RoadStackCrossing(
                        crossing.id(),
                        roadA,
                        crossing.position(),
                        stationA,
                        stationB,
                        crossing.type(),
                        null,
                        null,
                        crossing.crossingClearance(),
                        crossing.sharedElevation()));
                }
                continue;
            }

            network.registerCrossing(new RoadCrossing(
                crossing.id(),
                roadA,
                stationA,
                roadB,
                stationB,
                crossing.position(),
                crossing.type(),
                elevated,
                crossing.crossingClearance(),
                crossing.sharedElevation(),
                crossing.cornerRadius()));
        }
    }

    private static void migrateStackAfterMerge(
            RoadNetwork network,
            String headRoadId,
            String removedTailRoadId,
            double headLengthBefore,
            boolean appendTail) {
        List<RoadStackCrossing> stacks = new ArrayList<>(network.getStackCrossings().values());
        for (RoadStackCrossing stack : stacks) {
            if (!removedTailRoadId.equals(stack.roadId()) && !headRoadId.equals(stack.roadId())) {
                continue;
            }
            if (headRoadId.equals(stack.roadId()) && appendTail) {
                // Stations on head unchanged when tail is appended.
                continue;
            }
            double stationA = rewriteMergedStation(
                stack.roadId(), stack.stationA(), headRoadId, removedTailRoadId, headLengthBefore, appendTail);
            double stationB = rewriteMergedStation(
                stack.roadId(), stack.stationB(), headRoadId, removedTailRoadId, headLengthBefore, appendTail);
            Double lower = stack.lowerStation() == null
                ? null
                : rewriteMergedStation(
                    stack.roadId(), stack.lowerStation(), headRoadId, removedTailRoadId, headLengthBefore, appendTail);
            Double upper = stack.upperStation() == null
                ? null
                : rewriteMergedStation(
                    stack.roadId(), stack.upperStation(), headRoadId, removedTailRoadId, headLengthBefore, appendTail);
            network.registerStackCrossing(new RoadStackCrossing(
                stack.id(),
                headRoadId,
                stack.position(),
                stationA,
                stationB,
                stack.type(),
                lower,
                upper,
                stack.crossingClearance(),
                stack.sharedElevation()));
        }
    }

    private static String rewriteMergedRoadId(String roadId, String headRoadId, String removedTailRoadId) {
        if (removedTailRoadId.equals(roadId)) {
            return headRoadId;
        }
        return roadId;
    }

    private static double rewriteMergedStation(
            String originalRoadId,
            double station,
            String headRoadId,
            String removedTailRoadId,
            double headLengthBefore,
            boolean appendTail) {
        if (removedTailRoadId.equals(originalRoadId)) {
            if (appendTail) {
                return station + Math.max(0.0, headLengthBefore);
            }
            return station;
        }
        if (headRoadId.equals(originalRoadId) && !appendTail) {
            // Head was shifted by prepended tail length — caller passes headLengthBefore as
            // pre-merge head length; for prepend the offset is the former tail length which
            // is encoded by rewriting only former-tail stations above. Head stations shift
            // when prepend: station += formerTailLength. We don't have formerTailLength here
            // separately; headLengthBefore for prepend path is unused for head stations —
            // Manager will pass formerTailLength as the offset via a dedicated param.
            // Convention: when !appendTail, headLengthBefore means formerTailLength.
            return station + Math.max(0.0, headLengthBefore);
        }
        return station;
    }

    private static Double offsetOptional(Double station, double splitStation) {
        if (station == null) {
            return null;
        }
        return Math.max(0.0, station - splitStation);
    }

}
