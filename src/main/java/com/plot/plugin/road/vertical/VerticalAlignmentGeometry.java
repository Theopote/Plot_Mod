package com.plot.plugin.road.vertical;

import com.plot.plugin.road.station.RoadStationFormat;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 纵断面几何求值：沿桩号求标高/坡度、K 值、采样与 PVI 描述。
 */
public final class VerticalAlignmentGeometry {

    private static final double EPSILON = 1e-9;

    private VerticalAlignmentGeometry() {
    }

    public record ProfileSample(double station, double elevation, double gradePercent) {
    }

    private sealed interface CompiledSegment permits TangentSegment, CurveSegment {
        boolean contains(double station);

        double elevationAt(double station);

        double gradeAt(double station);
    }

    private record TangentSegment(
            double startStation,
            double endStation,
            double startElevation,
            double gradePercent) implements CompiledSegment {

        @Override
        public boolean contains(double station) {
            return station >= startStation - EPSILON && station <= endStation + EPSILON;
        }

        @Override
        public double elevationAt(double station) {
            double clamped = Math.max(startStation, Math.min(station, endStation));
            return startElevation + (gradePercent / 100.0) * (clamped - startStation);
        }

        @Override
        public double gradeAt(double station) {
            return gradePercent;
        }
    }

    private record CurveSegment(
            double bvcStation,
            double pviStation,
            double evcStation,
            double bvcElevation,
            double incomingGradePercent,
            double outgoingGradePercent) implements CompiledSegment {

        private double length() {
            return evcStation - bvcStation;
        }

        @Override
        public boolean contains(double station) {
            return station >= bvcStation - EPSILON && station <= evcStation + EPSILON;
        }

        @Override
        public double elevationAt(double station) {
            double clamped = Math.max(bvcStation, Math.min(station, evcStation));
            double x = clamped - bvcStation;
            double length = length();
            if (length <= EPSILON) {
                return bvcElevation;
            }
            double gIn = incomingGradePercent / 100.0;
            double gOut = outgoingGradePercent / 100.0;
            return bvcElevation + gIn * x + (gOut - gIn) * x * x / (2.0 * length);
        }

        @Override
        public double gradeAt(double station) {
            double length = length();
            if (length <= EPSILON) {
                return incomingGradePercent;
            }
            double x = Math.max(0.0, Math.min(station - bvcStation, length));
            return incomingGradePercent + (outgoingGradePercent - incomingGradePercent) * (x / length);
        }
    }

    public static boolean isEvaluable(RoadVerticalAlignment alignment) {
        return alignment != null && alignment.sortedPvis().size() >= 2;
    }

    public static OptionalDouble elevationAt(RoadVerticalAlignment alignment, double station) {
        if (!isEvaluable(alignment) || station < alignment.startStation() - EPSILON) {
            return OptionalDouble.empty();
        }
        List<PointOfVerticalIntersection> pvis = alignment.sortedPvis();
        if (pvis.isEmpty()) {
            return OptionalDouble.empty();
        }
        if (station > pvis.getLast().getStation() + EPSILON) {
            return OptionalDouble.empty();
        }
        for (CompiledSegment segment : compile(pvis)) {
            if (segment.contains(station)) {
                return OptionalDouble.of(segment.elevationAt(station));
            }
        }
        return OptionalDouble.of(pvis.getLast().getElevation());
    }

    public static OptionalDouble gradeAt(RoadVerticalAlignment alignment, double station) {
        if (!isEvaluable(alignment) || station < alignment.startStation() - EPSILON) {
            return OptionalDouble.empty();
        }
        List<PointOfVerticalIntersection> pvis = alignment.sortedPvis();
        if (station > pvis.getLast().getStation() + EPSILON) {
            return OptionalDouble.empty();
        }
        for (CompiledSegment segment : compile(pvis)) {
            if (segment.contains(station)) {
                return OptionalDouble.of(segment.gradeAt(station));
            }
        }
        int last = pvis.size() - 1;
        return OptionalDouble.of(tangentGradePercent(pvis.get(last - 1), pvis.get(last)));
    }

    /**
     * K = L / |Δg|，Δg 为相邻切线坡度代数差（百分数，如 5% 与 2% 之差为 3）。
     */
    public static double kValue(double curveLength, double incomingGradePercent, double outgoingGradePercent) {
        double delta = Math.abs(outgoingGradePercent - incomingGradePercent);
        if (curveLength <= EPSILON || delta <= EPSILON) {
            return 0.0;
        }
        return curveLength / delta;
    }

    public static double curveLengthFromK(double kValue, double incomingGradePercent, double outgoingGradePercent) {
        if (kValue <= EPSILON) {
            return 0.0;
        }
        return kValue * Math.abs(outgoingGradePercent - incomingGradePercent);
    }

    public static double tangentGradePercent(PointOfVerticalIntersection from, PointOfVerticalIntersection to) {
        double deltaStation = to.getStation() - from.getStation();
        if (deltaStation <= EPSILON) {
            return 0.0;
        }
        return 100.0 * (to.getElevation() - from.getElevation()) / deltaStation;
    }

