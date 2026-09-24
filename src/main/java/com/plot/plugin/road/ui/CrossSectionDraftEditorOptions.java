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
        boolean showSlopeBatter,
        boolean showCarriagewayCore) {

    public static CrossSectionDraftEditorOptions adopt() {
        return new CrossSectionDraftEditorOptions("default", false, null, true, false, true, true);
    }

    public static CrossSectionDraftEditorOptions batch() {
        return new CrossSectionDraftEditorOptions(
            "batch",
            true,
            "plugin.road.batch_edit_writes_explicit",
            false,
            false,
            false,
            true);
    }

    public static CrossSectionDraftEditorOptions roadEdit() {
        return new CrossSectionDraftEditorOptions("road", false, null, true, true, true, true);
    }

    /** 样式 Tab：路肩/人行道/中央分隔带等外观，不含宽度、车道与坡度。 */
    public static CrossSectionDraftEditorOptions styleAppearance() {
        return new CrossSectionDraftEditorOptions("style", false, null, false, false, false, false);
    }

    /** 样式 Tab「高级道路设计」内的横断面工程参数。 */
    public static CrossSectionDraftEditorOptions styleAdvanced() {
        return new CrossSectionDraftEditorOptions("style_adv", false, null, true, true, true, true);
    }

    public static CrossSectionDraftEditorOptions stationVariable(int index) {
        return new CrossSectionDraftEditorOptions("var_xs_" + index, false, null, true, false, true, true);
    }
}
