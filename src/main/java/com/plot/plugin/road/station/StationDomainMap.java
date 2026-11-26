package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentPolylineFitter;
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
 * between ordered anchors — never an unverified whole-road length ratio.
 */
public final class StationDomainMap {
    private static final double EPSILON = 1e-6;
    private static final double MATCH_TOLERANCE = 0.75;
    private static final double COLINEAR_ANGLE_RADIANS = Math.toRadians(0.5);
    private static final double COLINEAR_RATIO_TOLERANCE = 1e-3;

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

    public int anchorCount() {
        return instanceAnchors.length;
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

    /** Unreliable empty/failure map — conversions return NaN. */
    public static StationDomainMap unreliable(double instanceTotal, double canonicalTotal) {
        double i = Math.max(0.0, instanceTotal);
        double c = Math.max(0.0, canonicalTotal);
        return new StationDomainMap(new double[] {0.0, i}, new double[] {0.0, c}, false);
    }

    public static StationDomainMap build(RoadNetwork network, Road road) {
        if (network == null || road == null || !RoadStationing.isStationable(network, road)) {
            return unreliable(0.0, 0.0);
        }
        double instanceTotal = RoadPlanGeometry.instanceLength(network, road);
        if (!Double.isFinite(instanceTotal) || instanceTotal <= EPSILON) {
            return unreliable(0.0, 0.0);
        }
        if (!RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return identity(instanceTotal);
        }
        double designTotal = RoadPlanGeometry.designLength(network, road);
        if (!Double.isFinite(designTotal) || designTotal <= EPSILON) {
            return unreliable(instanceTotal, 0.0);
        }

        // Whole-road collinear parametric stretch — verified once for the road, not per-anchor.
        if (isVerifiedCollinearParametricStretch(network, road, instanceTotal, designTotal)) {
            return new StationDomainMap(
                new double[] {0.0, instanceTotal},
                new double[] {0.0, designTotal},
                true);
        }

        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        List<PlanCenterlineSample> haSamples = RoadPlanGeometry.sampleHaNativeUnrotatedPublic(
            alignment, HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
        if (haSamples.size() < 2) {
            return unreliable(instanceTotal, designTotal);
        }

        List<Double> instance = new ArrayList<>();
        List<Double> canonical = new ArrayList<>();
        if (!addAnchorStrict(instance, canonical, 0.0, 0.0)) {
            return unreliable(instanceTotal, designTotal);
        }
        double minNativeCursor = -EPSILON;
        int matchedInterior = 0;
        int expectedInterior = 0;

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
            List<Double> chainLocals = chainLocalFeatureStations(segment, points);
            expectedInterior += Math.max(0, chainLocals.size() - 2);
            for (double chainLocal : chainLocals) {
                boolean interior = chainLocal > EPSILON && chainLocal < segment.length() - EPSILON;
                boolean boundary = !interior;
                AnchorMatch match = matchAnchor(
                    network, road, haSamples, designTotal, segment, points, chainLocal, minNativeCursor);
                if (match == null) {
                    if (boundary && chainLocal > EPSILON) {
                        // Missing a non-origin segment boundary → map cannot be trusted.
                        return unreliable(instanceTotal, designTotal);
                    }
                    continue;
                }
                if (!addAnchorStrict(instance, canonical, match.instanceStation(), match.canonicalStation())) {
                    return unreliable(instanceTotal, designTotal);
                }
                minNativeCursor = match.nativeStation();
                if (interior) {
                    matchedInterior++;
                }
            }
        }

        if (instance.isEmpty()) {
            return unreliable(instanceTotal, designTotal);
        }
        if (Math.abs(instance.getLast() - instanceTotal) > EPSILON
                || Math.abs(canonical.getLast() - designTotal) > EPSILON) {
            if (!addAnchorStrict(instance, canonical, instanceTotal, designTotal)) {
                return unreliable(instanceTotal, designTotal);
            }
        }

        boolean reliable = evaluateReliability(
            instance, canonical, instanceTotal, designTotal, expectedInterior, matchedInterior);
        return new StationDomainMap(toArray(instance), toArray(canonical), reliable);
    }

    /**
     * Instance → canonical with endpoint clamp (legacy convenience).
     * Prefer {@link #toCanonicalStrict} when out-of-range must fail.
     */
    public double toCanonical(double instanceChainage) {
        return toCanonical(instanceChainage, true);
    }

    public double toCanonicalStrict(double instanceChainage) {
        return toCanonical(instanceChainage, false);
    }

    public double toCanonical(double instanceChainage, boolean clamp) {
        if (!reliable || !Double.isFinite(instanceChainage) || instanceAnchors.length < 2) {
            return Double.NaN;
        }
        return interpolate(instanceAnchors, canonicalAnchors, instanceChainage, clamp);
    }

    public double toInstance(double canonicalStation) {
        return toInstance(canonicalStation, true);
    }

    public double toInstanceStrict(double canonicalStation) {
        return toInstance(canonicalStation, false);
    }

    public double toInstance(double canonicalStation, boolean clamp) {
        if (!reliable || !Double.isFinite(canonicalStation) || canonicalAnchors.length < 2) {
            return Double.NaN;
        }
        return interpolate(canonicalAnchors, instanceAnchors, canonicalStation, clamp);
    }

    /**
     * Chain-direction feature stations (0 and length inclusive, plus interior vertices).
     * Uses {@link OrientedRoadSegment#geometryLocalFromChainLocal} so reverse edges
     * still emit ascending instance chainage.
     */
    private static List<Double> chainLocalFeatureStations(
            OrientedRoadSegment segment,
            List<Vec2d> points) {
        List<Double> chainLocals = new ArrayList<>();
        chainLocals.add(0.0);
        double walked = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d a = points.get(i);
            Vec2d b = points.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            walked += a.distance(b);
            if (walked <= segment.geometryChainStart() + EPSILON
                    || walked >= segment.geometryChainEnd() - EPSILON) {
                continue;
            }
            double chainLocal = segment.chainLocalFromGeometryLocal(walked);
            if (chainLocal > EPSILON && chainLocal < segment.length() - EPSILON) {
                chainLocals.add(chainLocal);
            }
        }
        chainLocals.add(segment.length());
        chainLocals.sort(Double::compareTo);
        // Dedup
        List<Double> unique = new ArrayList<>(chainLocals.size());
        for (double value : chainLocals) {
            if (unique.isEmpty() || Math.abs(unique.getLast() - value) > EPSILON) {
                unique.add(value);
            }
        }
        return unique;
    }

