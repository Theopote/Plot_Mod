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

    /** 浮动横断面设计器 · 结构：行车道、车道数、标线；页底另挂隔离带与路灯（不含边坡与路肩条带）。 */
    public static CrossSectionDraftEditorOptions styleFloatingStructure() {
        return new CrossSectionDraftEditorOptions(
            "xs_structure", false, null, true, false, false, true, false, true, true);
    }

    /** 浮动横断面设计器 · 路肩/自行车道/人行道条带（不含行车道核心、隔离带与边坡）。 */
    public static CrossSectionDraftEditorOptions styleFloatingStrips() {
        return new CrossSectionDraftEditorOptions(
            "xs_strips", false, null, false, false, false, false, true, false, true);
    }

    /** 浮动横断面 · 边坡页（路边形式 / 放坡比；最大纵坡归纵断面工作区）。 */
    public static CrossSectionDraftEditorOptions styleFloatingSlope() {
        return new CrossSectionDraftEditorOptions(
            "xs_slope", false, null, false, false, true, false, false, false, true);
    }

    /** 材质页与结构页底隔离带/路灯等无横断面几何字段的控件选项。 */
    public static CrossSectionDraftEditorOptions styleBatch() {
        return new CrossSectionDraftEditorOptions(
            "style_batch", false, null, false, false, false, false, false, false, true);
    }
}
