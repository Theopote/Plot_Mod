package com.plot.plugin.road.crossing;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.RoadStation;
import com.plot.plugin.road.station.RoadStationing;
import com.plot.plugin.road.station.SegmentStation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migrates crossing registry ownership after logical topology edits using physical pass
 * bindings (edge + local arc). Never assumes travel direction or widens Matcher tolerance.
 */
public final class RoadCrossingOwnershipMigrator {
    private static final double EPSILON = 1e-6;
    private static final double NODE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private RoadCrossingOwnershipMigrator() {
    }

    public enum Kind { TWO_ROAD, STACK }

    public enum PassRole { A, B }

    /** Ephemeral capture of one physical pass (edit-transaction only). */
    public record CrossingPassBinding(
            String crossingId,
            Kind kind,
            PassRole role,
            String roadId,
            String edgeId,
            double localDistance,
            double originalStation,
            Vec2d worldPosition,
            CrossingType type,
            String elevatedRoadId,
            Double crossingClearance,
            Double sharedElevation,
            Double cornerRadius,
            Double lowerStationHint,
            Double upperStationHint) {
        public CrossingPassBinding {
            if (worldPosition != null) {
                worldPosition = worldPosition.copy();
            }
        }
    }

    public record CaptureResult(List<CrossingPassBinding> bindings, boolean hadUnresolved) {
        public CaptureResult {
            bindings = bindings != null ? List.copyOf(bindings) : List.of();
        }

        public boolean isEmpty() {
            return bindings.isEmpty();
        }
    }

    public record ApplyResult(boolean complete, int rewritten, int removedSpurious) {
        public boolean isPending() {
            return !complete;
        }
    }

    public static CaptureResult captureForRoads(RoadNetwork network, Set<String> roadIds) {
        if (network == null || roadIds == null || roadIds.isEmpty()) {
            return new CaptureResult(List.of(), false);
        }
        List<CrossingPassBinding> bindings = new ArrayList<>();
        boolean unresolved = false;
        for (RoadCrossing crossing : network.getCrossings().values()) {
            if (!roadIds.contains(crossing.roadAId()) && !roadIds.contains(crossing.roadBId())) {
                continue;
            }
            Optional<CrossingPassBinding> a = captureTwoRoadPass(network, crossing, PassRole.A);
            Optional<CrossingPassBinding> b = captureTwoRoadPass(network, crossing, PassRole.B);
            if (a.isEmpty() || b.isEmpty()) {
                unresolved = true;
                continue;
            }
            bindings.add(a.get());
            bindings.add(b.get());
        }
        for (RoadStackCrossing stack : network.getStackCrossings().values()) {
            if (!roadIds.contains(stack.roadId())) {
                continue;
            }
            Optional<CrossingPassBinding> a = captureStackPass(network, stack, PassRole.A);
            Optional<CrossingPassBinding> b = captureStackPass(network, stack, PassRole.B);
            if (a.isEmpty() || b.isEmpty()) {
                unresolved = true;
                continue;
            }
            bindings.add(a.get());
            bindings.add(b.get());
        }
        return new CaptureResult(bindings, unresolved);
    }

