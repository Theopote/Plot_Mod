package com.plot.plugin.road.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.SlopeFormatUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.utils.PlotI18n;

/**
 * 横断面草稿读写：认领/批量只写 draft；单条道路编辑同步到 {@link Road} 并附带继承钩子。
 */
public final class CrossSectionDraftMutator {
    private final CrossSectionDraft draft;
    private final Runnable onChanged;
    private final CrossSectionDraftFieldHooks hooks;
    private final Road road;
    private final RoadSystemConfig config;

    private CrossSectionDraftMutator(
            CrossSectionDraft draft,
            Runnable onChanged,
            CrossSectionDraftFieldHooks hooks,
            Road road,
            RoadSystemConfig config) {
        this.draft = draft;
        this.onChanged = onChanged;
        this.hooks = hooks != null ? hooks : CrossSectionDraftFieldHooks.NONE;
        this.road = road;
        this.config = config;
    }

    public static CrossSectionDraftMutator forDraft(CrossSectionDraft draft, Runnable onChanged) {
        return new CrossSectionDraftMutator(draft, onChanged, CrossSectionDraftFieldHooks.NONE, null, null);
    }

    public static CrossSectionDraftMutator forDraftWithHistory(CrossSectionDraft draft, Runnable onHistory) {
        CrossSectionDraftFieldHooks hooks = onHistory != null
            ? new DraftHistoryHooks(onHistory)
            : CrossSectionDraftFieldHooks.NONE;
        return new CrossSectionDraftMutator(draft, null, hooks, null, null);
    }

    public static CrossSectionDraftMutator forRoad(
            Road road,
            RoadSystemConfig config,
            Runnable onHistory) {
        CrossSectionDraft draft = CrossSectionDraft.fromRoad(road, config);
        CrossSectionDraftFieldHooks hooks = new RoadFieldHooks(onHistory);
        return new CrossSectionDraftMutator(draft, null, hooks, road, config);
    }

    public CrossSectionDraft draft() {
        return draft;
    }

    public CrossSectionDraftFieldHooks hooks() {
        return hooks;
    }

    public boolean isRoadEdit() {
        return road != null;
    }

    public void setLaneCount(int laneCount) {
        draft.setLaneCount(laneCount);
        if (road != null) {
            road.setLaneCount(laneCount);
        }
        notifyChanged();
    }

    public void setWidth(int width) {
        draft.setWidth(width);
        if (road != null) {
            road.setWidth(width);
        }
        notifyChanged();
    }

    public void setLaneWidthAt(int index, int laneWidth) {
        draft.setLaneWidthAt(index, laneWidth);
        if (road != null) {
            road.getCrossSection().getCarriageway().setLaneWidthAt(index, laneWidth);
        }
        notifyChanged();
    }

    public void setMaterial(MaterialMix material) {
        draft.setMaterial(material);
        if (road != null) {
            road.setMaterial(material);
        }
        notifyChanged();
    }

    public void setIncludeShoulder(boolean includeShoulder) {
        draft.setIncludeShoulder(includeShoulder);
        if (road != null) {
            road.setIncludeShoulder(includeShoulder);
        }
        notifyChanged();
    }

    public void setShoulderWidth(int shoulderWidth) {
        draft.setShoulderWidth(shoulderWidth);
        if (road != null) {
            road.setShoulderWidth(shoulderWidth);
        }
        notifyChanged();
    }

    public void setIncludeSlopeBatter(boolean includeSlopeBatter) {
        draft.setIncludeSlopeBatter(includeSlopeBatter);
        if (road != null) {
            road.setIncludeSlopeBatter(includeSlopeBatter);
        }
        notifyChanged();
    }

    public void setFillSlopeRatio(float fillSlopeRatio) {
        draft.setFillSlopeRatio(fillSlopeRatio);
        if (road != null) {
            road.setFillSlopeRatio(fillSlopeRatio);
        }
        notifyChanged();
    }

