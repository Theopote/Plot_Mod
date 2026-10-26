package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.crossing.CrossingType;
import com.plot.plugin.road.crossing.RoadCrossing;
import com.plot.plugin.road.crossing.RoadStackCrossing;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.ProfileSource;
import com.plot.plugin.road.station.RoadStationing;

import java.util.*;

/**
 * Lightweight editor solve. Crossing heights are solved together before any PVI is
 * written; terrain sampling, grade smoothing and voxel compilation are deliberately separate.
 */
public final class RoadProfileConstraintSynchronizer {
    private static final double EPSILON = 1e-6;

    public record Result(Set<String> changedRoadIds, Set<String> unresolvedConstraints) {
        public Result {
            changedRoadIds = Set.copyOf(changedRoadIds);
            unresolvedConstraints = Set.copyOf(unresolvedConstraints);
        }
    }

    private record Point(String roadId, double station) { }
    /** For separation, a is below b. Otherwise a and b share one elevation. */
    private record Link(String id, Point a, Point b, boolean separated, double clearance) { }
    private static final class Group {
        double elevation;
        double priority = -1;
        boolean pinned;
        Double lockedElevation;
    }

    private RoadProfileConstraintSynchronizer() { }

    public static Result synchronize(
            RoadNetwork network, Road source, RoadVerticalAlignment before, RoadSystemConfig config) {
        return synchronize(network, source, before, config, RoadWorldElevationBounds.fallback());
    }

