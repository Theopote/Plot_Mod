package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadEdge;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.profile.ProfileControlPoint;
import com.plot.plugin.road.profile.ProfilePointRole;
import com.plot.plugin.road.station.OrientedRoadSegment;
import com.plot.plugin.road.station.RoadStationing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Projects road-level PVIs into a selected edge's unfolded longitudinal profile. */
public final class VerticalProfileControlPoints {
    private static final double EPSILON = VerticalProfileConstants.STATION_EPSILON;

    public record ControlPoint(
            int pviIndex,
            double roadStation,
            double localDistance,
            double elevation,
            Double leftGradePercent,
            Double rightGradePercent,
            boolean endpoint,
            boolean sharedJunction,
            boolean elevationEditable) { }

    private VerticalProfileControlPoints() { }

    /** 道路级纵断面：全部 PVI，X 为 canonical road station。 */
    public static List<ProfileControlPoint> forRoad(RoadNetwork network, Road road) {
        if (network == null || road == null || road.getVerticalAlignment() == null) {
            return List.of();
        }
        return forAlignment(network, road, road.getVerticalAlignment());
    }

    /** 从指定 alignment 投影控制点（Draft 渲染 / 编辑预览）。 */
    public static List<ProfileControlPoint> forAlignment(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        if (network == null || road == null || alignment == null) {
            return List.of();
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            return List.of();
        }
        List<PointOfVerticalIntersection> pvis = alignment.getPvis();
        List<ProfileControlPoint> result = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            Double left = i > 0
                ? VerticalAlignmentGeometry.tangentGradePercent(pvis.get(i - 1), pvi)
                : null;
            Double right = i + 1 < pvis.size()
                ? VerticalAlignmentGeometry.tangentGradePercent(pvi, pvis.get(i + 1))
                : null;
            boolean sharedJunction = VerticalAlignmentJunctionSynchronizer.isSharedJunctionAtStation(
                network, road, pvi.getStation());
            ProfilePointRole role = resolveRole(road, i, pvis.size(), pvi, sharedJunction);
            result.add(new ProfileControlPoint(
                i,
                pvi.getStation(),
                pvi.getElevation(),
                role,
                left,
                right,
                sharedJunction,
                elevationEditable(road, pvi, sharedJunction, i, pvis.size())));
        }
        return List.copyOf(result);
    }

    private static ProfilePointRole resolveRole(
            Road road,
            int index,
            int count,
            PointOfVerticalIntersection pvi,
            boolean sharedJunction) {
        boolean loop = road != null && road.getTopologyMode() == RoadTopologyMode.LOOP;
        if (index == 0) {
            return loop ? ProfilePointRole.LOOP_SEAM_START : ProfilePointRole.START_ENDPOINT;
        }
        if (index == count - 1) {
            return loop ? ProfilePointRole.LOOP_SEAM_END : ProfilePointRole.END_ENDPOINT;
        }
        if (sharedJunction || pvi.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
            return ProfilePointRole.JUNCTION_FIXED;
        }
        return ProfilePointRole.INTERIOR_PVI;
    }

    /** @deprecated 纵断面编辑器已升级为道路级；保留供过渡与单测。 */
    @Deprecated
    public static List<ControlPoint> forEdge(RoadNetwork network, Road road, RoadEdge edge) {
        if (network == null || road == null || edge == null) {
            return List.of();
        }
        if (road.getVerticalMode() == RoadVerticalMode.FLAT) {
            return List.of();
        }
        if (road.getVerticalAlignment() == null) {
            return List.of();
        }
        Optional<OrientedRoadSegment> oriented = RoadStationing.orientedSegment(network, road, edge.getId());
        if (oriented.isEmpty()) {
            return List.of();
        }
        List<PointOfVerticalIntersection> pvis = road.getVerticalAlignment().getPvis();
        List<ControlPoint> result = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            var local = oriented.get().geometryLocalAtRoadStation(pvi.getStation());
            if (local.isEmpty()) {
                continue;
            }
            Double left = i > 0
                ? VerticalAlignmentGeometry.tangentGradePercent(pvis.get(i - 1), pvi)
                : null;
            Double right = i + 1 < pvis.size()
                ? VerticalAlignmentGeometry.tangentGradePercent(pvi, pvis.get(i + 1))
                : null;
            boolean endpoint = i == 0 || i == pvis.size() - 1;
            boolean sharedJunction = VerticalAlignmentJunctionSynchronizer.isSharedJunctionAtStation(
                network, road, pvi.getStation());
            result.add(new ControlPoint(
                i, pvi.getStation(), local.getAsDouble(), pvi.getElevation(), left, right,
                endpoint,
                sharedJunction,
                elevationEditable(road, pvi, sharedJunction, i, pvis.size())));
        }
        return List.copyOf(result);
    }

    /** Returns a copy with one PVI elevation changed, preserving station and curve length. */
    public static RoadVerticalAlignment withElevation(
            RoadVerticalAlignment source,
            int pviIndex,
            double elevation) {
        return withElevation(source, pviIndex, elevation, null);
    }

    public static RoadVerticalAlignment withElevation(
            RoadVerticalAlignment source,
            int pviIndex,
            double elevation,
            Road road) {
        if (source == null || pviIndex < 0 || pviIndex >= source.pviCount()
                || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("invalid PVI edit");
        }
        boolean syncLoopSeam = road != null && road.getTopologyMode() == RoadTopologyMode.LOOP
            && source.pviCount() >= 2
            && (pviIndex == 0 || pviIndex == source.pviCount() - 1);
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (int i = 0; i < source.pviCount(); i++) {
            PointOfVerticalIntersection pvi = source.getPvis().get(i);
            boolean apply = i == pviIndex || (syncLoopSeam && (i == 0 || i == source.pviCount() - 1));
            edited.add(apply
                ? new PointOfVerticalIntersection(
                    pvi.getStation(), elevation, pvi.getCurveLength(), pvi.getConstraint())
                : pvi.copy());
        }
        return new RoadVerticalAlignment(edited);
    }

    /** Moves one control point while preserving endpoint stations and minimum neighbor spacing. */
    public static RoadVerticalAlignment move(
            RoadVerticalAlignment source,
            int pviIndex,
            double requestedStation,
            double elevation,
            double roadLength) {
        if (source == null || pviIndex < 0 || pviIndex >= source.pviCount()
                || !Double.isFinite(requestedStation) || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("invalid PVI move");
        }
        if (!VerticalProfileDesignRules.slopeAllowed(roadLength)) {
            return VerticalProfileDesignRules.flatAlignment(roadLength, elevation);
        }
        List<PointOfVerticalIntersection> pvis = source.getPvis();
        double station;
        if (pviIndex == 0 || pviIndex == pvis.size() - 1) {
            station = pvis.get(pviIndex).getStation();
        } else {
            double minimum = pvis.get(pviIndex - 1).getStation()
                + VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            double maximum = pvis.get(pviIndex + 1).getStation()
                - VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
            maximum = Math.min(maximum, roadLength);
            if (minimum > maximum) {
                station = pvis.get(pviIndex).getStation();
            } else {
                station = Math.max(minimum, Math.min(maximum, requestedStation));
            }
        }
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            edited.add(i == pviIndex
                ? new PointOfVerticalIntersection(
                    station, elevation, pvi.getCurveLength(), pvi.getConstraint())
                : pvi.copy());
        }
        return normalizeAdjacentCurves(new RoadVerticalAlignment(edited), pviIndex);
    }

    /**
     * 可改高程：普通端点与中间变坡点可以；平交共享桩号与 JUNCTION_FIXED 走交叉标记。
     * 端点桩号仍由 {@link #move} 锁住。
     */
    public static boolean isEditablePvi(RoadNetwork network, Road road, ControlPoint point) {
        if (network == null || road == null || point == null) {
            return false;
        }
        if (road.getVerticalAlignment() == null
                || point.pviIndex() < 0
                || point.pviIndex() >= road.getVerticalAlignment().pviCount()) {
            return false;
        }
        return point.elevationEditable();
    }

    public static boolean isEditablePvi(RoadNetwork network, Road road, ProfileControlPoint point) {
        if (network == null || road == null || point == null) {
            return false;
        }
        if (road.getVerticalAlignment() == null
                || point.pviIndex() < 0
                || point.pviIndex() >= road.getVerticalAlignment().pviCount()) {
            return false;
        }
        return point.elevationEditable();
    }

    private static boolean elevationEditable(
            Road road,
            PointOfVerticalIntersection pvi,
            boolean sharedJunction,
            int index,
            int count) {
        if (road == null || pvi == null || road.getVerticalMode() == RoadVerticalMode.FLAT) {
            return false;
        }
        if (index == 0 || index == count - 1) {
            return true;
        }
        return !sharedJunction && pvi.getConstraint() != VerticalControlPointConstraint.JUNCTION_FIXED;
    }

    public static boolean canAutoSmooth(RoadNetwork network, Road road, ControlPoint point) {
        if (!isEditablePvi(network, road, point)) {
            return false;
        }
        if (road.getVerticalAlignment() == null) {
            return false;
        }
        int index = point.pviIndex();
        return index > 0 && index < road.getVerticalAlignment().pviCount() - 1;
    }

    public static boolean canAutoSmooth(RoadNetwork network, Road road, ProfileControlPoint point) {
        if (!isEditablePvi(network, road, point)) {
            return false;
        }
        if (road.getVerticalAlignment() == null) {
            return false;
        }
        int index = point.pviIndex();
        return index > 0 && index < road.getVerticalAlignment().pviCount() - 1;
    }

    public static boolean exceedsGradeLimit(ControlPoint point, double maxGradePercent) {
        if (point == null || maxGradePercent <= EPSILON) {
            return false;
        }
        return point.leftGradePercent() != null
                && Math.abs(point.leftGradePercent()) > maxGradePercent + EPSILON
            || point.rightGradePercent() != null
                && Math.abs(point.rightGradePercent()) > maxGradePercent + EPSILON;
    }

    public static ControlPoint toLegacyControlPoint(ProfileControlPoint point) {
        if (point == null) {
            return null;
        }
        return new ControlPoint(
            point.pviIndex(),
            point.roadStation(),
            point.roadStation(),
            point.elevation(),
            point.leftGradePercent(),
            point.rightGradePercent(),
            point.endpoint(),
            point.sharedJunction(),
            point.elevationEditable());
    }

    public static boolean exceedsGradeLimit(ProfileControlPoint point, double maxGradePercent) {
        if (point == null || maxGradePercent <= EPSILON) {
            return false;
        }
        return point.leftGradePercent() != null
                && Math.abs(point.leftGradePercent()) > maxGradePercent + EPSILON
            || point.rightGradePercent() != null
                && Math.abs(point.rightGradePercent()) > maxGradePercent + EPSILON;
    }

    /** 在已有纵断面中插入变坡点；要求至少已有两个端点。 */
    public static RoadVerticalAlignment insertAt(
            RoadVerticalAlignment source,
            double station,
            double elevation,
            double roadLength) {
        if (source == null || source.pviCount() < 2
                || !Double.isFinite(station) || !Double.isFinite(elevation)) {
            throw new IllegalArgumentException("invalid PVI insert");
        }
        List<PointOfVerticalIntersection> pvis = new ArrayList<>(source.getPvis());
        int insertIndex = pvis.size() - 1;
        for (int i = 1; i < pvis.size(); i++) {
            if (station + EPSILON < pvis.get(i).getStation()) {
                insertIndex = i;
                break;
            }
            if (Math.abs(station - pvis.get(i).getStation()) <= EPSILON) {
                throw new IllegalArgumentException("duplicate PVI station");
            }
        }
        double minimum = pvis.get(insertIndex - 1).getStation()
            + VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        double maximum = pvis.get(insertIndex).getStation()
            - VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        if (minimum > maximum) {
            throw new IllegalArgumentException("insufficient room for PVI insert");
        }
        station = Math.max(minimum, Math.min(maximum, station));
        pvis.add(insertIndex, PointOfVerticalIntersection.of(station, elevation));
        return new RoadVerticalAlignment(pvis);
    }

    /**
     * 无有效纵断面时从端点标高引导创建，否则插入新变坡点。
     */
    public static RoadVerticalAlignment bootstrapOrInsert(
            RoadVerticalAlignment source,
            double roadLength,
            double startElevation,
            double endElevation,
            double insertStation,
            double insertElevation) {
        if (!Double.isFinite(roadLength) || roadLength <= EPSILON) {
            throw new IllegalArgumentException("invalid road length");
        }
        if (!VerticalProfileDesignRules.slopeAllowed(roadLength)) {
            return VerticalProfileDesignRules.flatAlignment(roadLength, insertElevation);
        }
        if (source != null && source.pviCount() >= 2) {
            return insertAt(source, insertStation, insertElevation, roadLength);
        }
        double minRun = VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        boolean hasInteriorRoom = roadLength >= 2.0 * minRun - EPSILON;
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        pvis.add(PointOfVerticalIntersection.of(0.0, startElevation));
        if (hasInteriorRoom) {
            double station = Math.max(minRun, Math.min(roadLength - minRun, insertStation));
            if (station > EPSILON && roadLength - station > EPSILON) {
                pvis.add(PointOfVerticalIntersection.of(station, insertElevation));
            }
        }
        pvis.add(PointOfVerticalIntersection.of(roadLength, endElevation));
        return new RoadVerticalAlignment(pvis);
    }

    /** 删除变坡点，至少保留两个端点。 */
    public static RoadVerticalAlignment removeAt(RoadVerticalAlignment source, int pviIndex) {
        if (source == null || source.pviCount() <= 2 || pviIndex < 0 || pviIndex >= source.pviCount()) {
            throw new IllegalArgumentException("invalid PVI delete");
        }
        List<PointOfVerticalIntersection> pvis = new ArrayList<>(source.getPvis());
        pvis.remove(pviIndex);
        return new RoadVerticalAlignment(pvis);
    }

    /** 更新中间变坡点竖曲线长度。 */
    public static RoadVerticalAlignment withCurveLength(
            RoadVerticalAlignment source,
            int pviIndex,
            double curveLength) {
        if (source == null || pviIndex <= 0 || pviIndex >= source.pviCount() - 1
                || curveLength < 0.0 || !Double.isFinite(curveLength)) {
            throw new IllegalArgumentException("invalid curve length edit");
        }
        List<PointOfVerticalIntersection> pvis = source.getPvis();
        double maxLength = maxCurveLength(pvis, pviIndex);
        double clamped = Math.min(maxLength, curveLength);
        List<PointOfVerticalIntersection> edited = new ArrayList<>();
        for (int i = 0; i < pvis.size(); i++) {
            PointOfVerticalIntersection pvi = pvis.get(i);
            if (i != pviIndex) {
                edited.add(pvi.copy());
                continue;
            }
            Double curve = clamped > EPSILON ? clamped : null;
            edited.add(new PointOfVerticalIntersection(
                pvi.getStation(), pvi.getElevation(), curve, pvi.getConstraint()));
        }
        return normalizeAdjacentCurves(new RoadVerticalAlignment(edited), pviIndex);
    }

    static double maxCurveLength(List<PointOfVerticalIntersection> pvis, int pviIndex) {
        if (pvis == null || pviIndex <= 0 || pviIndex >= pvis.size() - 1) {
            return 0.0;
        }
        double station = pvis.get(pviIndex).getStation();
        double leftSpan = station - pvis.get(pviIndex - 1).getStation();
        if (pviIndex - 1 > 0 && pvis.get(pviIndex - 1).hasCurve()) {
            leftSpan = station - curveEndStation(pvis.get(pviIndex - 1));
        }
        double rightSpan = pvis.get(pviIndex + 1).getStation() - station;
        if (pviIndex + 1 < pvis.size() - 1 && pvis.get(pviIndex + 1).hasCurve()) {
            rightSpan = curveStartStation(pvis.get(pviIndex + 1)) - station;
        }
        double minRun = VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        return Math.max(
            0.0,
            2.0 * Math.min(leftSpan - minRun, rightSpan - minRun));
    }

    private static double curveStartStation(PointOfVerticalIntersection pvi) {
        return pvi.getStation() - pvi.getCurveLength() * 0.5;
    }

    private static double curveEndStation(PointOfVerticalIntersection pvi) {
        return pvi.getStation() + pvi.getCurveLength() * 0.5;
    }

    private static RoadVerticalAlignment normalizeAdjacentCurves(
            RoadVerticalAlignment alignment,
            int centerIndex) {
        List<PointOfVerticalIntersection> pvis = new ArrayList<>(alignment.getPvis());
        int lo = Math.max(1, centerIndex - 1);
        int hi = Math.min(pvis.size() - 2, centerIndex + 1);
        for (int i = lo; i <= hi; i++) {
            clampCurveLengthInPlace(pvis, i);
        }
        return new RoadVerticalAlignment(pvis);
    }

    private static void clampCurveLengthInPlace(List<PointOfVerticalIntersection> pvis, int pviIndex) {
        PointOfVerticalIntersection pvi = pvis.get(pviIndex);
        if (!pvi.hasCurve()) {
            return;
        }
        double maxLength = maxCurveLength(pvis, pviIndex);
        double current = pvi.getCurveLength();
        if (current <= maxLength + EPSILON) {
            return;
        }
        Double curve = maxLength > EPSILON ? maxLength : null;
        pvis.set(pviIndex, new PointOfVerticalIntersection(
            pvi.getStation(), pvi.getElevation(), curve, pvi.getConstraint()));
    }
}
