package com.plot.plugin.road.vertical;

import java.util.ArrayList;
import java.util.List;

/** Fits a non-overlapping symmetric vertical curve at a selected middle PVI. */
public final class VerticalProfileCurveFitter {
    private static final double EPSILON = 1e-6;
    public record Result(RoadVerticalAlignment alignment, boolean changed, boolean hasSpace) { }
    private VerticalProfileCurveFitter() { }

    public static Result fitAt(RoadVerticalAlignment source, int pviIndex) {
        if (source == null || !source.hasStrictlyIncreasingStorageOrder()
                || pviIndex <= 0 || pviIndex >= source.pviCount() - 1) {
            return new Result(source != null ? source.copy() : new RoadVerticalAlignment(), false, false);
        }
        List<PointOfVerticalIntersection> pvis = source.getPvis();
        PointOfVerticalIntersection previous = pvis.get(pviIndex - 1);
        PointOfVerticalIntersection current = pvis.get(pviIndex);
        PointOfVerticalIntersection next = pvis.get(pviIndex + 1);
        // A symmetric curve generally does not pass through its tangent-intersection elevation.
        // Shared junctions must preserve their exact network elevation, so transition curves belong
        // on adjacent free PVIs instead.
        if (current.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
            return new Result(source.copy(), false, false);
        }
        double leftBoundary = previous.getStation()
            + (previous.hasCurve() ? previous.getCurveLength() * 0.5 : 0.0);
        double rightBoundary = next.getStation()
            - (next.hasCurve() ? next.getCurveLength() * 0.5 : 0.0);
        double length = VerticalProfileDesignRules.suggestedTransitionLength(
            current.getStation() - leftBoundary, rightBoundary - current.getStation());
        if (length <= EPSILON) {
            return new Result(source.copy(), false, false);
        }
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            edited.add(i == pviIndex
                ? new PointOfVerticalIntersection(
                    pvi.getStation(), pvi.getElevation(), length, pvi.getConstraint())
                : pvi.copy());
        }
        boolean changed = !current.hasCurve() || Math.abs(current.getCurveLength() - length) > EPSILON;
        return new Result(new RoadVerticalAlignment(edited), changed, true);
    }
}
