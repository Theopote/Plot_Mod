package com.plot.plugin.powerline.ui;

import com.plot.core.model.Shape;
import com.plot.plugin.powerline.model.PoleSpacingMode;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.path.PowerLineSourceSync;
import com.plot.plugin.powerline.path.SourceSyncStatus;
import com.plot.plugin.powerline.style.PowerLineSpacingPolicy;
import com.plot.plugin.powerline.style.PowerLineStyleEditor;
import com.plot.plugin.powerline.style.PowerLineStylePreset;
import com.plot.plugin.powerline.style.PowerLineStylePresetCatalog;
import com.plot.plugin.powerline.style.PoleSpacingProfile;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

/** 线路 Tab：全部线路、认领路径、杆塔布置。 */
public final class PowerLineRoutePanel {
    private final PowerLineUiContext ctx;
    private final PowerLineAdoptPanel adoptPanel;
    private final PowerLineOverviewPanel overviewPanel;
    private final PowerLineSingleTowerSection singleTowerSection;

    public PowerLineRoutePanel(
            PowerLineUiContext ctx,
            PowerLineOverviewPanel overviewPanel,
            PlacedSingleTowerPanel placedSingleTowerPanel) {
        this.ctx = ctx;
        this.adoptPanel = new PowerLineAdoptPanel(ctx);
        this.overviewPanel = overviewPanel;
        this.singleTowerSection = new PowerLineSingleTowerSection(ctx, placedSingleTowerPanel);
    }

    public void render() {
        ctx.selection().retainExisting(ctx.project());
        PowerLineFootprint line = ctx.selection().primary(ctx.project());

        renderCurrentLineHeader();

        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.section.path"));
        adoptPanel.render(line);

        if (line != null) {
            ImGui.separator();
            renderLineName(line);
            renderSourceReference(line);
            ImGui.separator();
            PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.section.placement"));
            renderPolePlacement(line);
            renderTerrainAvoidance(line);
            renderAdvancedSpacing(line);
        }

        ImGui.separator();
        singleTowerSection.render();

        ImGui.separator();
        renderProjectSection();
    }

    public void renderDeleteConfirmPopup() {
        overviewPanel.renderDeleteConfirmPopup();
    }