    /**
     * After edge storage reverse, remap captured geometry-local distances {@code d' = L - d}
     * so the same world point resolves. Does not change canonical-station semantics by itself —
     * Reverse Edge keeps station; Reverse Road mirrors via flipped design walk.
     */
    public static CaptureResult remapLocalDistancesAfterStorageReverse(
            RoadNetwork network,
            CaptureResult capture,
            Set<String> reversedEdgeIds) {
        if (network == null || capture == null || capture.isEmpty()
                || reversedEdgeIds == null || reversedEdgeIds.isEmpty()) {
            return capture;
        }
        List<CrossingPassBinding> remapped = new ArrayList<>(capture.bindings().size());
        for (CrossingPassBinding binding : capture.bindings()) {
            if (binding.edgeId() == null || !reversedEdgeIds.contains(binding.edgeId())) {
                remapped.add(binding);
                continue;
            }
            RoadEdge edge = network.getEdge(binding.edgeId());
            double length = edge != null ? edge.getLength() : 0.0;
            double local = Math.max(0.0, Math.min(length, length - binding.localDistance()));
            remapped.add(new CrossingPassBinding(
                binding.crossingId(),
                binding.kind(),
                binding.role(),
                binding.roadId(),
                binding.edgeId(),
                local,
                binding.originalStation(),
                binding.worldPosition(),
                binding.type(),
                binding.elevatedRoadId(),
                binding.crossingClearance(),
                binding.sharedElevation(),
                binding.cornerRadius(),
                binding.lowerStationHint(),
                binding.upperStationHint()));
        }
        return new CaptureResult(remapped, capture.hadUnresolved());
    }

    /** Logical split/merge: unresolved passes fail the edit (do not drop designs). */
    public static ApplyResult reapply(RoadNetwork network, CaptureResult capture) {
        return reapply(network, capture, Set.of());
    }

