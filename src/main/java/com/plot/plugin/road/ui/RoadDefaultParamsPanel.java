package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginUiColors;

import com.plot.plugin.config.RoadSystemConfig;
import com.plot.plugin.road.RoadCrossSectionPreviewRenderer;
import com.plot.plugin.road.RoadParameterLimits;
import com.plot.plugin.road.model.RoadNode;
import com.plot.plugin.road.model.section.CrossSectionDraft;
import com.plot.ui.component.EngineeringSlopeInput;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

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
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.adopt_road_type_hint"));
        renderThemeSelector();
        ImGui.spacing();
        renderPresetSelector();
    }

    /** Adopt 向导 Step 3：横断面预览与高级参数。 */
    public void renderCrossSectionStep() {
        renderRoutePrimary();
    }

    /** 路线 Tab（0 条选择）：新道路默认类型、宽度/车道与坡度预设。 */
    public void renderRoutePrimary() {
        RoadPresetCards.renderConfig(ctx);
        ImGui.spacing();
        RoadRouteQuickTune.renderConfigDefaults(ctx);
        RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.advanced_defaults"))) {
            renderAdvancedDefaultsCollapsible();
        }
    }

    /** 单选道路「更多道路设置」折叠区。 */
    public void renderAdvancedDefaultsCollapsible() {
        renderThemeSelector();
        ImGui.spacing();
        renderAdvancedCrossSectionFields(ctx.networkManager().getConfig());
    }

    private void renderCrossSectionDefaults() {
        RoadSystemConfig config = ctx.networkManager().getConfig();
        RoadUiWidgets.textWrappedColored(
            PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.road.default_params_scope_hint"));
        RoadCrossSectionPreviewRenderer.render(config);
        ImGui.spacing();
        RoadPresetCards.renderConfig(ctx);
        ImGui.spacing();
        RoadStyleProductControls.renderConfigMaxSlopePresets(ctx);

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

        RoadPresetCards.renderConfig(ctx);
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

}