    private void renderProjectSection() {
        int count = ctx.project().getLineCount();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.section.all_lines_count", count),
                ImGuiTreeNodeFlags.None)) {
            overviewPanel.renderProjectSection(true);
        }
    }

    private void renderCurrentLineHeader() {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.current_line"));
        ImGui.sameLine();
        float width = ImGui.getContentRegionAvail().x;
        if (width > 0f) {
            ImGui.setNextItemWidth(width);
        }
        if (!PowerLineUiWidgets.renderLineSelector(ctx)) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.current_line_empty"));
        }
    }

    private void renderSourceReference(PowerLineFootprint line) {
        if (!PowerLineSourceSync.hasLinkedSource(line)) {
            return;
        }
        java.util.List<Shape> canvasShapes = ctx.host().appState().getShapes();
        SourceSyncStatus status = PowerLineSourceSync.resolveStatus(line, canvasShapes);
        switch (status) {
            case NOT_LINKED, OK -> {
                return;
            }
            case MISSING -> PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.source_missing"));
            case UNSUPPORTED -> PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.source_unsupported"));
            case DEGENERATE -> PowerLineUiWidgets.textColored(
                PluginUiColors.WARNING,
                PlotI18n.tr("plugin.powerline.source_degenerate"));
            case STALE -> {
                PowerLineUiWidgets.textColored(
                    PluginUiColors.WARNING,
                    PlotI18n.tr("plugin.powerline.source_stale"));
                ImGui.spacing();
                if (ImGui.button(PlotI18n.tr("plugin.powerline.relayout_from_source"), 0, 0)) {
                    ctx.relayoutLineFromSource(line);
                }
            }
        }
        if (status != SourceSyncStatus.OK && status != SourceSyncStatus.NOT_LINKED) {
            ImGui.spacing();
            if (ImGui.button(PlotI18n.tr("plugin.powerline.path.detach_keep_snapshot"), 0, 0)) {
                ctx.detachSourceAndKeepLayout(line);
            }
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.path.detach_keep_snapshot_hint"));
        }
    }

    private void renderLineName(PowerLineFootprint line) {
        if (!line.getId().equals(ctx.lineNameEditingId())) {
            ctx.lineNameBuffer().set(line.getName());
            ctx.setLineNameEditingId(line.getId());
        }
        PowerLineUiWidgets.inputTextStableLineEdit(
            ctx,
            "plugin.powerline.line_name",
            "line_name",
            ctx.lineNameBuffer(),
            name -> line.setName(name));
    }

    private void renderPolePlacement(PowerLineFootprint line) {
        renderPlacementMode(line);
        ImGui.spacing();
        if (line.getPoleSpacingMode() == PoleSpacingMode.AUTO_SPACING) {
            renderSpacingPresets(line);
            renderSpacingSlider(line);
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.TOWER_COUNT) {
            renderTowerCountInput(line);
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.ENDPOINTS_ONLY) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.placement_mode_hint.ENDPOINTS_ONLY"));
        } else if (line.getPoleSpacingMode() == PoleSpacingMode.ENDPOINTS_WITH_CORNERS) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.placement_mode_hint.ENDPOINTS_WITH_CORNERS"));
        }
        renderCornerBehaviorHint(line);
    }

    private void renderCornerBehaviorHint(PowerLineFootprint line) {
        String hintKey = switch (line.getPoleSpacingMode()) {
            case AUTO_SPACING -> "plugin.powerline.route.corner_hint.auto_spacing";
            case ENDPOINTS_WITH_CORNERS -> "plugin.powerline.route.corner_hint.endpoints_with_corners";
            default -> null;
        };
        if (hintKey == null) {
            return;
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr(hintKey));
    }

    private void renderPlacementMode(PowerLineFootprint line) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.placement_mode"));
        PoleSpacingMode[] modes = PoleSpacingMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.powerline.route.placement_mode." + modes[i].name());
        }
        int current = line.getPoleSpacingMode().ordinal();
        ImInt index = new ImInt(current);
        if (ImGui.combo(
                PowerLineUiWidgets.stableLabel("plugin.powerline.route.placement_mode", "placement_mode"),
                index,
                labels)) {
            PoleSpacingMode selected = modes[index.get()];
            if (selected != line.getPoleSpacingMode()) {
                ctx.pushEditSnapshot();
                line.setPoleSpacingMode(selected);
                ctx.invalidatePreview();
            }
        }
    }

    private void renderSpacingPresets(PowerLineFootprint line) {
        PowerLineUiPresets.SpacingDensity detected = PowerLineSpacingPolicy.detectDensity(line);
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX() * 2f) / 3f;
        for (PowerLineUiPresets.SpacingDensity density : PowerLineUiPresets.SpacingDensity.values()) {
            if (density != PowerLineUiPresets.SpacingDensity.values()[0]) {
                ImGui.sameLine();
            }
            boolean selected = !line.isSpacingCustomized() && density == detected;
            String label = PlotI18n.tr("plugin.powerline.route.spacing." + density.name().toLowerCase());
            if (selected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, PluginUiColors.ACCENT_BLUE);
            }
            if (ImGui.button(label + "##preset_" + density.name(), buttonWidth, 0)) {
                ctx.pushEditSnapshot();
                PowerLineUiPresets.applySpacing(line, density);
                ctx.invalidatePreview();
            }
            if (selected) {
                ImGui.popStyleColor();
            }
        }
        ImGui.spacing();
    }

    private void renderSpacingSlider(PowerLineFootprint line) {
        float sliderMin = (float) PowerLineFootprint.MIN_CONFIGURABLE_SPACING;
        float sliderMax = (float) PowerLineSpacingPolicy.sliderMax(line);
        float[] spacing = {(float) line.getMaxPoleSpacing()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "pole_spacing",
            "plugin.powerline.route.pole_spacing",
            spacing,
            sliderMin,
            sliderMax,
            "%.0f",
            value -> {
                line.setMaxPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });

        ImGui.sameLine();
        ImGui.setNextItemWidth(64f);
        float[] spacingInput = {spacing[0]};
        PowerLineUiWidgets.inputFloatStableLineEdit(
            ctx,
            "pole_spacing_input",
            spacingInput,
            1f,
            4f,
            "%.0f",
            sliderMin,
            sliderMax,
            value -> {
                line.setMaxPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });
        spacing[0] = spacingInput[0];
        ImGui.sameLine();
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.spacing_blocks"));
    }

    private void renderTowerCountInput(PowerLineFootprint line) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.tower_count"));
        int[] count = {line.getTargetTowerCount()};
        PowerLineUiWidgets.inputIntStableLineEdit(
            ctx,
            "tower_count",
            count,
            1,
            1,
            line::setTargetTowerCount);
        double worldLength = line.computeWorldPathLength(ctx.coordinates());
        int poles = count[0];
        if (poles > 1 && worldLength > 0.0) {
            double implied = worldLength / (poles - 1);
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.route.tower_count_implied_spacing", String.format("%.0f", implied)));
        }
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.route.tower_count_hint"));
    }

    private void renderTerrainAvoidance(PowerLineFootprint line) {
        boolean enabled = line.isTerrainAvoidanceEnabled();
        if (ImGui.checkbox(PlotI18n.tr("plugin.powerline.route.avoid_terrain"), enabled)) {
            ctx.pushEditSnapshot();
            line.setTerrainAvoidanceEnabled(!enabled);
            ctx.invalidatePreview();
        }
        PowerLineUiWidgets.textColored(PluginUiColors.HINT_GRAY, PlotI18n.tr("plugin.powerline.route.avoid_terrain_hint"));
    }

    private void renderAdvancedSpacing(PowerLineFootprint line) {
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.route.advanced"),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        PoleSpacingProfile profile = PowerLineSpacingPolicy.profileFor(line);
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.route.spacing_style_range",
                profile.recommendedMin(),
                profile.preferred(),
                profile.recommendedMax()));
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr(
                "plugin.powerline.route.current_spacing",
                line.getMinPoleSpacing(),
                line.getMaxPoleSpacing()));

        float sliderMin = (float) PowerLineFootprint.MIN_CONFIGURABLE_SPACING;
        float sliderMax = (float) PowerLineSpacingPolicy.sliderMax(line);
        float[] minSpacing = {(float) line.getMinPoleSpacing()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "min_pole_spacing",
            "plugin.powerline.min_pole_spacing",
            minSpacing,
            sliderMin,
            sliderMax,
            "%.1f",
            value -> {
                line.setMinPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });

        float[] maxSpacing = {(float) line.getMaxPoleSpacing()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "max_pole_spacing",
            "plugin.powerline.max_pole_spacing",
            maxSpacing,
            sliderMin,
            sliderMax,
            "%.1f",
            value -> {
                line.setMaxPoleSpacing(value);
                PowerLineStyleEditor.afterSpacingEdit(line);
            });

        renderSpacingRecommendation(line);

        float[] cornerAngle = {(float) line.getCornerAngleThreshold()};
        PowerLineUiWidgets.sliderFloatStableLineEdit(
            ctx,
            "corner_angle",
            "plugin.powerline.corner_angle",
            cornerAngle,
            0f,
            90f,
            "%.1f",
            line::setCornerAngleThreshold);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(PlotI18n.tr("plugin.powerline.route.corner_hint.detail"));
        }

        ctx.actions().closestMandatorySpacingViolation(line).ifPresent(distance -> PowerLineUiWidgets.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr("plugin.powerline.min_spacing_warning", distance)));
        renderAutoAddedPoles(line);
    }

    private void renderSpacingRecommendation(PowerLineFootprint line) {
        PowerLineStylePreset preset = PowerLineStylePresetCatalog.activePreset(line);
        if (preset == null || !line.isSpacingCustomized()) {
            return;
        }
        if (!PowerLineSpacingPolicy.differsFromStyleRecommendation(line, preset)) {
            return;
        }
        PoleSpacingProfile profile = preset.getSpacingProfile();
        PowerLineUiWidgets.textColored(
            PluginUiColors.WARNING,
            PlotI18n.tr(
                "plugin.powerline.route.spacing_recommendation",
                profile.preferred(),
                line.getMaxPoleSpacing()));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.route.apply_recommended_spacing"), 0, 0)) {
            ctx.pushEditSnapshot();
            PowerLineSpacingPolicy.applyStyleDefaultSpacing(line, profile);
            PowerLineStyleEditor.afterSpacingAdopted(line);
            ctx.invalidatePreview();
        }
    }

    private void renderAutoAddedPoles(PowerLineFootprint line) {
        var constraints = line.getLayoutConstraints();
        if (constraints.isEmpty()) {
            return;
        }
        ImGui.separator();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.auto_poles.section"));
        ImGui.spacing();
        for (int i = 0; i < constraints.size(); i++) {
            var constraint = constraints.get(i);
            ImGui.pushID("powerline_auto_pole_" + i);
            PowerLineStatusIcon.renderBulletLine(PlotI18n.tr(
                "plugin.powerline.route.auto_poles.entry",
                String.format("%.1f", constraint.getRequiredStationing()),
                PowerLineAutoPoleLabels.friendlyReason(constraint)));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.route.auto_poles.remove"), 0, 0)) {
                ctx.pushEditSnapshot();
                line.removeLayoutConstraint(i);
                ctx.invalidatePreview();
            }
            ImGui.popID();
            ImGui.spacing();
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.route.auto_poles.clear_all"), 0, 0)) {
            ctx.pushEditSnapshot();
            line.clearLayoutConstraints();
            ctx.invalidatePreview();
        }
    }
}