    /**
     * Re-resolve captured passes after a topology edit.
     *
     * @param deletedEdgeIds edges removed in this edit; only crossings whose physical pass
     *                       used one of these edges may be dropped. All other resolve/conversion
     *                       failures leave the design untouched and set {@code complete=false}.
     */
    public static ApplyResult reapply(
            RoadNetwork network,
            CaptureResult capture,
            Set<String> deletedEdgeIds) {
        if (network == null || capture == null) {
            return new ApplyResult(true, 0, 0);
        }
        if (capture.isEmpty()) {
            return new ApplyResult(!capture.hadUnresolved(), 0, 0);
        }
        Set<String> deleted = deletedEdgeIds != null ? deletedEdgeIds : Set.of();
        Map<String, List<CrossingPassBinding>> twoRoad = new HashMap<>();
        Map<String, List<CrossingPassBinding>> stacks = new HashMap<>();
        for (CrossingPassBinding binding : capture.bindings()) {
            if (binding.kind() == Kind.TWO_ROAD) {
                twoRoad.computeIfAbsent(binding.crossingId(), ignored -> new ArrayList<>()).add(binding);
            } else {
                stacks.computeIfAbsent(binding.crossingId(), ignored -> new ArrayList<>()).add(binding);
            }
        }
        int rewritten = 0;
        int removed = 0;
        boolean complete = !capture.hadUnresolved();

        for (Map.Entry<String, List<CrossingPassBinding>> entry : twoRoad.entrySet()) {
            CrossingPassBinding passA = findRole(entry.getValue(), PassRole.A);
            CrossingPassBinding passB = findRole(entry.getValue(), PassRole.B);
            if (passA == null || passB == null) {
                complete = false;
                continue;
            }
            Optional<ResolvedPass> resolvedA = resolvePass(network, passA);
            Optional<ResolvedPass> resolvedB = resolvePass(network, passB);
            if (resolvedA.isEmpty() || resolvedB.isEmpty()) {
                if (passEdgeDeleted(passA, passB, resolvedA, resolvedB, deleted)) {
                    network.removeCrossing(entry.getKey());
                    removed++;
                } else {
                    complete = false;
                }
                continue;
            }
            ResolvedPass a = resolvedA.get();
            ResolvedPass b = resolvedB.get();
            String elevated = remapElevated(
                passA.elevatedRoadId(), passA.roadId(), passB.roadId(), a.roadId(), b.roadId());

            if (a.roadId().equals(b.roadId())) {
                if (!canConvertTwoRoadToStack(network, passA, passB, a, b, entry.getKey())) {
                    complete = false;
                    continue;
                }
                Double lower = null;
                Double upper = null;
                if (passA.type() == CrossingType.GRADE_SEPARATED) {
                    if (passA.elevatedRoadId().equals(passA.roadId())) {
                        upper = a.station();
                        lower = b.station();
                    } else {
                        upper = b.station();
                        lower = a.station();
                    }
                }
                network.removeCrossing(entry.getKey());
                network.registerStackCrossing(new RoadStackCrossing(
                    entry.getKey(),
                    a.roadId(),
                    midpoint(a.position(), b.position()),
                    a.station(),
                    b.station(),
                    passA.type(),
                    lower,
                    upper,
                    passA.crossingClearance(),
                    passA.sharedElevation()));
                rewritten++;
                continue;
            }

            network.registerCrossing(new RoadCrossing(
                entry.getKey(),
                a.roadId(),
                a.station(),
                b.roadId(),
                b.station(),
                midpoint(a.position(), b.position()),
                passA.type(),
                elevated,
                passA.crossingClearance(),
                passA.sharedElevation(),
                passA.cornerRadius()));
            rewritten++;
        }

        for (Map.Entry<String, List<CrossingPassBinding>> entry : stacks.entrySet()) {
            CrossingPassBinding passA = findRole(entry.getValue(), PassRole.A);
            CrossingPassBinding passB = findRole(entry.getValue(), PassRole.B);
            if (passA == null || passB == null) {
                complete = false;
                continue;
            }
            Optional<ResolvedPass> resolvedA = resolvePass(network, passA);
            Optional<ResolvedPass> resolvedB = resolvePass(network, passB);
            if (resolvedA.isEmpty() || resolvedB.isEmpty()) {
                if (passEdgeDeleted(passA, passB, resolvedA, resolvedB, deleted)) {
                    network.removeStackCrossing(entry.getKey());
                    removed++;
                } else {
                    complete = false;
                }
                continue;
            }
            ResolvedPass a = resolvedA.get();
            ResolvedPass b = resolvedB.get();

            if (!a.roadId().equals(b.roadId())) {
                String elevated = null;
                if (passA.type() == CrossingType.GRADE_SEPARATED) {
                    if (passA.upperStationHint() == null) {
                        complete = false;
                        continue;
                    }
                    double da = Math.abs(passA.upperStationHint() - passA.originalStation());
                    double db = Math.abs(passA.upperStationHint() - passB.originalStation());
                    elevated = da <= db ? a.roadId() : b.roadId();
                }
                network.removeStackCrossing(entry.getKey());
                network.registerCrossing(new RoadCrossing(
                    entry.getKey(),
                    a.roadId(),
                    a.station(),
                    b.roadId(),
                    b.station(),
                    midpoint(a.position(), b.position()),
                    passA.type(),
                    elevated,
                    passA.crossingClearance(),
                    passA.sharedElevation(),
                    passA.cornerRadius()));
                rewritten++;
                continue;
            }

            Double lower = remapStationHint(passA.lowerStationHint(), passA, passB, a, b);
            Double upper = remapStationHint(passA.upperStationHint(), passA, passB, a, b);
            if (passA.type() == CrossingType.GRADE_SEPARATED
                    && (upper == null || lower == null)) {
                complete = false;
                continue;
            }
            network.registerStackCrossing(new RoadStackCrossing(
                entry.getKey(),
                a.roadId(),
                midpoint(a.position(), b.position()),
                a.station(),
                b.station(),
                passA.type(),
                lower,
                upper,
                passA.crossingClearance(),
                passA.sharedElevation()));
            rewritten++;
        }
        return new ApplyResult(complete, rewritten, removed);
    }

    private static boolean passEdgeDeleted(
            CrossingPassBinding passA,
            CrossingPassBinding passB,
            Optional<ResolvedPass> resolvedA,
            Optional<ResolvedPass> resolvedB,
            Set<String> deletedEdgeIds) {
        return (resolvedA.isEmpty() && deletedEdgeIds.contains(passA.edgeId()))
            || (resolvedB.isEmpty() && deletedEdgeIds.contains(passB.edgeId()));
    }

