package com.plot.plugin.road.ui;

import com.plot.core.material.MaterialMix;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.model.section.CrossSectionResolveReport;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.model.section.RoadsideEdgeMode;
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

    /** 中央隔离带、路灯等附属设施。 */
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
            RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(editor.options.bannerKey()));
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
        if (editor.options.showCarriagewayCore()) {
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.lane_count") + "##" + id + "_lanes",
                laneCountArr,
                RoadParameterLimits.MIN_LANE_COUNT,
                RoadParameterLimits.MAX_LANE_COUNT,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setLaneCount(laneCountArr[0]);
            }
            laneCount = draft.laneCount();
            mutator.afterLaneCountField();

            int[] laneWidthArr = {draft.defaultLaneWidth()};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.lane_travel_width") + "##" + id + "_lane_w",
                laneWidthArr,
                RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS,
                RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setDefaultLaneWidth(laneWidthArr[0]);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.lane_travel_width"));
            }

            ImGui.textDisabled(PlotI18n.tr("plugin.road.carriageway_width_readonly", draft.width()));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_width"));
            }
            mutator.afterWidthField();
        } else {
            laneCount = draft.laneCount();
        }

        if (editor.options.showLaneWidths() && laneCount > 1) {
            if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.custom_lane_widths") + "##" + id + "_lanes_adv")) {
                var resolved = draft.resolveLaneWidths();
                for (int i = 0; i < laneCount; i++) {
                    int current = i < draft.laneWidths().size() && draft.laneWidths().get(i) > 0
                        ? draft.laneWidths().get(i)
                        : resolved.get(i);
                    int[] perLaneArr = {current};
                    if (ImGui.sliderInt(
                        PlotI18n.tr("plugin.road.lane_width_label", i + 1)
                            + "##" + id + "_lane_" + i,
                        perLaneArr,
                        RoadParameterLimits.MIN_LANE_WIDTH_BLOCKS,
                        RoadParameterLimits.MAX_LANE_WIDTH_BLOCKS,
                        "%d")) {
                        if (ImGui.isItemActivated()) {
                            hooks.onItemActivated();
                        }
                        mutator.setLaneWidthAt(i, perLaneArr[0]);
                    }
                }
                ImGui.textDisabled(PlotI18n.tr("plugin.road.carriageway_width_readonly", draft.width()));
            }
        }

        // 结构：车道分割线 / 中央标线（与行车道几何一起编辑）。
        if (editor.options.showMarkings()) {
            renderMarkingFields(editor);
        }

        if (!editor.options.showAppearanceStrips()) {
            if (editor.options.showSlopeBatter()) {
                renderSlopeBatterFields(editor);
            }
            if (editor.options.showMaxSlope()) {
                renderMaxSlopeField(editor);
            }
            return;
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
                PlotI18n.tr("plugin.road.shoulder_width") + "##" + id + "_shoulder_w",
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

        if (editor.options.showSlopeBatter()) {
            renderSlopeBatterFields(editor);
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
                PlotI18n.tr("plugin.road.bike_lane_width") + "##" + id + "_bike_w",
                bikeWidthArr,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d")) {
                if (ImGui.isItemActivated()) {
                    hooks.onItemActivated();
                }
                mutator.setBikeLaneWidth(bikeWidthArr[0]);
            }
            mutator.afterBikeLaneWidthField();
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
                PlotI18n.tr("plugin.road.sidewalk_width") + "##" + id + "_sw_w",
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

        if (editor.options.showMaxSlope()) {
            renderMaxSlopeField(editor);
        }
        renderGeometryWarning(mutator);
    }

    private static void renderSlopeBatterFields(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        boolean roadEdit = editor.roadEdit;
        String id = editor.id;

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.slope_batter_section"));
        RoadUiWidgets.textWrappedColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.roadside_edge_hint"));
        String[] edgeLabels = {
            PlotI18n.tr("plugin.road.roadside_edge.none"),
            PlotI18n.tr("plugin.road.roadside_edge.slope_batter"),
            PlotI18n.tr("plugin.road.roadside_edge.retaining_wall")
        };
        int edgeIndex = switch (draft.roadsideEdgeMode()) {
            case SLOPE_BATTER -> 1;
            case RETAINING_WALL -> 2;
            default -> 0;
        };
        imgui.type.ImInt edgeRef = new imgui.type.ImInt(edgeIndex);
        if (ImGui.combo(
                PlotI18n.tr("plugin.road.roadside_edge_mode") + "##" + id + "_edge_mode",
                edgeRef,
                edgeLabels)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            RoadsideEdgeMode mode = switch (edgeRef.get()) {
                case 1 -> RoadsideEdgeMode.SLOPE_BATTER;
                case 2 -> RoadsideEdgeMode.RETAINING_WALL;
                default -> RoadsideEdgeMode.NONE;
            };
            mutator.setRoadsideEdgeMode(mode);
        }
        mutator.afterSlopeEnabledField();
        if (draft.includeSlopeBatter()) {
            if (SlopeAnglePresetControl.render(
                id + "_slope_angle",
                draft.fillSlopeRatio(),
                draft.cutSlopeRatio(),
                (fillRatio, cutRatio, linked) -> {
                    if (roadEdit) {
                        hooks.onItemActivated();
                    }
                    mutator.setFillSlopeRatio(fillRatio);
                    mutator.setCutSlopeRatio(cutRatio);
                })) {
                mutator.afterFillSlopeField();
                mutator.afterCutSlopeField();
            }
            ImGui.spacing();
            if (ImGui.treeNodeEx(PlotI18n.tr("plugin.road.slope_angle_advanced") + "##" + id)) {
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
                ImGui.treePop();
            }
        }
    }

    private static void renderMaxSlopeField(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        boolean roadEdit = editor.roadEdit;
        String id = editor.id;

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
                mutator::setMaterial,
            hooks.pushHistoryOnPicker()
        );
        mutator.afterMaterialField();
        renderMaterialWarning(mutator);

        if (draft.includeSidewalk()) {
            final String[] sidewalkMaterial = {draft.sidewalkMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                sidewalkMaterial[0] != null
                    ? sidewalkMaterial[0]
                    : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL,
                    mutator::setSidewalkMaterial,
                hooks.pushHistoryOnPicker()
            );
            mutator.afterSidewalkMaterialField();
        }

        if (draft.includeSlopeBatter()) {
            final String[] fillSlopeMaterial = {draft.fillSlopeMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_fill_slope_material",
                PlotI18n.tr("plugin.road.fill_slope_material"),
                fillSlopeMaterial[0],
                    mutator::setFillSlopeMaterial,
                hooks.pushHistoryOnPicker()
            );
            mutator.afterFillSlopeMaterialField();
            final String[] cutSlopeMaterial = {draft.cutSlopeMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_cut_slope_material",
                PlotI18n.tr("plugin.road.cut_slope_material"),
                cutSlopeMaterial[0] != null ? cutSlopeMaterial[0] : "",
                    mutator::setCutSlopeMaterial,
                hooks.pushHistoryOnPicker()
            );
            mutator.afterCutSlopeMaterialField();
        }
        if (draft.includeRetainingWall()) {
            final String[] wallMaterial = {draft.retainingWallMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                editor.ctx,
                "##" + id + "_retaining_wall_material",
                PlotI18n.tr("plugin.road.retaining_wall_material"),
                wallMaterial[0],
                    mutator::setRetainingWallMaterial,
                hooks.pushHistoryOnPicker()
            );
        }

        final String[] markingMaterial = {draft.markingMaterial()};
        RoadUiWidgets.renderBlockMaterialPicker(
            editor.ctx,
            "##" + id + "_marking_material",
            PlotI18n.tr("plugin.road.marking_material"),
            markingMaterial[0],
                mutator::setMarkingMaterial,
            hooks.pushHistoryOnPicker()
        );
        mutator.afterMarkingMaterialField();
        renderMaterialWarning(mutator);
        renderResolveReportWarnings(editor);
    }

    private static void renderGeometryWarning(CrossSectionDraftMutator mutator) {
        String warning = mutator.lastGeometryWarning();
        if (warning == null || warning.isBlank()) {
            return;
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, warning);
    }

    private static void renderMaterialWarning(CrossSectionDraftMutator mutator) {
        String warning = mutator.lastMaterialWarning();
        if (warning == null || warning.isBlank()) {
            return;
        }
        RoadUiWidgets.textWrappedColored(PluginUiColors.WARNING, warning);
    }

    private static void renderResolveReportWarnings(EditorContext editor) {
        RoadSystemConfig defaults = editor.ctx != null && editor.ctx.networkManager() != null
            ? editor.ctx.networkManager().getConfig()
            : new RoadSystemConfig("cross-section-ui");
        CrossSectionResolveReport report = ResolvedCrossSection.resolveWithReport(
            editor.draft.toCrossSection(),
            defaults).report();
        if (report == null || report.isEmpty()) {
            return;
        }
        for (CrossSectionResolveReport.MaterialReplacement replacement : report.materialReplacements()) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr(
                    "plugin.road.material_gravity_unsafe_replaced",
                    replacement.requested(),
                    replacement.resolved()));
        }
        if (!report.geometryNotes().isEmpty()
                && (editor.mutator.lastGeometryWarning() == null
                    || editor.mutator.lastGeometryWarning().isBlank())) {
            RoadUiWidgets.textWrappedColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.road.median_fit_adjusted"));
        }
    }

    /** 结构：车道分割线与中央标线。 */
    private static void renderMarkingFields(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        boolean roadEdit = editor.roadEdit;
        String id = editor.id;

        boolean multiLane = draft.laneCount() > 1;
        if (multiLane) {
            ImBoolean laneDividersRef = new ImBoolean(draft.laneDividers());
            if (ImGui.checkbox(PlotI18n.tr("plugin.road.lane_dividers") + "##" + id + "_dividers", laneDividersRef)) {
                if (roadEdit) {
                    hooks.onItemActivated();
                }
                mutator.setLaneDividers(laneDividersRef.get());
            }
            mutator.afterLaneDividersField();

            CenterLineStyle centerLineStyle = draft.centerLineStyle();
            String[] centerLineLabels = {
                PlotI18n.tr("plugin.road.center_line.none"),
                PlotI18n.tr("plugin.road.center_line.single_yellow"),
                PlotI18n.tr("plugin.road.center_line.double_yellow"),
                PlotI18n.tr("plugin.road.center_line.rail")
            };
            ImInt centerLineIndex = new ImInt(switch (centerLineStyle) {
                case SINGLE_DASHED -> 1;
                case DOUBLE_SOLID -> 2;
                case RAIL -> 3;
                default -> 0;
            });
            if (ImGui.combo(
                PlotI18n.tr("plugin.road.center_line_style") + "##" + id + "_center",
                centerLineIndex,
                centerLineLabels)) {
                if (roadEdit) {
                    hooks.onItemActivated();
                }
                CenterLineStyle selected = switch (centerLineIndex.get()) {
                    case 1 -> CenterLineStyle.SINGLE_DASHED;
                    case 2 -> CenterLineStyle.DOUBLE_SOLID;
                    case 3 -> CenterLineStyle.RAIL;
                    default -> CenterLineStyle.NONE;
                };
                mutator.setCenterLineStyle(selected);
                if (selected == CenterLineStyle.SINGLE_DASHED || selected == CenterLineStyle.DOUBLE_SOLID) {
                    mutator.setMarkingMaterial("minecraft:yellow_concrete");
                } else if (selected != CenterLineStyle.NONE) {
                    mutator.setMarkingMaterial(ResolvedCrossSection.DEFAULT_MARKING_MATERIAL);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(PlotI18n.tr("hint.plot.road.center_line_blocks"));
            }
            mutator.afterCenterLineField();
        } else if (draft.laneDividers() || draft.centerLineStyle() != CenterLineStyle.NONE) {
            // 单车道无可分隔对象：强制关闭标线，避免预览残留中央色块。
            mutator.setLaneDividers(false);
            mutator.setCenterLineStyle(CenterLineStyle.NONE);
        }
    }

    /** 附属设施：路表中央隔离带。 */
    private static void renderMedianFields(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        boolean roadEdit = editor.roadEdit;
        String id = editor.id;

        ImBoolean medianRef = new ImBoolean(draft.includeMedian());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_median") + "##" + id + "_median", medianRef)) {
            if (roadEdit) {
                hooks.onItemActivated();
            }
            mutator.setIncludeMedian(medianRef.get());
        }
        mutator.afterMedianIncludeField();
        if (!draft.includeMedian()) {
            return;
        }
        int[] medianOptions = RoadParameterLimits.validMedianWidths(draft.width());
        int clampedMedian = RoadParameterLimits.clampMedianWidthBlocks(
            draft.width(), Math.max(1, draft.medianWidth()));
        if (clampedMedian != draft.medianWidth()) {
            mutator.setMedianWidth(clampedMedian);
        }
        int[] medianWidthArr = {clampedMedian};
        int minMedian = medianOptions.length > 0 ? medianOptions[0] : 1;
        int maxMedian = medianOptions.length > 0
            ? medianOptions[medianOptions.length - 1]
            : RoadParameterLimits.MAX_BRIDGE_PIER_DIAMETER_BLOCKS;
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.median_width") + "##" + id + "_median_w",
            medianWidthArr,
            minMedian,
            maxMedian,
            "%d")) {
            if (ImGui.isItemActivated()) {
                hooks.onItemActivated();
            }
            mutator.setMedianWidth(medianWidthArr[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.median_width_parity"));
        }
        mutator.afterMedianWidthField();

        int[] medianHeightArr = {draft.medianHeight()};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.median_height") + "##" + id + "_median_h",
            medianHeightArr,
            RoadParameterLimits.MIN_MEDIAN_HEIGHT_BLOCKS,
            RoadParameterLimits.MAX_MEDIAN_HEIGHT_BLOCKS,
            "%d")) {
            if (ImGui.isItemActivated()) {
                hooks.onItemActivated();
            }
            mutator.setMedianHeight(medianHeightArr[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.median_height"));
        }
        mutator.afterMedianHeightField();
    }

    private static void renderFurniture(EditorContext editor) {
        CrossSectionDraft draft = editor.draft;
        CrossSectionDraftMutator mutator = editor.mutator;
        CrossSectionDraftFieldHooks hooks = editor.hooks;
        String id = editor.id;

        renderMedianFields(editor);
        ImGui.spacing();

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