    public static Result synchronize(
            RoadNetwork network, Road source, RoadVerticalAlignment before, RoadSystemConfig config,
            RoadElevationBounds bounds) {
        if (network == null || source == null || config == null) {
            return new Result(Set.of(), Set.of());
        }
        List<Link> links = collectLinks(network, config, source, before);
        // Include the whole connected set of roads, without touching unrelated road networks.
        Set<String> connected = new HashSet<>(Set.of(source.getId()));
        boolean expanded;
        do {
            expanded = false;
            for (Link link : links) {
                if (connected.contains(link.a.roadId) || connected.contains(link.b.roadId)) {
                    expanded |= connected.add(link.a.roadId);
                    expanded |= connected.add(link.b.roadId);
                }
            }
        } while (expanded);
        links.removeIf(link -> !connected.contains(link.a.roadId));
        Map<Point, Point> parents = new LinkedHashMap<>();
        for (Link link : links) {
            parents.putIfAbsent(link.a, link.a);
            parents.putIfAbsent(link.b, link.b);
        }
        for (Link link : links) {
            if (!link.separated) parents.put(root(parents, link.b), root(parents, link.a));
        }
        Set<String> unresolved = new LinkedHashSet<>();
        Map<Point, Group> groups = new LinkedHashMap<>();
        for (Point point : parents.keySet()) {
            Road road = network.getRoad(point.roadId);
            var elevation = VerticalAlignmentGeometry.elevationAt(
                road != null ? road.getVerticalAlignment() : null, point.station);
            if (elevation.isEmpty()) {
                unresolved.add("missing-profile:" + point.roadId + ":" + point.station);
                continue;
            }
            double value = elevation.getAsDouble();
            boolean driving = source.getId().equals(point.roadId);
            var old = driving ? VerticalAlignmentGeometry.elevationAt(before, point.station)
                : OptionalDouble.empty();
            double delta = old.isPresent() ? Math.abs(value - old.getAsDouble()) : 0;
            double priority = driving ? 1 + delta : 0;
            Group group = groups.computeIfAbsent(root(parents, point), ignored -> new Group());
            boolean locked = false;
            if (road != null) {
                locked = road.getVerticalAlignment().getPvis().stream().anyMatch(pvi ->
                    Math.abs(pvi.getStation() - point.station) <= EPSILON
                        && (pvi.getConstraint() == VerticalControlPointConstraint.USER_LOCKED
                            || pvi.getConstraint() == VerticalControlPointConstraint.ENDPOINT_FIXED));
            }
            if (locked) {
                if (group.lockedElevation != null && Math.abs(group.lockedElevation - value) > EPSILON) {
                    unresolved.add("conflicting-lock:" + point.roadId + ":" + point.station);
                }
                group.lockedElevation = value;
            }
            // At a self crossing, the branch actually moved by the user drives the other branch.
            if (priority > group.priority + EPSILON) {
                group.elevation = value;
                group.priority = priority;
                group.pinned = driving && delta > EPSILON;
            } else if (driving && delta > EPSILON && Math.abs(priority - group.priority) <= EPSILON
                    && Math.abs(group.elevation - value) > EPSILON) {
                unresolved.add("conflicting-edit:" + point.roadId + ":" + point.station);
            }
        }
        for (var entry : groups.entrySet()) {
            Group group = entry.getValue();
            if (group.lockedElevation == null) continue;
            if (group.pinned && Math.abs(group.elevation - group.lockedElevation) > EPSILON) {
                unresolved.add("conflicting-lock:" + entry.getKey().roadId + ":" + entry.getKey().station);
            }
            group.elevation = group.lockedElevation;
            group.pinned = true;
        }
        // Project minimum-clearance inequalities. A surplus is retained, never collapsed to equality.
        int budget = Math.max(1, 2 * groups.size() + 1);
        for (int pass = 0; pass < budget; pass++) {
            boolean changed = false;
            for (Link link : links) {
                if (!link.separated) continue;
                Group lower = groups.get(root(parents, link.a));
                Group upper = groups.get(root(parents, link.b));
                if (lower == null || upper == null) continue;
                if (link.a.roadId.equals(link.b.roadId) && lower != upper) {
                    if (lower.priority > upper.priority + EPSILON && upper.lockedElevation == null) upper.pinned = false;
                    else if (upper.priority > lower.priority + EPSILON && lower.lockedElevation == null) lower.pinned = false;
                }
                if (upper.elevation - lower.elevation >= link.clearance - EPSILON) continue;
                if (lower == upper || lower.pinned && upper.pinned) continue;
                if (upper.pinned) lower.elevation = upper.elevation - link.clearance;
                else upper.elevation = lower.elevation + link.clearance;
                changed = true;
            }
            if (!changed) break;
        }
        for (Link link : links) {
            Group a = groups.get(root(parents, link.a));
            Group b = groups.get(root(parents, link.b));
            if (a == null || b == null || !Double.isFinite(a.elevation) || !Double.isFinite(b.elevation)
                    || a.elevation != bounds.clamp(a.elevation) || b.elevation != bounds.clamp(b.elevation)
                    || link.separated && b.elevation - a.elevation < link.clearance - EPSILON) {
                unresolved.add(link.id);
            }
        }
        // An infeasible cycle or missing sampled profile must not partially rewrite its neighbours.
        if (!unresolved.isEmpty()) return new Result(Set.of(), unresolved);
        Set<String> changedRoads = new LinkedHashSet<>();
        Map<String, Double> flatRoadElevations = new LinkedHashMap<>();
        Map<String, Double> flatRoadPriorities = new LinkedHashMap<>();
        for (Point point : parents.keySet()) {
            accumulateFlatRoadElevation(
                network, point, groups, parents, source, flatRoadElevations, flatRoadPriorities);
        }
        for (Map.Entry<String, Double> entry : flatRoadElevations.entrySet()) {
            Road flatRoad = network.getRoad(entry.getKey());
            if (flatRoad == null) {
                continue;
            }
            FlatVerticalIntentSupport.setWholeRoadElevation(
                network,
                flatRoad,
                entry.getValue(),
                effectiveMaxGrade(flatRoad, config));
            changedRoads.add(entry.getKey());
        }
        Map<String, Map<Double, Double>> targets = new LinkedHashMap<>();
        for (Point point : parents.keySet()) {
            Group group = groups.get(root(parents, point));
            if (group != null) targets.computeIfAbsent(point.roadId, ignored -> new LinkedHashMap<>())
                .put(point.station, group.elevation);
        }
        targets.forEach((roadId, elevations) -> {
            Road targetRoad = network.getRoad(roadId);
            if (targetRoad == null || targetRoad.getVerticalMode() == RoadVerticalMode.FLAT) {
                return;
            }
            if (setElevations(targetRoad, elevations)) {
                changedRoads.add(roadId);
            }
        });
        for (Link link : links) {
            if (link.separated) continue;
            double value = groups.get(root(parents, link.a)).elevation;
            if (link.id.startsWith("node:")) {
                network.getNode(link.id.substring(5)).setManualElevation(
                    value, com.plot.plugin.road.model.ManualElevationOrigin.PROFILE_PROPAGATED);
            } else if (link.id.startsWith("crossing:")) {
                network.setCrossingSharedElevation(link.id.substring(9), value);
            } else if (link.id.startsWith("stack:")) {
                network.setStackCrossingSharedElevation(link.id.substring(6), value);
            }
        }
        return new Result(changedRoads, unresolved);
    }

    private static double effectiveMaxGrade(Road road, RoadSystemConfig config) {
        if (road != null && road.getMaxSlope() != null) {
            return road.getMaxSlope();
        }
        return config != null ? config.getMaxSlope() : 8.0;
    }

