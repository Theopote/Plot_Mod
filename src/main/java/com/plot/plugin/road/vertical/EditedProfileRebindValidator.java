package com.plot.plugin.road.vertical;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

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

    private static boolean coversCanonicalLength(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        double length = RoadStationing.canonicalLength(network, road);
        if (!Double.isFinite(length) || length <= ENDPOINT_STATION_TOLERANCE) {
            return false;
        }
        return alignment.startStation() <= ENDPOINT_STATION_TOLERANCE
            && alignment.endStation() >= length - ENDPOINT_STATION_TOLERANCE;
    }
}
