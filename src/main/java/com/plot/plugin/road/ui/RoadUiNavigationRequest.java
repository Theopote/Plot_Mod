package com.plot.plugin.road.ui;

import com.plot.plugin.road.ui.editor.RoadFloatingEditorKind;

/**
 * 跨工作区导航意图：由 {@link RoadUiContext} 排队，{@link com.plot.plugin.road.manager.RoadUIManager}
 * 在草稿离开检查通过后统一消费。
 */
public final class RoadUiNavigationRequest {
    private final RoadUiTab targetTab;
    private final String roadId;
    private final String edgeId;
    private final String crossingId;
    private final RoadFloatingEditorKind editorKind;

    private RoadUiNavigationRequest(
            RoadUiTab targetTab,
            String roadId,
            String edgeId,
            String crossingId,
            RoadFloatingEditorKind editorKind) {
        this.targetTab = targetTab;
        this.roadId = roadId;
        this.edgeId = edgeId;
        this.crossingId = crossingId;
        this.editorKind = editorKind;
    }

    public static RoadUiNavigationRequest tab(RoadUiTab tab) {
        return new RoadUiNavigationRequest(tab, null, null, null, null);
    }

    public static RoadUiNavigationRequest of(
            RoadUiTab tab,
            String roadId,
            String edgeId,
            String crossingId,
            RoadFloatingEditorKind editorKind) {
        return new RoadUiNavigationRequest(tab, roadId, edgeId, crossingId, editorKind);
    }

    public RoadUiTab targetTab() {
        return targetTab;
    }

    public String roadId() {
        return roadId;
    }

    public String edgeId() {
        return edgeId;
    }

    public String crossingId() {
        return crossingId;
    }

    public RoadFloatingEditorKind editorKind() {
        return editorKind;
    }
}
