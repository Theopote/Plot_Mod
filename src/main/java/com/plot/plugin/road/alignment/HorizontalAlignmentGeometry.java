package com.plot.plugin.road.alignment;

import com.plot.api.geometry.Vec2d;
import com.plot.plugin.road.station.ChainageDisplayContext;
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

    /**
     * 按线元类型分段采样中心线（画布叠加层示意用）。相邻同类型线元各自成段。
     */
    public static List<List<Vec2d>> sampleElementsOfType(
            RoadHorizontalAlignment alignment,
            HorizontalAlignmentElementType type,
            double spacing) {
        List<List<Vec2d>> segments = new ArrayList<>();
        if (alignment == null || alignment.isEmpty() || type == null) {
            return segments;
        }
        double step = spacing > EPSILON ? spacing : INTEGRATION_STEP;
        double station = 0.0;
        for (HorizontalAlignmentElement element : alignment.getElements()) {
            double length = element.getLength();
            if (element.getType() == type && length > EPSILON) {
                List<Vec2d> points = new ArrayList<>();
                for (double local = 0.0; local <= length + EPSILON; local += step) {
                    double chainage = station + Math.min(local, length);
                    poseAt(alignment, chainage).ifPresent(pose ->
                        points.add(new Vec2d(pose.x(), pose.y())));
                }
                poseAt(alignment, station + length).ifPresent(pose -> {
                    Vec2d end = new Vec2d(pose.x(), pose.y());
                    if (points.isEmpty() || points.getLast().distanceSquared(end) > 1e-10) {
                        points.add(end);
                    }
                });
                if (points.size() >= 2) {
                    segments.add(List.copyOf(points));
                }
            }
            station += length;
        }
        return List.copyOf(segments);
    }

    /**
     * Extract a contiguous HA sub-chain {@code [rangeStart, rangeEnd]} as a new alignment.
     * Tangents and circular arcs may be truncated; mid-spiral cuts refuse (not lossless).
     */
    public static Optional<RoadHorizontalAlignment> extractRange(
            RoadHorizontalAlignment source,
            double rangeStart,
            double rangeEnd) {
        if (source == null || source.isEmpty()) {
            return Optional.empty();
        }
        double total = source.totalLength();
        if (!Double.isFinite(rangeStart) || !Double.isFinite(rangeEnd)
                || rangeStart < -EPSILON
                || rangeEnd > total + EPSILON
                || rangeEnd <= rangeStart + EPSILON) {
            return Optional.empty();
        }
        double start = Math.max(0.0, Math.min(rangeStart, total));
        double end = Math.max(0.0, Math.min(rangeEnd, total));
        if (end <= start + EPSILON) {
            return Optional.empty();
        }
        Optional<AlignmentPose> startPose = poseAt(source, start);
        if (startPose.isEmpty()) {
            return Optional.empty();
        }
        List<HorizontalAlignmentElement> extracted = new ArrayList<>();
        double traversed = 0.0;
        for (HorizontalAlignmentElement element : source.getElements()) {
            double elementLength = element.getLength();
            double elementStart = traversed;
            double elementEnd = traversed + elementLength;
            traversed = elementEnd;
            if (elementEnd <= start + EPSILON || elementStart >= end - EPSILON) {
                continue;
            }
            double clipStart = Math.max(start, elementStart);
            double clipEnd = Math.min(end, elementEnd);
            double clipLen = clipEnd - clipStart;
            if (clipLen <= EPSILON) {
                continue;
            }
            double withinStart = clipStart - elementStart;
            Optional<HorizontalAlignmentElement> piece =
                truncateElement(element, withinStart, clipLen);
            if (piece.isEmpty()) {
                return Optional.empty();
            }
            extracted.add(piece.get());
        }
        if (extracted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RoadHorizontalAlignment(
            new Vec2d(startPose.get().x(), startPose.get().y()),
            startPose.get().bearingRadians(),
            extracted));
    }

    private static Optional<HorizontalAlignmentElement> truncateElement(
            HorizontalAlignmentElement element,
            double withinStart,
            double clipLength) {
        if (element == null || clipLength <= EPSILON) {
            return Optional.empty();
        }
        return switch (element.getType()) {
            case TANGENT -> Optional.of(HorizontalAlignmentElement.tangent(clipLength));
            case CIRCULAR_ARC -> Optional.of(HorizontalAlignmentElement.circularArc(
                clipLength, element.getRadius(), element.getDirection()));
            case SPIRAL -> {
                // Clothoid factory assumes κ₀=0 at element start — mid-spiral refuse.
                if (withinStart > EPSILON) {
                    yield Optional.empty();
                }
                yield Optional.of(HorizontalAlignmentElement.spiral(
                    clipLength, element.getSpiralParameterA()));
            }
        };
    }

    /**
     * Reverse HA so the former end becomes the new origin (design-opposed extract).
     * SPIRAL elements cannot be losslessly reversed — returns {@code null} (R05).
     */
    public static RoadHorizontalAlignment reverse(RoadHorizontalAlignment alignment) {
        if (alignment == null || alignment.isEmpty()) {
            return alignment != null ? alignment.copy() : null;
        }
        for (HorizontalAlignmentElement element : alignment.getElements()) {
            if (element != null && element.getType() == HorizontalAlignmentElementType.SPIRAL) {
                return null;
            }
        }
        double total = alignment.totalLength();
        Optional<AlignmentPose> endPose = poseAt(alignment, total);
        if (endPose.isEmpty()) {
            return alignment.copy();
        }
        List<HorizontalAlignmentElement> reversed = new ArrayList<>();
        List<HorizontalAlignmentElement> elements = alignment.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HorizontalAlignmentElement element = elements.get(i);
            reversed.add(switch (element.getType()) {
                case TANGENT -> HorizontalAlignmentElement.tangent(element.getLength());
                case CIRCULAR_ARC -> HorizontalAlignmentElement.circularArc(
                    element.getLength(),
                    element.getRadius(),
                    element.getDirection() == TurnDirection.LEFT
                        ? TurnDirection.RIGHT
                        : TurnDirection.LEFT);
                case SPIRAL -> HorizontalAlignmentElement.spiral(
                    element.getLength(), element.getSpiralParameterA());
            });
        }
        double bearing = endPose.get().bearingRadians() + Math.PI;
        while (bearing <= -Math.PI) {
            bearing += 2.0 * Math.PI;
        }
        while (bearing > Math.PI) {
            bearing -= 2.0 * Math.PI;
        }
        return new RoadHorizontalAlignment(
            new Vec2d(endPose.get().x(), endPose.get().y()),
            bearing,
            reversed);
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
        return describeElementRange(element, start, end);
    }

    /**
     * @param canvasToWorldScale 将画布域桩号/半径/A 换算为展示用方块（1m=1格）的比例
     */
    public static String describeElement(
            HorizontalAlignmentElement element,
            double startChainageCanvas,
            ChainageDisplayContext display,
            double canvasToWorldScale) {
        double scale = Double.isFinite(canvasToWorldScale) && canvasToWorldScale > 0.0
            ? canvasToWorldScale
            : 1.0;
        String start = display.format(startChainageCanvas * scale);
        String end = display.format((startChainageCanvas + element.getLength()) * scale);
        return describeElementRange(element, start, end, scale);
    }

    private static String describeElementRange(
            HorizontalAlignmentElement element,
            String start,
            String end) {
        return describeElementRange(element, start, end, 1.0);
    }

    private static String describeElementRange(
            HorizontalAlignmentElement element,
            String start,
            String end,
            double canvasToWorldScale) {
        double scale = Double.isFinite(canvasToWorldScale) && canvasToWorldScale > 0.0
            ? canvasToWorldScale
            : 1.0;
        return switch (element.getType()) {
            case TANGENT -> start + "–" + end + " T";
            case CIRCULAR_ARC -> start + "–" + end + " R="
                + String.format("%.0f", element.getRadius() * scale)
                + (element.getDirection() == TurnDirection.LEFT ? " L" : " R");
            case SPIRAL -> start + "–" + end + " A="
                + String.format("%.0f", element.getSpiralParameterA() * scale);
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
