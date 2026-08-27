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
 * 认领默认、批量编辑共用的横断面草稿 UI。
 */
public final class CrossSectionDraftEditor {
    private CrossSectionDraftEditor() {
    }

    public static void render(
            RoadUiContext ctx,
            CrossSectionDraft draft,
            CrossSectionDraftEditorOptions options,
            Runnable onChanged) {
        if (draft == null) {
            return;
        }
        if (options.showBanner() && options.bannerKey() != null) {
            ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(options.bannerKey()));
            ImGui.spacing();
        }

        String id = options.idPrefix();
        int laneCount = draft.laneCount();
        int[] laneCountArr = {laneCount};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_count", laneCountArr[0]) + "##" + id + "_lanes",
            laneCountArr,
            RoadParameterLimits.MIN_LANE_COUNT,
            RoadParameterLimits.MAX_LANE_COUNT,
            "%d")) {
            draft.setLaneCount(laneCountArr[0]);
            notifyChanged(onChanged);
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
            draft.setWidth(widthArr[0]);
            notifyChanged(onChanged);
        }
        width = draft.width();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_width"));
        }

        if (options.showLaneWidths() && laneCount > 1) {
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
                    draft.setLaneWidthAt(i, laneWidthArr[0]);
                    notifyChanged(onChanged);
                }
            }
        }

        final MaterialMix[] material = {draft.material()};
        RoadUiWidgets.renderMaterialMixPicker(
            ctx,
            "##" + id + "_road_material",
            PlotI18n.tr("plugin.road.material"),
            material[0],
            value -> {
                draft.setMaterial(value);
                notifyChanged(onChanged);
            },
            false
        );

        ImBoolean shoulderRef = new ImBoolean(draft.includeShoulder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder") + "##" + id + "_shoulder", shoulderRef)) {
            draft.setIncludeShoulder(shoulderRef.get());
            notifyChanged(onChanged);
        }
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
                draft.setShoulderWidth(shoulderWidthArr[0]);
                notifyChanged(onChanged);
            }
        }

        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.slope_batter_section"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.slope_batter_section_hint"));
        ImBoolean slopeRef = new ImBoolean(draft.includeSlopeBatter());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_slope_batter") + "##" + id + "_slope", slopeRef)) {
            draft.setIncludeSlopeBatter(slopeRef.get());
            notifyChanged(onChanged);
        }
        if (draft.includeSlopeBatter()) {
            float[] fillSlopeArr = {draft.fillSlopeRatio()};
            if (EngineeringSlopeInput.render(
                id + "_fill_slope_ratio",
                PlotI18n.tr("plugin.road.fill_slope_ratio_label"),
                fillSlopeArr,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                draft.setFillSlopeRatio(fillSlopeArr[0]);
                notifyChanged(onChanged);
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.fill_slope_ratio");

            float[] cutSlopeArr = {draft.cutSlopeRatio()};
            if (EngineeringSlopeInput.render(
                id + "_cut_slope_ratio",
                PlotI18n.tr("plugin.road.cut_slope_ratio_label"),
                cutSlopeArr,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                draft.setCutSlopeRatio(cutSlopeArr[0]);
                notifyChanged(onChanged);
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.cut_slope_ratio");

            final String[] fillSlopeMaterial = {draft.fillSlopeMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##" + id + "_fill_slope_material",
                PlotI18n.tr("plugin.road.fill_slope_material"),
                fillSlopeMaterial[0],
                value -> {
                    draft.setFillSlopeMaterial(value);
                    notifyChanged(onChanged);
                },
                false
            );
            final String[] cutSlopeMaterial = {draft.cutSlopeMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##" + id + "_cut_slope_material",
                PlotI18n.tr("plugin.road.cut_slope_material"),
                cutSlopeMaterial[0] != null ? cutSlopeMaterial[0] : "",
                value -> {
                    draft.setCutSlopeMaterial(value);
                    notifyChanged(onChanged);
                },
                false
            );
        }

        ImBoolean sidewalkRef = new ImBoolean(draft.includeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##" + id + "_sw", sidewalkRef)) {
            draft.setIncludeSidewalk(sidewalkRef.get());
            notifyChanged(onChanged);
        }
        if (draft.includeSidewalk()) {
            int[] sidewalkWidthArr = {draft.sidewalkWidth()};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidthArr[0]) + "##" + id + "_sw_w",
                sidewalkWidthArr,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d")) {
                draft.setSidewalkWidth(sidewalkWidthArr[0]);
                notifyChanged(onChanged);
            }
            final String[] sidewalkMaterial = {draft.sidewalkMaterial()};
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##" + id + "_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                sidewalkMaterial[0] != null
                    ? sidewalkMaterial[0]
                    : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL,
                value -> {
                    draft.setSidewalkMaterial(value);
                    notifyChanged(onChanged);
                },
                false
            );
        }

        ImBoolean bikeRef = new ImBoolean(draft.includeBikeLane());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_bike_lane") + "##" + id + "_bike", bikeRef)) {
            draft.setIncludeBikeLane(bikeRef.get());
            notifyChanged(onChanged);
        }
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
                draft.setBikeLaneWidth(bikeWidthArr[0]);
                notifyChanged(onChanged);
            }
        }

        ImBoolean medianRef = new ImBoolean(draft.includeMedian());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_median") + "##" + id + "_median", medianRef)) {
            draft.setIncludeMedian(medianRef.get());
            notifyChanged(onChanged);
        }
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
                draft.setMedianWidth(medianWidthArr[0]);
                notifyChanged(onChanged);
            }
        }

        ImBoolean drainRef = new ImBoolean(draft.includeDrainage());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage") + "##" + id + "_drain", drainRef)) {
            draft.setIncludeDrainage(drainRef.get());
            notifyChanged(onChanged);
        }

        ImBoolean laneDividersRef = new ImBoolean(draft.laneDividers());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.lane_dividers") + "##" + id + "_dividers", laneDividersRef)) {
            draft.setLaneDividers(laneDividersRef.get());
            notifyChanged(onChanged);
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
            draft.setCenterLineStyle(switch (centerLineIndex.get()) {
                case 1 -> CenterLineStyle.SINGLE_DASHED;
                case 2 -> CenterLineStyle.DOUBLE_SOLID;
                default -> CenterLineStyle.NONE;
            });
            notifyChanged(onChanged);
        }

        final String[] markingMaterial = {draft.markingMaterial()};
        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##" + id + "_marking_material",
            PlotI18n.tr("plugin.road.marking_material"),
            markingMaterial[0],
            value -> {
                draft.setMarkingMaterial(value);
                notifyChanged(onChanged);
            },
            false
        );

        int[] lightSpacing = {draft.streetlightSpacing()};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.streetlight_spacing") + "##" + id + "_lights",
            lightSpacing,
            RoadParameterLimits.STREETLIGHT_DISABLED,
            RoadParameterLimits.MAX_STREETLIGHT_SPACING,
            lightSpacing[0] <= 0 ? PlotI18n.tr("plugin.road.streetlight_off") : "%dm"
        )) {
            int value = lightSpacing[0];
            if (value > 0 && value < RoadParameterLimits.MIN_STREETLIGHT_SPACING) {
                value = RoadParameterLimits.MIN_STREETLIGHT_SPACING;
            }
            draft.setStreetlightSpacing(value);
            notifyChanged(onChanged);
        } else {
            draft.setStreetlightSpacing(lightSpacing[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.streetlight_spacing"));
        }

        if (options.showMaxSlope()) {
            float[] maxSlopeArr = {draft.maxSlope()};
            if (EngineeringSlopeInput.render(
                id + "_max_slope",
                PlotI18n.tr("plugin.road.max_slope_label"),
                maxSlopeArr,
                EngineeringSlopeInput.ValueKind.GRADE
            )) {
                draft.setMaxSlope(maxSlopeArr[0]);
                notifyChanged(onChanged);
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.max_slope");
        }
    }

    private static void notifyChanged(Runnable onChanged) {
        if (onChanged != null) {
            onChanged.run();
        }
    }
}
