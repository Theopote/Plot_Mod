package com.plot.plugin.road.pipeline.profile;

import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.vertical.ResolvedProfileAuthority;
import com.plot.plugin.road.vertical.RoadVerticalMode;

import java.util.*;

/** Projects soft node targets together so independent edge solves cannot restart at a cliff. */
final class AutomaticNodeElevationContinuity {
    private record Arc(String node, int rise) { }
    private record Visit(String node, double height) { }

    private AutomaticNodeElevationContinuity() { }

    static Map<String, Integer> project(RoadNetwork network,
            Map<String, List<JunctionFeasibleElevationResolver.ApproachConstraint>> approaches,
            Map<String, Integer> heights) {
        Map<String, List<Arc>> graph = new LinkedHashMap<>();
        Set<String> fixed = new HashSet<>();
        Map<String, Boolean> fixedRoads = new HashMap<>();
        for (var node : network.getNodes().values()) {
            if (!heights.containsKey(node.getId()) || node.isGradeSeparated()) continue;
            String id = node.getId();
            graph.put(id, new ArrayList<>());
            if (node.getManualElevation() != null) fixed.add(id);
            for (String roadId : network.getDistinctRoadIdsAtNode(id)) {
                boolean designed = fixedRoads.computeIfAbsent(roadId, key -> {
                    var road = network.getRoad(key);
                    return road != null && (road.getVerticalMode() == RoadVerticalMode.FLAT
                        || ResolvedProfileAuthority.usesEditedProfileForGeneration(network, road));
                });
                if (designed) fixed.add(id);
            }
        }
        graph.forEach((id, arcs) -> {
            for (var approach : approaches.getOrDefault(id, List.of())) {
                if (!graph.containsKey(approach.oppositeNodeId())) {
                    // Keep the boundary to a grade-separated layer unchanged.
                    fixed.add(id);
                    continue;
                }
                // Integer pavement necessarily permits one quantization step.
                int rise = Math.max(1, (int) Math.ceil(approach.lengthBlocks() * approach.maxSlopePercent() / 100.0 - 1e-9));
                arcs.add(new Arc(approach.oppositeNodeId(), rise));
            }
        });
        Map<String, Integer> result = new LinkedHashMap<>(heights);
        Set<String> visited = new HashSet<>();
        for (String root : graph.keySet()) {
            if (!visited.add(root)) continue;
            Set<String> component = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(root);
            boolean cliff = false;
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                component.add(id);
                for (var arc : graph.get(id)) {
                    cliff |= Math.abs((long) heights.get(id) - heights.get(arc.node)) > arc.rise;
                    if (visited.add(arc.node)) queue.addLast(arc.node);
                }
            }
            if (!cliff) continue;
            Map<String, Double> preferred = new HashMap<>();
            Map<String, Double> anchors = new HashMap<>();
            for (String id : component) {
                preferred.put(id, (double) heights.get(id));
                if (fixed.contains(id)) anchors.put(id, (double) heights.get(id));
            }
            var lowEnvelope = envelope(graph, preferred, false);
            var highEnvelope = envelope(graph, preferred, true);
            var upperBounds = envelope(graph, anchors, false);
            var lowerBounds = envelope(graph, anchors, true);
            // Genuine conflicting user/crossing locks remain reported as infeasible;
            // never silently move a hard design to make the preview look plausible.
            boolean feasible = component.stream().allMatch(id ->
                -lowerBounds.getOrDefault(id, Double.POSITIVE_INFINITY)
                    <= upperBounds.getOrDefault(id, Double.POSITIVE_INFINITY) + 1e-9);
            if (!feasible) continue;
            for (String id : component) {
                double value = (lowEnvelope.get(id) - highEnvelope.get(id)) * 0.5;
                value = Math.max(-lowerBounds.getOrDefault(id, Double.POSITIVE_INFINITY), value);
                value = Math.min(upperBounds.getOrDefault(id, Double.POSITIVE_INFINITY), value);
                result.put(id, (int) Math.round(value));
            }
        }
        return result;
    }

    /** Multi-source shortest paths form a slope-limited envelope in O(E log V). */
    private static Map<String, Double> envelope(Map<String, List<Arc>> graph,
            Map<String, Double> sources, boolean negate) {
        Map<String, Double> values = new HashMap<>();
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::height));
        sources.forEach((id, elevation) -> {
            double height = negate ? -elevation : elevation;
            values.put(id, height);
            queue.add(new Visit(id, height));
        });
        while (!queue.isEmpty()) {
            var visit = queue.remove();
            if (visit.height > values.get(visit.node)) continue;
            for (var arc : graph.get(visit.node)) {
                double candidate = visit.height + arc.rise;
                if (candidate < values.getOrDefault(arc.node, Double.POSITIVE_INFINITY)) {
                    values.put(arc.node, candidate);
                    queue.add(new Visit(arc.node, candidate));
                }
            }
        }
        return values;
    }
}