    private record ResolvedPass(String roadId, double station, Vec2d position, String edgeId) {
    }

    private static CrossingPassBinding findRole(List<CrossingPassBinding> bindings, PassRole role) {
        for (CrossingPassBinding binding : bindings) {
            if (binding.role() == role) {
                return binding;
            }
        }
        return null;
    }

    private static Optional<CrossingPassBinding> captureTwoRoadPass(
            RoadNetwork network,
            RoadCrossing crossing,
            PassRole role) {
        String roadId = role == PassRole.A ? crossing.roadAId() : crossing.roadBId();
        double station = role == PassRole.A ? crossing.stationA() : crossing.stationB();
        Road road = network.getRoad(roadId);
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(resolved.get().segmentId());
        if (edge == null) {
            return Optional.empty();
        }
        Vec2d point = RoadGeometryUtils.pointAtDistance(edge.getCenterlinePoints(), resolved.get().localDistance());
        if (point == null) {
            point = crossing.position();
        }
        return Optional.of(new CrossingPassBinding(
            crossing.id(),
            Kind.TWO_ROAD,
            role,
            roadId,
            edge.getId(),
            resolved.get().localDistance(),
            station,
            point,
            crossing.type(),
            crossing.elevatedRoadId(),
            crossing.crossingClearance(),
            crossing.sharedElevation(),
            crossing.cornerRadius(),
            null,
            null));
    }

    private static Optional<CrossingPassBinding> captureStackPass(
            RoadNetwork network,
            RoadStackCrossing stack,
            PassRole role) {
        double station = role == PassRole.A ? stack.stationA() : stack.stationB();
        Road road = network.getRoad(stack.roadId());
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        Optional<SegmentStation> resolved = RoadStationing.resolve(network, road, station);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(resolved.get().segmentId());
        if (edge == null) {
            return Optional.empty();
        }
        Vec2d point = RoadGeometryUtils.pointAtDistance(edge.getCenterlinePoints(), resolved.get().localDistance());
        if (point == null) {
            point = stack.position();
        }
        return Optional.of(new CrossingPassBinding(
            stack.id(),
            Kind.STACK,
            role,
            stack.roadId(),
            edge.getId(),
            resolved.get().localDistance(),
            station,
            point,
            stack.type(),
            null,
            stack.crossingClearance(),
            stack.sharedElevation(),
            null,
            stack.lowerStation(),
            stack.upperStation()));
    }

    /**
     * Resolve a captured physical pass via edgeId + localDistance only.
     * Missing edges return empty — never scan the whole network for a nearby road.
     */
    private static Optional<ResolvedPass> resolvePass(RoadNetwork network, CrossingPassBinding binding) {
        if (binding == null || binding.edgeId() == null) {
            return Optional.empty();
        }
        RoadEdge edge = network.getEdge(binding.edgeId());
        if (edge == null || edge.getRoadId() == null) {
            return Optional.empty();
        }
        Road road = network.getRoad(edge.getRoadId());
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        Optional<RoadStation> station = RoadStationing.stationAt(
            network, road, binding.edgeId(), binding.localDistance());
        if (station.isEmpty()) {
            return Optional.empty();
        }
        Vec2d point = RoadGeometryUtils.pointAtDistance(
            edge.getCenterlinePoints(), binding.localDistance());
        if (point == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPass(
            road.getId(), station.get().chainageMeters(), point, edge.getId()));
    }

    private static boolean canConvertTwoRoadToStack(
            RoadNetwork network,
            CrossingPassBinding passA,
            CrossingPassBinding passB,
            ResolvedPass a,
            ResolvedPass b,
            String crossingId) {
        if (passA.cornerRadius() != null) {
            return false;
        }
        if (passA.type() == CrossingType.GRADE_SEPARATED) {
            String elevated = passA.elevatedRoadId();
            if (elevated == null) {
                return false;
            }
            if (!elevated.equals(passA.roadId()) && !elevated.equals(passB.roadId())) {
                return false;
            }
        }
        if (!isValidStackConversion(network, a, b)) {
            return false;
        }
        return !conflictsWithExistingStack(network, a.roadId(), a.station(), b.station(), crossingId);
    }

