package com.plot.plugin.road.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

/**
 * 认领默认、批量编辑、单条道路编辑共用的横断面草稿 UI。
 */
public final class CrossSectionDraftEditor {
    private CrossSectionDraftEditor() {
    }

    public static void render(
            RoadUiContext ctx,
            CrossSectionDraft draft,
            CrossSectionDraftEditorOptions options,
            Runnable onChanged) {
        render(ctx, CrossSectionDraftMutator.forDraft(draft, onChanged), options);
    }

    public static void render(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            CrossSectionDraftEditorOptions options) {
        EditorContext editor = EditorContext.of(ctx, mutator, options);
        if (editor == null) {
            return;
        }
        renderBanner(editor);
        renderCrossSection(editor);
        renderMaterials(editor);
        renderFurniture(editor);
    }

    /** 几何与工程约束（车道、路肩、边坡比、最大坡度等）。 */
    public static void renderCrossSection(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            CrossSectionDraftEditorOptions options) {
        EditorContext editor = EditorContext.of(ctx, mutator, options);
        if (editor != null) {
            renderCrossSection(editor);
        }
    }

    /** 铺面与边坡材质。 */
    public static void renderMaterials(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            CrossSectionDraftEditorOptions options) {
        EditorContext editor = EditorContext.of(ctx, mutator, options);
        if (editor != null) {
            renderMaterials(editor);
        }
    }

    /** 排水、标线、路灯等附属设施。 */
    public static void renderFurniture(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            CrossSectionDraftEditorOptions options) {
        EditorContext editor = EditorContext.of(ctx, mutator, options);
        if (editor != null) {
            renderFurniture(editor);
        }
    }

