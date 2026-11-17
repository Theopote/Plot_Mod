package com.plot.plugin.road.ui;

import com.plot.plugin.road.profile.ProfileChartInteraction;

/** 纵断面编辑器窗口内的可变 UI 状态。 */
public final class ProfileEditorState {
    public int selectedProfilePvi = -1;
    public int activeProfilePvi = -1;
    public final float[] selectedProfileElevation = {64f};
    public String profileAutoFixMessage = "";
    public boolean controlPointsExpanded = false;
    public boolean elevationEditPending = false;
    public int pendingProfilePvi = -1;
    public float pendingClickX;
    public float pendingClickY;
    public int activeCurveHandlePvi = -1;
    public ProfileChartInteraction.CurveHandleSide activeCurveHandle =
        ProfileChartInteraction.CurveHandleSide.NONE;
    public int contextMenuPvi = -1;

    public void reset() {
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