    private static AnchorMatch matchAnchor(
            RoadNetwork network,
            Road road,
            List<PlanCenterlineSample> haSamples,
            double designTotal,
            OrientedRoadSegment segment,
            List<Vec2d> points,
            double chainLocal,
            double minNativeCursor) {
        double geometryLocal = segment.geometryLocalFromChainLocal(chainLocal);
        double instanceStation = segment.roadStationAtChainLocal(chainLocal);
        Vec2d world = RoadGeometryUtils.pointAtDistance(points, geometryLocal);
        if (world == null) {
            return null;
        }
        double nativeStation = projectHaNative(haSamples, world, minNativeCursor);
        if (!Double.isFinite(nativeStation)) {
            return null;
        }
        double canonicalStation = RoadPlanGeometry.canonicalFromHaNative(
            network, road, nativeStation, designTotal);
        if (!Double.isFinite(canonicalStation)) {
            return null;
        }
        return new AnchorMatch(instanceStation, canonicalStation, nativeStation);
    }

    /**
     * Project onto HA sample polyline segments (not discrete nearest sample only).
     * {@code minNativeCursor} enforces along-chain order so repeat XY passes stay distinct.
     */
    private static double projectHaNative(
            List<PlanCenterlineSample> samples,
            Vec2d world,
            double minNativeCursor) {
        double bestDist = Double.POSITIVE_INFINITY;
        double bestStation = Double.NaN;
        for (int i = 0; i < samples.size() - 1; i++) {
            PlanCenterlineSample a = samples.get(i);
            PlanCenterlineSample b = samples.get(i + 1);
            if (a == null || b == null || a.position() == null || b.position() == null) {
                continue;
            }
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(a.position(), b.position(), world);
            double distance = projected.distance(world);
            if (distance >= bestDist) {
                continue;
            }
            double span = a.position().distance(b.position());
            double t = span <= EPSILON ? 0.0 : a.position().distance(projected) / span;
            double nativeStation = a.canonicalStation() + t * (b.canonicalStation() - a.canonicalStation());
            if (nativeStation + EPSILON < minNativeCursor) {
                continue;
            }
            bestDist = distance;
            bestStation = nativeStation;
        }
        if (!(bestDist <= MATCH_TOLERANCE) || !Double.isFinite(bestStation)) {
            return Double.NaN;
        }
        return bestStation;
    }