    private static boolean isValidStackConversion(RoadNetwork network, ResolvedPass a, ResolvedPass b) {
        if (a.position() == null || b.position() == null) {
            return false;
        }
        if (a.position().distance(b.position()) > NODE_TOLERANCE) {
            return false;
        }
        if (Math.abs(a.station() - b.station()) <= EPSILON) {
            return false;
        }
        RoadEdge ea = network.getEdge(a.edgeId());
        RoadEdge eb = network.getEdge(b.edgeId());
        if (ea != null && eb != null && !ea.getId().equals(eb.getId())) {
            Set<String> shared = new HashSet<>(Set.of(ea.getStartNodeId(), ea.getEndNodeId()));
            shared.retainAll(Set.of(eb.getStartNodeId(), eb.getEndNodeId()));
            if (!shared.isEmpty()
                    && (nearEndpoint(ea, a.position()) || nearEndpoint(eb, b.position()))) {
                return false;
            }
        }
        Road road = network.getRoad(a.roadId());
        if (road != null && road.getTopologyMode() == RoadTopologyMode.LOOP) {
            double len = RoadStationing.canonicalLength(network, road);
            if (isLoopZeroOrL(a.station(), len) && isLoopZeroOrL(b.station(), len)) {
                return false;
            }
        }
        return true;
    }

    private static boolean conflictsWithExistingStack(
            RoadNetwork network,
            String roadId,
            double stationA,
            double stationB,
            String excludeCrossingId) {
        for (RoadStackCrossing existing : network.stackCrossingsForRoad(roadId)) {
            if (excludeCrossingId != null && excludeCrossingId.equals(existing.id())) {
                continue;
            }
            boolean nearA = Math.abs(existing.stationA() - stationA) <= NODE_TOLERANCE
                || Math.abs(existing.stationB() - stationA) <= NODE_TOLERANCE;
            boolean nearB = Math.abs(existing.stationA() - stationB) <= NODE_TOLERANCE
                || Math.abs(existing.stationB() - stationB) <= NODE_TOLERANCE;
            if (nearA && nearB) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearEndpoint(RoadEdge edge, Vec2d position) {
        List<com.plot.api.geometry.Vec2d> pts = edge.getCenterlinePoints();
        if (pts.size() < 2 || position == null) {
            return false;
        }
        return pts.getFirst().distance(position) <= NODE_TOLERANCE
            || pts.getLast().distance(position) <= NODE_TOLERANCE;
    }

    private static boolean isLoopZeroOrL(double station, double length) {
        return station <= EPSILON || Math.abs(station - length) <= EPSILON;
    }

    private static String remapElevated(
            String elevated,
            String origA,
            String origB,
            String newA,
            String newB) {
        if (elevated == null) {
            return null;
        }
        if (elevated.equals(origA)) {
            return newA;
        }
        if (elevated.equals(origB)) {
            return newB;
        }
        return elevated;
    }

    private static Double remapStationHint(
            Double hint,
            CrossingPassBinding passA,
            CrossingPassBinding passB,
            ResolvedPass a,
            ResolvedPass b) {
        if (hint == null) {
            return null;
        }
        double da = Math.abs(hint - passA.originalStation());
        double db = Math.abs(hint - passB.originalStation());
        return da <= db ? a.station() : b.station();
    }

    private static Vec2d midpoint(Vec2d a, Vec2d b) {
        if (a == null) {
            return b != null ? b.copy() : new Vec2d(0, 0);
        }
        if (b == null) {
            return a.copy();
        }
        return new Vec2d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5);
    }
}
