package com.plot.plugin.road.ui;

/**
 * {@link CrossSectionDraftEditor} 渲染选项。
 */
public record CrossSectionDraftEditorOptions(
        String idPrefix,
        boolean showBanner,
        String bannerKey,
        boolean showLaneWidths,
        boolean showMaxSlope) {

    public static CrossSectionDraftEditorOptions adopt() {
        return new CrossSectionDraftEditorOptions("default", false, null, true, false);
    }

    public static CrossSectionDraftEditorOptions batch() {
        return new CrossSectionDraftEditorOptions(
            "batch",
            true,
            "plugin.road.batch_edit_writes_explicit",
            false,
            false);
    }

    public static CrossSectionDraftEditorOptions roadEdit() {
        return new CrossSectionDraftEditorOptions("road", false, null, true, true);
    }

    public static CrossSectionDraftEditorOptions stationVariable(int index) {
        return new CrossSectionDraftEditorOptions("var_xs_" + index, false, null, true, false);
    }
}
