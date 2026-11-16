package com.plot.plugin.road.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.SlopeFormatUtils;
import com.plot.plugin.road.pipeline.construction.RoadConstructionMaterialPolicy;
import com.plot.plugin.road.pipeline.construction.RoadConstructionMaterialPolicy.MaterialRole;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;
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
    private String lastMaterialWarning;
    private String lastGeometryWarning;

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

    public String lastMaterialWarning() {
        return lastMaterialWarning;
    }

    public String lastGeometryWarning() {
        return lastGeometryWarning;
    }

    public CrossSectionDraftFieldHooks hooks() {
        return hooks;
    }

    public boolean isRoadEdit() {
        return road != null;
    }

    public void setLaneCount(int laneCount) {
        draft.setLaneCount(laneCount);
        // 清分车道覆盖，与均匀单车道宽滑条语义一致。
        draft.setDefaultLaneWidth(draft.defaultLaneWidth());
        if (laneCount <= 1) {
            draft.setLaneDividers(false);
            draft.setCenterLineStyle(CenterLineStyle.NONE);
        }
        if (road != null) {
            road.setLaneCount(laneCount);
            if (laneCount <= 1) {
                road.setLaneDividers(false);
                road.setCenterLineStyle(CenterLineStyle.NONE);
            }
            road.getCrossSection().getCarriageway().applyUniformDefaultLaneWidth(draft.defaultLaneWidth());
            syncRoadCarriagewayWidth();
        }
        enforceMedianFit();
        notifyChanged();
    }

    public void setWidth(int width) {
        draft.setWidth(width);
        if (road != null) {
            road.setWidth(width);
        }
        enforceMedianFit();
        notifyChanged();
    }

    public void setDefaultLaneWidth(int laneWidth) {
        draft.setDefaultLaneWidth(laneWidth);
        if (road != null) {
            // 必须清掉道路上残留的显式分车道宽，否则 resolve 仍用旧值，预览总宽不跟着变。
            road.getCrossSection().getCarriageway().applyUniformDefaultLaneWidth(laneWidth);
            syncRoadCarriagewayWidth();
        }
        notifyChanged();
    }

    public void setLaneWidthAt(int index, int laneWidth) {
        draft.setLaneWidthAt(index, laneWidth);
        if (road != null) {
            road.getCrossSection().getCarriageway().setLaneWidthAt(index, laneWidth);
            syncRoadCarriagewayWidth();
        }
        notifyChanged();
    }

    public void setMaterial(MaterialMix material) {
        MaterialMix safe = RoadConstructionMaterialPolicy.resolve(
            material,
            MaterialRole.CARRIAGEWAY,
            this::noteMaterialReplacement);
        draft.setMaterial(safe);
        if (road != null) {
            road.setMaterial(safe);
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

    public void setRoadsideEdgeMode(RoadsideEdgeMode mode) {
        draft.setRoadsideEdgeMode(mode);
        if (road != null) {
            road.setRoadsideEdgeMode(mode);
        }
        notifyChanged();
    }

    public void setRetainingWallMaterial(String wallMaterial) {
        String safe = sanitizeStoredMaterial(wallMaterial, MaterialRole.CUT_SLOPE);
        draft.setRetainingWallMaterial(safe);
        if (road != null) {
            road.setRetainingWallMaterial(safe);
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
        String safe = sanitizeStoredMaterial(fillSlopeMaterial, MaterialRole.FILL_SLOPE);
        draft.setFillSlopeMaterial(safe);
        if (road != null) {
            road.setFillSlopeMaterial(safe);
        }
        notifyChanged();
    }

    public void setCutSlopeMaterial(String cutSlopeMaterial) {
        String safe = sanitizeStoredMaterial(cutSlopeMaterial, MaterialRole.CUT_SLOPE);
        draft.setCutSlopeMaterial(safe);
        if (road != null) {
            road.setCutSlopeMaterial(safe);
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
        String safe = sanitizeStoredMaterial(sidewalkMaterial, MaterialRole.SIDEWALK);
        draft.setSidewalkMaterial(safe);
        if (road != null) {
            road.setSidewalkMaterial(safe);
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
        if (includeMedian) {
            draft.setMedianWidth(RoadParameterLimits.clampMedianWidthBlocks(
                draft.width(), Math.max(1, draft.medianWidth())));
        }
        if (road != null) {
            road.setIncludeMedian(includeMedian);
            if (includeMedian) {
                road.setMedianWidth(draft.medianWidth());
                road.setMedianHeight(draft.medianHeight());
            }
        }
        notifyChanged();
    }

    public void setMedianWidth(int medianWidth) {
        int clamped = RoadParameterLimits.clampMedianWidthBlocks(draft.width(), medianWidth);
        draft.setMedianWidth(clamped);
        if (road != null) {
            road.setMedianWidth(clamped);
        }
        notifyChanged();
    }

    public void setMedianHeight(int medianHeight) {
        draft.setMedianHeight(medianHeight);
        if (road != null) {
            road.setMedianHeight(draft.medianHeight());
        }
        notifyChanged();
    }

    public void setLaneDividers(boolean laneDividers) {
        draft.setLaneDividers(laneDividers);
        if (road != null) {
            road.setLaneDividers(laneDividers);
            syncRoadCarriagewayWidth();
        }
        notifyChanged();
    }

    public void setCenterLineStyle(CenterLineStyle centerLineStyle) {
        draft.setCenterLineStyle(centerLineStyle);
        if (road != null) {
            road.setCenterLineStyle(centerLineStyle);
            syncRoadCarriagewayWidth();
        }
        notifyChanged();
    }

    public void setMarkingMaterial(String markingMaterial) {
        String safe = sanitizeStoredMaterial(markingMaterial, MaterialRole.MARKING);
        draft.setMarkingMaterial(safe);
        if (road != null) {
            road.setMarkingMaterial(safe);
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
            road.getWidth() == null
                && !road.getCrossSection().getCarriageway().hasConfiguredDefaultLaneWidth()
                && !road.getCrossSection().getCarriageway().hasExplicitLaneWidths(),
            PlotI18n.tr("plugin.road.inherit_default_int", config.getRoadWidth()),
            () -> resetToInherit(() -> {
                road.setWidth(null);
                road.getCrossSection().getCarriageway().setDefaultLaneWidth(null);
                road.getCrossSection().getCarriageway().clearExplicitLaneWidths();
            }));
    }

    public void afterLaneCountField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_lane_count",
            road.getLaneCount() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getLaneCount()),
            () -> resetToInherit(() -> {
                road.getCrossSection().getCarriageway().setLaneCount(null);
                road.getCrossSection().getCarriageway().setLanes(java.util.List.of());
            }));
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
            () -> resetToInherit(
                () -> road.getCrossSection().getCarriageway().setMaterial((MaterialMix) null)));
    }

    public void afterSidewalkMaterialField() {
        if (road == null || config == null || !road.getEffectiveIncludeSidewalk(config)) {
            return;
        }
        hooks.afterField(
            "road_sidewalk_material",
            road.getSidewalkMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getSelectedSidewalkMaterial()),
            () -> resetToInherit(() -> road.setSidewalkMaterial(null)));
    }

    public void afterFillSlopeMaterialField() {
        if (road == null || config == null || !road.getEffectiveIncludeSlopeBatter(config)) {
            return;
        }
        hooks.afterField(
            "road_fill_slope_material",
            road.getFillSlopeMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getFillSlopeMaterial()),
            () -> resetToInherit(() -> road.setFillSlopeMaterial(null)));
    }

    public void afterCutSlopeMaterialField() {
        if (road == null || config == null || !road.getEffectiveIncludeSlopeBatter(config)) {
            return;
        }
        hooks.afterField(
            "road_cut_slope_material",
            road.getCutSlopeMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getCutSlopeMaterial()),
            () -> resetToInherit(() -> road.setCutSlopeMaterial(null)));
    }

    public void afterMarkingMaterialField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_marking_material",
            road.getMarkingMaterial() == null,
            PlotI18n.tr("plugin.road.inherit_default_material", config.getMarkingMaterial()),
            () -> resetToInherit(() -> road.setMarkingMaterial(null)));
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
            () -> resetToInherit(() -> road.setLaneDividers(null)));
    }

    public void afterCenterLineField() {
        if (road == null || config == null) {
            return;
        }
        String inheritedLabel = PlotI18n.tr(switch (config.getCenterLineStyle()) {
            case SINGLE_DASHED -> "plugin.road.center_line.single_dashed";
            case DOUBLE_SOLID -> "plugin.road.center_line.double_solid";
            case RAIL -> "plugin.road.center_line.rail";
            default -> "plugin.road.center_line.none";
        });
        hooks.afterField(
            "road_center_line",
            road.getCenterLineStyle() == null,
            PlotI18n.tr("plugin.road.inherit_default_center_line", inheritedLabel),
            () -> resetToInherit(() -> road.setCenterLineStyle(null)));
    }

    public void afterBikeLaneWidthField() {
        if (road == null || config == null || !road.getEffectiveIncludeBikeLane(config)) {
            return;
        }
        hooks.afterField(
            "road_bike_lane_width",
            road.getBikeLaneWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getBikeLaneWidth()),
            () -> resetToInherit(() -> road.setBikeLaneWidth(null)));
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
            () -> resetToInherit(() -> road.setIncludeShoulder(null)));
    }

    public void afterShoulderWidthField() {
        if (road == null || config == null || !road.getEffectiveIncludeShoulder(config)) {
            return;
        }
        hooks.afterField(
            "road_shoulder_width",
            road.getShoulderWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getShoulderWidth()),
            () -> resetToInherit(() -> road.setShoulderWidth(null)));
    }

    public void afterSlopeEnabledField() {
        if (road == null || config == null) {
            return;
        }
        String inheritLabel = switch (config.getRoadsideEdgeMode()) {
            case SLOPE_BATTER -> PlotI18n.tr("plugin.road.roadside_edge.slope_batter");
            case RETAINING_WALL -> PlotI18n.tr("plugin.road.roadside_edge.retaining_wall");
            case NONE -> PlotI18n.tr("plugin.road.roadside_edge.none");
        };
        hooks.afterField(
            "road_slope_enabled",
            road.getRoadsideEdgeMode() == null,
            PlotI18n.tr("plugin.road.inherit_default_batter", inheritLabel),
            () -> resetToInherit(() -> road.setRoadsideEdgeMode(null)));
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
            () -> resetToInherit(() -> road.setFillSlopeRatio(null)));
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
            () -> resetToInherit(() -> road.setCutSlopeRatio(null)));
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
            () -> resetToInherit(() -> road.setIncludeBikeLane(null)));
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
            () -> resetToInherit(() -> road.setIncludeSidewalk(null)));
    }

    public void afterSidewalkWidthField() {
        if (road == null || config == null || !road.getEffectiveIncludeSidewalk(config)) {
            return;
        }
        hooks.afterField(
            "road_sidewalk_width",
            road.getSidewalkWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getSidewalkWidth()),
            () -> resetToInherit(() -> road.setSidewalkWidth(null)));
    }

    public void afterMedianIncludeField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_median",
            road.getIncludeMedian() == null,
            PlotI18n.tr(config.isIncludeMedian()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            () -> resetToInherit(() -> road.setIncludeMedian(null)));
    }

    public void afterMedianWidthField() {
        if (road == null || config == null || !isMedianEffectivelyEnabled()) {
            return;
        }
        hooks.afterField(
            "road_median_width",
            road.getMedianWidth() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getMedianWidth()),
            () -> resetToInherit(() -> road.setMedianWidth(null)));
    }

    public void afterMedianHeightField() {
        if (road == null || config == null || !isMedianEffectivelyEnabled()) {
            return;
        }
        hooks.afterField(
            "road_median_height",
            road.getMedianHeight() == null,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getMedianHeight()),
            () -> resetToInherit(() -> road.setMedianHeight(null)));
    }

    private boolean isMedianEffectivelyEnabled() {
        if (Boolean.TRUE.equals(road.getIncludeMedian())) {
            return true;
        }
        return road.getIncludeMedian() == null && config.isIncludeMedian();
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
            () -> resetToInherit(() -> road.setMaxSlope(null)));
    }

    public void afterStreetlightField() {
        if (road == null || config == null) {
            return;
        }
        hooks.afterField(
            "road_streetlight",
            road.getStreetlightSpacing() == null,
            config.getStreetlightSpacing() > 0
                ? PlotI18n.tr("plugin.road.inherit_default_int", config.getStreetlightSpacing())
                : PlotI18n.tr("plugin.road.inherit_default_disabled"),
            () -> resetToInherit(() -> road.setStreetlightSpacing(null)));
    }

    /** 清除道路覆盖后，把 draft 重新灌成继承后的有效值，保证同帧 UI/预览立刻变化。 */
    private void resetToInherit(Runnable clearRoadOverride) {
        if (clearRoadOverride != null) {
            clearRoadOverride.run();
        }
        if (road != null && config != null) {
            draft.replaceFrom(CrossSectionDraft.fromRoad(road, config));
        }
        notifyChanged();
    }

    private String sanitizeStoredMaterial(String material, MaterialRole role) {
        if (material == null || material.isBlank()) {
            return material;
        }
        return RoadConstructionMaterialPolicy.resolveStored(material, role, this::noteMaterialReplacement);
    }

    private void noteMaterialReplacement(String requested, String resolved) {
        if (requested != null && resolved != null && !requested.equals(resolved)) {
            lastMaterialWarning = PlotI18n.tr("plugin.road.material_gravity_unsafe_replaced", requested, resolved);
        }
    }

    private void enforceMedianFit() {
        lastGeometryWarning = null;
        if (!draft.includeMedian()) {
            return;
        }
        int requested = Math.max(1, draft.medianWidth());
        int clamped = RoadParameterLimits.clampMedianWidthBlocks(draft.width(), requested);
        if (clamped != requested) {
            draft.setMedianWidth(clamped);
            if (road != null) {
                road.setMedianWidth(clamped);
            }
            lastGeometryWarning = PlotI18n.tr(
                "plugin.road.median_width_clamped",
                requested,
                draft.laneCount(),
                draft.width(),
                clamped);
        }
    }

    private void syncRoadCarriagewayWidth() {
        if (road == null) {
            return;
        }
        CenterLineStyle style = draft.centerLineStyle() != null
            ? draft.centerLineStyle()
            : CenterLineStyle.NONE;
        road.getCrossSection().getCarriageway().syncDerivedWidth(style, draft.laneDividers(), 0);
        if (draft.includeMedian()) {
            int clamped = RoadParameterLimits.clampMedianWidthBlocks(
                draft.width(), Math.max(1, draft.medianWidth()));
            draft.setMedianWidth(clamped);
            road.setMedianWidth(clamped);
        }
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
