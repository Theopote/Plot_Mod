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
 * 从道路链折线中心线拟合 {@link RoadHorizontalAlignment}（切线 + 圆曲线 PI 链）。
 */
public final class HorizontalAlignmentPolylineFitter {

    private static final double MIN_POINT_SPACING = 1e-3;
    private static final double MIN_TANGENT_LENGTH = 0.1;
    private static final double MIN_RADIUS = 0.5;
    private static final double MAX_RADIUS = 500.0;
    private static final double COLINEAR_ANGLE_RADIANS = Math.toRadians(0.5);
    private static final double SIMPLIFY_ANGLE_RADIANS = Math.toRadians(2.0);
    private static final double RADIUS_LEG_FRACTION = 0.4;

    private HorizontalAlignmentPolylineFitter() {
    }

    public static Optional<RoadHorizontalAlignment> fit(RoadNetwork network, Road road) {
        if (network == null || road == null || !RoadStationing.isStationable(network, road)) {
            return Optional.empty();
        }
        List<Vec2d> chainPoints = collectChainPoints(network, road);
        if (chainPoints.size() < 2) {
            return Optional.empty();
        }
        List<Vec2d> vertices = simplifyVertices(chainPoints, SIMPLIFY_ANGLE_RADIANS);
        if (vertices.size() < 2) {
            return Optional.empty();
        }
        if (vertices.size() == 2 || isColinear(vertices, COLINEAR_ANGLE_RADIANS)) {
            return Optional.of(fitStraight(vertices));
        }
        return fitTangentArcChain(vertices);
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

    private static Optional<RoadHorizontalAlignment> fitTangentArcChain(List<Vec2d> vertices) {
        int vertexCount = vertices.size();
        double[] legLengths = new double[vertexCount - 1];
        for (int i = 0; i < vertexCount - 1; i++) {
            legLengths[i] = vertices.get(i).distance(vertices.get(i + 1));
            if (legLengths[i] < MIN_POINT_SPACING) {
                return Optional.empty();
            }
        }

        double[] turnAngles = new double[vertexCount];
        double[] radii = new double[vertexCount];
        for (int i = 1; i < vertexCount - 1; i++) {
            turnAngles[i] = signedTurnAngle(vertices.get(i - 1), vertices.get(i), vertices.get(i + 1));
            if (Math.abs(turnAngles[i]) < COLINEAR_ANGLE_RADIANS) {
                continue;
            }
            radii[i] = clampRadius(Math.min(legLengths[i - 1], legLengths[i]) * RADIUS_LEG_FRACTION);
            double inset = tangentInset(radii[i], turnAngles[i]);
            if (legLengths[i - 1] <= inset + MIN_TANGENT_LENGTH
                    || legLengths[i] <= inset + MIN_TANGENT_LENGTH) {
                return Optional.empty();
            }
        }

        Vec2d startDirection = vertices.get(1).subtract(vertices.get(0));
        double startBearing = Math.atan2(startDirection.y, startDirection.x);
        RoadHorizontalAlignment alignment = new RoadHorizontalAlignment(
            vertices.getFirst().copy(),
            startBearing,
            List.of());

        for (int leg = 0; leg < vertexCount - 1; leg++) {
            double tangentLength = legLengths[leg];
            int cornerAtStart = leg;
            int cornerAtEnd = leg + 1;
            if (cornerAtEnd < vertexCount - 1 && Math.abs(turnAngles[cornerAtEnd]) >= COLINEAR_ANGLE_RADIANS) {
                tangentLength -= tangentInset(radii[cornerAtEnd], turnAngles[cornerAtEnd]);
            }
            if (cornerAtStart >= 1
                    && cornerAtStart < vertexCount - 1
                    && Math.abs(turnAngles[cornerAtStart]) >= COLINEAR_ANGLE_RADIANS) {
                tangentLength -= tangentInset(radii[cornerAtStart], turnAngles[cornerAtStart]);
            }
            if (tangentLength < MIN_TANGENT_LENGTH) {
                return Optional.empty();
            }
            alignment.addElement(HorizontalAlignmentElement.tangent(tangentLength));

            if (cornerAtEnd < vertexCount - 1 && Math.abs(turnAngles[cornerAtEnd]) >= COLINEAR_ANGLE_RADIANS) {
                double arcLength = radii[cornerAtEnd] * Math.abs(turnAngles[cornerAtEnd]);
                TurnDirection direction = turnAngles[cornerAtEnd] > 0.0
                    ? TurnDirection.LEFT
                    : TurnDirection.RIGHT;
                alignment.addElement(HorizontalAlignmentElement.circularArc(
                    arcLength,
                    radii[cornerAtEnd],
                    direction));
            }
        }
        return alignment.isEmpty() ? Optional.empty() : Optional.of(alignment);
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
