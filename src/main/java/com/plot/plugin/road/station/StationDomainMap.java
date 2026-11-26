package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight piecewise map between instance chainage and canonical station.
 * Built once per road (cached in {@link RoadStationingReadCache}); queries interpolate
 * between ordered anchors — never a whole-road length ratio.
 */
public final class StationDomainMap {
    private static final double EPSILON = 1e-6;
    private static final double MATCH_TOLERANCE = 0.75;

    private final double[] instanceAnchors;
    private final double[] canonicalAnchors;
    private final boolean reliable;

    private StationDomainMap(double[] instanceAnchors, double[] canonicalAnchors, boolean reliable) {
        this.instanceAnchors = instanceAnchors;
        this.canonicalAnchors = canonicalAnchors;
        this.reliable = reliable;
    }

    public boolean isReliable() {
        return reliable;
    }

    public double instanceTotal() {
        return instanceAnchors.length == 0 ? 0.0 : instanceAnchors[instanceAnchors.length - 1];
    }

    public double canonicalTotal() {
        return canonicalAnchors.length == 0 ? 0.0 : canonicalAnchors[canonicalAnchors.length - 1];
    }

    public static StationDomainMap identity(double length) {
        double L = Math.max(0.0, length);
        return new StationDomainMap(new double[] {0.0, L}, new double[] {0.0, L}, true);
    }

