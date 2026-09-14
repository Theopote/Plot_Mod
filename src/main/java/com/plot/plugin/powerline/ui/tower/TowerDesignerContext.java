package com.plot.plugin.powerline.ui.tower;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.ui.PoleDesignerEditScope;

/** Per-frame editing context passed into tower designer sub-panels. */
public record TowerDesignerContext(
        PoleDesign draft,
        TowerDesignerSession session,
        TowerDesignerUiState uiState,
        Runnable pushDraftSnapshot,
        PoleDesignerEditScope editScope) {

    public TowerDesignerContext(
            PoleDesign draft,
            TowerDesignerSession session,
            TowerDesignerUiState uiState,
            Runnable pushDraftSnapshot) {
        this(draft, session, uiState, pushDraftSnapshot, PoleDesignerEditScope.DESIGN_TEMPLATE);
    }

    /** 线路实例调参：不可切换 Legacy/参数化或塔型 Profile。 */
    public boolean locksStructureKind() {
        return editScope == PoleDesignerEditScope.LINE_INSTANCE;
    }

    public boolean canBuild() {
        return session.canBuild();
    }
}
