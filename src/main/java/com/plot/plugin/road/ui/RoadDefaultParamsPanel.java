package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.plugin.road.style.RoadStyle;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

/**
 * 认领道路时的默认参数与预设配置。
 */
public final class RoadDefaultParamsPanel {
    private final RoadUiContext ctx;

    public RoadDefaultParamsPanel(RoadUiContext ctx) {
        this.ctx = ctx;
    }

    public void render() {
        renderCrossSectionDefaults();
    }

    /** Adopt 向导 Step 2：道路类型预设。 */
    public void renderRoadTypeStep() {
        RoadUiSections.step("plugin.road.section.adopt_step2_road_type");
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.adopt_road_type_hint"));
        renderPresetSelector();
    }

    /** Adopt 向导 Step 3：横断面预览与高级参数。 */
    public void renderCrossSectionStep() {
        RoadUiSections.step("plugin.road.section.adopt_step3_cross_section");
        renderCrossSectionDefaults();
    }

    private void renderCrossSectionDefaults() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        ImGui.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.default_params_scope_hint"));
        RoadCrossSectionPreviewRenderer.render(config);
        ImGui.spacing();

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.adopt_advanced"))) {
            renderAdvancedCrossSectionFields(config);
        }
    }

    private void renderAdvancedCrossSectionFields(RoadSystemConfig config) {
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        CrossSectionDraftEditor.render(
            ctx,
            draft,
            CrossSectionDraftEditorOptions.adopt(),
            () -> {
                draft.applyToConfig(config);
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                markCustom();
            });

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

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
            (float) RoadParameterLimits.MIN_CONTINUOUS_SLOPE_LENGTH,
            (float) RoadParameterLimits.MAX_CONTINUOUS_SLOPE_LENGTH,
            PlotI18n.tr("plugin.road.max_continuous_slope_length", maxContinuousLength[0])
        )) {
            config.setMaxContinuousSlopeLength(maxContinuousLength[0]);
            markCustom();
        }

        float relaxedMax = (float) Math.min(
            RoadParameterLimits.MAX_RELAXED_SLOPE_LENGTH,
            config.getMaxContinuousSlopeLength());
        float[] relaxedLength = {(float) config.getRelaxedSlopeLength()};
        if (ImGui.sliderFloat(
            "##relaxed_slope_length",
            relaxedLength,
            (float) RoadParameterLimits.MIN_RELAXED_SLOPE_LENGTH,
            relaxedMax,
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
    }

    private void renderPresetSelector() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
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
                ctx.onGenerationConfigChanged();
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
            ImGui.textColored(PluginUiColors.ACCENT_BLUE, "●");
        } else if (!selectedId.isBlank()) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("preset.road." + selectedId));
        }
        ImGui.spacing();
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
        // 默认/全局参数变更后失效预览，避免按过期阈值落地
        ctx.onGenerationConfigChanged();
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
            ImGui.pushStyleColor(ImGuiCol.Border, PluginUiColors.ACCENT_BLUE);
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
}
