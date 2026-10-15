package com.plot.plugin.road.ui;

import com.plot.plugin.road.profile.ProfileChartInteraction;

/** 纵断面编辑器窗口内的可变 UI 状态。 */
final class ProfileEditorState {
    int selectedProfilePvi = -1;
    int activeProfilePvi = -1;
    final float[] selectedProfileElevation = {64f};
    String profileAutoFixMessage = "";
    boolean controlPointsExpanded = false;
    boolean elevationEditPending = false;
    int pendingProfilePvi = -1;
    float pendingClickX;
    float pendingClickY;
    int activeCurveHandlePvi = -1;
    ProfileChartInteraction.CurveHandleSide activeCurveHandle =
        ProfileChartInteraction.CurveHandleSide.NONE;
    int contextMenuPvi = -1;

    void reset() {
        selectedProfilePvi = -1;
        activeProfilePvi = -1;
        profileAutoFixMessage = "";
        controlPointsExpanded = false;
        elevationEditPending = false;
        pendingProfilePvi = -1;
        pendingClickX = 0f;
        pendingClickY = 0f;
        activeCurveHandlePvi = -1;
        activeCurveHandle =
            ProfileChartInteraction.CurveHandleSide.NONE;
        contextMenuPvi = -1;
    }
}
