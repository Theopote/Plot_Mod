package com.plot.plugin.road.profile;

/**
 * 道路级纵断面图控制点（canonical road station）。
 */
public record ProfileControlPoint(
        int pviIndex,
        double roadStation,
        double elevation,
        ProfilePointRole role,
        Double leftGradePercent,
        Double rightGradePercent,
        boolean sharedJunction,
        boolean elevationEditable) {

    public boolean endpoint() {
        return role == ProfilePointRole.START_ENDPOINT || role == ProfilePointRole.END_ENDPOINT;
    }
}
