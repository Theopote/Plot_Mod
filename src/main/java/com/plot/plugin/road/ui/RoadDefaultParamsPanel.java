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
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * 认领道路时的默认参数与预设配置。
 */
public final class RoadDefaultParamsPanel {
    private static final float PRESET_CARD_MIN_WIDTH = 96f;
    private static final float PRESET_CARD_PADDING_X = 4f;
    private static final float PRESET_CARD_PADDING_TOP = 8f;
    private static final float PRESET_CARD_PADDING_BOTTOM = 2f;
    private static final float PRESET_PREVIEW_GAP = 1f;
    /** 图示区高度；与 {@link RoadCrossSectionPreviewRenderer.MiniRenderOptions#presetCard()} 比例配套。 */
    private static final float PRESET_PREVIEW_HEIGHT = 32f;

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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.adopt_road_type_hint"));
        renderThemeSelector();
        ImGui.spacing();
        renderPresetSelector();
    }

    /** Adopt 向导 Step 3：横断面预览与高级参数。 */
    public void renderCrossSectionStep() {
        RoadUiSections.step("plugin.road.section.adopt_step3_cross_section");
        renderCrossSectionDefaults();
    }

    private void renderCrossSectionDefaults() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.default_params_scope_hint"));
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

    private void renderThemeSelector() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadThemeSelector.renderForConfig(config, () -> {
            ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
            ctx.onGenerationConfigChanged();
        });
    }

    private void renderPresetSelector() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        String selectedId = config.getSelectedPreset();
        boolean customSelected = selectedId == null || selectedId.isBlank();

        String header = PlotI18n.tr("plugin.road.preset_section");
        if (!customSelected) {
            header += " — " + PlotI18n.tr("preset.road." + selectedId);
        } else {
            header += " — " + PlotI18n.tr("plugin.road.preset_custom");
        }

        if (!ImGui.collapsingHeader(header + "##road_preset_section", ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        float gap = PRESET_CARD_PADDING_X;
        float avail = ImGui.getContentRegionAvail().x;
        int columns = avail >= PRESET_CARD_MIN_WIDTH * 2f + gap ? 2 : 1;
        float cardWidth = columns == 2 ? (avail - gap) * 0.5f : avail;
        String themeId = config.getRoadThemeId();

        renderPresetCardGrid(config, config.getStyles(), selectedId, cardWidth, columns, gap, themeId);

        ImGui.spacing();
        if (ImGui.button(PlotI18n.tr("plugin.road.preset_custom") + "##road_preset_custom")) {
            config.markCustom();
        }
        if (customSelected) {
            ImGui.sameLine();
            ImGui.textColored(PluginUiColors.ACCENT_BLUE, "●");
        }
        ImGui.spacing();
    }

    private void renderPresetCardGrid(
            RoadSystemConfig config,
            List<RoadStyle> styles,
            String selectedId,
            float cardWidth,
            int columns,
            float gap,
            String themeId) {
        List<PresetCardLayout> layouts = new ArrayList<>(styles.size());
        for (RoadStyle style : styles) {
            layouts.add(buildPresetCardLayout(style, cardWidth, themeId));
        }

        for (int index = 0; index < layouts.size(); index++) {
            if (index > 0 && index % columns == 0) {
                ImGui.dummy(0f, gap);
            }
            if (index % columns != 0) {
                ImGui.sameLine(0, gap);
            }

            int rowEnd = Math.min(index + columns, layouts.size());
            float rowHeight = 0f;
            for (int rowIndex = index; rowIndex < rowEnd; rowIndex++) {
                rowHeight = Math.max(rowHeight, layouts.get(rowIndex).height());
            }

            PresetCardLayout layout = layouts.get(index);
            if (renderPresetCard(layout, cardWidth, rowHeight, layout.style().id.equals(selectedId))) {
                config.applyStyle(layout.style());
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                ctx.onGenerationConfigChanged();
            }
        }
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
        ctx.onGenerationConfigChanged();
    }

    private static PresetCardLayout buildPresetCardLayout(RoadStyle style, float cardWidth, String themeId) {
        RoadCrossSectionPreviewRenderer.CrossSectionLayout sectionLayout =
            RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromStyle(style, themeId);
        String presetName = PlotI18n.tr("preset.road." + style.id);
        String caption = presetName + " ("
            + RoadCrossSectionPreviewRenderer.formatPresetCaption(sectionLayout) + ")";
        float innerWidth = Math.max(1f, cardWidth - PRESET_CARD_PADDING_X * 2f);
        float captionHeight = RoadUiWidgets.wrappedTextHeight(caption, innerWidth);
        float height = PRESET_CARD_PADDING_TOP
            + PRESET_PREVIEW_HEIGHT
            + PRESET_PREVIEW_GAP
            + captionHeight
            + PRESET_CARD_PADDING_BOTTOM;
        return new PresetCardLayout(style, sectionLayout, caption, height);
    }

    private boolean renderPresetCard(
            PresetCardLayout layout,
            float width,
            float height,
            boolean selected) {
        ImGui.pushID(layout.style().id);
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
        RoadCrossSectionPreviewRenderer.renderMini(
            drawList,
            layout.sectionLayout(),
            pos.x,
            pos.y,
            contentWidth,
            PRESET_PREVIEW_HEIGHT,
            RoadCrossSectionPreviewRenderer.MiniRenderOptions.presetCard()
        );
        ImGui.dummy(contentWidth, PRESET_PREVIEW_HEIGHT);

        ImGui.dummy(0f, PRESET_PREVIEW_GAP);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + contentWidth);
        ImGui.text(layout.caption());
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

    private record PresetCardLayout(
            RoadStyle style,
            RoadCrossSectionPreviewRenderer.CrossSectionLayout sectionLayout,
            String caption,
            float height) {
    }
}
