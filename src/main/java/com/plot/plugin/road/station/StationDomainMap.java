package com.plot.plugin.road.station;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.RoadGeometryUtils;
import com.plot.plugin.road.RoadNetworkBuilder;
import com.plot.plugin.road.alignment.HorizontalAlignmentCenterlineMaterializer;
import com.plot.plugin.road.alignment.HorizontalAlignmentElementType;
import com.plot.plugin.road.alignment.HorizontalAlignmentPolylineFitter;
import com.plot.plugin.road.alignment.PlanCenterlineSample;
import com.plot.plugin.road.alignment.RoadHorizontalAlignment;
import com.plot.plugin.road.alignment.RoadPlanGeometry;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Piecewise map between <strong>unrotated physical instance</strong> chainage and
 * <strong>HA native</strong> station. Seam rotation is applied outside this class
 * via {@link RoadPlanGeometry#haNativeStation} / {@link RoadPlanGeometry#canonicalFromHaNative}.
 */
public final class StationDomainMap {
    private static final double EPSILON = 1e-6;
    private static final double MATCH_TOLERANCE = 0.75;
    private static final double COLINEAR_ANGLE_RADIANS = Math.toRadians(0.5);
    /** Corner / kink threshold — gentle arc samples are not critical interiors. */
    private static final double CRITICAL_CORNER_ANGLE_RADIANS = Math.toRadians(15.0);
    private static final double COLINEAR_RATIO_TOLERANCE = 1e-3;
    private static final double POSE_TOLERANCE = RoadNetworkBuilder.NODE_TOLERANCE;

    private final double[] physicalAnchors;
    private final double[] nativeAnchors;
    private final boolean reliable;

    private StationDomainMap(double[] physicalAnchors, double[] nativeAnchors, boolean reliable) {
        this.physicalAnchors = physicalAnchors;
        this.nativeAnchors = nativeAnchors;
        this.reliable = reliable;
    }

    public boolean isReliable() {
        return reliable;
    }

    public int anchorCount() {
        return physicalAnchors.length;
    }

    public double instanceTotal() {
        return physicalAnchors.length == 0 ? 0.0 : physicalAnchors[physicalAnchors.length - 1];
    }

    /** HA native total (design length when reliable). */
    public double nativeTotal() {
        return nativeAnchors.length == 0 ? 0.0 : nativeAnchors[nativeAnchors.length - 1];
    }

    /** @deprecated use {@link #nativeTotal()} — kept for callers that expected design/canonical span */
    @Deprecated
    public double canonicalTotal() {
        return nativeTotal();
    }

    public static StationDomainMap identity(double length) {
        double L = Math.max(0.0, length);
        return new StationDomainMap(new double[] {0.0, L}, new double[] {0.0, L}, true);
    }

    public static StationDomainMap unreliable(double instanceTotal, double nativeTotal) {
        double i = Math.max(0.0, instanceTotal);
        double n = Math.max(0.0, nativeTotal);
        return new StationDomainMap(new double[] {0.0, i}, new double[] {0.0, n}, false);
    }

    public static StationDomainMap build(RoadNetwork network, Road road) {
        if (network == null || road == null || !RoadStationing.isStationable(network, road)) {
            return unreliable(0.0, 0.0);
        }
        double physicalTotal = physicalInstanceLength(network, road);
        if (!Double.isFinite(physicalTotal) || physicalTotal <= EPSILON) {
            return unreliable(0.0, 0.0);
        }
        if (!RoadPlanGeometry.hasDesignAlignment(network, road)) {
            return identity(physicalTotal);
        }
        double designTotal = RoadPlanGeometry.designLength(network, road);
        if (!Double.isFinite(designTotal) || designTotal <= EPSILON) {
            return unreliable(physicalTotal, 0.0);
        }

        if (isVerifiedCollinearParametricStretch(network, road, physicalTotal, designTotal)) {
            return new StationDomainMap(
                new double[] {0.0, physicalTotal},
                new double[] {0.0, designTotal},
                true);
        }

        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        List<PlanCenterlineSample> haSamples = RoadPlanGeometry.sampleHaNativeUnrotatedPublic(
            alignment, HorizontalAlignmentCenterlineMaterializer.DEFAULT_SAMPLE_SPACING_METERS);
        if (haSamples.size() < 2) {
            return unreliable(physicalTotal, designTotal);
        }

        List<Double> physical = new ArrayList<>();
        List<Double> nativeStations = new ArrayList<>();
        if (!addAnchorStrict(physical, nativeStations, 0.0, 0.0)) {
            return unreliable(physicalTotal, designTotal);
        }

        double minNativeCursor = -EPSILON;
        Set<String> matchedEdgeBoundaries = new HashSet<>();
        Set<String> requiredEdgeBoundaries = new HashSet<>();
        int matchedCriticalInteriors = 0;
        int expectedCriticalInteriors = 0;

        // Seam is NOT a map-build anchor — offset is queried later via toNative(physicalSeam).
        List<OrientedRoadSegment> segments =
            RoadStationing.physicalEdgeSegmentsForMaterialization(network, road);
        for (OrientedRoadSegment segment : segments) {
            RoadEdge edge = network.getEdge(segment.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> points = edge.getCenterlinePoints();
            if (points == null || points.size() < 2) {
                continue;
            }
            String entryKey = segment.edgeId() + "#entry";
            String exitKey = segment.edgeId() + "#exit";
            requiredEdgeBoundaries.add(entryKey);
            requiredEdgeBoundaries.add(exitKey);

            List<Double> chainLocals = chainLocalFeatureStations(segment, points);
            Set<Double> criticalChainLocals = curvatureChangeChainLocals(segment, points);
            for (double chainLocal : chainLocals) {
                boolean atEntry = chainLocal <= EPSILON;
                boolean atExit = chainLocal >= segment.length() - EPSILON;
                boolean criticalInterior = !atEntry && !atExit
                    && isNearAny(criticalChainLocals, chainLocal);
                if (criticalInterior) {
                    expectedCriticalInteriors++;
                }
                AnchorMatch match = matchPhysicalAnchor(
                    haSamples, segment, points, chainLocal, minNativeCursor);
                if (match == null) {
                    if (atEntry || atExit || criticalInterior) {
                        return unreliable(physicalTotal, designTotal);
                    }
                    continue;
                }
                if (!addAnchorStrict(physical, nativeStations, match.physicalStation(), match.nativeStation())) {
                    if (atEntry || atExit || criticalInterior) {
                        return unreliable(physicalTotal, designTotal);
                    }
                    continue;
                }
                minNativeCursor = match.nativeStation();
                if (atEntry) {
                    matchedEdgeBoundaries.add(entryKey);
                }
                if (atExit) {
                    matchedEdgeBoundaries.add(exitKey);
                }
                if (criticalInterior) {
                    matchedCriticalInteriors++;
                }
            }
        }

        if (physical.isEmpty()) {
            return unreliable(physicalTotal, designTotal);
        }
        // Close endpoints: chord-sampled polylines may be slightly shorter than design.
        if (Math.abs(physical.getLast() - physicalTotal) > EPSILON
                || Math.abs(nativeStations.getLast() - designTotal) > EPSILON) {
            if (Math.abs(nativeStations.getLast() - designTotal) <= 10.0 * EPSILON
                    && physical.getLast() + EPSILON < physicalTotal) {
                physical.set(physical.size() - 1, physicalTotal);
            } else if (Math.abs(physical.getLast() - physicalTotal) <= 10.0 * EPSILON
                    && nativeStations.getLast() + EPSILON < designTotal) {
                nativeStations.set(nativeStations.size() - 1, designTotal);
            } else if (!addAnchorStrict(physical, nativeStations, physicalTotal, designTotal)) {
                return unreliable(physicalTotal, designTotal);
            }
        }

        boolean reliable = evaluateReliability(
            physical,
            nativeStations,
            physicalTotal,
            designTotal,
            requiredEdgeBoundaries,
            matchedEdgeBoundaries,
            expectedCriticalInteriors,
            matchedCriticalInteriors);
        return new StationDomainMap(toArray(physical), toArray(nativeStations), reliable);
    }

    /** Physical instance → HA native. */
    public double toNative(double physicalInstance, boolean clamp) {
        if (!reliable || !Double.isFinite(physicalInstance) || physicalAnchors.length < 2) {
            return Double.NaN;
        }
        return interpolate(physicalAnchors, nativeAnchors, physicalInstance, clamp);
    }

    public double toNative(double physicalInstance) {
        return toNative(physicalInstance, true);
    }

    /** HA native → physical instance. */
    public double toPhysical(double nativeStation, boolean clamp) {
        if (!reliable || !Double.isFinite(nativeStation) || nativeAnchors.length < 2) {
            return Double.NaN;
        }
        return interpolate(nativeAnchors, physicalAnchors, nativeStation, clamp);
    }

    public double toPhysical(double nativeStation) {
        return toPhysical(nativeStation, true);
    }

    /**
     * @deprecated physical→native; name kept for LINEAR tests where native≈canonical.
     */
    @Deprecated
    public double toCanonical(double physicalInstance) {
        return toNative(physicalInstance, true);
    }

    @Deprecated
    public double toCanonicalStrict(double physicalInstance) {
        return toNative(physicalInstance, false);
    }

    @Deprecated
    public double toInstance(double nativeStation) {
        return toPhysical(nativeStation, true);
    }

    @Deprecated
    public double toInstanceStrict(double nativeStation) {
        return toPhysical(nativeStation, false);
    }

    private static double physicalInstanceLength(RoadNetwork network, Road road) {
        double length = 0.0;
        for (OrientedRoadSegment segment :
                RoadStationing.physicalEdgeSegmentsForMaterialization(network, road)) {
            length += segment.length();
        }
        return length;
    }

    /** Chain-locals at polyline vertices where bearing changes beyond collinear tolerance. */
    private static Set<Double> curvatureChangeChainLocals(
            OrientedRoadSegment segment,
            List<Vec2d> points) {
        Set<Double> critical = new HashSet<>();
        if (points == null || points.size() < 3) {
            return critical;
        }
        double walked = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            Vec2d prev = points.get(i - 1);
            Vec2d cur = points.get(i);
            Vec2d next = points.get(i + 1);
            walked += prev.distance(cur);
            if (cur == null || prev == null || next == null) {
                continue;
            }
            Vec2d in = cur.subtract(prev);
            Vec2d out = next.subtract(cur);
            if (in.lengthSquared() < EPSILON * EPSILON || out.lengthSquared() < EPSILON * EPSILON) {
                continue;
            }
            double angle = Math.abs(Math.atan2(
                in.x * out.y - in.y * out.x,
                in.x * out.x + in.y * out.y));
            if (angle <= CRITICAL_CORNER_ANGLE_RADIANS) {
                continue;
            }
            if (walked <= segment.geometryChainStart() + EPSILON
                    || walked >= segment.geometryChainEnd() - EPSILON) {
                continue;
            }
            double chainLocal = segment.chainLocalFromGeometryLocal(walked);
            if (chainLocal > EPSILON && chainLocal < segment.length() - EPSILON) {
                critical.add(chainLocal);
            }
        }
        return critical;
    }

    private static boolean isNearAny(Set<Double> values, double candidate) {
        for (double value : values) {
            if (Math.abs(value - candidate) <= EPSILON) {
                return true;
            }
        }
        return false;
    }

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
        List<Double> unique = new ArrayList<>(chainLocals.size());
        for (double value : chainLocals) {
            if (unique.isEmpty() || Math.abs(unique.getLast() - value) > EPSILON) {
                unique.add(value);
            }
        }
        return unique;
    }

    private static AnchorMatch matchPhysicalAnchor(
            List<PlanCenterlineSample> haSamples,
            OrientedRoadSegment segment,
            List<Vec2d> points,
            double chainLocal,
            double minNativeCursor) {
        double geometryLocal = segment.geometryLocalFromChainLocal(chainLocal);
        double physicalStation = segment.roadStationAtChainLocal(chainLocal);
        Vec2d world = RoadGeometryUtils.pointAtDistance(points, geometryLocal);
        if (world == null) {
            return null;
        }
        double nativeStation = projectHaNative(haSamples, world, minNativeCursor);
        if (!Double.isFinite(nativeStation)) {
            return null;
        }
        return new AnchorMatch(physicalStation, nativeStation);
    }

    /**
     * Project onto HA sample polyline. {@code minNativeCursor} enforces ascending native
     * along the <em>unrotated physical</em> walk (no LOOP wrap).
     * <p>
     * Among in-tolerance hits with {@code native >= cursor}, prefer the <em>minimum spatial
     * error</em> (then earliest native). Closed 0/L wrap still tie-breaks without false ambiguity.
     */
    private static double projectHaNative(
            List<PlanCenterlineSample> samples,
            Vec2d world,
            double minNativeCursor) {
        double bestStation = Double.NaN;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < samples.size() - 1; i++) {
            PlanCenterlineSample a = samples.get(i);
            PlanCenterlineSample b = samples.get(i + 1);
            if (a == null || b == null || a.position() == null || b.position() == null) {
                continue;
            }
            Vec2d projected = RoadGeometryUtils.projectPointOnSegment(a.position(), b.position(), world);
            double distance = projected.distance(world);
            if (distance > MATCH_TOLERANCE + EPSILON) {
                continue;
            }
            double span = a.position().distance(b.position());
            double t = span <= EPSILON ? 0.0 : a.position().distance(projected) / span;
            // PlanCenterlineSample.canonicalStation holds HA native for sampleHaNativeUnrotated.
            double nativeStation = a.canonicalStation() + t * (b.canonicalStation() - a.canonicalStation());
            if (nativeStation + EPSILON < minNativeCursor) {
                continue;
            }
            if (!Double.isFinite(bestStation)
                    || distance + EPSILON < bestDist
                    || (Math.abs(distance - bestDist) <= EPSILON && nativeStation + EPSILON < bestStation)) {
                bestStation = nativeStation;
                bestDist = distance;
            }
        }
        return bestStation;
    }

    private static boolean addAnchorStrict(
            List<Double> physical,
            List<Double> nativeStations,
            double p,
            double n) {
        if (!Double.isFinite(p) || !Double.isFinite(n)) {
            return false;
        }
        if (!physical.isEmpty()
                && Math.abs(physical.getLast() - p) <= EPSILON
                && Math.abs(nativeStations.getLast() - n) <= EPSILON) {
            return true;
        }
        if (!physical.isEmpty() && p + EPSILON < physical.getLast()) {
            return false;
        }
        if (!nativeStations.isEmpty() && n + EPSILON < nativeStations.getLast()) {
            return false;
        }
        if (!physical.isEmpty()
                && Math.abs(p - physical.getLast()) <= EPSILON
                && Math.abs(n - nativeStations.getLast()) > EPSILON) {
            // Shared-node re-entry can project with chord jitter; keep the existing native.
            // Do not let a later ordinary sample overwrite a confirmed edge-boundary native.
            return Math.abs(n - nativeStations.getLast()) <= MATCH_TOLERANCE;
        }
        if (!nativeStations.isEmpty()
                && Math.abs(n - nativeStations.getLast()) <= EPSILON
                && Math.abs(p - physical.getLast()) > EPSILON) {
            // Closed HA endpoint: several trailing physical samples clamp to the same native.
            // Endpoint-only clamp — never overwrite an earlier confirmed boundary with a
            // later ordinary anchor that shares native but advances physical improperly.
            if (p + EPSILON >= physical.getLast()) {
                physical.set(physical.size() - 1, p);
                return true;
            }
            return false;
        }
        physical.add(p);
        nativeStations.add(n);
        return true;
    }

    private static boolean evaluateReliability(
            List<Double> physical,
            List<Double> nativeStations,
            double physicalTotal,
            double designTotal,
            Set<String> requiredEdgeBoundaries,
            Set<String> matchedEdgeBoundaries,
            int expectedCriticalInteriors,
            int matchedCriticalInteriors) {
        if (physical.size() < 2 || nativeStations.size() != physical.size()) {
            return false;
        }
        if (!matchedEdgeBoundaries.containsAll(requiredEdgeBoundaries)) {
            return false;
        }
        if (expectedCriticalInteriors > 0
                && matchedCriticalInteriors < expectedCriticalInteriors) {
            return false;
        }
        if (!isStrictlyMonotonic(physical) || !isStrictlyMonotonic(nativeStations)) {
            return false;
        }
        if (Math.abs(physical.getFirst()) > EPSILON || Math.abs(nativeStations.getFirst()) > EPSILON) {
            return false;
        }
        if (Math.abs(physical.getLast() - physicalTotal) > 10.0 * EPSILON) {
            return false;
        }
        if (Math.abs(nativeStations.getLast() - designTotal) > 10.0 * EPSILON) {
            return false;
        }
        return true;
    }

    private static boolean isVerifiedCollinearParametricStretch(
            RoadNetwork network,
            Road road,
            double physicalTotal,
            double designTotal) {
        List<Vec2d> chain = HorizontalAlignmentPolylineFitter.collectChainPoints(network, road);
        if (chain.size() < 2 || !HorizontalAlignmentPolylineFitter.isColinear(chain, COLINEAR_ANGLE_RADIANS)) {
            return false;
        }
        RoadHorizontalAlignment alignment = road.getHorizontalAlignment();
        if (alignment == null || alignment.isEmpty() || alignment.getElements().size() != 1) {
            return false;
        }
        if (alignment.getElements().getFirst().getType() != HorizontalAlignmentElementType.TANGENT) {
            return false;
        }
        Vec2d origin = alignment.getOrigin();
        Vec2d chainStart = chain.getFirst();
        if (origin == null || chainStart == null || origin.distance(chainStart) > POSE_TOLERANCE) {
            return false;
        }
        Vec2d firstLeg = chain.get(1).subtract(chain.getFirst());
        if (firstLeg.lengthSquared() < EPSILON * EPSILON) {
            return false;
        }
        double chainBearing = Math.atan2(firstLeg.y, firstLeg.x);
        double delta = Math.abs(Math.atan2(
            Math.sin(alignment.getStartBearingRadians() - chainBearing),
            Math.cos(alignment.getStartBearingRadians() - chainBearing)));
        if (delta > COLINEAR_ANGLE_RADIANS) {
            return false;
        }
        double ratio = designTotal / physicalTotal;
        return Double.isFinite(ratio)
            && ratio > COLINEAR_RATIO_TOLERANCE
            && Math.abs(ratio * physicalTotal - designTotal) <= EPSILON;
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

    private record AnchorMatch(double physicalStation, double nativeStation) {
    }
}
