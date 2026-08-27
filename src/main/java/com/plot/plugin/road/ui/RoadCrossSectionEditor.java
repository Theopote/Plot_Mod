package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.SlopeFormatUtils;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.CenterLineStyle;
import com.plot.plugin.road.model.section.Lane;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;

/**
 * 道路横断面编辑（行车道、路肩、人行道、标线等），编辑 Tab 与批量编辑共用。
 */
public final class RoadCrossSectionEditor {
    private RoadCrossSectionEditor() {
    }

    public static void renderPreview(Road road, RoadSystemConfig config) {
        if (road == null) {
            return;
        }
        ResolvedCrossSection resolved = road.getCrossSection().resolve(config);
        float maxSlope = road.getMaxSlope() != null ? road.getMaxSlope() : config.getMaxSlope();
        ImGui.text(PlotI18n.tr("plugin.road.cross_section_preview"));
        float width = ImGui.getContentRegionAvail().x;
        if (width < 40f) {
            return;
        }
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float height = 56f;
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromResolved(resolved, maxSlope),
            origin.x,
            origin.y,
            width,
            height);
        ImGui.dummy(width, height);
        ImGui.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.road.lane_count_summary", resolved.laneCount, resolved.carriagewayWidth));
    }

    public static void renderPresetButtons(RoadUiContext ctx, Road road, Runnable onChanged) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.apply_preset_to_road"));
        float gap = ImGui.getStyle().getItemSpacingX();
        float buttonWidth = (ImGui.getContentRegionAvail().x - gap) * 0.5f;
        int column = 0;
        for (RoadStyle style : config.getStyles()) {
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (ImGui.button(PlotI18n.tr("preset.road." + style.id) + "##road_style_" + style.id, buttonWidth, 0)) {
                ctx.networkManager().pushHistory();
                road.applyStyle(style);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
            column = (column + 1) % 2;
        }
    }

    public static void renderFields(RoadUiContext ctx, Road road, Runnable onHistory) {
        if (road == null) {
            return;
        }
        RoadSystemConfig config = ctx.networkManager().getConfig();

        if (ImGui.button(PlotI18n.tr("plugin.road.inherit_all_defaults") + "##inherit_all")) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.inheritAllDefaults();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.inherit_all_defaults"));
        }
        ImGui.spacing();

        int[] laneCount = {road.getCrossSection().getCarriageway().getEffectiveLaneCount()};
        boolean laneCountChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.road.lane_count", laneCount[0]) + "##lanes", laneCount,
            RoadParameterLimits.MIN_LANE_COUNT, RoadParameterLimits.MAX_LANE_COUNT, "%d");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        if (laneCountChanged) {
            road.setLaneCount(laneCount[0]);
        }

        int[] width = {road.getWidth() != null ? road.getWidth() : config.getRoadWidth()};
        boolean widthInherited = road.getWidth() == null;
        boolean widthChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.road.road_width", width[0]) + "##road_width", width,
            RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH, RoadParameterLimits.MAX_CARRIAGEWAY_WIDTH, "%d");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        if (widthChanged) {
            road.setWidth(width[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.road_width"));
        }
        RoadUiWidgets.renderOverrideFooter(
            widthInherited,
            PlotI18n.tr("plugin.road.inherit_default_int", config.getRoadWidth()),
            "road_width",
            withHistory(onHistory, () -> road.setWidth(null)));

        if (laneCount[0] > 1) {
            road.getCrossSection().getCarriageway().syncLaneCount(laneCount[0]);
            List<Integer> resolved = road.getCrossSection().getCarriageway().resolveLaneWidths(width[0]);
            for (int i = 0; i < laneCount[0]; i++) {
                List<Lane> lanes = road.getCrossSection().getCarriageway().getLanes();
                Lane lane = i < lanes.size() ? lanes.get(i) : new Lane();
                int[] laneWidth = {
                    lane.getWidth() != null ? lane.getWidth() : resolved.get(i)
                };
                int perLaneMax = RoadParameterLimits.maxPerLaneWidth(width[0], laneCount[0]);
                boolean laneWidthChanged = ImGui.sliderInt(
                    PlotI18n.tr("plugin.road.lane_width_label", i + 1, laneWidth[0]) + "##lane_" + i,
                    laneWidth, RoadParameterLimits.MIN_STRIP_WIDTH, perLaneMax, "%d");
                if (ImGui.isItemActivated() && onHistory != null) {
                    onHistory.run();
                }
                if (laneWidthChanged) {
                    road.getCrossSection().getCarriageway().setLaneWidthAt(i, laneWidth[0]);
                }
            }
        }

        RoadUiWidgets.renderMaterialMixPicker(
            ctx,
            "##road_material",
            PlotI18n.tr("plugin.road.material"),
            road.getMaterial() != null ? road.getMaterial() : config.getSelectedMaterial(),
            road::setMaterial,
            true
        );

        ImBoolean shoulderRef = new ImBoolean(road.getEffectiveIncludeShoulder(config));
        boolean shoulderInherited = road.getIncludeShoulder() == null;
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder") + "##shoulder", shoulderRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeShoulder(shoulderRef.get());
        }
        RoadUiWidgets.renderOverrideFooter(
            shoulderInherited,
            PlotI18n.tr(config.isIncludeShoulder()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            "road_shoulder",
            withHistory(onHistory, () -> road.setIncludeShoulder(null)));
        if (road.getEffectiveIncludeShoulder(config)) {
            int rawShoulder = road.getShoulderWidth() != null
                ? road.getShoulderWidth()
                : config.getShoulderWidth();
            int[] shoulderWidth = {
                Math.max(RoadParameterLimits.MIN_STRIP_WIDTH, rawShoulder)
            };
            boolean shoulderWidthInherited = road.getShoulderWidth() == null;
            boolean shoulderChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.road.shoulder_width", shoulderWidth[0]) + "##shoulder_w",
                shoulderWidth,
                RoadParameterLimits.MIN_STRIP_WIDTH,
                RoadParameterLimits.MAX_STRIP_WIDTH,
                "%d");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            if (shoulderChanged) {
                road.setShoulderWidth(shoulderWidth[0]);
            }
            RoadUiWidgets.renderOverrideFooter(
                shoulderWidthInherited,
                PlotI18n.tr("plugin.road.inherit_default_int", config.getShoulderWidth()),
                "road_shoulder_width",
                withHistory(onHistory, () -> road.setShoulderWidth(null)));
        }

        renderSlopeBatterFields(ctx, road, config, onHistory);

        ImBoolean bikeLaneRef = new ImBoolean(road.getEffectiveIncludeBikeLane(config));
        boolean bikeLaneInherited = road.getIncludeBikeLane() == null;
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_bike_lane") + "##bike_lane", bikeLaneRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeBikeLane(bikeLaneRef.get());
        }
        RoadUiWidgets.renderOverrideFooter(
            bikeLaneInherited,
            PlotI18n.tr("plugin.road.inherit_default_disabled"),
            "road_bike_lane",
            withHistory(onHistory, () -> road.setIncludeBikeLane(null)));
        if (road.getEffectiveIncludeBikeLane(config)) {
            int[] bikeWidth = {
                road.getBikeLaneWidth() != null ? road.getBikeLaneWidth() : 1
            };
            boolean bikeChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.road.bike_lane_width", bikeWidth[0]) + "##bike_w",
                bikeWidth, RoadParameterLimits.MIN_STRIP_WIDTH, RoadParameterLimits.MAX_STRIP_WIDTH, "%d");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            if (bikeChanged) {
                road.setBikeLaneWidth(bikeWidth[0]);
            }
        }

        ImBoolean sidewalkRef = new ImBoolean(road.getEffectiveIncludeSidewalk(config));
        boolean sidewalkInherited = road.getIncludeSidewalk() == null;
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##sidewalk", sidewalkRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeSidewalk(sidewalkRef.get());
        }
        RoadUiWidgets.renderOverrideFooter(
            sidewalkInherited,
            PlotI18n.tr(config.isIncludeSidewalk()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            "road_sidewalk",
            withHistory(onHistory, () -> road.setIncludeSidewalk(null)));
        if (road.getEffectiveIncludeSidewalk(config)) {
            int[] sidewalkWidth = {
                road.getSidewalkWidth() != null ? road.getSidewalkWidth() : config.getSidewalkWidth()
            };
            boolean sidewalkWidthInherited = road.getSidewalkWidth() == null;
            boolean swChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##sw_w",
                sidewalkWidth, RoadParameterLimits.MIN_STRIP_WIDTH, RoadParameterLimits.MAX_STRIP_WIDTH, "%d");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            if (swChanged) {
                road.setSidewalkWidth(sidewalkWidth[0]);
            }
            RoadUiWidgets.renderOverrideFooter(
                sidewalkWidthInherited,
                PlotI18n.tr("plugin.road.inherit_default_int", config.getSidewalkWidth()),
                "road_sidewalk_width",
                withHistory(onHistory, () -> road.setSidewalkWidth(null)));

            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                road.getSidewalkMaterial() != null
                    ? road.getSidewalkMaterial()
                    : config.getSelectedSidewalkMaterial(),
                material -> {
                    if (onHistory != null) {
                        onHistory.run();
                    }
                    road.setSidewalkMaterial(material);
                },
                true
            );
        }

        ImBoolean drainRef = new ImBoolean(road.getEffectiveIncludeDrainage(config));
        boolean drainInherited = road.getIncludeDrainage() == null;
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage") + "##drain", drainRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeDrainage(drainRef.get());
        }
        RoadUiWidgets.renderOverrideFooter(
            drainInherited,
            PlotI18n.tr(config.isIncludeDrainage()
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            "road_drain",
            withHistory(onHistory, () -> road.setIncludeDrainage(null)));

        ImBoolean medianRef = new ImBoolean(road.getIncludeMedian() != null && road.getIncludeMedian());
        boolean medianInherited = road.getIncludeMedian() == null;
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_median") + "##median", medianRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeMedian(medianRef.get());
        }
        RoadUiWidgets.renderOverrideFooter(
            medianInherited,
            PlotI18n.tr("plugin.road.inherit_default_disabled"),
            "road_median",
            withHistory(onHistory, () -> road.setIncludeMedian(null)));
        if (medianRef.get()) {
            int[] medianWidth = {road.getMedianWidth() != null ? road.getMedianWidth() : 1};
            boolean medianWidthInherited = road.getMedianWidth() == null;
            boolean medianChanged = ImGui.sliderInt(
                PlotI18n.tr("plugin.road.median_width", medianWidth[0]) + "##median_w",
                medianWidth, RoadParameterLimits.MIN_STRIP_WIDTH, RoadParameterLimits.MAX_STRIP_WIDTH, "%d");
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
            if (medianChanged) {
                road.setMedianWidth(medianWidth[0]);
            }
            RoadUiWidgets.renderOverrideFooter(
                medianWidthInherited,
                PlotI18n.tr("plugin.road.inherit_default_int", 1),
                "road_median_width",
                withHistory(onHistory, () -> road.setMedianWidth(null)));
        }

        ImBoolean laneDividersRef = new ImBoolean(
            road.getLaneDividers() != null
                ? road.getLaneDividers()
                : road.getCrossSection().getCarriageway().getEffectiveLaneCount() > 1);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.lane_dividers") + "##dividers", laneDividersRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setLaneDividers(laneDividersRef.get());
        }

        renderCenterLineStylePicker(road, onHistory);

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##marking_material",
            PlotI18n.tr("plugin.road.marking_material"),
            road.getMarkingMaterial() != null
                ? road.getMarkingMaterial()
                : ResolvedCrossSection.DEFAULT_MARKING_MATERIAL,
            material -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setMarkingMaterial(material);
            },
            true
        );

        float[] maxSlope = {road.getMaxSlope() != null ? road.getMaxSlope() : config.getMaxSlope()};
        boolean maxSlopeInherited = road.getMaxSlope() == null;
        if (EngineeringSlopeInput.render(
            "road_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setMaxSlope(maxSlope[0]);
        }
        RoadUiWidgets.renderOverrideFooter(
            maxSlopeInherited,
            PlotI18n.tr(
                "plugin.road.inherit_default_percent",
                SlopeFormatUtils.formatPercent(config.getMaxSlope())),
            "road_max_slope",
            withHistory(onHistory, () -> road.setMaxSlope(null)));

        // 0 = 关闭；开启时最小间距与 normalize 一致，避免设 1–7 被静默抬到 8
        int currentLights = road.getStreetlightSpacing() != null ? road.getStreetlightSpacing() : 0;
        boolean streetlightInherited = road.getStreetlightSpacing() == null;
        int[] lightSpacing = {currentLights};
        boolean lightsChanged = ImGui.sliderInt(
            PlotI18n.tr("plugin.road.streetlight_spacing") + "##lights",
            lightSpacing,
            RoadParameterLimits.STREETLIGHT_DISABLED,
            RoadParameterLimits.MAX_STREETLIGHT_SPACING,
            lightSpacing[0] <= 0 ? PlotI18n.tr("plugin.road.streetlight_off") : "%dm");
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
        if (lightsChanged) {
            int value = lightSpacing[0];
            if (value > 0 && value < RoadParameterLimits.MIN_STREETLIGHT_SPACING) {
                value = RoadParameterLimits.MIN_STREETLIGHT_SPACING;
            }
            road.setStreetlightSpacing(value);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("hint.plot.road.streetlight_spacing"));
        }
        RoadUiWidgets.renderOverrideFooter(
            streetlightInherited,
            PlotI18n.tr("plugin.road.inherit_default_disabled"),
            "road_streetlight",
            withHistory(onHistory, () -> road.setStreetlightSpacing(null)));
    }

    private static Runnable withHistory(Runnable onHistory, Runnable action) {
        return () -> {
            if (onHistory != null) {
                onHistory.run();
            }
            action.run();
        };
    }

    private static void renderSlopeBatterFields(
            RoadUiContext ctx,
            Road road,
            RoadSystemConfig config,
            Runnable onHistory) {
        ImGui.spacing();
        ImGui.text(PlotI18n.tr("plugin.road.slope_batter_section"));
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.slope_batter_section_hint"));

        Boolean slopeEnabledValue = road.getIncludeSlopeBatter();
        boolean slopeEnabledInherited = slopeEnabledValue == null;
        boolean slopeEnabled = slopeEnabledValue != null
            ? slopeEnabledValue
            : road.getEffectiveIncludeSlopeBatter(config);
        ImBoolean slopeRef = new ImBoolean(slopeEnabled);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_slope_batter") + "##road_slope", slopeRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeSlopeBatter(slopeRef.get());
        }
        boolean configSlopeEnabled = config.getFillSlopeRatio() > 0f || config.getCutSlopeRatio() > 0f;
        RoadUiWidgets.renderOverrideFooter(
            slopeEnabledInherited,
            PlotI18n.tr(configSlopeEnabled
                ? "plugin.road.inherit_default_enabled"
                : "plugin.road.inherit_default_disabled"),
            "road_slope_enabled",
            withHistory(onHistory, () -> road.setIncludeSlopeBatter(null)));

        if (!slopeRef.get()) {
            return;
        }

        float[] fillSlopeRatio = {
            road.getFillSlopeRatio() != null
                ? road.getFillSlopeRatio()
                : road.getEffectiveFillSlopeRatio(config)
        };
        boolean fillSlopeInherited = road.getFillSlopeRatio() == null;
        if (EngineeringSlopeInput.render(
            "road_fill_slope_ratio",
            PlotI18n.tr("plugin.road.fill_slope_ratio_label"),
            fillSlopeRatio,
            EngineeringSlopeInput.ValueKind.BATTER
        )) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setFillSlopeRatio(fillSlopeRatio[0]);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.fill_slope_ratio");
        RoadUiWidgets.renderOverrideFooter(
            fillSlopeInherited,
            PlotI18n.tr(
                "plugin.road.inherit_default_batter",
                SlopeFormatUtils.formatRatio(config.getFillSlopeRatio())),
            "road_fill_slope",
            withHistory(onHistory, () -> road.setFillSlopeRatio(null)));

        float[] cutSlopeRatio = {
            road.getCutSlopeRatio() != null
                ? road.getCutSlopeRatio()
                : road.getEffectiveCutSlopeRatio(config)
        };
        boolean cutSlopeInherited = road.getCutSlopeRatio() == null;
        if (EngineeringSlopeInput.render(
            "road_cut_slope_ratio",
            PlotI18n.tr("plugin.road.cut_slope_ratio_label"),
            cutSlopeRatio,
            EngineeringSlopeInput.ValueKind.BATTER
        )) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setCutSlopeRatio(cutSlopeRatio[0]);
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.cut_slope_ratio");
        RoadUiWidgets.renderOverrideFooter(
            cutSlopeInherited,
            PlotI18n.tr(
                "plugin.road.inherit_default_batter",
                SlopeFormatUtils.formatRatio(config.getCutSlopeRatio())),
            "road_cut_slope",
            withHistory(onHistory, () -> road.setCutSlopeRatio(null)));

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##road_fill_slope_material",
            PlotI18n.tr("plugin.road.fill_slope_material"),
            road.getFillSlopeMaterial() != null
                ? road.getFillSlopeMaterial()
                : road.getEffectiveFillSlopeMaterial(config),
            material -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setFillSlopeMaterial(material);
            },
            true
        );

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##road_cut_slope_material",
            PlotI18n.tr("plugin.road.cut_slope_material"),
            road.getCutSlopeMaterial() != null
                ? road.getCutSlopeMaterial()
                : road.getEffectiveCutSlopeMaterial(config),
            material -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setCutSlopeMaterial(material);
            },
            true
        );
    }

    private static void renderCenterLineStylePicker(Road road, Runnable onHistory) {
        CenterLineStyle current = road.getCenterLineStyle() != null
            ? road.getCenterLineStyle()
            : CenterLineStyle.NONE;
        String[] labels = {
            PlotI18n.tr("plugin.road.center_line.none"),
            PlotI18n.tr("plugin.road.center_line.single_dashed"),
            PlotI18n.tr("plugin.road.center_line.double_solid")
        };
        ImInt styleIndex = new ImInt(switch (current) {
            case SINGLE_DASHED -> 1;
            case DOUBLE_SOLID -> 2;
            default -> 0;
        });
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        if (ImGui.combo(PlotI18n.tr("plugin.road.center_line_style") + "##center_line", styleIndex, labels)) {
            CenterLineStyle selected = switch (styleIndex.get()) {
                case 1 -> CenterLineStyle.SINGLE_DASHED;
                case 2 -> CenterLineStyle.DOUBLE_SOLID;
                default -> CenterLineStyle.NONE;
            };
            if (onHistory != null) {
                onHistory.run();
            }
            road.setCenterLineStyle(selected);
        }
    }
}
