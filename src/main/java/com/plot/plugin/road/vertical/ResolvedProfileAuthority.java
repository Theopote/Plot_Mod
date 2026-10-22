package com.plot.plugin.road.vertical;

import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.RoadNetwork;
import com.plot.plugin.road.station.RoadStationing;

/**
 * 生成时纵断面权威解析：自动设计、用户编辑曲线、水平道路三选一，不混用。
 */
public final class ResolvedProfileAuthority {

    public enum Kind {
        EDITED,
        AUTO,
        FLAT
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
        RoadVerticalMode mode = road.getVerticalMode();
        if (mode == RoadVerticalMode.FLAT) {
            return new Resolution(Kind.FLAT, false);
        }
        if (road.getEffectiveProfileSource() == ProfileSource.EDITED) {
            if (hasValidEditedProfile(network, road)) {
                return new Resolution(Kind.EDITED, false);
            }
            return new Resolution(Kind.AUTO, true);
        }
        return new Resolution(Kind.AUTO, false);
    }

    public static boolean usesEditedProfileForGeneration(RoadNetwork network, Road road) {
        return resolve(network, road).usesEditedProfile();
    }

    public static boolean hasValidEditedProfile(RoadNetwork network, Road road) {
        if (road == null || network == null) {
            return false;
        }
        if (!RoadStationing.isStationable(network, road)) {
            return false;
        }
        if (!VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
            return false;
        }
        if (road.getEffectiveProfileSource() != ProfileSource.EDITED) {
            return false;
        }
        return EditedProfileGeometry.matches(
            network,
            road,
            road.getEditedGeometrySignature());
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
        if (!VerticalAlignmentGeometry.isEvaluable(road.getVerticalAlignment())) {
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

    /** 几何变更后使 EDITED 来源失效并切回 AUTO（保留保存的 VA）。 */
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
        road.setProfileSource(ProfileSource.AUTO);
        return true;
    }
}
