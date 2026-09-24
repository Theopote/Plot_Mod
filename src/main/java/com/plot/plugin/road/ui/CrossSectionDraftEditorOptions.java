package com.plot.plugin.road.ui;

/**
 * {@link CrossSectionDraftEditor} 渲染选项。
 */
public record CrossSectionDraftEditorOptions(
        String idPrefix,
        boolean showBanner,
        String bannerKey,
        boolean showLaneWidths,
        boolean showMaxSlope,
        boolean showSlopeBatter) {

    public static CrossSectionDraftEditorOptions adopt() {
        return new CrossSectionDraftEditorOptions("default", false, null, true, false, true);
    }

    public static CrossSectionDraftEditorOptions batch() {
        return new CrossSectionDraftEditorOptions(
            "batch",
            true,
            "plugin.road.batch_edit_writes_explicit",
            false,
            false,
            false);
    }

    public static CrossSectionDraftEditorOptions roadEdit() {
        return new CrossSectionDraftEditorOptions("road", false, null, true, true, true);
    }

    /** 样式 Tab 主界面：车道/宽度/路肩/中央分隔带，不含分车道宽、边坡与工程坡度。 */
    public static CrossSectionDraftEditorOptions stylePrimary() {
        return new CrossSectionDraftEditorOptions("style", false, null, false, false, false);
    }

    /** 样式 Tab「高级道路设计」内的横断面工程参数。 */
    public static CrossSectionDraftEditorOptions styleAdvanced() {
        return new CrossSectionDraftEditorOptions("style_adv", false, null, true, true, true);
    }

    public static CrossSectionDraftEditorOptions stationVariable(int index) {
        return new CrossSectionDraftEditorOptions("var_xs_" + index, false, null, true, false, true);
    }
}
