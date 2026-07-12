package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.manager.RoadNetworkManager;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;

/**
 * 批量横断面编辑字段（扩展 Phase C 路肩/车道/排水）。
 */
public final class RoadBatchCrossSectionEditor {
    private RoadBatchCrossSectionEditor() {
    }

    public static void renderDraftFields(RoadUiContext ctx, RoadNetworkManager.BatchEditDefaults draft) {
        int width = draft.width();
        int laneCount = draft.laneCount();
        final String[] material = {draft.material()};
        boolean includeShoulder = draft.includeShoulder();
        int shoulderWidth = draft.shoulderWidth();
        boolean includeSidewalk = draft.includeSidewalk();
        int sidewalkWidth = draft.sidewalkWidth();
        final String[] sidewalkMaterial = {draft.sidewalkMaterial()};
        boolean includeDrainage = draft.includeDrainage();
        boolean laneDividers = draft.laneDividers();
        float maxSlope = draft.maxSlope();

        int[] laneCountArr = {laneCount};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.lane_count", laneCountArr[0]) + "##batch_lanes", laneCountArr, 1, 4, "%d")) {
            laneCount = laneCountArr[0];
        }

        int[] widthArr = {width};
        if (ImGui.sliderInt(PlotI18n.tr("plugin.road.road_width", widthArr[0]) + "##batch_width", widthArr, 3, 20, "%d")) {
            width = widthArr[0];
        }

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##batch_road_material",
            PlotI18n.tr("plugin.road.material"),
            material[0],
            value -> material[0] = value,
            false
        );

        ImBoolean shoulderRef = new ImBoolean(includeShoulder);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder") + "##batch_shoulder", shoulderRef)) {
            includeShoulder = shoulderRef.get();
        }
        if (includeShoulder) {
            int[] shoulderWidthArr = {shoulderWidth};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.shoulder_width", shoulderWidthArr[0]) + "##batch_shoulder_w",
                shoulderWidthArr, 0, 3, "%d")) {
                shoulderWidth = shoulderWidthArr[0];
            }
        }

        ctx.batchIncludeSidewalkRef().set(includeSidewalk);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk") + "##batch_sw", ctx.batchIncludeSidewalkRef())) {
            includeSidewalk = ctx.batchIncludeSidewalkRef().get();
        }

        if (includeSidewalk) {
            int[] sidewalkWidthArr = {sidewalkWidth};
            if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidthArr[0]) + "##batch_sw_w",
                sidewalkWidthArr, 1, 3, "%d")) {
                sidewalkWidth = sidewalkWidthArr[0];
            }

            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##batch_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                sidewalkMaterial[0],
                value -> sidewalkMaterial[0] = value,
                false
            );
        }

        ImBoolean drainRef = new ImBoolean(includeDrainage);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage") + "##batch_drain", drainRef)) {
            includeDrainage = drainRef.get();
        }

        ImBoolean laneDividersRef = new ImBoolean(laneDividers);
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.lane_dividers") + "##batch_dividers", laneDividersRef)) {
            laneDividers = laneDividersRef.get();
        }

        float[] maxSlopeArr = {maxSlope};
        if (com.plot.ui.component.EngineeringSlopeInput.render(
            "batch_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlopeArr,
            com.plot.ui.component.EngineeringSlopeInput.ValueKind.GRADE
        )) {
            maxSlope = maxSlopeArr[0];
        }

        if (ImGui.button(PlotI18n.tr("plugin.road.apply_batch"), ImGui.getContentRegionAvailX(), 0)) {
            ctx.networkManager().applyBatchEdit(new RoadNetworkManager.BatchEditDefaults(
                width,
                laneCount,
                material[0],
                includeShoulder,
                shoulderWidth,
                includeSidewalk,
                sidewalkWidth,
                sidewalkMaterial[0],
                includeDrainage,
                laneDividers,
                maxSlope));
        }
    }
}
