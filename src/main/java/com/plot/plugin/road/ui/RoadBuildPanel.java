package com.plot.plugin.road.ui;

/**
 * 建造工作区：正式预览、验证与 Minecraft 落地（转发至 {@link RoadGeneratePanel}）。
 */
public final class RoadBuildPanel {
    private final RoadGeneratePanel generatePanel;

    public RoadBuildPanel(RoadGeneratePanel generatePanel) {
        this.generatePanel = generatePanel;
    }

    public void render() {
        generatePanel.render();
    }

    public void renderDeferredModals() {
        generatePanel.renderDeferredModals();
    }

    public void renderBuildConfirmPopup() {
        generatePanel.renderBuildConfirmPopup();
    }
}
