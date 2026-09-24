package com.plot.plugin.road.ui;

/**
 * 建造 Tab：预览、验证、纵断面结果与 Minecraft 落地。
 */
public final class RoadBuildPanel {
    private final RoadGeneratePanel generatePanel;

    public RoadBuildPanel(RoadGeneratePanel generatePanel) {
        this.generatePanel = generatePanel;
    }

    public void render() {
        generatePanel.render();
    }

    public void openProfileForEdge(String edgeId) {
        generatePanel.openProfileForEdge(edgeId);
    }

    public void renderBuildConfirmPopup() {
        generatePanel.renderBuildConfirmPopup();
    }
}
