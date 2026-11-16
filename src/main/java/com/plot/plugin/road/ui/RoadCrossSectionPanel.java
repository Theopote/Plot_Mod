package com.plot.plugin.road.ui;

import com.plot.plugin.road.ui.editor.RoadFloatingEditorHost;

/**
 * 横断面工作区：样式、基础尺寸、结构与设施入口（复用原编辑工作区）。
 */
public final class RoadCrossSectionPanel {
    private final RoadEditPanel editPanel;

    public RoadCrossSectionPanel(
            RoadUiContext ctx,
            RoadDefaultParamsPanel defaultParamsPanel,
            RoadFloatingEditorHost floatingEditorHost) {
        this.editPanel = new RoadEditPanel(ctx, defaultParamsPanel, floatingEditorHost);
    }

    public RoadEditPanel editPanel() {
        return editPanel;
    }

    public void render() {
        editPanel.render();
    }

    public boolean tryLeaveEditors() {
        return editPanel.tryLeaveEdit();
    }

    public boolean finishPendingEdits() {
        return editPanel.finishPendingEdits();
    }

    public void reconcileRoadScope() {
        editPanel.reconcileRoadScope();
    }
}
