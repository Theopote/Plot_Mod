package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.Road;
import com.plot.plugin.road.model.section.ResolvedCrossSection;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;

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
            (int) 0xFF808080FFL,
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
        for (RoadSystemConfig.RoadPreset preset : config.getPresets()) {
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (ImGui.button(PlotI18n.tr("preset.road." + preset.id) + "##road_preset_" + preset.id, buttonWidth, 0)) {
                ctx.networkManager().pushHistory();
                road.applyPreset(preset);
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
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.lane_count", laneCount[0]) + "##lanes", laneCount, 1, 4, "%d")) {
            road.setLaneCount(laneCount[0]);
        }
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }

        int[] width = {road.getWidth() != null ? road.getWidth() : config.getRoadWidth()};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", width[0]) + "##road_width", width, 3, 20, "%d")) {
            road.setWidth(width[0]);
        }
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##road_material",
            PlotI18n.tr("plugin.road.material"),
            road.getMaterial() != null ? road.getMaterial() : config.getSelectedMaterial(),
            material -> {
                if (onHistory != null) {
                    onHistory.run();
                }
                road.setMaterial(material);
            },
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
                shoulderWidth, 0, 3, "%d")) {
                road.setShoulderWidth(shoulderWidth[0]);
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
                sidewalkWidth, 1, 3, "%d")) {
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
                medianWidth, 1, 3, "%d")) {
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
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.streetlight_spacing") + "##lights", lightSpacing, 0, 50, "%dm")) {
            road.setStreetlightSpacing(lightSpacing[0] > 0 ? lightSpacing[0] : null);
        }
        if (ImGui.isItemActivated() && onHistory != null) {
            onHistory.run();
        }
    }
}
