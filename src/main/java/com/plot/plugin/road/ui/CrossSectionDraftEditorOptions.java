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
        boolean showCarriagewayCore,
        boolean showAppearanceStrips) {

    public static CrossSectionDraftEditorOptions adopt() {
        return new CrossSectionDraftEditorOptions("default", false, null, true, false, true, true, true);
    }

    public static CrossSectionDraftEditorOptions batch() {
        return new CrossSectionDraftEditorOptions(
            "batch",
            true,
            "plugin.road.batch_edit_writes_explicit",
            false,
            false,
            false,
            true,
            true);
    }

    public static CrossSectionDraftEditorOptions roadEdit() {
        return new CrossSectionDraftEditorOptions("road", false, null, true, true, true, true, true);
    }

    /** 编辑 Tab 主界面：路肩/人行道/中央分隔带等外观，不含宽度、车道与坡度。 */
    public static CrossSectionDraftEditorOptions styleAppearance() {
        return new CrossSectionDraftEditorOptions("style", false, null, false, false, false, false, true);
    }

    /** 高级道路设计：分车道宽、边坡比等工程参数（不重复主界面的宽度/车道/路肩/人行道）。 */
    public static CrossSectionDraftEditorOptions styleAdvanced() {
        return new CrossSectionDraftEditorOptions("style_adv", false, null, true, false, true, false, false);
    }

    /** 样式 Tab 批量编辑：材质与附属设施。 */
    public static CrossSectionDraftEditorOptions styleBatch() {
        return new CrossSectionDraftEditorOptions("style_batch", false, null, false, false, false, false, false);
    }

    public static CrossSectionDraftEditorOptions stationVariable(int index) {
        return new CrossSectionDraftEditorOptions("var_xs_" + index, false, null, true, false, true, true, true);
    }
}
