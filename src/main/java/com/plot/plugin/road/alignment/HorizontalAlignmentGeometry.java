package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 平面线形几何求值：沿里程求位姿、采样中心线点列。
 */
public final class HorizontalAlignmentGeometry {

    private static final double INTEGRATION_STEP = 1.0;
    private static final double EPSILON = 1e-9;

    private HorizontalAlignmentGeometry() {
    }

    public static double totalLength(RoadHorizontalAlignment alignment) {
        return alignment != null ? alignment.totalLength() : 0.0;
    }

    public static Optional<AlignmentPose> poseAt(RoadHorizontalAlignment alignment, double chainage) {
        if (alignment == null || alignment.isEmpty() || chainage < -EPSILON) {
            return Optional.empty();
        }
        double total = alignment.totalLength();
        if (chainage > total + EPSILON) {
            return Optional.empty();
        }
        double local = Math.max(0.0, Math.min(chainage, total));

        AlignmentPose pose = AlignmentPose.origin(alignment.getStartBearingRadians())
            .translated(alignment.getOrigin().x, alignment.getOrigin().y);
        double traversed = 0.0;

        for (HorizontalAlignmentElement element : alignment.getElements()) {
            double elementLength = element.getLength();
            if (local <= traversed + elementLength + EPSILON) {
                double within = local - traversed;
                return Optional.of(advanceAlongElement(pose, element, within));
            }
            pose = advanceAlongElement(pose, element, elementLength);
            traversed += elementLength;
        }
        return Optional.of(pose);
    }

    public static List<Vec2d> sample(RoadHorizontalAlignment alignment, double spacing) {
        List<Vec2d> points = new ArrayList<>();
        if (alignment == null || alignment.isEmpty()) {
            return points;
        }
        double step = spacing > EPSILON ? spacing : INTEGRATION_STEP;
        double total = alignment.totalLength();
        for (double chainage = 0.0; chainage <= total + EPSILON; chainage += step) {
            poseAt(alignment, chainage).ifPresent(pose -> points.add(new Vec2d(pose.x(), pose.y())));
        }
        if (points.isEmpty() || total > 0.0) {
            poseAt(alignment, total).ifPresent(pose -> {
                Vec2d end = new Vec2d(pose.x(), pose.y());
                if (points.isEmpty() || !points.getLast().equals(end)) {
                    points.add(end);
                }
            });
        }
        return points;
    }

    public static double elementStartChainage(RoadHorizontalAlignment alignment, int elementIndex) {
        if (alignment == null || elementIndex < 0) {
            return -1.0;
        }
        double station = 0.0;
        List<HorizontalAlignmentElement> elements = alignment.getElements();
        if (elementIndex >= elements.size()) {
            return -1.0;
        }
        for (int i = 0; i < elementIndex; i++) {
            station += elements.get(i).getLength();
        }
        return station;
    }

    public static String describeElement(
            HorizontalAlignmentElement element,
            double startChainage,
            RoadStationFormat format) {
        String start = RoadStationing.format(startChainage, format);
        String end = RoadStationing.format(startChainage + element.getLength(), format);
        return switch (element.getType()) {
            case TANGENT -> start + "–" + end + " T";
            case CIRCULAR_ARC -> start + "–" + end + " R="
                + String.format("%.0f", element.getRadius())
                + (element.getDirection() == TurnDirection.LEFT ? " L" : " R");
            case SPIRAL -> start + "–" + end + " A="
                + String.format("%.0f", element.getSpiralParameterA());
        };
    }

    private static AlignmentPose advanceAlongElement(
            AlignmentPose start,
            HorizontalAlignmentElement element,
            double distance) {
        if (distance <= EPSILON) {
            return start;
        }
        double length = Math.min(distance, element.getLength());
        return switch (element.getType()) {
            case TANGENT -> integrate(start, length, ignored -> 0.0);
            case CIRCULAR_ARC -> {
                double kappa = element.getDirection().sign() / element.getRadius();
                yield integrate(start, length, ignored -> kappa);
            }
            case SPIRAL -> {
                double k0 = start.curvature();
                double elementLength = element.getLength();
                double a = element.getSpiralParameterA();
                double k1 = k0 + elementLength / (a * a);
                yield integrate(start, length, s -> k0 + (k1 - k0) * (s / elementLength));
            }
        };
    }

    private static AlignmentPose integrate(
            AlignmentPose start,
            double length,
            java.util.function.DoubleUnaryOperator curvatureAtLocalS) {
        double x = start.x();
        double y = start.y();
        double bearing = start.bearingRadians();
        double s = 0.0;
        while (s < length - EPSILON) {
            double ds = Math.min(INTEGRATION_STEP, length - s);
            double kappa = curvatureAtLocalS.applyAsDouble(s + ds * 0.5);
            double midBearing = bearing + kappa * ds * 0.5;
            x += ds * Math.cos(midBearing);
            y += ds * Math.sin(midBearing);
            bearing += kappa * ds;
            s += ds;
        }
        double endKappa = curvatureAtLocalS.applyAsDouble(length);
        return new AlignmentPose(x, y, bearing, endKappa);
    }
}