    /** 水平道路在约束求解后保持单一基准高程，不再写入 per-junction override。 */
    private static void accumulateFlatRoadElevation(
            RoadNetwork network,
            Point point,
            Map<Point, Group> groups,
            Map<Point, Point> parents,
            Road source,
            Map<String, Double> elevations,
            Map<String, Double> priorities) {
        Road road = network.getRoad(point.roadId);
        if (road == null || road.getVerticalMode() != RoadVerticalMode.FLAT) {
            return;
        }
        Group group = groups.get(root(parents, point));
        if (group == null || !Double.isFinite(group.elevation)) {
            return;
        }
        double priority = source.getId().equals(point.roadId) ? (group.pinned ? 3.0 : 2.0) : 1.0;
        Double existing = priorities.get(point.roadId);
        if (existing == null || priority > existing) {
            elevations.put(point.roadId, group.elevation);
            priorities.put(point.roadId, priority);
        }
    }

    /** Materialize tangent crossing anchors for editor handles without changing the curve. */
    public static Set<String> seedCrossingAnchors(RoadNetwork network, RoadSystemConfig config) {
        Set<String> changed = new LinkedHashSet<>();
        for (var link : collectLinks(network, config)) {
            for (var crossing : List.of(link.a, link.b)) {
                Road road = network.getRoad(crossing.roadId);
                if (road == null
                        || road.getVerticalMode() == RoadVerticalMode.FLAT
                        || road.getEffectiveProfileSource() != ProfileSource.EDITED) {
                    continue;
                }
                var alignment = road.getVerticalAlignment();
                var height = VerticalAlignmentGeometry.elevationAt(alignment, crossing.station);
                if (height.isEmpty()) continue;
                var points = new ArrayList<>(alignment.getPvis());
                int existing = -1;
                boolean insideCurve = false;
                for (int i = 0; i < points.size(); i++) {
                    var pvi = points.get(i);
                    if (Math.abs(pvi.getStation() - crossing.station) < EPSILON) existing = i;
                    if (pvi.hasCurve() && Math.abs(pvi.getStation() - crossing.station) < pvi.getCurveLength() / 2 + EPSILON) {
                        insideCurve = true;
                    }
                }
                if (existing >= 0) {
                    var pvi = points.get(existing);
                    if (pvi.getConstraint() != VerticalControlPointConstraint.FREE) continue;
                    points.set(existing, pvi.withConstraint(VerticalControlPointConstraint.JUNCTION_FIXED));
                } else {
                    // Splitting an existing parabola would alter its shape. The explicit
                    // smooth action remains available for that case.
                    if (insideCurve || crossing.station <= points.getFirst().getStation()
                            || crossing.station >= points.getLast().getStation()) continue;
                    points.add(new PointOfVerticalIntersection(crossing.station, height.getAsDouble(), null,
                        VerticalControlPointConstraint.JUNCTION_FIXED));
                    points.sort(Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
                }
                var mode = road.getVerticalMode();
                road.setVerticalAlignment(new RoadVerticalAlignment(points));
                road.setVerticalMode(mode);
                changed.add(road.getId());
            }
        }
        return changed;
    }

    /** A curve-length edit changes shape, while every existing crossing keeps its road height. */
    public static void preserveCrossingElevations(
            RoadNetwork network, Road road, RoadVerticalAlignment before, RoadSystemConfig config) {
        Map<Double, Double> targets = new LinkedHashMap<>();
        for (var link : collectLinks(network, config)) {
            for (var point : List.of(link.a, link.b)) {
                if (!road.getId().equals(point.roadId)) continue;
                VerticalAlignmentGeometry.elevationAt(before, point.station)
                    .ifPresent(value -> targets.put(point.station, value));
            }
        }
        if (!targets.isEmpty()) setElevations(road, targets, false);
    }

    /** Insert a missing crossing control point; never silently ignore an interior intersection. */
    public static boolean setElevation(Road road, double station, double elevation) {
        if (road == null || !Double.isFinite(station) || !Double.isFinite(elevation)
                || VerticalAlignmentGeometry.elevationAt(road.getVerticalAlignment(), station).isEmpty()) {
            return false;
        }
        return setElevations(road, Map.of(station, elevation));
    }

    static boolean setElevations(Road road, Map<Double, Double> targets) {
        return setElevations(road, targets, true);
    }

    static boolean setElevations(Road road, Map<Double, Double> targets, boolean smoothNewAnchors) {
        List<PointOfVerticalIntersection> points = new ArrayList<>();
        Map<Double, Double> anchors = new LinkedHashMap<>();
        for (var pvi : road.getVerticalAlignment().getPvis()) {
            Double target = targets.entrySet().stream()
                .filter(entry -> Math.abs(entry.getKey() - pvi.getStation()) <= EPSILON)
                .map(Map.Entry::getValue).findFirst().orElse(null);
            if (target != null) {
                var constraint = pvi.getConstraint().isAutomaticallyMovable()
                    ? VerticalControlPointConstraint.JUNCTION_FIXED : pvi.getConstraint();
                points.add(pvi.withConstraint(constraint));
                anchors.put(pvi.getStation(), target);
            } else {
                points.add(pvi.copy());
            }
        }
        for (var entry : targets.entrySet()) {
            if (anchors.keySet().stream().anyMatch(station -> Math.abs(station - entry.getKey()) <= EPSILON)) continue;
            points.add(new PointOfVerticalIntersection(entry.getKey(), entry.getValue(), null,
                VerticalControlPointConstraint.JUNCTION_FIXED));
            anchors.put(entry.getKey(), entry.getValue());
        }
        points.sort(Comparator.comparingDouble(PointOfVerticalIntersection::getStation));
        // Only shorten curves intersected by a newly inserted crossing anchor.
        // Curves elsewhere in the user's design retain their original lengths.
        for (int i = 1; i < points.size() - 1; i++) {
            var point = points.get(i);
            if (!point.hasCurve()) continue;
            double maximum = point.getCurveLength();
            for (double station : anchors.keySet()) {
                double distance = Math.abs(station - point.getStation());
                if (distance > EPSILON && distance <= point.getCurveLength() / 2 + EPSILON) {
                    maximum = Math.min(maximum, distance);
                }
            }
            points.set(i, new PointOfVerticalIntersection(point.getStation(), point.getElevation(), maximum, point.getConstraint()));
        }
        for (int i = 1; i < points.size() - 1; i++) {
            var point = points.get(i);
            if (!anchors.containsKey(point.getStation())) continue;
            Double length = point.getCurveLength();
            boolean newAnchor = road.getVerticalAlignment().getPvis().stream().noneMatch(old ->
                Math.abs(old.getStation() - point.getStation()) <= EPSILON
                    && old.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED);
            double maximum = VerticalProfileControlPoints.maxCurveLength(points, i);
            if (smoothNewAnchors && newAnchor && !point.hasCurve()) length = Math.min(8.0, maximum);
            if (length != null) length = Math.min(length, maximum);
            points.set(i, new PointOfVerticalIntersection(point.getStation(), point.getElevation(), length, point.getConstraint()));
        }
        var updated = VerticalProfileControlPoints.withAnchoredElevations(new RoadVerticalAlignment(points), anchors);
        var previous = road.getVerticalAlignment().getPvis();
        boolean changed = previous.size() != updated.pviCount();
        if (!changed) {
            for (int i = 0; i < previous.size(); i++) {
                var a = previous.get(i);
                var b = updated.getPvis().get(i);
                changed |= Math.abs(a.getElevation() - b.getElevation()) > EPSILON
                    || !Objects.equals(a.getCurveLength(), b.getCurveLength()) || a.getConstraint() != b.getConstraint();
            }
        }
        if (changed) road.setVerticalAlignment(updated);
        return changed;
    }

    private static Point root(Map<Point, Point> parents, Point point) {
        Point parent = parents.get(point);
        if (!parent.equals(point)) {
            parent = root(parents, parent);
            parents.put(point, parent);
        }
        return parent;
    }

    /** Validate the final connected design without changing any user's control points. */
    public static Set<String> validate(RoadNetwork network, Set<String> roadIds, RoadSystemConfig config) {
        Set<String> connected = new HashSet<>(roadIds);
        List<Link> links = collectLinks(network, config);
        boolean changed;
        do {
            changed = false;
            for (Link link : links) {
                if (connected.contains(link.a.roadId) || connected.contains(link.b.roadId)) {
                    changed |= connected.add(link.a.roadId);
                    changed |= connected.add(link.b.roadId);
                }
            }
        } while (changed);
        Set<String> unresolved = new LinkedHashSet<>();
        for (Link link : links) {
            if (!connected.contains(link.a.roadId)) continue;
            var a = network.getRoad(link.a.roadId);
            var b = network.getRoad(link.b.roadId);
            var ya = VerticalAlignmentGeometry.elevationAt(a != null ? a.getVerticalAlignment() : null, link.a.station);
            var yb = VerticalAlignmentGeometry.elevationAt(b != null ? b.getVerticalAlignment() : null, link.b.station);
            if (ya.isEmpty() || yb.isEmpty()
                    || (link.separated ? yb.getAsDouble() - ya.getAsDouble() < link.clearance - EPSILON
                        : Math.abs(ya.getAsDouble() - yb.getAsDouble()) > EPSILON)) unresolved.add(link.id);
        }
        return Set.copyOf(unresolved);
    }

    private static List<Link> collectLinks(RoadNetwork network, RoadSystemConfig config) {
        return collectLinks(network, config, null, null);
    }

    private static List<Link> collectLinks(
            RoadNetwork network, RoadSystemConfig config, Road source, RoadVerticalAlignment before) {
        java.util.function.ToDoubleFunction<Point> roleHeight = point -> {
            if (source != null && source.getId().equals(point.roadId)) {
                var previous = VerticalAlignmentGeometry.elevationAt(before, point.station);
                if (previous.isPresent()) return previous.getAsDouble();
            }
            return sampledElevation(network, point);
        };
        List<Link> links = new ArrayList<>();
        network.getCrossings().values().stream().sorted(Comparator.comparing(RoadCrossing::id)).forEach(c -> {
            Point a = new Point(c.roadAId(), c.stationA());
            Point b = new Point(c.roadBId(), c.stationB());
            boolean separated = c.type() == CrossingType.GRADE_SEPARATED;
            boolean aUpper = c.elevatedRoadId() != null ? c.roadAId().equals(c.elevatedRoadId())
                : roleHeight.applyAsDouble(a) > roleHeight.applyAsDouble(b);
            links.add(new Link("crossing:" + c.id(), separated && aUpper ? b : a,
                separated && aUpper ? a : b, separated,
                c.crossingClearance() != null ? c.crossingClearance() : config.getDefaultCrossingClearance()));
        });
        network.getStackCrossings().values().stream().sorted(Comparator.comparing(RoadStackCrossing::id)).forEach(c ->
            links.add(new Link("stack:" + c.id(),
                new Point(c.roadId(), c.isGradeSeparated() ? c.resolvedLowerStation() : c.stationA()),
                new Point(c.roadId(), c.isGradeSeparated() ? c.resolvedUpperStation() : c.stationB()),
                c.isGradeSeparated(), c.crossingClearance() != null
                    ? c.crossingClearance() : config.getDefaultCrossingClearance())));
        Map<String, Set<Point>> byNode = new TreeMap<>();
        for (Road road : network.getRoads().values()) {
            for (var segment : RoadStationing.orientedSegments(network, road)) {
                for (String nodeId : List.of(segment.entryNodeId(), segment.exitNodeId())) {
                    var station = segment.roadStationAtNode(nodeId);
                    if (station.isPresent()) byNode.computeIfAbsent(nodeId, ignored -> new LinkedHashSet<>())
                        .add(new Point(road.getId(), station.getAsDouble()));
                }
            }
        }
        byNode.forEach((nodeId, points) -> {
            var node = network.getNode(nodeId);
            List<Point> ordered = points.stream().sorted(Comparator.comparing(Point::roadId)
                .thenComparingDouble(Point::station)).toList();
            if (node == null || ordered.size() < 2) return;
            if (node.isStackGradeSeparated() && network.getStackCrossing(node.getStackCrossingId()) != null) return;
            if (!node.isGradeSeparated()) {
                for (int i = 1; i < ordered.size(); i++) {
                    links.add(new Link("node:" + nodeId, ordered.getFirst(), ordered.get(i), false, 0));
                }
                return;
            }
            Point upper = ordered.stream().filter(p -> p.roadId.equals(node.getElevatedRoadId())).findFirst()
                .orElseGet(() -> ordered.stream().max(Comparator.comparingDouble(roleHeight)
                    .thenComparing(Point::roadId))
                    .orElseThrow());
            List<Point> lower = ordered.stream().filter(p -> !p.equals(upper)).toList();
            for (int i = 1; i < lower.size(); i++) {
                links.add(new Link("node:" + nodeId, lower.getFirst(), lower.get(i), false, 0));
            }
            links.add(new Link("node:" + nodeId, lower.getFirst(), upper, true,
                node.getCrossingClearance() != null ? node.getCrossingClearance() : config.getDefaultCrossingClearance()));
        });
        return links;
    }

    private static double sampledElevation(RoadNetwork network, Point point) {
        Road road = network.getRoad(point.roadId);
        return VerticalAlignmentGeometry.elevationAt(road != null ? road.getVerticalAlignment() : null,
            point.station).orElse(Double.NEGATIVE_INFINITY);
    }
}
