package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 校验保存的编辑曲线是否仍适用于当前路径几何，才允许重新确认绑定。
 */
public final class EditedProfileRebindValidator {

    /** 起终点桩号与道路长度的容差（米）。 */
    private static final double ENDPOINT_STATION_TOLERANCE = 1.0;

    public enum Failure {
        NOT_STATIONABLE,
        NOT_EVALUABLE,
        STATIONS_DO_NOT_COVER_PATH,
        EXCEEDS_MAX_GRADE,
        CONSTRAINT_CONFLICT
    }

    private EditedProfileRebindValidator() {
    }

    public static Failure validate(RoadNetwork network, Road road, RoadSystemConfig config) {
        if (network == null || road == null) {
            return Failure.NOT_STATIONABLE;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return Failure.NOT_STATIONABLE;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return Failure.NOT_EVALUABLE;
        }
        if (!coversCanonicalLength(network, road, alignment)) {
            return Failure.STATIONS_DO_NOT_COVER_PATH;
        }
        float maxGrade = road.getEffectiveMaxSlope(config);
        if (VerticalAlignmentGradeSmoother.exceedsGradeLimit(alignment, maxGrade)) {
            return Failure.EXCEEDS_MAX_GRADE;
        }
        if (config != null) {
            Set<String> unresolved = RoadProfileConstraintSynchronizer.validate(
                network, Set.of(road.getId()), config);
            if (!unresolved.isEmpty()) {
                return Failure.CONSTRAINT_CONFLICT;
            }
        }
        return null;
    }

    public static boolean canRebind(RoadNetwork network, Road road, RoadSystemConfig config) {
        return validate(network, road, config) == null;
    }

    /**
     * True when VA endpoints correspond to canonical stations 0 and L (bidirectional).
     * Rejects curves that start before 0, end after L, or leave a gap inside the road.
     */
    public static boolean coversCanonicalLength(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        if (network == null || road == null || alignment == null || alignment.isEmpty()) {
            return false;
        }
        double length = RoadStationing.canonicalLength(network, road);
        if (!Double.isFinite(length) || length <= ENDPOINT_STATION_TOLERANCE) {
            return false;
        }
        double start = alignment.startStation();
        double end = alignment.endStation();
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            return false;
        }
        // Bidirectional: must sit at 0 and L within tolerance; no overhang beyond path.
        if (Math.abs(start) > ENDPOINT_STATION_TOLERANCE) {
            return false;
        }
        if (Math.abs(end - length) > ENDPOINT_STATION_TOLERANCE) {
            return false;
        }
        if (start < -ENDPOINT_STATION_TOLERANCE || end > length + ENDPOINT_STATION_TOLERANCE) {
            return false;
        }
        return true;
    }

    /**
     * When endpoints are within tolerance of 0/L, return a copy with stations snapped to
     * exact 0 and L (elevations unchanged). Returns null if coverage is not valid.
     */
    public static RoadVerticalAlignment normalizeEndpointsToCanonical(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        if (!coversCanonicalLength(network, road, alignment)) {
            return null;
        }
        double length = RoadStationing.canonicalLength(network, road);
        List<PointOfVerticalIntersection> pvis = alignment.getPvis();
        if (pvis.size() < 2) {
            return null;
        }
        List<PointOfVerticalIntersection> normalized = new ArrayList<>(pvis.size());
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            if (i == 0) {
                normalized.add(copyWithStation(pvi, 0.0));
            } else if (i == pvis.size() - 1) {
                normalized.add(copyWithStation(pvi, length));
            } else {
                normalized.add(pvi);
            }
        }
        return new RoadVerticalAlignment(normalized);
    }

    private static PointOfVerticalIntersection copyWithStation(
            PointOfVerticalIntersection source,
            double station) {
        PointOfVerticalIntersection copy = source.hasCurve()
            ? PointOfVerticalIntersection.withCurve(station, source.getElevation(), source.getCurveLength())
            : PointOfVerticalIntersection.of(station, source.getElevation());
        if (source.getConstraint() != null) {
            return copy.withConstraint(source.getConstraint());
        }
        return copy;
    }
}
