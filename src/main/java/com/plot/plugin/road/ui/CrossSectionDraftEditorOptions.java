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
        boolean showAppearanceStrips,
        boolean showMarkings,
        boolean sectionCards) {

    public CrossSectionDraftEditorOptions(
            String idPrefix,
            boolean showBanner,
            String bannerKey,
            boolean showLaneWidths,
            boolean showMaxSlope,
            boolean showSlopeBatter,
            boolean showCarriagewayCore,
            boolean showAppearanceStrips,
            boolean showMarkings) {
        this(idPrefix, showBanner, bannerKey, showLaneWidths, showMaxSlope, showSlopeBatter,
            showCarriagewayCore, showAppearanceStrips, showMarkings, false);
    }

    public static CrossSectionDraftEditorOptions adopt() {
        return new CrossSectionDraftEditorOptions(
            "default", false, null, true, false, true, true, true, true);
    }

    /** 高级默认参数：不含车道数/单车道宽（由上方「快速调整」承接）。 */
    public static CrossSectionDraftEditorOptions adoptAdvanced() {
        return new CrossSectionDraftEditorOptions(
            "default_adv", false, null, true, false, true, false, true, true, true);
    }

    public static CrossSectionDraftEditorOptions batch() {
        return new CrossSectionDraftEditorOptions(
            "batch",
            true,
            "plugin.road.batch_edit_writes_explicit",
            false,
            false,
            false,
            false,
            true,
            true);
    }

    /** 批量浮动窗：分段套卡。 */
    public static CrossSectionDraftEditorOptions batchSectionCards() {
        return new CrossSectionDraftEditorOptions(
            "batch_cards",
            true,
            "plugin.road.batch_edit_writes_explicit",
            false,
            false,
            false,
            false,
            true,
            true,
            true);
    }

    public static CrossSectionDraftEditorOptions roadEdit() {
        return new CrossSectionDraftEditorOptions(
            "road", false, null, true, true, true, true, true, true);
    }

    /** 编辑 Tab 主界面：路肩/人行道等外观，不含宽度、车道与坡度。 */
    public static CrossSectionDraftEditorOptions styleAppearance() {
        return new CrossSectionDraftEditorOptions(
            "style", false, null, false, false, false, false, true, false);
    }

    /** 高级道路设计：分车道宽、边坡比等工程参数（不重复主界面的宽度/车道/路肩/人行道）。 */
    public static CrossSectionDraftEditorOptions styleAdvanced() {
        return new CrossSectionDraftEditorOptions(
            "style_adv", false, null, true, false, true, false, false, false);
    }

    /** 浮动横断面设计器 · 结构：行车道、车道数、分割线/中央标线（不含边坡与路肩条带）。 */
    public static CrossSectionDraftEditorOptions styleFloatingStructure() {
        return new CrossSectionDraftEditorOptions(
            "xs_structure", false, null, true, false, false, true, false, true, true);
    }

    /** 浮动横断面设计器 · 路肩/自行车道/人行道条带（不含行车道核心、隔离带与边坡）。 */
    public static CrossSectionDraftEditorOptions styleFloatingStrips() {
        return new CrossSectionDraftEditorOptions(
            "xs_strips", false, null, false, false, false, false, true, false, true);
    }

    /** 浮动横断面 · 边坡页。 */
    public static CrossSectionDraftEditorOptions styleFloatingSlope() {
        return new CrossSectionDraftEditorOptions(
            "xs_slope", false, null, false, true, true, false, false, false, true);
    }

    /** 样式 Tab 批量编辑：材质；附属设施页复用（隔离带 + 路灯）。 */
    public static CrossSectionDraftEditorOptions styleBatch() {
        return new CrossSectionDraftEditorOptions(
            "style_batch", false, null, false, false, false, false, false, false, true);
    }
}
