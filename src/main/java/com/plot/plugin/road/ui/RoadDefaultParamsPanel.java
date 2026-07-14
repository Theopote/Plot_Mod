package com.plot.plugin.road.ui;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
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

        float gap = PRESET_CARD_PADDING_X;
        float cardWidth = (ImGui.getContentRegionAvail().x - gap) * 0.5f;
        float cardHeight = presetCardHeight();
        int column = 0;
        int index = 0;
        for (RoadStyle style : config.getStyles()) {
            if (index > 0 && index % 2 == 0) {
                ImGui.dummy(0f, gap);
            }
            if (column > 0) {
                ImGui.sameLine(0, gap);
            }
            if (renderPresetCard(style, cardWidth, cardHeight, style.id.equals(selectedId))) {
                config.applyStyle(style);
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
            }
            column = (column + 1) % 2;
            index++;
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

    private static final float PRESET_CARD_PADDING_X = 4f;
    private static final float PRESET_CARD_PADDING_TOP = 8f;
    private static final float PRESET_CARD_PADDING_BOTTOM = 2f;
    private static final float PRESET_PREVIEW_GAP = 1f;
    /** 图示区高度；与 {@link RoadCrossSectionPreviewRenderer.MiniRenderOptions#presetCard()} 比例配套。 */
    private static final float PRESET_PREVIEW_HEIGHT = 28f;

    private static float presetCardHeight() {
        return PRESET_CARD_PADDING_TOP
            + PRESET_PREVIEW_HEIGHT
            + PRESET_PREVIEW_GAP
            + ImGui.getTextLineHeight()
            + PRESET_CARD_PADDING_BOTTOM;
    }

    private boolean renderPresetCard(
            RoadStyle style,
            float width,
            float height,
            boolean selected) {
        ImGui.pushID(style.id);
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Border, (int) 0xFF4DA6FFFFL);
        }
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, PRESET_CARD_PADDING_X, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.beginChild(
            "##preset_card",
            width,
            height,
            true,
            ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse
        );

        ImGui.dummy(0f, PRESET_CARD_PADDING_TOP);
        float contentWidth = ImGui.getContentRegionAvail().x;
        ImVec2 pos = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        RoadCrossSectionPreviewRenderer.CrossSectionLayout layout =
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(style);
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout,
            pos.x,
            pos.y,
            contentWidth,
            PRESET_PREVIEW_HEIGHT,
            RoadCrossSectionPreviewRenderer.MiniRenderOptions.presetCard()
        );
        ImGui.dummy(contentWidth, PRESET_PREVIEW_HEIGHT);

        String presetName = PlotI18n.tr("preset.road." + style.id);
        String caption = presetName + " (" + RoadCrossSectionPreviewRenderer.formatPresetCaption(layout) + ")";
        ImGui.dummy(0f, PRESET_PREVIEW_GAP);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + contentWidth);
        ImGui.text(caption);
        ImGui.popTextWrapPos();

        boolean clicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(0);
        ImGui.endChild();
        ImGui.popStyleVar(2);
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

        float[] fillFactor = {config.getFillFactor()};
        if (ImGui.sliderFloat(
            "##road_fill_factor",
            fillFactor,
            1.0f,
            2.0f,
            PlotI18n.tr("plugin.road.fill_factor", String.format("%.2f", fillFactor[0]))
        )) {
            config.setFillFactor(fillFactor[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.fill_factor");

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
                config.getCutSlopeMaterial(),
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
