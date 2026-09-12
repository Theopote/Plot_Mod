package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.design.PoleDesign;
import com.plot.plugin.powerline.design.PoleDesignResolver;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.TowerRole;
import com.plot.plugin.powerline.placement.SingleTowerDesignResolver;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;

/** Route Tab：独立杆塔放置与管理。 */
public final class PowerLineSingleTowerSection {
    private final PowerLineUiContext ctx;
    private final PlacedSingleTowerPanel placedSingleTowerPanel;

    public PowerLineSingleTowerSection(PowerLineUiContext ctx, PlacedSingleTowerPanel placedSingleTowerPanel) {
        this.ctx = ctx;
        this.placedSingleTowerPanel = placedSingleTowerPanel;
    }

    public void render(PowerLineFootprint line) {
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.route.section.single_towers"));
        renderPlacement(line);
        placedSingleTowerPanel.render();
    }

    private void renderPlacement(PowerLineFootprint line) {
        ImGui.spacing();
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.single_tower.section"));
        if (line == null) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.single_tower.need_line"));
            return;
        }
        if (ctx.singleTowerPlacement().isActive()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.STATUS_INFO,
                PlotI18n.tr("plugin.powerline.single_tower.placing_hint"));
            if (ImGui.button(PlotI18n.tr("plugin.powerline.single_tower.cancel"), 0, 0)) {
                ctx.singleTowerPlacement().cancelPlacement();
            }
            return;
        }
        renderRolePicker(line);
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.single_tower.section_hint"));
        if (ImGui.button(PlotI18n.tr("plugin.powerline.single_tower.start"), 0, 0)) {
            ctx.singleTowerPlacement().beginPlacement(line);
        }
    }

    private void renderRolePicker(PowerLineFootprint line) {
        java.util.List<TowerRole> roles = SingleTowerRoleOptions.selectableRoles(line);
        TowerRole selected = SingleTowerRoleOptions.normalizeSelection(line, ctx.state().getSingleTowerRole());
        if (selected != ctx.state().getSingleTowerRole()) {
            ctx.state().setSingleTowerRole(selected);
        }

        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.single_tower.role_label"));
        int currentIndex = SingleTowerRoleOptions.indexOf(roles, selected);
        imgui.type.ImInt roleIndex = new imgui.type.ImInt(currentIndex);
        if (ImGui.combo("##single_tower_role", roleIndex, roles.stream()
                .map(SingleTowerRoleOptions::label)
                .toArray(String[]::new))) {
            ctx.state().setSingleTowerRole(SingleTowerRoleOptions.roleAt(roles, roleIndex.get()));
        }

        PoleDesign design = SingleTowerDesignResolver.resolve(
            line,
            ctx.state().getSingleTowerRole(),
            new PoleDesignResolver(ctx.state().getDesignProject()));
        String designLabel = design != null
            ? design.getName()
            : PlotI18n.tr("plugin.powerline.single_tower.no_design");
        PowerLineUiWidgets.textColored(
            PluginUiColors.HINT_GRAY,
            PlotI18n.tr("plugin.powerline.single_tower.resolved_design", designLabel));
    }
}
