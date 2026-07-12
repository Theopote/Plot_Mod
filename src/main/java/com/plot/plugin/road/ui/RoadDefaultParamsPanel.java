package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.RoadNode;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;

/**
 * 认领道路时的默认参数与预设配置。
 */
public final class RoadDefaultParamsPanel {
    private final RoadUiContext ctx;

    public RoadDefaultParamsPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        renderPresetSelector();
        RoadCrossSectionPreviewRenderer.render(config);
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.text(PlotI18n.tr("plugin.road.basic_params"));
        int[] roadWidth = {config.getRoadWidth()};
        if (ImGui.sliderInt("##road_width", roadWidth, 3, 20, PlotI18n.tr("plugin.road.road_width", roadWidth[0]))) {
            config.setRoadWidth(roadWidth[0]);
            markCustom();
        }

        float[] maxSlope = {config.getMaxSlope()};
        if (EngineeringSlopeInput.render(
            "default_max_slope",
            PlotI18n.tr("plugin.road.max_slope_label"),
            maxSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            config.setMaxSlope(maxSlope[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.max_slope");

        float[] maxContinuousLength = {(float) config.getMaxContinuousSlopeLength()};
        if (ImGui.sliderFloat(
            "##max_continuous_slope_length",
            maxContinuousLength,
            5.0f,
            100.0f,
            PlotI18n.tr("plugin.road.max_continuous_slope_length", maxContinuousLength[0])
        )) {
            config.setMaxContinuousSlopeLength(maxContinuousLength[0]);
            markCustom();
        }

        float[] relaxedLength = {(float) config.getRelaxedSlopeLength()};
        if (ImGui.sliderFloat(
            "##relaxed_slope_length",
            relaxedLength,
            1.0f,
            30.0f,
            PlotI18n.tr("plugin.road.relaxed_slope_length", relaxedLength[0])
        )) {
            config.setRelaxedSlopeLength(relaxedLength[0]);
            markCustom();
        }

        float[] relaxedSlope = {config.getRelaxedSlopePercent()};
        if (EngineeringSlopeInput.render(
            "default_relaxed_slope",
            PlotI18n.tr("plugin.road.relaxed_slope_percent_label"),
            relaxedSlope,
            EngineeringSlopeInput.ValueKind.GRADE
        )) {
            config.setRelaxedSlopePercent(relaxedSlope[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.relaxed_slope_percent");

        renderDefaultJunctionSettings();

        ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_sidewalk"), ctx.adoptIncludeSidewalkRef())) {
            config.setIncludeSidewalk(ctx.adoptIncludeSidewalkRef().get());
            markCustom();
        }

        if (config.isIncludeSidewalk()) {
            int[] sidewalkWidth = {config.getSidewalkWidth()};
            if (ImGui.sliderInt(
                "##default_sidewalk_width",
                sidewalkWidth,
                1,
                3,
                PlotI18n.tr("plugin.road.sidewalk_width", sidewalkWidth[0])
            )) {
                config.setSidewalkWidth(sidewalkWidth[0]);
                markCustom();
            }
        }

        RoadUiWidgets.renderBlockMaterialPicker(
            ctx,
            "##default_road_material",
            PlotI18n.tr("plugin.road.material"),
            config.getSelectedMaterial(),
            blockId -> {
                config.setSelectedMaterial(blockId);
                markCustom();
            },
            false
        );

        if (config.isIncludeSidewalk()) {
            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##default_sidewalk_material",
                PlotI18n.tr("plugin.road.sidewalk_material"),
                config.getSelectedSidewalkMaterial(),
                blockId -> {
                    config.setSelectedSidewalkMaterial(blockId);
                    markCustom();
                },
                false
            );
        }

        renderAdvancedEngineeringSettings();
    }

    private void renderDefaultJunctionSettings() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        float[] defaultRadius = {config.getDefaultCornerRadius()};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.default_corner_radius", defaultRadius[0]),
            defaultRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            "%.1f m"
        )) {
            config.setDefaultCornerRadius(defaultRadius[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.default_corner_radius");
    }

    private void markCustom() {
        ctx.networkManager().getConfig().markCustom();
    }

    private void renderPresetSelector() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.text(PlotI18n.tr("plugin.road.road_presets"));
        String selectedId = config.getSelectedPreset();
        boolean customSelected = selectedId == null || selectedId.isBlank();

        float gap = ImGui.getStyle().getItemSpacingX();
        float cardWidth = (ImGui.getContentRegionAvail().x - gap) * 0.5f;
        float cardHeight = 54f;
        int column = 0;
        for (RoadSystemConfig.RoadPreset preset : config.getPresets()) {
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (renderPresetCard(preset, cardWidth, cardHeight, preset.id.equals(selectedId))) {
                config.applyPreset(preset);
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
            }
            column = (column + 1) % 2;
        }

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.road.preset_custom") + "##road_preset_custom")) {
            config.markCustom();
        }
        if (customSelected) {
            ImGui.sameLine();
            ImGui.textColored((int) 0xFF4DA6FFFFL, "●");
        } else if (!selectedId.isBlank()) {
            ImGui.sameLine();
            ImGui.textColored((int) 0xFF808080FFL,
                PlotI18n.tr("preset.road." + selectedId));
        }
        ImGui.spacing();
    }

    private boolean renderPresetCard(
            RoadSystemConfig.RoadPreset preset,
            float width,
            float height,
            boolean selected) {
        ImGui.pushID(preset.id);
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Border, (int) 0xFF4DA6FFFFL);
        }
        ImGui.beginChild("##preset_card", width, height, true);
        ImVec2 pos = ImGui.getCursorScreenPos();
        float labelH = ImGui.getTextLineHeightWithSpacing();
        float previewH = Math.max(18f, height - labelH - 4f);
        ImDrawList drawList = ImGui.getWindowDrawList();
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromPreset(preset),
            pos.x + 3f,
            pos.y + 2f,
            width - 6f,
            previewH);
        ImGui.dummy(width - 6f, previewH);
        ImGui.text(PlotI18n.tr("preset.road." + preset.id));
        boolean clicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(0);
        ImGui.endChild();
        if (selected) {
            ImGui.popStyleColor();
        }
        ImGui.popID();
        return clicked;
    }

    private void renderAdvancedEngineeringSettings() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        if (!ImGui.collapsingHeader(PlotI18n.tr("plugin.road.advanced_settings"))) {
            return;
        }

        int[] bridgeThreshold = {config.getBridgeThreshold()};
        if (ImGui.sliderInt(
            "##road_bridge_threshold",
            bridgeThreshold,
            1,
            20,
            PlotI18n.tr("plugin.road.bridge_threshold", bridgeThreshold[0])
        )) {
            config.setBridgeThreshold(bridgeThreshold[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.bridge_threshold");

        int[] tunnelThreshold = {config.getTunnelThreshold()};
        if (ImGui.sliderInt(
            "##road_tunnel_threshold",
            tunnelThreshold,
            1,
            30,
            PlotI18n.tr("plugin.road.tunnel_threshold", tunnelThreshold[0])
        )) {
            config.setTunnelThreshold(tunnelThreshold[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.tunnel_threshold");

        ImBoolean shoulderRef = new ImBoolean(config.isIncludeShoulder());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_shoulder"), shoulderRef)) {
            config.setIncludeShoulder(shoulderRef.get());
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.include_shoulder");

        if (config.isIncludeShoulder()) {
            int[] shoulderWidth = {config.getShoulderWidth()};
            if (ImGui.sliderInt(
                "##road_shoulder_width",
                shoulderWidth,
                0,
                3,
                PlotI18n.tr("plugin.road.shoulder_width", shoulderWidth[0])
            )) {
                config.setShoulderWidth(shoulderWidth[0]);
                markCustom();
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.shoulder_width");

            float[] fillSlopeRatio = {config.getFillSlopeRatio()};
            if (EngineeringSlopeInput.render(
                "fill_slope_ratio",
                PlotI18n.tr("plugin.road.fill_slope_ratio_label"),
                fillSlopeRatio,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                config.setFillSlopeRatio(fillSlopeRatio[0]);
                markCustom();
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.fill_slope_ratio");

            float[] cutSlopeRatio = {config.getCutSlopeRatio()};
            if (EngineeringSlopeInput.render(
                "cut_slope_ratio",
                PlotI18n.tr("plugin.road.cut_slope_ratio_label"),
                cutSlopeRatio,
                EngineeringSlopeInput.ValueKind.BATTER
            )) {
                config.setCutSlopeRatio(cutSlopeRatio[0]);
                markCustom();
            }
            RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.cut_slope_ratio");

            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##fill_slope_material",
                PlotI18n.tr("plugin.road.fill_slope_material"),
                config.getFillSlopeMaterial(),
                blockId -> {
                    config.setFillSlopeMaterial(blockId);
                    markCustom();
                },
                false
            );

            RoadUiWidgets.renderBlockMaterialPicker(
                ctx,
                "##cut_slope_material",
                PlotI18n.tr("plugin.road.cut_slope_material"),
                config.getCutSlopeMaterial().isBlank()
                    ? config.getFillSlopeMaterial()
                    : config.getCutSlopeMaterial(),
                blockId -> {
                    config.setCutSlopeMaterial(blockId);
                    markCustom();
                },
                false
            );
        }

        ImBoolean drainageRef = new ImBoolean(config.isIncludeDrainage());
        if (ImGui.checkbox(PlotI18n.tr("plugin.road.include_drainage"), drainageRef)) {
            config.setIncludeDrainage(drainageRef.get());
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.include_drainage");
    }
}
