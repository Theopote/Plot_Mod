package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.model.RoadTopologyMode;
import com.plot.plugin.road.station.RoadStationing;

/**
 * 生成时纵断面权威解析：自动设计、用户编辑曲线、水平道路三选一，不混用。
 * <p>
 * Note: EDITED geometry signatures now include HA element fields. Saved projects from
 * older builds may need a one-time reconfirm after upgrade (signature mismatch → AUTO).
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
     * {@link #NONE} means the road is not claiming EDITED, or EDITED is valid under FLAT/AUTO.
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

    public record Resolution(Kind kind, EditedValidity editedValidity, boolean editedSignatureStale) {
        public Resolution(Kind kind, boolean editedSignatureStale) {
            this(kind, EditedValidity.NONE, editedSignatureStale);
        }

        public boolean usesEditedProfile() {
            return kind == Kind.EDITED;
        }

        /** Confirmed EDITED claim that must not generate (keep source; fail closed). */
        public boolean blocksGeneration() {
            return editedValidity == EditedValidity.INCOMPLETE_COVERAGE
                || editedValidity == EditedValidity.LOOP_ELEVATION_MISMATCH
                || editedValidity == EditedValidity.UNRELIABLE_DOMAIN
                || editedValidity == EditedValidity.NOT_EVALUABLE
                || editedValidity == EditedValidity.NOT_STATIONABLE;
        }
    }

    private ResolvedProfileAuthority() {
    }

    public static Resolution resolve(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return new Resolution(Kind.AUTO, EditedValidity.NONE, false);
        }
        if (NetworkElevationAuthority.isUniformFlat(network)) {
            return new Resolution(Kind.FLAT, EditedValidity.NONE, false);
        }
        RoadVerticalMode mode = road.getVerticalMode();
        if (mode == RoadVerticalMode.FLAT) {
            return new Resolution(Kind.FLAT, EditedValidity.NONE, false);
        }
        if (road.getEffectiveProfileSource() == ProfileSource.EDITED) {
            EditedValidity validity = evaluateEditedValidity(network, road);
            if (validity == EditedValidity.VALID) {
                return new Resolution(Kind.EDITED, EditedValidity.VALID, false);
            }
            if (validity == EditedValidity.SIGNATURE_MISMATCH) {
                // Geometry stale — treat as AUTO for solve dispatch; demote via invalidate.
                return new Resolution(Kind.AUTO, EditedValidity.SIGNATURE_MISMATCH, true);
            }
            // Illegal confirmed EDITED: keep claim visible via validity; do not use as AUTO design.
            return new Resolution(Kind.AUTO, validity, false);
        }
        return new Resolution(Kind.AUTO, EditedValidity.NONE, false);
    }

    public static boolean usesEditedProfileForGeneration(RoadNetwork network, Road road) {
        return resolve(network, road).usesEditedProfile();
    }

    /** True when the road still claims EDITED as its intended source (valid or not). */
    public static boolean claimsEditedProfile(Road road) {
        return road != null && road.getEffectiveProfileSource() == ProfileSource.EDITED;
    }

    /**
     * Fail-closed gate: confirmed EDITED that cannot legally generate.
     * Prefer {@link Resolution#blocksGeneration()} from {@link #resolve}.
     */
    public static boolean isIllegalEditedClaim(RoadNetwork network, Road road) {
        return resolve(network, road).blocksGeneration();
    }

    public static EditedValidity evaluateEditedValidity(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return EditedValidity.NONE;
        }
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED
                && !hasStoredEditedCurve(road)) {
            return EditedValidity.NONE;
        }
        // Signature mismatch first when claiming EDITED — distinguishes stale geometry from illegal VA.
        if (road.getEffectiveProfileSource() == ProfileSource.EDITED
                && !EditedProfileGeometry.matches(network, road, road.getEditedGeometrySignature())) {
            return EditedValidity.SIGNATURE_MISMATCH;
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
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED) {
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
     * Plane-geometry change only: demote EDITED → AUTO when signature mismatches (keep VA).
     * Illegal VA / topology / domain failures are not demoted — generation must fail closed.
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
        if (!EditedProfileGeometry.matches(network, road, road.getEditedGeometrySignature())) {
            road.setProfileSource(ProfileSource.AUTO);
            return true;
        }
        return false;
    }

    static boolean loopElevationsConsistent(
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
        double continuousDelta = Math.abs(start.getAsDouble() - end.getAsDouble());
        if (continuousDelta > LOOP_ELEVATION_TOLERANCE) {
            return false;
        }
        // Integer pavement must also meet at the seam (avoids 80.49 vs 80.51 → 80/81).
        int startBlock = com.plot.core.geometry.VoxelElevationDiscretizer
            .quantizeContinuous(start.getAsDouble());
        int endBlock = com.plot.core.geometry.VoxelElevationDiscretizer
            .quantizeContinuous(end.getAsDouble());
        return startBlock == endBlock;
    }
}