    public void setCutSlopeRatio(float cutSlopeRatio) {
        draft.setCutSlopeRatio(cutSlopeRatio);
        if (road != null) {
            road.setCutSlopeRatio(cutSlopeRatio);
        }
        notifyChanged();
    }

    public void setFillSlopeMaterial(String fillSlopeMaterial) {
        draft.setFillSlopeMaterial(fillSlopeMaterial);
        if (road != null) {
            road.setFillSlopeMaterial(fillSlopeMaterial);
        }
        notifyChanged();
    }

    public void setCutSlopeMaterial(String cutSlopeMaterial) {
        draft.setCutSlopeMaterial(cutSlopeMaterial);
        if (road != null) {
            road.setCutSlopeMaterial(cutSlopeMaterial);
        }
        notifyChanged();
    }

    public void setIncludeSidewalk(boolean includeSidewalk) {
        draft.setIncludeSidewalk(includeSidewalk);
        if (road != null) {
            road.setIncludeSidewalk(includeSidewalk);
        }
        notifyChanged();
    }

    public void setSidewalkWidth(int sidewalkWidth) {
        draft.setSidewalkWidth(sidewalkWidth);
        if (road != null) {
            road.setSidewalkWidth(sidewalkWidth);
        }
        notifyChanged();
    }

    public void setSidewalkMaterial(String sidewalkMaterial) {
        draft.setSidewalkMaterial(sidewalkMaterial);
        if (road != null) {
            road.setSidewalkMaterial(sidewalkMaterial);
        }
        notifyChanged();
    }

    public void setIncludeBikeLane(boolean includeBikeLane) {
        draft.setIncludeBikeLane(includeBikeLane);
        if (road != null) {
            road.setIncludeBikeLane(includeBikeLane);
        }
        notifyChanged();
    }

    public void setBikeLaneWidth(int bikeLaneWidth) {
        draft.setBikeLaneWidth(bikeLaneWidth);
        if (road != null) {
            road.setBikeLaneWidth(bikeLaneWidth);
        }
        notifyChanged();
    }

    public void setIncludeMedian(boolean includeMedian) {
        draft.setIncludeMedian(includeMedian);
        if (road != null) {
            road.setIncludeMedian(includeMedian);
        }
        notifyChanged();
    }

    public void setMedianWidth(int medianWidth) {
        draft.setMedianWidth(medianWidth);
        if (road != null) {
            road.setMedianWidth(medianWidth);
        }
        notifyChanged();
    }

    public void setIncludeDrainage(boolean includeDrainage) {
        draft.setIncludeDrainage(includeDrainage);
        if (road != null) {
            road.setIncludeDrainage(includeDrainage);
        }
        notifyChanged();
    }

    public void setLaneDividers(boolean laneDividers) {
        draft.setLaneDividers(laneDividers);
        if (road != null) {
            road.setLaneDividers(laneDividers);
        }
        notifyChanged();
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        draft.setCenterLineStyle(centerLineStyle);
        if (road != null) {
            road.setCenterLineStyle(centerLineStyle);
        }
        notifyChanged();
    }

    public void setMarkingMaterial(String markingMaterial) {
        draft.setMarkingMaterial(markingMaterial);
        if (road != null) {
            road.setMarkingMaterial(markingMaterial);
        }
        notifyChanged();
    }

    public void setStreetlightSpacing(int streetlightSpacing) {
        draft.setStreetlightSpacing(streetlightSpacing);
        if (road != null) {
            road.setStreetlightSpacing(streetlightSpacing);
        }
        notifyChanged();
    }

    public void setMaxSlope(float maxSlope) {
        draft.setMaxSlope(maxSlope);
        if (road != null) {
            road.setMaxSlope(maxSlope);
        }
        notifyChanged();
    }

