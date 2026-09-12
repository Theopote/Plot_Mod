package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;

/** Per-frame editing context passed into tower designer sub-panels. */
public record TowerDesignerContext(
        PoleDesign draft,
        TowerDesignerSession session,
        TowerDesignerUiState uiState,
        Runnable pushDraftSnapshot) {

    public boolean canBuild() {
        return session.canBuild();
    }
}
