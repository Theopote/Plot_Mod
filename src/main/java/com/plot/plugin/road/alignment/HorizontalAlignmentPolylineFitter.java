package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 从道路链折线中心线拟合 {@link RoadHorizontalAlignment}（切线 + 圆曲线）。
 * <p>
 * 优先识别倒圆角等留下的圆弧采样并合并为单个圆曲线；剩余尖角再用 PI 切线+圆弧近似。
 */
public final class HorizontalAlignmentPolylineFitter {

    private static final double MIN_POINT_SPACING = 1e-3;
    private static final double MIN_TANGENT_LENGTH = 0.1;
    private static final double MIN_RADIUS = 0.5;
    private static final double MAX_RADIUS = 500.0;
    private static final double COLINEAR_ANGLE_RADIANS = Math.toRadians(0.5);
    private static final double SHARP_CORNER_ANGLE_RADIANS = Math.toRadians(2.0);
    private static final double MIN_ARC_SWEEP_RADIANS = Math.toRadians(5.0);
    private static final double RADIUS_LEG_FRACTION = 0.4;
    /** 圆弧采样至少 4 点，避免把单个尖角三点圆当成真实圆弧。 */
    private static final int MIN_ARC_POINTS = 4;
    private static final double ARC_RADIUS_REL_TOL = 0.2;
    private static final double ARC_ABS_ERROR_FLOOR = 0.25;

    private HorizontalAlignmentPolylineFitter() {
    }

    public static Optional<RoadHorizontalAlignment> fit(RoadNetwork network, Road road) {
        if (road == null || !RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        List<Vec2d> chainPoints = collectChainPoints(network, road);
        if (chainPoints.size() < 2) {
            return Optional.empty();
        }
        if (chainPoints.size() == 2 || isColinear(chainPoints, COLINEAR_ANGLE_RADIANS)) {
            return Optional.of(fitStraight(chainPoints));
        }
        return fitPrimitives(chainPoints);
    }

    static List<Vec2d> collectChainPoints(RoadNetwork network, Road road) {
        List<Vec2d> points = new ArrayList<>();
        for (OrientedRoadSegment oriented : RoadStationing.orientedSegments(network, road)) {
            RoadEdge edge = network.getEdge(oriented.edgeId());
            if (edge == null) {
                continue;
            }
            List<Vec2d> geometry = edge.getCenterlinePoints();
            if (geometry == null || geometry.isEmpty()) {
                continue;
            }
            int startIndex = 0;
            if (!points.isEmpty()
                    && geometry.getFirst().distance(points.getLast()) <= MIN_POINT_SPACING) {
                startIndex = 1;
            }
            if (oriented.forward()) {
                for (int i = startIndex; i < geometry.size(); i++) {
                    appendDistinct(points, geometry.get(i));
                }
            } else {
                for (int i = geometry.size() - 1 - startIndex; i >= 0; i--) {
                    appendDistinct(points, geometry.get(i));
                }
            }
        }
        return List.copyOf(points);
    }

    private static void appendDistinct(List<Vec2d> points, Vec2d candidate) {
        if (candidate == null) {
            return;
        }
        if (points.isEmpty() || points.getLast().distance(candidate) > MIN_POINT_SPACING) {
            points.add(candidate.copy());
        }
    }

    static List<Vec2d> simplifyVertices(List<Vec2d> points, double angleThresholdRadians) {
        if (points.size() <= 2) {
            return List.copyOf(points);
        }
        List<Vec2d> simplified = new ArrayList<>();
        simplified.add(points.getFirst().copy());
        for (int i = 1; i < points.size() - 1; i++) {
            double turn = Math.abs(signedTurnAngle(points.get(i - 1), points.get(i), points.get(i + 1)));
            if (turn >= angleThresholdRadians) {
                simplified.add(points.get(i).copy());
            }
        }
        simplified.add(points.getLast().copy());
        return List.copyOf(simplified);
    }

    static boolean isColinear(List<Vec2d> points, double toleranceRadians) {
        if (points.size() <= 2) {
            return true;
        }
        Vec2d base = points.get(1).subtract(points.get(0));
        if (base.lengthSquared() < MIN_POINT_SPACING * MIN_POINT_SPACING) {
            return false;
        }
        double baseBearing = Math.atan2(base.y, base.x);
        for (int i = 2; i < points.size(); i++) {
            Vec2d leg = points.get(i).subtract(points.get(i - 1));
            if (leg.lengthSquared() < MIN_POINT_SPACING * MIN_POINT_SPACING) {
                continue;
            }
            double bearing = Math.atan2(leg.y, leg.x);
            if (angleDelta(baseBearing, bearing) > toleranceRadians) {
                return false;
            }
        }
        return true;
    }

    static RoadHorizontalAlignment fitStraight(List<Vec2d> points) {
        Vec2d start = points.getFirst();
        Vec2d end = points.getLast();
        Vec2d direction = end.subtract(start);
        double length = direction.length();
        double bearing = Math.atan2(direction.y, direction.x);
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(start.copy(), bearing, List.of());
        if (length > MIN_TANGENT_LENGTH) {
            alignment.addElement(HorizontalAlignmentElement.tangent(length));
        }
        return alignment;
    }

    private static Optional<RoadHorizontalAlignment> fitPrimitives(List<Vec2d> points) {
        List<ArcRun> arcRuns = detectArcRuns(points);
        Vec2d startDirection = points.get(1).subtract(points.get(0));
        if (startDirection.lengthSquared() < MIN_POINT_SPACING * MIN_POINT_SPACING) {
            return Optional.empty();
        }
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            points.getFirst().copy(),
            Math.atan2(startDirection.y, startDirection.x),
            List.of());

        int cursor = 0;
        for (ArcRun arc : arcRuns) {
            if (!emitStraightCorridor(alignment, points, cursor, arc.startIndex())) {
                return Optional.empty();
            }
            if (arc.arcLength() < MIN_TANGENT_LENGTH) {
                return Optional.empty();
            }
            alignment.addElement(HorizontalAlignmentElement.circularArc(
                arc.arcLength(),
                arc.radius(),
                arc.direction()));
            cursor = arc.endIndex();
        }
        if (!emitStraightCorridor(alignment, points, cursor, points.size() - 1)) {
            return Optional.empty();
        }
        return alignment.isEmpty() ? Optional.empty() : Optional.of(alignment);
    }