    private static void renderBanner(EditorContext editor) {
        if (editor.options.showBanner() && editor.options.bannerKey() != null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(editor.options.bannerKey()));
            ImGui.spacing();
        }
    }

    private static void renderCrossSection(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        boolean roadEdit = editor.roadEdit;
        String id = editor.id;

        int laneCount = draft.laneCount();
        int[] laneCountArr = {laneCount};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_count", laneCountArr[0]) + "##" + id + "_lanes",
            laneCountArr,
            RoadParameterLimits.MIN_LANE_COUNT,
            RoadParameterLimits.MAX_LANE_COUNT,
            "%d")) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setLaneCount(laneCountArr[0]);
        }
        laneCount = draft.laneCount();

        int width = draft.width();
        int[] widthArr = {width};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.road_width", widthArr[0]) + "##" + id + "_width",
            widthArr,
            RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH,
            RoadParameterLimits.MAX_CARRIAGEWAY_WIDTH,
            "%d")) {
            if (ImGui.isItemActivated()) {
                hooks.onItemActivated();
            }
            mutator.setWidth(widthArr[0]);
        }
        width = draft.width();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_width"));
        }
        mutator.afterWidthField();

        if (editor.options.showLaneWidths() && laneCount > 1) {
            var resolved = draft.resolveLaneWidths();
            for (int i = 0; i < laneCount; i++) {
                int current = i < draft.laneWidths().size() && draft.laneWidths().get(i) > 0
                    ? draft.laneWidths().get(i)
                    : resolved.get(i);
                int[] laneWidthArr = {current};
                int perLaneMax = RoadParameterLimits.maxPerLaneWidth(width, laneCount);
                if (ImGui.sliderInt(
                    PlotI18n.tr("plugin.road.lane_width_label", i + 1, laneWidthArr[0])
                        + "##" + id + "_lane_" + i,
                    laneWidthArr,
                    RoadParameterLimits.MIN_STRIP_WIDTH,
                    perLaneMax,
                    "%d")) {
                    if (ImGui.isItemActivated()) {
                        hooks.onItemActivated();
                    }
                    mutator.setLaneWidthAt(i, laneWidthArr[0]);
                }
            }
        }

        ImBoolean shoulderRef = new ImBoolean(draft.includeShoulder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder") + "##" + id + "_shoulder", shoulderRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeShoulder(shoulderRef.get());
        }
        mutator.afterShoulderIncludeField();
        if (draft.includeShoulder()) {
            int[] shoulderWidthArr = {
                Math.max(RoadParameterLimits.MIN_STRIP_WIDTH, draft.shoulderWidth())
            };
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.shoulder_width", shoulderWidthArr[0]) + "##" + id + "_shoulder_w",
                shoulderWidthArr,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setShoulderWidth(shoulderWidthArr[0]);
            }
            mutator.afterShoulderWidthField();
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.slope_batter_section"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.slope_batter_section_hint"));
        ImBoolean slopeRef = new ImBoolean(draft.includeSlopeBatter());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_slope_batter") + "##" + id + "_slope", slopeRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeSlopeBatter(slopeRef.get());
        }
        mutator.afterSlopeEnabledField();
        if (draft.includeSlopeBatter()) {
            float[] fillSlopeArr = {draft.fillSlopeRatio()};
            if (EngineeringSlopeInput.render(
                id + "_fill_slope_ratio",
                PlotI18n.tr("plugin.road.fill_slope_ratio_label"),
                fillSlopeArr,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                if (roadEdit) {
                    hooks.onItemActivated();
                }
                mutator.setFillSlopeRatio(fillSlopeArr[0]);
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.fill_slope_ratio");
            mutator.afterFillSlopeField();

            float[] cutSlopeArr = {draft.cutSlopeRatio()};
            if (EngineeringSlopeInput.render(
                id + "_cut_slope_ratio",
                PlotI18n.tr("plugin.road.cut_slope_ratio_label"),
                cutSlopeArr,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                if (roadEdit) {
                    hooks.onItemActivated();
                }
                mutator.setCutSlopeRatio(cutSlopeArr[0]);
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.cut_slope_ratio");
            mutator.afterCutSlopeField();
        }

        ImBoolean bikeRef = new ImBoolean(draft.includeBikeLane());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_bike_lane") + "##" + id + "_bike", bikeRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeBikeLane(bikeRef.get());
        }
        mutator.afterBikeLaneIncludeField();
        if (draft.includeBikeLane()) {
            int[] bikeWidthArr = {
                Math.max(RoadParameterLimits.MIN_STRIP_WIDTH, draft.bikeLaneWidth())
            };
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.bike_lane_width", bikeWidthArr[0]) + "##" + id + "_bike_w",
                bikeWidthArr,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setBikeLaneWidth(bikeWidthArr[0]);
            }
        }

        ImBoolean sidewalkRef = new ImBoolean(draft.includeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##" + id + "_sw", sidewalkRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeSidewalk(sidewalkRef.get());
        }
        mutator.afterSidewalkIncludeField();
        if (draft.includeSidewalk()) {
            int[] sidewalkWidthArr = {draft.sidewalkWidth()};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidthArr[0]) + "##" + id + "_sw_w",
                sidewalkWidthArr,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setSidewalkWidth(sidewalkWidthArr[0]);
            }
            mutator.afterSidewalkWidthField();
        }

        ImBoolean medianRef = new ImBoolean(draft.includeMedian());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_median") + "##" + id + "_median", medianRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeMedian(medianRef.get());
        }
        mutator.afterMedianIncludeField();
        if (draft.includeMedian()) {
            int[] medianWidthArr = {
                Math.max(RoadParameterLimits.MIN_STRIP_WIDTH, draft.medianWidth())
            };
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.median_width", medianWidthArr[0]) + "##" + id + "_median_w",
                medianWidthArr,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setMedianWidth(medianWidthArr[0]);
            }
            mutator.afterMedianWidthField();
        }

        if (editor.options.showMaxSlope()) {
            float[] maxSlopeArr = {draft.maxSlope()};
            if (EngineeringSlopeInput.render(
                id + "_max_slope",
                PlotI18n.tr("plugin.road.max_slope_label"),
                maxSlopeArr,
                EngineeringSlopeInput.ValueKind.GRADE
            )) {
                if (roadEdit) {
                    hooks.onItemActivated();
                }
                mutator.setMaxSlope(maxSlopeArr[0]);
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.max_slope");
            mutator.afterMaxSlopeField();
        }
    }

    private static void renderMaterials(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        String id = editor.id;

        final MaterialMix[] material = {draft.material()};
        RoadUiWidgets.renderMaterialMixPicker(
            editor.ctx,
            "##" + id + "_road_material",
            PlotI18n.tr("plugin.road.material"),
            material[0],
            value -> mutator.setMaterial(value),
            hooks.pushHistoryOnPicker()
        );

        if (draft.includeSidewalk()) {
            final String[] sidewalkMaterial = {draft.sidewalkMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                sidewalkMaterial[0] != null
                    ? sidewalkMaterial[0]
                    : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL,
                value -> mutator.setSidewalkMaterial(value),
                hooks.pushHistoryOnPicker()
            );
        }

        if (draft.includeSlopeBatter()) {
            final String[] fillSlopeMaterial = {draft.fillSlopeMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_fill_slope_material",
                PlotI18n.tr("plugin.road.fill_slope_material"),
                fillSlopeMaterial[0],
                value -> mutator.setFillSlopeMaterial(value),
                hooks.pushHistoryOnPicker()
            );
            final String[] cutSlopeMaterial = {draft.cutSlopeMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_cut_slope_material",
                PlotI18n.tr("plugin.road.cut_slope_material"),
                cutSlopeMaterial[0] != null ? cutSlopeMaterial[0] : "",
                value -> mutator.setCutSlopeMaterial(value),
                hooks.pushHistoryOnPicker()
            );
        }

        final String[] markingMaterial = {draft.markingMaterial()};
        RoadUiWidgets.renderBlockMaterialPicker(
            editor.ctx,
            "##" + id + "_marking_material",
            PlotI18n.tr("plugin.road.marking_material"),
            markingMaterial[0],
            value -> mutator.setMarkingMaterial(value),
            hooks.pushHistoryOnPicker()
        );
    }

    private static void renderFurniture(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        boolean roadEdit = editor.roadEdit;
        String id = editor.id;

        ImBoolean drainRef = new ImBoolean(draft.includeDrainage());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage") + "##" + id + "_drain", drainRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeDrainage(drainRef.get());
        }
        mutator.afterDrainField();

        ImBoolean laneDividersRef = new ImBoolean(draft.laneDividers());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.lane_dividers") + "##" + id + "_dividers", laneDividersRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setLaneDividers(laneDividersRef.get());
        }

        CenterLineStyle centerLineStyle = draft.centerLineStyle();
        String[] centerLineLabels = {
            PlotI18n.tr("plugin.road.center_line.none"),
            PlotI18n.tr("plugin.road.center_line.single_dashed"),
            PlotI18n.tr("plugin.road.center_line.double_solid")
        };
        ImInt centerLineIndex = new ImInt(switch (centerLineStyle) {
            case SINGLE_DASHED -> 1;
            case DOUBLE_SOLID -> 2;
            default -> 0;
        });
        if (ImGui.combo(
            PlotI18n.tr("plugin.road.center_line_style") + "##" + id + "_center",
            centerLineIndex,
            centerLineLabels)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setCenterLineStyle(switch (centerLineIndex.get()) {
                case 1 -> CenterLineStyle.SINGLE_DASHED;
                case 2 -> CenterLineStyle.DOUBLE_SOLID;
                default -> CenterLineStyle.NONE;
            });
        }

        int[] lightSpacing = {draft.streetlightSpacing()};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.streetlight_spacing") + "##" + id + "_lights",
            lightSpacing,
            RoadParameterLimits.STREETLIGHT_DISABLED,
            RoadParameterLimits.MAX_STREETLIGHT_SPACING,
            lightSpacing[0] <= 0 ? PlotI18n.tr("plugin.road.streetlight_off") : "%dm"
        )) {
            if (ImGui.isItemActivated()) {
                hooks.onItemActivated();
            }
            int value = lightSpacing[0];
            if (value > 0 && value < RoadParameterLimits.MIN_STREETLIGHT_SPACING) {
                value = RoadParameterLimits.MIN_STREETLIGHT_SPACING;
            }
            mutator.setStreetlightSpacing(value);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.streetlight_spacing"));
        }
        mutator.afterStreetlightField();
    }

    private record EditorContext(
            RoadUiContext ctx,
            CrossSectionDraftMutator mutator,
            CrossSectionDraftEditorOptions options,
            CrossSectionDraft draft,
            CrossSectionDraftFieldHooks hooks,
            boolean roadEdit,
            String id) {

        static EditorContext of(
                RoadUiContext ctx,
                CrossSectionDraftMutator mutator,
                CrossSectionDraftEditorOptions options) {
            CrossSectionDraft draft = mutator.draft();
            if (draft == null) {
                return null;
            }
            return new EditorContext(
                ctx,
                mutator,
                options,
                draft,
                mutator.hooks(),
                mutator.isRoadEdit(),
                options.idPrefix());
        }
    }
}
