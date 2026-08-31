package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.pipeline.profile.VerticalAlignmentProfileSupport;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 将道路级设计纵断面裁剪为单条边的局部里程，供纵断面图叠加绘制。
 */
public final class VerticalAlignmentProfileOverlay {

    private static final double MIN_SAMPLE_SPACING = 2.0;

    private final List<Double> distances;
    private final List<Integer> heights;

    public VerticalAlignmentProfileOverlay(List<Double> distances, List<Integer> heights) {
        this.distances = List.copyOf(distances);
        this.heights = List.copyOf(heights);
    }

    public List<Double> distances() {
        return distances;
    }

    public List<Integer> heights() {
        return heights;
    }

    public boolean isEmpty() {
        return distances.size() < 2;
    }

    public static Optional<VerticalAlignmentProfileOverlay> forEdge(RoadNetwork network, RoadEdge edge) {
        if (network == null || edge == null) {
            return Optional.empty();
        }
        String roadId = edge.getRoadId();
        if (roadId == null) {
            return Optional.empty();
        }
        Road road = network.getRoad(roadId);
        if (!VerticalAlignmentProfileSupport.shouldUseVerticalAlignment(network, road)) {
            return Optional.empty();
        }
        return RoadStationing.orientedSegment(network, road, edge.getId()).flatMap(oriented -> {
        double segmentStart = oriented.startStation();
        double edgeLength = oriented.length();
        double spacing = Math.max(MIN_SAMPLE_SPACING, edgeLength / 40.0);
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        List<Double> localDistances = new ArrayList<>();
        List<Integer> localHeights = new ArrayList<>();
        double segmentEnd = oriented.endStation();
        for (VerticalAlignmentGeometry.ProfileSample sample : VerticalAlignmentGeometry.sample(alignment, spacing)) {
            if (sample.station() < segmentStart - 1e-6 || sample.station() > segmentEnd + 1e-6) {
                continue;
            }
            double chainLocal = sample.station() - segmentStart;
            localDistances.add(oriented.geometryLocalFromChainLocal(chainLocal));
            localHeights.add((int) Math.round(sample.elevation()));
        }
        appendEndpointIfMissing(oriented, localDistances, localHeights, 0.0, alignment);
        appendEndpointIfMissing(oriented, localDistances, localHeights, edgeLength, alignment);
        if (localDistances.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new VerticalAlignmentProfileOverlay(localDistances, localHeights));
        });
    }

    private static void appendEndpointIfMissing(
            OrientedRoadSegment oriented,
            List<Double> distances,
            List<Integer> heights,
            double geometryLocalDistance,
            RoadVerticalAlignment alignment) {
        if (distances.stream().anyMatch(distance -> Math.abs(distance - geometryLocalDistance) < 1e-6)) {
            return;
        }
        double chainage = oriented.roadStationAtGeometryLocal(geometryLocalDistance);
        int height = (int) Math.round(VerticalAlignmentGeometry.elevationAt(alignment, chainage)
            .orElse(heights.isEmpty() ? 64.0 : heights.getLast()));
        distances.add(geometryLocalDistance);
        heights.add(height);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < distances.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> Double.compare(distances.get(a), distances.get(b)));
        List<Double> sortedDistances = new ArrayList<>();
        List<Integer> sortedHeights = new ArrayList<>();
        for (int index : indices) {
            sortedDistances.add(distances.get(index));
            sortedHeights.add(heights.get(index));
        }
        distances.clear();
        heights.clear();
        distances.addAll(sortedDistances);
        heights.addAll(sortedHeights);
    }
}
