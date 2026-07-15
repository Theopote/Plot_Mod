package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadParameterLimits;
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

        int[] laneCount = {road.getCrossSection().getCarriageway().getEffectiveLaneCount()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.lane_count", laneCount[0]) + "##lanes", laneCount,
            RoadParameterLimits.MIN_LANE_COUNT, RoadParameterLimits.MAX_LANE_COUNT, "%d")) {
            road.setLaneCount(laneCount[0]);
        }
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }

        int[] width = {road.getWidth() != null ? road.getWidth() : config.getRoadWidth()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", width[0]) + "##road_width", width,
            RoadParameterLimits.MIN_CARRIAGEWAY_WIDTH, RoadParameterLimits.MAX_CARRIAGEWAY_WIDTH, "%d")) {
            road.setWidth(width[0]);
        }
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }

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
                if (ImGui.sliderInt(
                    PlotI18n.tr("plugin.road.lane_width_label", i + 1, laneWidth[0]) + "##lane_" + i,
                    laneWidth, RoadParameterLimits.MIN_STRIP_WIDTH, perLaneMax, "%d")) {
                    road.getCrossSection().getCarriageway().setLaneWidthAt(i, laneWidth[0]);
                }
                if (ImGui.isItemActivated() && onHistory != null) {
                    onHistory.run();
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
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder") + "##shoulder", shoulderRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeShoulder(shoulderRef.get());
        }
        if (road.getEffectiveIncludeShoulder(config)) {
            int[] shoulderWidth = {
                road.getShoulderWidth() != null ? road.getShoulderWidth() : config.getShoulderWidth()
            };
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.shoulder_width", shoulderWidth[0]) + "##shoulder_w",
                shoulderWidth, 0, RoadParameterLimits.MAX_STRIP_WIDTH, "%d")) {
                road.setShoulderWidth(shoulderWidth[0]);
            }
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
        }

        ImBoolean bikeLaneRef = new ImBoolean(road.getEffectiveIncludeBikeLane(config));
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_bike_lane") + "##bike_lane", bikeLaneRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeBikeLane(bikeLaneRef.get());
        }
        if (road.getEffectiveIncludeBikeLane(config)) {
            int[] bikeWidth = {
                road.getBikeLaneWidth() != null ? road.getBikeLaneWidth() : 1
            };
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.bike_lane_width", bikeWidth[0]) + "##bike_w",
                bikeWidth, RoadParameterLimits.MIN_STRIP_WIDTH, RoadParameterLimits.MAX_STRIP_WIDTH, "%d")) {
                road.setBikeLaneWidth(bikeWidth[0]);
            }
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
        }

        ImBoolean sidewalkRef = new ImBoolean(road.getEffectiveIncludeSidewalk(config));
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##sidewalk", sidewalkRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeSidewalk(sidewalkRef.get());
        }
        if (road.getEffectiveIncludeSidewalk(config)) {
            int[] sidewalkWidth = {
                road.getSidewalkWidth() != null ? road.getSidewalkWidth() : config.getSidewalkWidth()
            };
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0]) + "##sw_w",
                sidewalkWidth, RoadParameterLimits.MIN_STRIP_WIDTH, RoadParameterLimits.MAX_STRIP_WIDTH, "%d")) {
                road.setSidewalkWidth(sidewalkWidth[0]);
            }
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }

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
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage") + "##drain", drainRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeDrainage(drainRef.get());
        }

        ImBoolean medianRef = new ImBoolean(road.getIncludeMedian() != null && road.getIncludeMedian());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_median") + "##median", medianRef)) {
            if (onHistory != null) {
                onHistory.run();
            }
            road.setIncludeMedian(medianRef.get());
        }
        if (medianRef.get()) {
            int[] medianWidth = {road.getMedianWidth() != null ? road.getMedianWidth() : 1};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.median_width", medianWidth[0]) + "##median_w",
                medianWidth, RoadParameterLimits.MIN_STRIP_WIDTH, RoadParameterLimits.MAX_STRIP_WIDTH, "%d")) {
                road.setMedianWidth(medianWidth[0]);
            }
            if (ImGui.isItemActivated() && onHistory != null) {
                onHistory.run();
            }
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
        if (EngineeringSlopeInput.render(
            "road_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            road.setMaxSlope(maxSlope[0]);
            if (onHistory != null) {
                onHistory.run();
            }
        }

        int[] lightSpacing = {road.getStreetlightSpacing() != null ? road.getStreetlightSpacing() : 0};
        if (ImGui.sliderInt(
            PlotI18n.tr("plugin.road.streetlight_spacing") + "##lights",
            lightSpacing,
            RoadParameterLimits.STREETLIGHT_DISABLED,
            RoadParameterLimits.MAX_STREETLIGHT_SPACING,
            "%dm")) {
            road.setStreetlightSpacing(lightSpacing[0]);
            lightSpacing[0] = road.getStreetlightSpacing() != null ? road.getStreetlightSpacing() : 0;
        }
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
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
            road.setCenterLineStyle(selected);
            if (onHistory != null) {
                onHistory.run();
            }
        }
    }
}
