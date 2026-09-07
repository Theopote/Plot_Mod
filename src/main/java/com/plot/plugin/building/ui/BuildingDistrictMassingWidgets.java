package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingBatchEditor;
import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.building.preset.BuildingPresetApplier;
import com.plot.plugin.building.preset.BuildingPresetCatalog;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 片区 Massing 共享控件（Overview / Edit）。 */
public final class BuildingDistrictMassingWidgets {
    private BuildingDistrictMassingWidgets() {
    }

    /**
     * 片区工具作用范围。
     * <ul>
     *   <li>{@link #SELECTED_ONLY} — Edit 片区工具：仅当前选中</li>
     *   <li>{@link #ALL_WHEN_EMPTY} — Overview 首页：无选中时整片</li>
     * </ul>
     */
    public enum DistrictMassingTarget {
        SELECTED_ONLY,
        ALL_WHEN_EMPTY
    }

    /** Overview Tab 片区体量首页。 */
    public static void renderOverviewHome(BuildingUiContext ctx) {
        int count = ctx.project().getBuildingCount();
        if (count == 0) {
            return;
        }

        ImGui.pushID("overview");
        try {
            ImGui.separator();
            ImGui.text(PlotI18n.tr("plugin.building.district_massing_home"));
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.district_massing_home_hint"));

            renderHeightDistribution(ctx, DistrictMassingTarget.ALL_WHEN_EMPTY);
            ImGui.spacing();
            renderPreviewGenerateAll(ctx, count);
        } finally {
            ImGui.popID();
        }
    }

    /** Edit Tab 片区多选工具（Preset / Batch / Height Distribution）。 */
    public static void renderEditDistrictTools(BuildingUiContext ctx, BuildingFootprint primary) {
        ImGui.pushID("edit");
        try {
            renderPresetSelector(ctx, primary);
            ImGui.spacing();
            renderBatchApply(ctx, primary);
            renderHeightDistribution(ctx, DistrictMassingTarget.SELECTED_ONLY);
        } finally {
            ImGui.popID();
        }
    }

    static void renderHeightDistribution(BuildingUiContext ctx, DistrictMassingTarget target) {
        List<BuildingFootprint> targets = resolveTargets(ctx, target);
        int count = targets.size();

        ImGui.text(PlotI18n.tr("plugin.building.height_distribution"));
        if (target == DistrictMassingTarget.ALL_WHEN_EMPTY) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.height_distribution_overview_hint", count));
        } else {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.height_distribution_hint", count));
        }

