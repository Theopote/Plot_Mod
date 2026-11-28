package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.RoadStationing;

/**
 * 生成时纵断面权威解析：自动设计、用户编辑曲线、水平道路三选一，不混用。
 */
public final class ResolvedProfileAuthority {

    private static final double LOOP_ELEVATION_TOLERANCE = 0.51;

    public enum Kind {
        EDITED,
        AUTO,
        FLAT
    }

    /**
     * Why an EDITED source is not currently valid for generation.
     * {@link #NONE} means the road is not claiming EDITED, or EDITED is valid.
     */
    public enum EditedValidity {
        VALID,
        /** Effective source is not EDITED. */
        NONE,
        NOT_STATIONABLE,
        UNRELIABLE_DOMAIN,
        NOT_EVALUABLE,
        INCOMPLETE_COVERAGE,
        SIGNATURE_MISMATCH,
        LOOP_ELEVATION_MISMATCH
    }

    public record Resolution(Kind kind, boolean editedSignatureStale) {
        public boolean usesEditedProfile() {
            return kind == Kind.EDITED;
        }
    }

    private ResolvedProfileAuthority() {
    }

    public static Resolution resolve(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return new Resolution(Kind.AUTO, false);
        }
        if (NetworkElevationAuthority.isUniformFlat(network)) {
            return new Resolution(Kind.FLAT, false);
        }
        RoadVerticalMode mode = road.getVerticalMode();
        if (mode == RoadVerticalMode.FLAT) {
            return new Resolution(Kind.FLAT, false);
        }
        if (road.getEffectiveProfileSource() == ProfileSource.EDITED) {
            if (hasValidEditedProfile(network, road)) {
                return new Resolution(Kind.EDITED, false);
            }
            // Keep Kind.AUTO only as a non-EDITED generation kind marker; callers that still
            // see ProfileSource.EDITED must fail closed rather than silently terrain-solve.
            return new Resolution(Kind.AUTO, true);
        }
        return new Resolution(Kind.AUTO, false);
    }

    public static boolean usesEditedProfileForGeneration(RoadNetwork network, Road road) {
        return resolve(network, road).usesEditedProfile();
    }

    /** True when the road still claims EDITED as its intended source (valid or not). */
    public static boolean claimsEditedProfile(Road road) {
        return road != null && road.getEffectiveProfileSource() == ProfileSource.EDITED;
    }

    /**
     * Fail-closed gate: user still wants a <em>confirmed</em> EDITED curve that cannot generate.
     * Geometry-stale / unbound-signature claims are not illegal — {@link #resolve} treats them as
     * AUTO without mutating; {@link #invalidateEditedProfileIfStale} demotes when appropriate.
     */
    public static boolean isIllegalEditedClaim(RoadNetwork network, Road road) {
        if (!claimsEditedProfile(road)) {
            return false;
        }
        // Only fail closed when the geometry signature still matches (user confirmed this path)
        // but the curve itself is incomplete, loop-broken, or unmappable.
        if (!EditedProfileGeometry.matches(network, road, road.getEditedGeometrySignature())) {
            return false;
        }
        EditedValidity validity = evaluateEditedValidity(network, road);
        return validity == EditedValidity.INCOMPLETE_COVERAGE
            || validity == EditedValidity.LOOP_ELEVATION_MISMATCH
            || validity == EditedValidity.UNRELIABLE_DOMAIN
            || validity == EditedValidity.NOT_EVALUABLE
            || validity == EditedValidity.NOT_STATIONABLE;
    }

    public static EditedValidity evaluateEditedValidity(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return EditedValidity.NONE;
        }
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED
                && !hasStoredEditedCurve(road)) {
            return EditedValidity.NONE;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return EditedValidity.NOT_STATIONABLE;
        }
        if (!RoadStationing.domainMap(network, road).isReliable()) {
            return EditedValidity.UNRELIABLE_DOMAIN;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return EditedValidity.NOT_EVALUABLE;
        }
        if (!EditedProfileRebindValidator.coversCanonicalLength(network, road, alignment)) {
            return EditedValidity.INCOMPLETE_COVERAGE;
        }
        if (!loopElevationsConsistent(network, road, alignment)) {
            return EditedValidity.LOOP_ELEVATION_MISMATCH;
        }
        if (road.getEffectiveProfileSource() == ProfileSource.EDITED
                && !EditedProfileGeometry.matches(network, road, road.getEditedGeometrySignature())) {
            return EditedValidity.SIGNATURE_MISMATCH;
        }
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED) {
            // Stored curve checked for activation: signature checked separately by canActivate.
            return EditedValidity.NONE;
        }
        return EditedValidity.VALID;
    }

    public static boolean hasValidEditedProfile(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return false;
        }
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED) {
            return false;
        }
        return evaluateEditedValidity(network, road) == EditedValidity.VALID;
    }

    public static boolean hasStoredEditedCurve(Road road) {
        if (road == null) {
            return false;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        return alignment != null && !alignment.isEmpty();
    }

    /**
     * 保存的编辑曲线是否已与当前路径几何绑定，可在不进入编辑器的情况下切回 EDITED 来源。
     */
    public static boolean canActivateEditedProfile(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return false;
        }
        if (!hasStoredEditedCurve(road)) {
            return false;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return false;
        }
        if (!RoadStationing.domainMap(network, road).isReliable()) {
            return false;
        }
        RoadVerticalAlignment alignment = road.getVerticalAlignment();
        if (!VerticalAlignmentGeometry.isEvaluable(alignment)) {
            return false;
        }
        if (!EditedProfileRebindValidator.coversCanonicalLength(network, road, alignment)) {
            return false;
        }
        if (!loopElevationsConsistent(network, road, alignment)) {
            return false;
        }
        return EditedProfileGeometry.matches(
            network,
            road,
            road.getEditedGeometrySignature());
    }

    /** 确认编辑曲线后写入来源与几何签名。 */
    public static void markEditedProfileConfirmed(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return;
        }
        if (road.getVerticalMode() != RoadVerticalMode.FLAT) {
            road.setVerticalMode(RoadVerticalMode.FIT_TERRAIN);
        }
        road.setProfileSource(ProfileSource.EDITED);
        road.setEditedGeometrySignature(EditedProfileGeometry.signature(network, road));
    }

    /**
     * 几何变更后使 EDITED 来源失效并切回 AUTO（保留保存的 VA）。
     * Signature / evaluability / stationability failures demote to AUTO.
     * Incomplete coverage or LOOP mismatch with a still-matching geometry signature
     * keeps EDITED so generation can fail closed instead of silently switching to AUTO.
     */
    public static boolean invalidateEditedProfileIfStale(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return false;
        }
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED) {
            return false;
        }
        if (hasValidEditedProfile(network, road)) {
            return false;
        }
        // Prefer geometry-staleness demotion even when coverage also fails after a path edit.
        if (!RoadStationing.isStationable(network, road)
                || !VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())
                || !EditedProfileGeometry.matches(network, road, road.getEditedGeometrySignature())) {
            road.setProfileSource(ProfileSource.AUTO);
            return true;
        }
        // Matching signature but incomplete/loop/unreliable — keep EDITED for fail-closed.
        return false;
    }

    private static boolean loopElevationsConsistent(
            RoadNetwork network,
            Road road,
            RoadVerticalAlignment alignment) {
        if (road.getTopologyMode() != RoadTopologyMode.LOOP) {
            return true;
        }
        var start = VerticalAlignmentGeometry.elevationAt(alignment, alignment.startStation());
        var end = VerticalAlignmentGeometry.elevationAt(alignment, alignment.endStation());
        if (start.isEmpty() || end.isEmpty()) {
            return false;
        }
        return Math.abs(start.getAsDouble() - end.getAsDouble()) <= LOOP_ELEVATION_TOLERANCE;
    }
}