    /**
     * 将 points[from..to] 写成切线；若中间有尖角则插入合成圆曲线（PI 近似）。
     */
    private static boolean emitStraightCorridor(
            RoadHorizontalAlignment alignment,
            List<Vec2d> points,
            int from,
            int to) {
        if (to <= from) {
            return true;
        }
        List<Integer> corners = new ArrayList<>();
        for (int i = from + 1; i < to; i++) {
            double turn = Math.abs(signedTurnAngle(points.get(i - 1), points.get(i), points.get(i + 1)));
            if (turn >= SHARP_CORNER_ANGLE_RADIANS) {
                corners.add(i);
            }
        }
        if (corners.isEmpty()) {
            double length = pathLength(points, from, to);
            if (length <= MIN_POINT_SPACING) {
                return true;
            }
            if (length < MIN_TANGENT_LENGTH) {
                return false;
            }
            alignment.addElement(HorizontalAlignmentElement.tangent(length));
            return true;
        }

        double pendingOutInset = 0.0;
        int segmentStart = from;
        for (int c = 0; c < corners.size(); c++) {
            int corner = corners.get(c);
            int nextBoundary = c + 1 < corners.size() ? corners.get(c + 1) : to;
            double turn = signedTurnAngle(points.get(corner - 1), points.get(corner), points.get(corner + 1));
            double pathIn = pathLength(points, segmentStart, corner);
            double pathOut = pathLength(points, corner, nextBoundary);
            double legIn = pathIn - pendingOutInset;
            double legOut = pathOut;
            if (legIn < MIN_POINT_SPACING || legOut < MIN_POINT_SPACING) {
                return false;
            }
            double radius = clampRadius(Math.min(legIn, legOut) * RADIUS_LEG_FRACTION);
            double inset = tangentInset(radius, turn);
            if (legIn <= inset + MIN_TANGENT_LENGTH || legOut <= inset + MIN_TANGENT_LENGTH) {
                return false;
            }
            double tangentIn = legIn - inset;
            alignment.addElement(HorizontalAlignmentElement.tangent(tangentIn));
            double arcLength = radius * Math.abs(turn);
            if (arcLength < MIN_TANGENT_LENGTH) {
                return false;
            }
            alignment.addElement(HorizontalAlignmentElement.circularArc(
                arcLength,
                radius,
                turn > 0.0 ? TurnDirection.LEFT : TurnDirection.RIGHT));
            pendingOutInset = inset;
            segmentStart = corner;
        }
        double tail = pathLength(points, segmentStart, to) - pendingOutInset;
        if (tail <= MIN_POINT_SPACING) {
            return true;
        }
        if (tail < MIN_TANGENT_LENGTH) {
            return false;
        }
        alignment.addElement(HorizontalAlignmentElement.tangent(tail));
        return true;
    }

    private static double pathLength(List<Vec2d> points, int from, int to) {
        double length = 0.0;
        for (int i = from; i < to; i++) {
            length += points.get(i).distance(points.get(i + 1));
        }
        return length;
    }

    private record ArcRun(
            int startIndex,
            int endIndex,
            double radius,
            double arcLength,
            TurnDirection direction) {
    }

