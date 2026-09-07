package com.plot.plugin.building.ui;

import com.plot.plugin.building.BuildingHeightDistribution;
import com.plot.plugin.building.model.BuildingFootprint;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.ui.component.UIUtils;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 片区 Massing 共享控件（Overview / Edit）。 */
public final class BuildingDistrictMassingWidgets {
    private BuildingDistrictMassingWidgets() {
    }

    public enum HeightDistributionTarget {
        /** 仅当前选中；无选中时不操作。 */
        SELECTED_ONLY,
        /** 无选中时作用于项目内全部建筑（Overview 首页）。 */
        ALL_WHEN_EMPTY
    }

    public static void renderHeightDistribution(
            BuildingUiContext ctx,
            String idPrefix,
            HeightDistributionTarget target) {
        ImGui.pushID(idPrefix);
        try {
            List<BuildingFootprint> targets = resolveTargets(ctx, target);
            int count = targets.size();

            ImGui.text(PlotI18n.tr("plugin.building.height_distribution"));
            if (target == HeightDistributionTarget.ALL_WHEN_EMPTY) {
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
        } finally {
            ImGui.popID();
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

    static List<BuildingFootprint> resolveTargets(
            BuildingUiContext ctx,
            HeightDistributionTarget target) {
        List<BuildingFootprint> selected = ctx.selection().resolve(ctx.project());
        if (!selected.isEmpty()) {
            return selected;
        }
        if (target == HeightDistributionTarget.ALL_WHEN_EMPTY) {
            return new ArrayList<>(ctx.project().getBuildings().values());
        }
        return selected;
    }
}
