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
        RoadPresetCards.renderConfigCompact(ctx);
        ImGui.spacing();
        RoadRouteQuickTune.renderConfigDefaults(ctx);

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.route.advanced_defaults"))) {
            renderAdvancedDefaultsCollapsible();
        }
    }

    /** 单选道路「更多道路设置」折叠区。 */
    public void renderAdvancedDefaultsCollapsible() {
        renderThemeSelector();
        RoadUiSections.categorySeparator();
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

        if (ImGui.collapsingHeader(PlotI18n.tr("plugin.road.adopt_advanced"))) {
            renderAdvancedCrossSectionFields(config);
        }
    }

    private void renderAdvancedCrossSectionFields(RoadSystemConfig config) {
        CrossSectionDraft draft = CrossSectionDraft.fromConfig(config);
        CrossSectionDraftEditor.render(
            ctx,
            draft,
            CrossSectionDraftEditorOptions.adoptAdvanced(),
            () -> {
                draft.applyToConfig(config);
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                markCustom();
            });

        RoadUiSections.categorySeparator();

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

        RoadUiSections.categorySeparator();
        RoadUiSections.section("plugin.road.section.bridge_structure_defaults");
        BridgeStructureControls.renderNetworkDefaults(ctx);

        RoadUiSections.categorySeparator();
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
        // 标签只保留参数名；数值在滑条上显示。勿把动态数值拼进 label，否则 ImGui ID 每帧变化导致拖动卡顿。
        float[] defaultRadius = {config.getDefaultCornerRadius()};
        if (ImGui.sliderFloat(
            PlotI18n.tr("plugin.road.default_corner_radius") + "##default_corner_radius",
            defaultRadius,
            0.0f,
            (float) RoadNode.MAX_CORNER_RADIUS,
            RoadUiFormat.SLIDER_METERS
        )) {
            config.setDefaultCornerRadius(defaultRadius[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.default_corner_radius");

        int[] defaultClearance = {(int) Math.round(config.getDefaultCrossingClearance())};
        if (ImGui.sliderInt(
                PlotI18n.tr("plugin.road.default_crossing_clearance") + "##default_crossing_clearance",
                defaultClearance,
                RoadParameterLimits.MIN_CROSSING_CLEARANCE,
                RoadParameterLimits.MAX_CROSSING_CLEARANCE,
                "%d")) {
            config.setDefaultCrossingClearance(defaultClearance[0]);
            markCustom();
        }
        RoadUiWidgets.renderEngineeringTooltip("hint.plot.road.default_crossing_clearance");
    }

    private void markCustom() {
        ctx.networkManager().getConfig().markCustom();
        ctx.onGenerationConfigChanged();
    }

}
