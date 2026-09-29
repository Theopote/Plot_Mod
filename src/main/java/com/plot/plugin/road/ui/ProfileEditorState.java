package com.plot.plugin.road.ui;

/** 纵断面编辑器窗口内的可变 UI 状态。 */
final class ProfileEditorState {
    int selectedProfilePvi = -1;
    int activeProfilePvi = -1;
    final float[] selectedProfileElevation = {64f};
    String profileAutoFixMessage = "";
    boolean controlPointsExpanded = false;
    boolean elevationEditPending = false;

    void reset() {
        selectedProfilePvi = -1;
        activeProfilePvi = -1;
        profileAutoFixMessage = "";
        controlPointsExpanded = false;
        elevationEditPending = false;
    }
}
