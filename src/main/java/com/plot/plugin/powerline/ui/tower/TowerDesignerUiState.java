package com.plot.plugin.powerline.ui.tower;

import imgui.type.ImBoolean;

/** ImGui-facing UI flags for the tower designer panels. */
public final class TowerDesignerUiState {
    public final ImBoolean showAdvancedStructure = new ImBoolean(false);
    public boolean convertManualConfirmPending;

    public void syncFromDraft(com.plot.plugin.powerline.design.PoleDesign draft) {
        if (draft != null && draft.isManualLegacyMode()) {
            showAdvancedStructure.set(true);
        }
    }
}
