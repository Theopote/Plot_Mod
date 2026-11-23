package com.plot.plugin.road.ui;
import com.plot.plugin.ui.PluginSectionCard;
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
        renderQuickTuneCard(() -> RoadRouteQuickTune.renderConfigDefaults(ctx));

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

    /** 主界面快速调整区（供 EditWorkspace 复用）。 */
    public void renderQuickTuneCard(Runnable body) {
        PluginSectionCard.render(
            "##road_defaults_quick",
            PlotI18n.tr("plugin.road.edit.quick_tune"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            body);
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
        var options = CrossSectionDraftEditorOptions.adoptAdvanced();
        CrossSectionDraftMutator mutator = CrossSectionDraftMutator.forDraft(
            draft,
            () -> {
                draft.applyToConfig(config);
                ctx.adoptIncludeSidewalkRef().set(config.isIncludeSidewalk());
                markCustom();
            });

        PluginSectionCard.render(
            "##road_defaults_strips",
            PlotI18n.tr("plugin.road.section.defaults_strips"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
                CrossSectionDraftEditor.renderAppearanceStrips(ctx, mutator, options);
                CrossSectionDraftEditor.renderMarkings(ctx, mutator, options);
                CrossSectionDraftEditor.renderLaneWidths(ctx, mutator, options);
                CrossSectionDraftEditor.renderSlopeBatter(ctx, mutator, options);
            });
        PluginSectionCard.render(
            "##road_defaults_materials",
            PlotI18n.tr("plugin.road.section.defaults_materials"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> CrossSectionDraftEditor.renderMaterials(ctx, mutator, options));
        PluginSectionCard.render(
            "##road_defaults_furniture",
            PlotI18n.tr("plugin.road.section.defaults_furniture"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> CrossSectionDraftEditor.renderFurniture(ctx, mutator, options));

        PluginSectionCard.render(
            "##road_defaults_max_grade",
            PlotI18n.tr("plugin.road.floating_editor.cross_section.section.max_grade"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> {
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
            });

        PluginSectionCard.render(
            "##road_defaults_bridge",
            PlotI18n.tr("plugin.road.section.bridge_structure_defaults"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            () -> BridgeStructureControls.renderNetworkDefaults(ctx));

        PluginSectionCard.render(
            "##road_defaults_junction",
            PlotI18n.tr("plugin.road.section.defaults_junction"),
            null,
            PluginSectionCard.SectionTone.NEUTRAL,
            true,
            this::renderDefaultJunctionSettings);
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