        BuildingHeightDistribution.Mode[] modes = BuildingHeightDistribution.Mode.values();
        String[] labels = new String[modes.length];
        int current = 0;
        for (int i = 0; i < modes.length; i++) {
            labels[i] = PlotI18n.tr("plugin.building.height_mode." + modes[i].name().toLowerCase());
            if (modes[i] == ctx.heightDistMode()) {
                current = i;
            }
        }
        ImInt modeIndex = new ImInt(current);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##height_dist_mode", modeIndex, labels)) {
            int picked = modeIndex.get();
            if (picked >= 0 && picked < modes.length) {
                ctx.setHeightDistMode(modes[picked]);
            }
        }

        if (ctx.heightDistMode() == BuildingHeightDistribution.Mode.UNIFORM) {
            int[] floors = {ctx.heightDistMaxFloors()};
            if (ImGui.sliderInt(
                    "##height_dist_uniform",
                    floors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.floors", floors[0]))) {
                ctx.setHeightDistMinFloors(floors[0]);
                ctx.setHeightDistMaxFloors(floors[0]);
            }
        } else {
            int[] minFloors = {ctx.heightDistMinFloors()};
            int[] maxFloors = {ctx.heightDistMaxFloors()};
            if (ImGui.sliderInt(
                    "##height_dist_min",
                    minFloors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.height_min_floors", minFloors[0]))) {
                ctx.setHeightDistMinFloors(minFloors[0]);
                if (ctx.heightDistMaxFloors() < ctx.heightDistMinFloors()) {
                    ctx.setHeightDistMaxFloors(ctx.heightDistMinFloors());
                }
            }
            if (ImGui.sliderInt(
                    "##height_dist_max",
                    maxFloors,
                    1,
                    32,
                    PlotI18n.tr("plugin.building.height_max_floors", maxFloors[0]))) {
                ctx.setHeightDistMaxFloors(maxFloors[0]);
                if (ctx.heightDistMinFloors() > ctx.heightDistMaxFloors()) {
                    ctx.setHeightDistMinFloors(ctx.heightDistMaxFloors());
                }
            }
        }

        if (ctx.heightDistMode() == BuildingHeightDistribution.Mode.RANDOM) {
            renderHeightDistSeedControls(ctx, targets);
        }

        boolean applyDisabled = count == 0;
        if (applyDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.apply_height_distribution", count),
                ImGui.getContentRegionAvailX(),
                0)) {
            ctx.actions().applyHeightDistribution(targets);
        }
        if (applyDisabled) {
            ImGui.endDisabled();
        }
    }

    private static void renderHeightDistSeedControls(
            BuildingUiContext ctx,
            List<BuildingFootprint> targets) {
        long seed = ctx.resolveHeightDistSeed(targets);
        String seedText = Long.toString(seed);
        if (!seedText.equals(ctx.heightDistSeedBuffer().get())) {
            ctx.heightDistSeedBuffer().set(seedText);
        }

        float randomizeWidth = ImGui.calcTextSize(PlotI18n.tr("plugin.building.height_dist_seed_randomize")).x
            + ImGui.getStyle().getFramePaddingX() * 2.0f;
        ImGui.setNextItemWidth(Math.max(80.0f, ImGui.getContentRegionAvailX() - randomizeWidth - ImGui.getStyle().getItemSpacingX()));
        if (ImGui.inputText(PlotI18n.tr("plugin.building.height_dist_seed"), ctx.heightDistSeedBuffer())) {
            try {
                long parsed = Long.parseLong(ctx.heightDistSeedBuffer().get().trim());
                ctx.setHeightDistSeed(parsed);
                ctx.setHeightDistSeedManual(true);
            } catch (NumberFormatException ignored) {
                ctx.heightDistSeedBuffer().set(seedText);
            }
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.height_dist_seed");
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.building.height_dist_seed_randomize"))) {
            ctx.setHeightDistSeed(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE);
            ctx.heightDistSeedBuffer().set(Long.toString(ctx.heightDistSeed()));
            ctx.setHeightDistSeedManual(true);
        }
    }

    private static void renderPreviewGenerateAll(BuildingUiContext ctx, int count) {
        var readiness = ctx.host().projection().checkWorldModificationReadiness();
        boolean generateDisabled = !readiness.ready()
            || ctx.host().placement().isBusy()
            || ctx.isDistrictPreviewBusy();

        float half = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f;
        if (generateDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(PlotI18n.tr("plugin.building.preview_all", count), half, 0)) {
            ctx.actions().previewEntireDistrict();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.building.generate_all", count), half, 0)) {
            ctx.actions().prepareGenerateEntireDistrict();
        }
        if (generateDisabled) {
            ImGui.endDisabled();
        }
        if (!readiness.ready()) {
            ImGui.textColored(PluginUiColors.ERROR_SOFT, readiness.message());
        }
    }

    private static void renderBatchApply(BuildingUiContext ctx, BuildingFootprint primary) {
        int count = ctx.selection().size();
        ImGui.textColored(PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.building.batch_edit_hint", count, primary.getName()));

        ImBoolean floors = new ImBoolean(ctx.batchFieldMask().floors);
        ImBoolean floorHeight = new ImBoolean(ctx.batchFieldMask().floorHeight);
        ImBoolean wall = new ImBoolean(ctx.batchFieldMask().wallThickness);
        ImBoolean materials = new ImBoolean(ctx.batchFieldMask().materials);
        ImBoolean roof = new ImBoolean(ctx.batchFieldMask().roof);
        ImBoolean windows = new ImBoolean(ctx.batchFieldMask().windows);

        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_floors"), floors)) {
            ctx.batchFieldMask().floors = floors.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_floor_height"), floorHeight)) {
            ctx.batchFieldMask().floorHeight = floorHeight.get();
        }
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_wall"), wall)) {
            ctx.batchFieldMask().wallThickness = wall.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_materials"), materials)) {
            ctx.batchFieldMask().materials = materials.get();
        }
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_roof"), roof)) {
            ctx.batchFieldMask().roof = roof.get();
        }
        ImGui.sameLine();
        if (ImGui.checkbox(PlotI18n.tr("plugin.building.batch_field_windows"), windows)) {
            ctx.batchFieldMask().windows = windows.get();
        }

        boolean applyDisabled = !ctx.batchFieldMask().anyEnabled();
        if (applyDisabled) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(
                PlotI18n.tr("plugin.building.apply_to_selected", count),
                ImGui.getContentRegionAvailX(),
                0)) {
            ctx.actions().applyMassingToSelected(
                primary,
                ctx.selection().resolve(ctx.project()));
        }
        if (applyDisabled) {
            ImGui.endDisabled();
        }
    }

    private static void renderPresetSelector(BuildingUiContext ctx, BuildingFootprint building) {
        List<BuildingPresetCatalog.BuildingPreset> presets = BuildingPresetCatalog.all();
        String[] labels = presets.stream()
            .map(p -> PlotI18n.tr("preset.building." + p.id()))
            .toArray(String[]::new);
        String[] ids = presets.stream()
            .map(BuildingPresetCatalog.BuildingPreset::id)
            .toArray(String[]::new);

        int currentIndex = 0;
        String currentPreset = building.getPresetId();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(currentPreset)) {
                currentIndex = i;
                break;
            }
        }

        ImGui.text(PlotI18n.tr("plugin.building.preset_section"));
        ImInt presetIndex = new ImInt(currentIndex);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##building_preset", presetIndex, labels)) {
            // selection only; apply on button
        }
        UIUtils.renderEngineeringTooltip("hint.plot.building.preset");

        if (!currentPreset.isBlank()) {
            ImGui.textColored(PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.building.preset_active", PlotI18n.tr("preset.building." + currentPreset)));
        }

        int selectedCount = ctx.selection().size();
        float buttonWidth = selectedCount > 1
            ? (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) / 2.0f
            : ImGui.getContentRegionAvailX();

        if (ImGui.button(PlotI18n.tr("plugin.building.apply_preset"), buttonWidth, 0)) {
            int picked = presetIndex.get();
            if (picked >= 0 && picked < ids.length) {
                ctx.actions().applyPresetToBuilding(ids[picked], building);
            }
        }

        if (selectedCount > 1) {
            ImGui.sameLine();
            if (ImGui.button(
                    PlotI18n.tr("plugin.building.apply_preset_to_selected", selectedCount),
                    buttonWidth,
                    0)) {
                int picked = presetIndex.get();
                if (picked >= 0 && picked < ids.length) {
                    ctx.actions().applyPresetToSelected(
                        ids[picked],
                        ctx.selection().resolve(ctx.project()));
                }
            }
        }
    }

    static List<BuildingFootprint> resolveTargets(
            BuildingUiContext ctx,
            DistrictMassingTarget target) {
        List<BuildingFootprint> selected = ctx.selection().resolve(ctx.project());
        if (!selected.isEmpty()) {
            return selected;
        }
        if (target == DistrictMassingTarget.ALL_WHEN_EMPTY) {
            return new ArrayList<>(ctx.project().getBuildings().values());
        }
        return selected;
    }
}