    public static VerticalCurveType curveTypeAtPvi(
            List<PointOfVerticalIntersection> pvis,
            int pviIndex) {
        if (pviIndex <= 0 || pviIndex >= pvis.size() - 1) {
            return VerticalCurveType.CREST;
        }
        double incoming = tangentGradePercent(pvis.get(pviIndex - 1), pvis.get(pviIndex));
        double outgoing = tangentGradePercent(pvis.get(pviIndex), pvis.get(pviIndex + 1));
        return VerticalCurveType.fromGradesPercent(incoming, outgoing);
    }

    public static List<ProfileSample> sample(RoadVerticalAlignment alignment, double spacing) {
        List<ProfileSample> samples = new ArrayList<>();
        if (!isEvaluable(alignment)) {
            return samples;
        }
        double step = spacing > EPSILON ? spacing : 10.0;
        double end = alignment.endStation();
        for (double station = alignment.startStation(); station <= end + EPSILON; station += step) {
            addSample(samples, alignment, station);
        }
        if (samples.isEmpty() || Math.abs(samples.getLast().station - end) > EPSILON) {
            addSample(samples, alignment, end);
        }
        return samples;
    }

    public static String describePvi(
            PointOfVerticalIntersection pvi,
            int index,
            int total,
            RoadStationFormat format) {
        String station = RoadStationing.format(pvi.getStation(), format);
        String elevation = String.format("%.2f", pvi.getElevation());
        if (pvi.hasCurve() && index > 0 && index < total - 1) {
            return station + " EL=" + elevation + " L=" + String.format("%.0f", pvi.getCurveLength());
        }
        return station + " EL=" + elevation;
    }

    public static String describeCurveAtPvi(
            List<PointOfVerticalIntersection> pvis,
            int pviIndex,
            RoadStationFormat format) {
        if (pviIndex <= 0 || pviIndex >= pvis.size() - 1) {
            return "";
        }
        PointOfVerticalIntersection pvi = pvis.get(pviIndex);
        if (!pvi.hasCurve()) {
            return "";
        }
        double incoming = tangentGradePercent(pvis.get(pviIndex - 1), pvi);
        double outgoing = tangentGradePercent(pvi, pvis.get(pviIndex + 1));
        double k = kValue(pvi.getCurveLength(), incoming, outgoing);
        VerticalCurveType type = VerticalCurveType.fromGradesPercent(incoming, outgoing);
        double half = pvi.getCurveLength() * 0.5;
        String bvc = RoadStationing.format(pvi.getStation() - half, format);
        String evc = RoadStationing.format(pvi.getStation() + half, format);
        String typeLabel = type == VerticalCurveType.CREST ? "Crest" : "Sag";
        return bvc + "–" + evc + " " + typeLabel + " K=" + String.format("%.1f", k)
            + " g" + formatGrade(incoming) + "→" + formatGrade(outgoing);
    }

    private static String formatGrade(double gradePercent) {
        return String.format("%.1f%%", gradePercent);
    }

    private static void addSample(List<ProfileSample> samples, RoadVerticalAlignment alignment, double station) {
        OptionalDouble elevation = elevationAt(alignment, station);
        OptionalDouble grade = gradeAt(alignment, station);
        if (elevation.isPresent()) {
            samples.add(new ProfileSample(
                station,
                elevation.getAsDouble(),
                grade.orElse(0.0)
            ));
        }
    }

    private static List<CompiledSegment> compile(List<PointOfVerticalIntersection> pvis) {
        List<CompiledSegment> segments = new ArrayList<>();
        if (pvis.size() < 2) {
            return segments;
        }

        int n = pvis.size();
        double[] tangentGrades = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            tangentGrades[i] = tangentGradePercent(pvis.get(i), pvis.get(i + 1));
        }

        double cursorStation = pvis.getFirst().getStation();
        double cursorElevation = pvis.getFirst().getElevation();

        for (int i = 0; i < n - 1; i++) {
            PointOfVerticalIntersection next = pvis.get(i + 1);
            double nextStation = next.getStation();
            double tangentEndStation = nextStation;
            Double curveLength = null;
            if (i + 1 < n - 1 && next.hasCurve()) {
                curveLength = next.getCurveLength();
                tangentEndStation = nextStation - curveLength * 0.5;
            }

            if (tangentEndStation > cursorStation + EPSILON) {
                double grade = tangentGrades[i];
                if (i > 0 && pvis.get(i).hasCurve()) {
                    grade = tangentGrades[i - 1];
                }
                segments.add(new TangentSegment(cursorStation, tangentEndStation, cursorElevation, grade));
                cursorElevation += (grade / 100.0) * (tangentEndStation - cursorStation);
                cursorStation = tangentEndStation;
            }

            if (curveLength != null && curveLength > EPSILON) {
                double incoming = tangentGrades[i];
                double outgoing = tangentGrades[i + 1];
                double half = curveLength * 0.5;
                double bvc = nextStation - half;
                double evc = nextStation + half;
                double bvcElevation = next.getElevation() - (incoming / 100.0) * half;
                segments.add(new CurveSegment(
                    bvc,
                    nextStation,
                    evc,
                    bvcElevation,
                    incoming,
                    outgoing
                ));
                cursorStation = evc;
                cursorElevation = next.getElevation() + (outgoing / 100.0) * half;
            } else {
                cursorStation = nextStation;
                cursorElevation = next.getElevation();
            }
        }
        return segments;
    }
}