    public static StationDomainMap build(RoadNetwork network, Road road) {
        if (network == null || road == null || !RoadStationing.isStationable(network, road)) {
            return new StationDomainMap(new double[] {0.0}, new double[] {0.0}, false);
        }
        double instanceTotal = RoadPlanGeometry.instanceLength(network, road);
        if (!Double.isFinite(instanceTotal) || instanceTotal <= EPSILON) {
            return new StationDomainMap(new double[] {0.0}, new double[] {0.0}, false);
        }
        if (!RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return identity(instanceTotal);
        }
        double designTotal = RoadPlanGeometry.designLength(network, road);
        if (!Double.isFinite(designTotal) || designTotal <= EPSILON) {
            return identity(instanceTotal);
        }
        if (Math.abs(designTotal - instanceTotal) <= EPSILON) {
            return identity(instanceTotal);
        }

        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        List<PlanCenterlineSample> haSamples = RoadPlanGeometry.sampleHaNativeUnrotatedPublic(
            alignment, HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
        if (haSamples.size() < 2) {
            return new StationDomainMap(new double[] {0.0, instanceTotal}, new double[] {0.0, designTotal}, false);
        }

        List<Double> instance = new ArrayList<>();
        List<Double> canonical = new ArrayList<>();
        addAnchor(instance, canonical, 0.0, 0.0);

        List<OrientedRoadSegment> segments = RoadStationing.orientedSegments(network, road);
        for (OrientedRoadSegment segment : segments) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points == null || points.size() < 2) {
                continue;
            }
            // Segment start (skip duplicate of previous end).
            appendBoundaryAnchor(
                network, road, haSamples, designTotal, instanceTotal,
                segment, points, segment.geometryChainStart(),
                instance, canonical);
            // Interior vertices as local feature anchors (keeps fillets local).
            double geomStart = segment.geometryChainStart();
            double geomEnd = segment.geometryChainEnd();
            double walked = 0.0;
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d a = points.get(i);
                Vec2d b = points.get(i + 1);
                double segLen = a.distance(b);
                double next = walked + segLen;
                if (next > geomStart + EPSILON && next < geomEnd - EPSILON) {
                    appendBoundaryAnchor(
                        network, road, haSamples, designTotal, instanceTotal,
                        segment, points, next,
                        instance, canonical);
                }
                walked = next;
            }
            appendBoundaryAnchor(
                network, road, haSamples, designTotal, instanceTotal,
                segment, points, geomEnd,
                instance, canonical);
        }
        addAnchor(instance, canonical, instanceTotal, designTotal);

        if (instance.size() < 2) {
            return new StationDomainMap(
                new double[] {0.0, instanceTotal},
                new double[] {0.0, designTotal},
                false);
        }
        return new StationDomainMap(
            toArray(instance),
            toArray(canonical),
            isMonotonic(instance) && isMonotonic(canonical));
    }

    public double toCanonical(double instanceChainage) {
        if (!Double.isFinite(instanceChainage) || instanceAnchors.length < 2) {
            return Double.NaN;
        }
        return interpolate(instanceAnchors, canonicalAnchors, instanceChainage);
    }

    public double toInstance(double canonicalStation) {
        if (!Double.isFinite(canonicalStation) || canonicalAnchors.length < 2) {
            return Double.NaN;
        }
        return interpolate(canonicalAnchors, instanceAnchors, canonicalStation);
    }

    private static void appendBoundaryAnchor(
            RoadNetwork network,
            Road road,
            List<PlanCenterlineSample> haSamples,
            double designTotal,
            double instanceTotal,
            OrientedRoadSegment segment,
            List<Vec2d> points,
            double geometryLocal,
            List<Double> instance,
            List<Double> canonical) {
        double clampedGeom = Math.max(segment.geometryChainStart(),
            Math.min(geometryLocal, segment.geometryChainEnd()));
        double instanceStation = segment.roadStationAtGeometryLocal(clampedGeom);
        Vec2d world = RoadGeometryUtils.pointAtDistance(points, clampedGeom);
        if (world == null) {
            return;
        }
        double nativeStation = nearestHaNative(haSamples, world);
        if (!Double.isFinite(nativeStation)) {
            return;
        }
        double canonicalStation = RoadPlanGeometry.canonicalFromHaNative(network, road, nativeStation, designTotal);
        // Shortened/stretched polylines that still lie on the HA geometry match
        // nearest-native ≈ instance station (identity overlap). That is the wrong
        // correspondence for arc-length domain conversion — prefer length-param map.
        if (instanceTotal > EPSILON
                && Math.abs(designTotal - instanceTotal) > EPSILON
                && Math.abs(canonicalStation - instanceStation) <= MATCH_TOLERANCE) {
            canonicalStation = instanceStation * (designTotal / instanceTotal);
        }
        addAnchor(instance, canonical, instanceStation, canonicalStation);
    }

    private static double nearestHaNative(List<PlanCenterlineSample> samples, Vec2d world) {
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStation = Double.NaN;
        for (PlanCenterlineSample sample : samples) {
            if (sample == null || sample.position() == null) {
                continue;
            }
            double d = sample.position().distance(world);
            if (d < bestDist) {
                bestDist = d;
                bestStation = sample.canonicalStation();
            }
        }
        if (bestDist > MATCH_TOLERANCE) {
            return Double.NaN;
        }
        return bestStation;
    }

    private static void addAnchor(
            List<Double> instance,
            List<Double> canonical,
            double i,
            double c) {
        if (!Double.isFinite(i) || !Double.isFinite(c)) {
            return;
        }
        if (!instance.isEmpty()
                && Math.abs(instance.getLast() - i) <= EPSILON
                && Math.abs(canonical.getLast() - c) <= EPSILON) {
            return;
        }
        // Keep instance non-decreasing; skip regressing anchors from noisy matches.
        if (!instance.isEmpty() && i + EPSILON < instance.getLast()) {
            return;
        }
        if (!canonical.isEmpty() && c + EPSILON < canonical.getLast()) {
            // Allow tiny numeric noise; reject clear regression.
            if (c < canonical.getLast() - 10.0 * EPSILON) {
                return;
            }
            c = Math.max(c, canonical.getLast());
        }
        instance.add(i);
        canonical.add(c);
    }

    private static double interpolate(double[] from, double[] to, double value) {
        if (value <= from[0] + EPSILON) {
            return to[0];
        }
        if (value >= from[from.length - 1] - EPSILON) {
            return to[to.length - 1];
        }
        for (int i = 0; i < from.length - 1; i++) {
            double a = from[i];
            double b = from[i + 1];
            if (value < a - EPSILON || value > b + EPSILON) {
                continue;
            }
            if (Math.abs(b - a) <= EPSILON) {
                return to[i];
            }
            double t = (value - a) / (b - a);
            return to[i] + t * (to[i + 1] - to[i]);
        }
        return to[to.length - 1];
    }

    private static boolean isMonotonic(List<Double> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) + EPSILON < values.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    private static double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