    private record CircleFit(
            double radius,
            double sweepRadians,
            double maxError,
            TurnDirection direction) {
        boolean acceptable() {
            if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
                return false;
            }
            if (Math.abs(sweepRadians) < MIN_ARC_SWEEP_RADIANS) {
                return false;
            }
            double tol = Math.max(ARC_ABS_ERROR_FLOOR, radius * ARC_RADIUS_REL_TOL * 0.5);
            return maxError <= tol;
        }
    }

    static List<ArcRun> detectArcRuns(List<Vec2d> points) {
        List<ArcRun> runs = new ArrayList<>();
        int n = points.size();
        boolean[] covered = new boolean[n];
        int i = 0;
        while (i <= n - MIN_ARC_POINTS) {
            if (covered[i]) {
                i++;
                continue;
            }
            int bestEnd = -1;
            CircleFit bestFit = null;
            for (int j = i + MIN_ARC_POINTS - 1; j < n; j++) {
                if (covered[j]) {
                    break;
                }
                CircleFit fit = fitCircleRun(points, i, j);
                if (fit != null && fit.acceptable()) {
                    bestEnd = j;
                    bestFit = fit;
                } else if (bestEnd >= 0) {
                    break;
                }
            }
            if (bestEnd >= 0 && bestFit != null) {
                runs.add(new ArcRun(
                    i,
                    bestEnd,
                    bestFit.radius(),
                    bestFit.radius() * Math.abs(bestFit.sweepRadians()),
                    bestFit.direction()));
                for (int k = i; k <= bestEnd; k++) {
                    covered[k] = true;
                }
                i = bestEnd;
            } else {
                i++;
            }
        }
        return List.copyOf(runs);
    }

    private static CircleFit fitCircleRun(List<Vec2d> points, int start, int end) {
        if (end - start + 1 < MIN_ARC_POINTS) {
            return null;
        }
        int mid = (start + end) / 2;
        Vec2d a = points.get(start);
        Vec2d b = points.get(mid);
        Vec2d c = points.get(end);
        Vec2d center = circumcenter(a, b, c);
        if (center == null) {
            return null;
        }
        double radius = center.distance(a);
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            return null;
        }
        double maxError = 0.0;
        for (int i = start; i <= end; i++) {
            maxError = Math.max(maxError, Math.abs(center.distance(points.get(i)) - radius));
        }
        double signedSweep = 0.0;
        for (int i = start + 1; i < end; i++) {
            signedSweep += signedTurnAngle(points.get(i - 1), points.get(i), points.get(i + 1));
        }
        if (Math.abs(signedSweep) < MIN_ARC_SWEEP_RADIANS) {
            double startAngle = Math.atan2(a.y - center.y, a.x - center.x);
            double endAngle = Math.atan2(c.y - center.y, c.x - center.x);
            signedSweep = normalizeSignedAngle(endAngle - startAngle);
        }
        for (int i = start + 1; i < end; i++) {
            double turn = signedTurnAngle(points.get(i - 1), points.get(i), points.get(i + 1));
            if (Math.abs(turn) < COLINEAR_ANGLE_RADIANS) {
                continue;
            }
            if ((turn > 0.0) != (signedSweep > 0.0)) {
                return null;
            }
        }
        for (int i = start + 1; i < end; i++) {
            Vec2d localCenter = circumcenter(points.get(i - 1), points.get(i), points.get(i + 1));
            if (localCenter == null) {
                continue;
            }
            double localR = localCenter.distance(points.get(i));
            if (localR < MIN_RADIUS) {
                continue;
            }
            if (Math.abs(localR - radius) > Math.max(ARC_ABS_ERROR_FLOOR, radius * ARC_RADIUS_REL_TOL)) {
                return null;
            }
        }
        TurnDirection direction = signedSweep > 0.0 ? TurnDirection.LEFT : TurnDirection.RIGHT;
        return new CircleFit(radius, signedSweep, maxError, direction);
    }

    private static Vec2d circumcenter(Vec2d a, Vec2d b, Vec2d c) {
        double d = 2.0 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y));
        if (Math.abs(d) < 1e-12) {
            return null;
        }
        double a2 = a.x * a.x + a.y * a.y;
        double b2 = b.x * b.x + b.y * b.y;
        double c2 = c.x * c.x + c.y * c.y;
        double ux = (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / d;
        double uy = (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / d;
        return new Vec2d(ux, uy);
    }

    private static double tangentInset(double radius, double turnAngleRadians) {
        return radius * Math.tan(Math.abs(turnAngleRadians) * 0.5);
    }

    private static double clampRadius(double radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    static double signedTurnAngle(Vec2d before, Vec2d corner, Vec2d after) {
        Vec2d incoming = corner.subtract(before);
        Vec2d outgoing = after.subtract(corner);
        if (incoming.lengthSquared() < MIN_POINT_SPACING * MIN_POINT_SPACING
                || outgoing.lengthSquared() < MIN_POINT_SPACING * MIN_POINT_SPACING) {
            return 0.0;
        }
        double incomingBearing = Math.atan2(incoming.y, incoming.x);
        double outgoingBearing = Math.atan2(outgoing.y, outgoing.x);
        return normalizeSignedAngle(outgoingBearing - incomingBearing);
    }

    private static double angleDelta(double left, double right) {
        return Math.abs(normalizeSignedAngle(right - left));
    }

    private static double normalizeSignedAngle(double radians) {
        double angle = radians;
        while (angle <= -Math.PI) {
            angle += 2.0 * Math.PI;
        }
        while (angle > Math.PI) {
            angle -= 2.0 * Math.PI;
        }
        return angle;
    }
}