    public void afterWidthField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_width",
            road.getWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getRoadWidth()),
            () -> road.setWidth(null));
    }

    public void afterLaneCountField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_lane_count",
            road.getLaneCount() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getLaneCount()),
            () -> road.getCrossSection().getCarriageway().setLaneCount(null));
    }

    public void afterMaterialField() {
        if (road == null || config == null) {
            return;
        }
        String inheritedMaterial = config.getSelectedMaterial() != null
            ? config.getSelectedMaterial().getPrimaryMaterial()
            : "";
        hooks.afterField(
            "road_material",
            road.getMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", inheritedMaterial),
            () -> road.getCrossSection().getCarriageway().setMaterial((MaterialMix) null));
    }

    public void afterSidewalkMaterialField() {
        if (road == null || config == null || !road.getEffectiveIncludeSidewalk(config)) {
            return;
        }
        hooks.afterField(
            "road_sidewalk_material",
            road.getSidewalkMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getSelectedSidewalkMaterial()),
            () -> road.setSidewalkMaterial(null));
    }

    public void afterFillSlopeMaterialField() {
        if (road == null || config == null || !road.getEffectiveIncludeSlopeBatter(config)) {
            return;
        }
        hooks.afterField(
            "road_fill_slope_material",
            road.getFillSlopeMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getFillSlopeMaterial()),
            () -> road.setFillSlopeMaterial(null));
    }

    public void afterCutSlopeMaterialField() {
        if (road == null || config == null || !road.getEffectiveIncludeSlopeBatter(config)) {
            return;
        }
        hooks.afterField(
            "road_cut_slope_material",
            road.getCutSlopeMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getCutSlopeMaterial()),
            () -> road.setCutSlopeMaterial(null));
    }

    public void afterMarkingMaterialField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_marking_material",
            road.getMarkingMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getMarkingMaterial()),
            () -> road.setMarkingMaterial(null));
    }

    public void afterLaneDividersField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_lane_dividers",
            road.getLaneDividers() == null,
            PlotI18n.tr(config.isLaneDividers()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> road.setLaneDividers(null));
    }

    public void afterCenterLineField() {
        if (road == null || config == null) {
            return;
        }
        String inheritedLabel = PlotI18n.tr(switch (config.getCenterLineStyle()) {
            case SINGLE_DASHED -> "plugin.road.center_line.single_dashed";
            case DOUBLE_SOLID -> "plugin.road.center_line.double_solid";
            default -> "plugin.road.center_line.none";
        });
        hooks.afterField(
            "road_center_line",
            road.getCenterLineStyle() == null,
            PlotI18n.tr("plugin.road.inherit_default_center_line", inheritedLabel),
            () -> road.setCenterLineStyle(null));
    }

    public void afterBikeLaneWidthField() {
        if (road == null || config == null || !road.getEffectiveIncludeBikeLane(config)) {
            return;
        }
        hooks.afterField(
            "road_bike_lane_width",
            road.getBikeLaneWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getBikeLaneWidth()),
            () -> road.setBikeLaneWidth(null));
    }

    public void afterShoulderIncludeField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_shoulder",
            road.getIncludeShoulder() == null,
            PlotI18n.tr(config.isIncludeShoulder()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> road.setIncludeShoulder(null));
    }

    public void afterShoulderWidthField() {
        if (road == null || config == null || !road.getEffectiveIncludeShoulder(config)) {
            return;
        }
        hooks.afterField(
            "road_shoulder_width",
            road.getShoulderWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getShoulderWidth()),
            () -> road.setShoulderWidth(null));
    }

    public void afterSlopeEnabledField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_slope_enabled",
            road.getIncludeSlopeBatter() == null,
            PlotI18n.tr(config.isIncludeSlopeBatter()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> road.setIncludeSlopeBatter(null));
    }

    public void afterFillSlopeField() {
        if (road == null || config == null || !road.getEffectiveIncludeSlopeBatter(config)) {
            return;
        }
        hooks.afterField(
            "road_fill_slope",
            road.getFillSlopeRatio() == null,
            PlotI18n.tr(
                "plugin.road.inherit_default_batter",
                SlopeFormatUtils.formatRatio(config.getFillSlopeRatio())),
            () -> road.setFillSlopeRatio(null));
    }

    public void afterCutSlopeField() {
        if (road == null || config == null || !road.getEffectiveIncludeSlopeBatter(config)) {
            return;
        }
        hooks.afterField(
            "road_cut_slope",
            road.getCutSlopeRatio() == null,
            PlotI18n.tr(
                "plugin.road.inherit_default_batter",
                SlopeFormatUtils.formatRatio(config.getCutSlopeRatio())),
            () -> road.setCutSlopeRatio(null));
    }

    public void afterBikeLaneIncludeField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_bike_lane",
            road.getIncludeBikeLane() == null,
            PlotI18n.tr(config.isIncludeBikeLane()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> road.setIncludeBikeLane(null));
    }

    public void afterSidewalkIncludeField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_sidewalk",
            road.getIncludeSidewalk() == null,
            PlotI18n.tr(config.isIncludeSidewalk()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> road.setIncludeSidewalk(null));
    }

    public void afterSidewalkWidthField() {
        if (road == null || config == null || !road.getEffectiveIncludeSidewalk(config)) {
            return;
        }
        hooks.afterField(
            "road_sidewalk_width",
            road.getSidewalkWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getSidewalkWidth()),
            () -> road.setSidewalkWidth(null));
    }

    public void afterDrainField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_drain",
            road.getIncludeDrainage() == null,
            PlotI18n.tr(config.isIncludeDrainage()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> road.setIncludeDrainage(null));
    }

    public void afterMedianIncludeField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_median",
            road.getIncludeMedian() == null,
            PlotI18n.tr("plugin.road.inherit_default_disabled"),
            () -> road.setIncludeMedian(null));
    }

    public void afterMedianWidthField() {
        if (road == null || config == null
            || road.getIncludeMedian() == null
            || !road.getIncludeMedian()) {
            return;
        }
        hooks.afterField(
            "road_median_width",
            road.getMedianWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getMedianWidth()),
            () -> road.setMedianWidth(null));
    }

    public void afterMaxSlopeField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_max_slope",
            road.getMaxSlope() == null,
            PlotI18n.tr(
                "plugin.road.inherit_default_percent",
                SlopeFormatUtils.formatPercent(config.getMaxSlope())),
            () -> road.setMaxSlope(null));
    }

    public void afterStreetlightField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_streetlight",
            road.getStreetlightSpacing() == null,
            PlotI18n.tr("plugin.road.inherit_default_disabled"),
            () -> road.setStreetlightSpacing(null));
    }

    private void notifyChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private static final class DraftHistoryHooks implements CrossSectionDraftFieldHooks {
        private final Runnable onHistory;

        private DraftHistoryHooks(Runnable onHistory) {
            this.onHistory = onHistory;
        }

        @Override
        public void onItemActivated() {
            if (onHistory != null) {
                onHistory.run();
            }
        }

        @Override
        public boolean pushHistoryOnPicker() {
            return true;
        }
    }

    private static final class RoadFieldHooks implements CrossSectionDraftFieldHooks {
        private final Runnable onHistory;

        private RoadFieldHooks(Runnable onHistory) {
            this.onHistory = onHistory;
        }

        @Override
        public void onItemActivated() {
            if (onHistory != null) {
                onHistory.run();
            }
        }

        @Override
        public boolean pushHistoryOnPicker() {
            return true;
        }

        @Override
        public void afterField(String fieldId, boolean inherited, String inheritLabel, Runnable reset) {
            RoadUiWidgets.renderOverrideFooter(
                inherited,
                inheritLabel,
                fieldId,
                withHistory(reset));
        }

        private Runnable withHistory(Runnable action) {
            return () -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                action.run();
            };
        }
    }
}