    private static boolean addAnchorStrict(
            List<Double> instance,
            List<Double> canonical,
            double i,
            double c) {
        if (!Double.isFinite(i) || !Double.isFinite(c)) {
            return false;
        }
        if (!instance.isEmpty()
                && Math.abs(instance.getLast() - i) <= EPSILON
                && Math.abs(canonical.getLast() - c) <= EPSILON) {
            return true;
        }
        if (!instance.isEmpty() && i + EPSILON < instance.getLast()) {
            return false;
        }
        if (!canonical.isEmpty() && c + EPSILON < canonical.getLast()) {
            return false;
        }
        // Reject unexplained zero-length intervals after the first pair.
        if (!instance.isEmpty()
                && Math.abs(i - instance.getLast()) <= EPSILON
                && Math.abs(c - canonical.getLast()) > EPSILON) {
            return false;
        }
        if (!canonical.isEmpty()
                && Math.abs(c - canonical.getLast()) <= EPSILON
                && Math.abs(i - instance.getLast()) > EPSILON) {
            return false;
        }
        instance.add(i);
        canonical.add(c);
        return true;
    }

    private static boolean evaluateReliability(
            List<Double> instance,
            List<Double> canonical,
            double instanceTotal,
            double designTotal,
            int expectedInterior,
            int matchedInterior) {
        if (instance.size() < 2 || canonical.size() != instance.size()) {
            return false;
        }
        // Endpoints alone are not enough when the road has interior geometry to match.
        if (expectedInterior > 0 && matchedInterior == 0 && instance.size() <= 2) {
            return false;
        }
        if (!isStrictlyMonotonic(instance) || !isStrictlyMonotonic(canonical)) {
            return false;
        }
        if (Math.abs(instance.getFirst()) > EPSILON || Math.abs(canonical.getFirst()) > EPSILON) {
            return false;
        }
        if (Math.abs(instance.getLast() - instanceTotal) > 10.0 * EPSILON) {
            return false;
        }
        if (Math.abs(canonical.getLast() - designTotal) > 10.0 * EPSILON) {
            return false;
        }
        // Require coverage: at least one interior anchor when totals diverge locally,
        // or when more than two anchors were expected from vertices.
        if (expectedInterior > 0 && matchedInterior < Math.min(expectedInterior, 1)) {
            return false;
        }
        return true;
    }

    /**
     * Whole-road collinear parametric correspondence (identity or uniform stretch).
     * Equal totals alone are not enough — both the instance chain and HA must be a
     * single collinear tangent parameterisation. Non-colinear same-length roads still
     * go through local anchor matching.
     */
    private static boolean isVerifiedCollinearParametricStretch(
            RoadNetwork network,
            Road road,
            double instanceTotal,
            double designTotal) {
        List<Vec2d> chain = HorizontalAlignmentPolylineFitter.collectChainPoints(network, road);
        if (chain.size() < 2 || !HorizontalAlignmentPolylineFitter.isColinear(chain, COLINEAR_ANGLE_RADIANS)) {
            return false;
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        if (alignment == null || alignment.isEmpty() || alignment.getElements().size() != 1) {
            return false;
        }
        double ratio = designTotal / instanceTotal;
        return Double.isFinite(ratio)
            && ratio > COLINEAR_RATIO_TOLERANCE
            && Math.abs(ratio * instanceTotal - designTotal) <= EPSILON;
    }

    private static double interpolate(double[] from, double[] to, double value, boolean clamp) {
        if (from.length < 2) {
            return Double.NaN;
        }
        if (value < from[0] - EPSILON) {
            return clamp ? to[0] : Double.NaN;
        }
        if (value > from[from.length - 1] + EPSILON) {
            return clamp ? to[to.length - 1] : Double.NaN;
        }
        if (value <= from[0] + EPSILON) {
            return to[0];
        }
        if (value >= from[from.length - 1] - EPSILON) {
            return to[to.length - 1];
        }
        int hi = upperBound(from, value);
        int lo = hi - 1;
        if (lo < 0 || hi >= from.length) {
            return Double.NaN;
        }
        double a = from[lo];
        double b = from[hi];
        if (Math.abs(b - a) <= EPSILON) {
            return Double.NaN;
        }
        double t = (value - a) / (b - a);
        return to[lo] + t * (to[hi] - to[lo]);
    }

    private static int upperBound(double[] values, double key) {
        int lo = 0;
        int hi = values.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (values[mid] < key - EPSILON) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        if (lo == 0) {
            return 1;
        }
        return lo;
    }

    private static boolean isStrictlyMonotonic(List<Double> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) <= values.get(i - 1) + EPSILON) {
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

    private record AnchorMatch(double instanceStation, double canonicalStation, double nativeStation) {
    }
}
