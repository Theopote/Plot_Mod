package com.plot.plugin.powerline.ui;

import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.plugin.powerline.model.SingleTowerPlacementStatus;
import com.plot.plugin.powerline.placement.SingleTowerOrientation;
import com.plot.plugin.ui.PluginUiColors;
import com.plot.utils.PlotI18n;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/** 已放置单塔列表：选中、定位、删除。 */
public final class PlacedSingleTowerPanel {
    private final PowerLineUiContext ctx;
    private final PlacedSingleTowerActions actions;

    public PlacedSingleTowerPanel(PowerLineUiContext ctx, PlacedSingleTowerActions actions) {
        this.ctx = ctx;
        this.actions = actions;
    }

    public void render() {
        List<PlacedSingleTower> towers = ctx.state().getPlacedSingleTowers();
        ImGui.spacing();
        ImGui.setNextItemOpen(false, ImGuiCond.FirstUseEver);
        if (!ImGui.collapsingHeader(
                PlotI18n.tr("plugin.powerline.single_tower.manage.section_count", towers.size()),
                ImGuiTreeNodeFlags.None)) {
            return;
        }
        if (towers.isEmpty()) {
            PowerLineUiWidgets.textColored(
                PluginUiColors.HINT_GRAY,
                PlotI18n.tr("plugin.powerline.single_tower.manage.empty"));
            return;
        }

        ImGui.beginChild("powerline_placed_towers_list", 0, Math.min(180f, towers.size() * 52f + 8f), true);
        for (PlacedSingleTower tower : towers) {
            renderTowerRow(tower);
        }
        ImGui.endChild();
    }

    public void renderDeleteConfirmPopup() {
        if (!PowerLineUiWidgets.beginDeferredPopupModal(
                "##powerline_single_tower_delete_confirm",
                ctx.state().isPlacedSingleTowerDeleteConfirmPending(),
                () -> ctx.state().setPlacedSingleTowerDeleteConfirmPending(false))) {
            return;
        }
        PlacedSingleTower tower = ctx.state().findPlacedSingleTower(
            ctx.state().getPendingDeletePlacedSingleTowerId());
        String label = tower != null ? tower.getDesignLabel() : "";
        PowerLineUiWidgets.text(PlotI18n.tr("plugin.powerline.single_tower.delete_confirm", label));
        if (ImGui.button(PlotI18n.tr("button.plot.confirm"), 120, 0)) {
            actions.deleteTower(ctx.state().getPendingDeletePlacedSingleTowerId());
            ctx.state().setPendingDeletePlacedSingleTowerId("");
            ctx.state().setPlacedSingleTowerDeleteConfirmPending(false);
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("button.plot.cancel"), 120, 0)) {
            ctx.state().setPendingDeletePlacedSingleTowerId("");
            ctx.state().setPlacedSingleTowerDeleteConfirmPending(false);
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderTowerRow(PlacedSingleTower tower) {
        ImGui.pushID(tower.getId());
        boolean selected = tower.getId().equals(ctx.state().getSelectedPlacedSingleTowerId());
        String summary = PlotI18n.tr(
            "plugin.powerline.single_tower.manage.item",
            tower.getDesignLabel(),
            String.format("%.0f, %.0f", tower.getPlanPoint().x, tower.getPlanPoint().y),
            PlotI18n.tr(SingleTowerOrientation.labelKey(tower.getRotationQuadrant())),
            actions.resolveStyleLineName(tower, ctx.project()));
        if (tower.getPlacementStatus() == SingleTowerPlacementStatus.PARTIAL) {
            summary += " · " + PlotI18n.tr(
                "plugin.powerline.single_tower.manage.partial",
                tower.getPlacedBlockCount(),
                tower.getExpectedBlockCount());
        }
        if (ImGui.selectable(
                PowerLineUiWidgets.stableSelectableLabel(summary, tower.getId()),
                selected)) {
            actions.selectTower(tower.getId());
        }
        if (ImGui.button(PlotI18n.tr("plugin.powerline.locate") + "##locate", 0, 0)) {
            actions.locateTower(tower);
        }
        ImGui.sameLine();
        if (ImGui.button(PlotI18n.tr("plugin.powerline.delete") + "##delete", 0, 0)) {
            actions.requestDelete(tower.getId());
        }
        ImGui.separator();
        ImGui.popID();
    }
}
