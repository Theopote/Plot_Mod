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
            return forFlatEditor(network, road, alignment);
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

    /**
     * 水平道路纵断面：绿线两端端点，仅允许竖向拖动以整体抬升/降低。
     */
    public static List<ProfileControlPoint> forFlatEditor(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        if (network == null || road == null || alignment == null || alignment.isEmpty()) {
            return List.of();
        }
        double length = RoadStationing.canonicalLength(network, road);
        if (!Double.isFinite(length) || length <= EPSILON) {
            return List.of();
        }
        double elevation = alignment.getPvis().getFirst().getElevation();
        return List.of(
            new ProfileControlPoint(
                0, 0.0, elevation, ProfilePointRole.START_ENDPOINT, null, null, false, true),
            new ProfileControlPoint(
                1, length, elevation, ProfilePointRole.END_ENDPOINT, null, null, false, true));
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
        RoadVerticalAlignment result = new RoadVerticalAlignment(edited);
        if (source.getPvis().get(pviIndex).getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
            return withAnchoredElevations(result, java.util.Map.of(source.getPvis().get(pviIndex).getStation(), elevation));
        }
        return result;
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
        RoadVerticalAlignment result = normalizeAdjacentCurves(new RoadVerticalAlignment(edited), pviIndex);
        if (pvis.get(pviIndex).getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
            return withAnchoredElevations(result, java.util.Map.of(station, elevation));
        }
        return result;
    }

    /** Solve tangent heights together so curves pass through the actual crossing elevations. */
    public static RoadVerticalAlignment withAnchoredElevations(
            RoadVerticalAlignment source, java.util.Map<Double, Double> anchors) {
        var points = source.getPvis();
        int count = points.size();
        double[] lower = new double[count], diagonal = new double[count];
        double[] upper = new double[count], values = new double[count];
        for (int i = 0; i < count; i++) {
            var point = points.get(i);
            Double target = anchors.get(point.getStation());
            diagonal[i] = 1;
            values[i] = target != null ? target : point.getElevation();
            if (target != null && point.hasCurve() && i > 0 && i < count - 1) {
                lower[i] = point.getCurveLength() / (8 * (point.getStation() - points.get(i - 1).getStation()));
                upper[i] = point.getCurveLength() / (8 * (points.get(i + 1).getStation() - point.getStation()));
                diagonal[i] -= lower[i] + upper[i];
            }
        }
        for (int i = 1; i < count; i++) {
            double factor = lower[i] / diagonal[i - 1];
            diagonal[i] -= factor * upper[i - 1];
            values[i] -= factor * values[i - 1];
        }
        for (int i = count - 1; i >= 0; i--) {
            values[i] = (values[i] - (i + 1 < count ? upper[i] * values[i + 1] : 0)) / diagonal[i];
        }
        List<PointOfVerticalIntersection> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var point = points.get(i);
            result.add(new PointOfVerticalIntersection(point.getStation(), values[i], point.getCurveLength(), point.getConstraint()));
        }
        return new RoadVerticalAlignment(result);
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

    public static boolean isCurveEditable(Road road, RoadVerticalAlignment alignment, int index) {
        if (road == null || road.getVerticalMode() == RoadVerticalMode.FLAT || alignment == null
                || index <= 0 || index >= alignment.pviCount() - 1) return false;
        var constraint = alignment.getPvis().get(index).getConstraint();
        return constraint != VerticalControlPointConstraint.USER_LOCKED
            && constraint != VerticalControlPointConstraint.ENDPOINT_FIXED;
    }

    public static boolean canAutoSmooth(RoadNetwork network, Road road, ControlPoint point) {
        return point != null && isCurveEditable(road, road != null ? road.getVerticalAlignment() : null, point.pviIndex());
    }

    public static boolean canAutoSmooth(RoadNetwork network, Road road, ProfileControlPoint point) {
        return point != null && isCurveEditable(road, road != null ? road.getVerticalAlignment() : null, point.pviIndex());
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
    public enum InsertFailureReason {
        INVALID_INPUT,
        DUPLICATE_STATION,
        INSUFFICIENT_SPACE,
        TOO_CLOSE_TO_NEIGHBOR,
        ROAD_TOO_SHORT
    }

    public record InsertResult(
            boolean success,
            RoadVerticalAlignment alignment,
            InsertFailureReason reason) {

        public static InsertResult ok(RoadVerticalAlignment alignment) {
            return new InsertResult(true, alignment, null);
        }

        public static InsertResult fail(InsertFailureReason reason) {
            return new InsertResult(false, null, reason);
        }
    }

    public static boolean canInsertAt(
            RoadVerticalAlignment source,
            double station,
            double roadLength) {
        if (!Double.isFinite(station) || !Double.isFinite(roadLength) || roadLength <= EPSILON) {
            return false;
        }
        if (!VerticalProfileDesignRules.slopeAllowed(roadLength)) {
            return false;
        }
        if (source == null || source.pviCount() < 2) {
            return canBootstrapInteriorPoint(station, roadLength);
        }
        return analyzeInsertRoom(source, station) == null;
    }

    public static InsertResult tryInsertAt(
            RoadVerticalAlignment source,
            double station,
            double elevation,
            double roadLength) {
        if (source == null || source.pviCount() < 2
                || !Double.isFinite(station) || !Double.isFinite(elevation)
                || !Double.isFinite(roadLength) || roadLength <= EPSILON) {
            return InsertResult.fail(InsertFailureReason.INVALID_INPUT);
        }
        InsertFailureReason room = analyzeInsertRoom(source, station);
        if (room != null) {
            return InsertResult.fail(room);
        }
        List<PointOfVerticalIntersection> pvis = new ArrayList<>(source.getPvis());
        int insertIndex = resolveInsertIndex(pvis, station);
        if (insertIndex < 0) {
            return InsertResult.fail(InsertFailureReason.DUPLICATE_STATION);
        }
        pvis.add(insertIndex, PointOfVerticalIntersection.of(station, elevation));
        return InsertResult.ok(new RoadVerticalAlignment(pvis));
    }

    public static InsertResult tryBootstrapOrInsert(
            RoadVerticalAlignment source,
            double roadLength,
            double startElevation,
            double endElevation,
            double insertStation,
            double insertElevation) {
        if (!Double.isFinite(roadLength) || roadLength <= EPSILON
                || !Double.isFinite(insertStation) || !Double.isFinite(insertElevation)) {
            return InsertResult.fail(InsertFailureReason.INVALID_INPUT);
        }
        if (!VerticalProfileDesignRules.slopeAllowed(roadLength)) {
            return InsertResult.fail(InsertFailureReason.ROAD_TOO_SHORT);
        }
        if (source != null && source.pviCount() >= 2) {
            return tryInsertAt(source, insertStation, insertElevation, roadLength);
        }
        if (!canBootstrapInteriorPoint(insertStation, roadLength)) {
            InsertFailureReason reason = roadLength
                    < 2.0 * VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH - EPSILON
                ? InsertFailureReason.INSUFFICIENT_SPACE
                : InsertFailureReason.TOO_CLOSE_TO_NEIGHBOR;
            return InsertResult.fail(reason);
        }
        return InsertResult.ok(bootstrapAlignment(
            roadLength, startElevation, endElevation, insertStation, insertElevation));
    }

    public static RoadVerticalAlignment insertAt(
            RoadVerticalAlignment source,
            double station,
            double elevation,
            double roadLength) {
        InsertResult result = tryInsertAt(source, station, elevation, roadLength);
        if (!result.success()) {
            throw new IllegalArgumentException(result.reason() != null
                ? result.reason().name()
                : "invalid PVI insert");
        }
        return result.alignment();
    }

    public static RoadVerticalAlignment bootstrapOrInsert(
            RoadVerticalAlignment source,
            double roadLength,
            double startElevation,
            double endElevation,
            double insertStation,
            double insertElevation) {
        InsertResult result = tryBootstrapOrInsert(
            source, roadLength, startElevation, endElevation, insertStation, insertElevation);
        if (!result.success()) {
            throw new IllegalArgumentException(result.reason() != null
                ? result.reason().name()
                : "invalid PVI insert");
        }
        return result.alignment();
    }

    private static InsertFailureReason analyzeInsertRoom(
            RoadVerticalAlignment source,
            double station) {
        if (source == null || source.pviCount() < 2 || !Double.isFinite(station)) {
            return InsertFailureReason.INVALID_INPUT;
        }
        List<PointOfVerticalIntersection> pvis = source.getPvis();
        if (resolveInsertIndex(pvis, station) < 0) {
            return InsertFailureReason.DUPLICATE_STATION;
        }
        int insertIndex = pvis.size() - 1;
        for (int i = 1; i < pvis.size(); i++) {
            if (station + EPSILON < pvis.get(i).getStation()) {
                insertIndex = i;
                break;
            }
        }
        double minimum = pvis.get(insertIndex - 1).getStation()
            + VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        double maximum = pvis.get(insertIndex).getStation()
            - VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        if (minimum > maximum) {
            return InsertFailureReason.INSUFFICIENT_SPACE;
        }
        if (station < minimum - EPSILON || station > maximum + EPSILON) {
            return InsertFailureReason.TOO_CLOSE_TO_NEIGHBOR;
        }
        return null;
    }

    private static int resolveInsertIndex(List<PointOfVerticalIntersection> pvis, double station) {
        int insertIndex = pvis.size() - 1;
        for (int i = 1; i < pvis.size(); i++) {
            if (station + EPSILON < pvis.get(i).getStation()) {
                insertIndex = i;
                break;
            }
            if (Math.abs(station - pvis.get(i).getStation()) <= EPSILON) {
                return -1;
            }
        }
        return insertIndex;
    }

    private static boolean canBootstrapInteriorPoint(double insertStation, double roadLength) {
        double minRun = VerticalProfileDesignRules.MIN_GRADE_RUN_LENGTH;
        if (roadLength < 2.0 * minRun - EPSILON) {
            return false;
        }
        return insertStation + EPSILON >= minRun
            && insertStation <= roadLength - minRun + EPSILON;
    }

    private static RoadVerticalAlignment bootstrapAlignment(
            double roadLength,
            double startElevation,
            double endElevation,
            double insertStation,
            double insertElevation) {
        List<PointOfVerticalIntersection> pvis = new ArrayList<>();
        pvis.add(PointOfVerticalIntersection.of(0.0, startElevation));
        pvis.add(PointOfVerticalIntersection.of(insertStation, insertElevation));
        pvis.add(PointOfVerticalIntersection.of(roadLength, endElevation));
        return new RoadVerticalAlignment(pvis);
    }

    /** Whether a PVI may be deleted from the profile editor (graph, panel, context menu). */
    public static boolean canDelete(RoadNetwork network, Road road, ProfileControlPoint point) {
        if (network == null || road == null || point == null) {
            return false;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (alignment == null) {
            return false;
        }
        int index = point.pviIndex();
        int count = alignment.pviCount();
        if (count <= 2 || index <= 0 || index >= count - 1) {
            return false;
        }
        if (point.role() == ProfilePointRole.JUNCTION_FIXED || point.sharedJunction()) {
            return false;
        }
        PointOfVerticalIntersection pvi = alignment.getPvis().get(index);
        return pvi.getConstraint() != VerticalControlPointConstraint.JUNCTION_FIXED;
    }

    public static boolean canDelete(RoadNetwork network, Road road, int pviIndex) {
        if (network == null || road == null || pviIndex < 0) {
            return false;
        }
        return forRoad(network, road).stream()
            .filter(point -> point.pviIndex() == pviIndex)
            .findFirst()
            .map(point -> canDelete(network, road, point))
            .orElse(false);
    }

    /** i18n key explaining why {@link #canDelete} is false for this PVI index. */
    public static String deleteDisabledMessageKey(RoadNetwork network, Road road, int pviIndex) {
        if (network == null || road == null || pviIndex < 0) {
            return "plugin.road.profile_pvi_delete_disabled";
        }
        return forRoad(network, road).stream()
            .filter(point -> point.pviIndex() == pviIndex)
            .findFirst()
            .map(point -> deleteDisabledMessageKey(network, road, point))
            .orElse("plugin.road.profile_pvi_delete_disabled");
    }

    public static String deleteDisabledMessageKey(
            RoadNetwork network,
            Road road,
            ProfileControlPoint point) {
        if (canDelete(network, road, point)) {
            return "plugin.road.profile_pvi_delete_disabled";
        }
        if (point != null
                && (point.role() == ProfilePointRole.JUNCTION_FIXED || point.sharedJunction())) {
            return "plugin.road.profile_pvi_delete_junction_fixed";
        }
        RoadVerticalAlignment alignment = road != null ? road.getVerticalAlignment() : null;
        if (alignment != null && point != null && point.pviIndex() >= 0
                && point.pviIndex() < alignment.pviCount()) {
            PointOfVerticalIntersection pvi = alignment.getPvis().get(point.pviIndex());
            if (pvi.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
                return "plugin.road.profile_pvi_delete_junction_fixed";
            }
        }
        return "plugin.road.profile_pvi_delete_disabled";
    }

    /** 删除变坡点，至少保留两个端点。 */
    public static RoadVerticalAlignment removeAt(RoadVerticalAlignment source, int pviIndex) {
        if (source == null || source.pviCount() <= 2
                || pviIndex <= 0 || pviIndex >= source.pviCount() - 1) {
            throw new IllegalArgumentException("invalid PVI delete");
        }
        PointOfVerticalIntersection pvi = source.getPvis().get(pviIndex);
        if (pvi.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
            throw new IllegalArgumentException("cannot delete junction-fixed PVI");
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
        RoadVerticalAlignment result = normalizeAdjacentCurves(new RoadVerticalAlignment(edited), pviIndex);
        java.util.Map<Double, Double> anchors = new java.util.LinkedHashMap<>();
        for (var point : pvis) {
            if (point.getConstraint() == VerticalControlPointConstraint.JUNCTION_FIXED) {
                anchors.put(point.getStation(), VerticalAlignmentGeometry.elevationAt(source, point.getStation()).orElseThrow());
            }
        }
        return withAnchoredElevations(result, anchors);
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
