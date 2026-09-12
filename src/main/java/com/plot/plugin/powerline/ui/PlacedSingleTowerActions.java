package com.plot.plugin.powerline.ui;

import com.plot.api.geometry.Vec2d;
import com.plot.api.world.PluginProjectionContext;
import com.plot.core.command.BlockRecord;
import com.plot.core.context.PluginContext;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.plugin.powerline.model.PowerLineFootprint;
import com.plot.plugin.powerline.model.PowerLineProject;
import com.plot.plugin.powerline.placement.SingleTowerRemoveCommand;
import com.plot.ui.canvas.Canvas;
import com.plot.ui.canvas.CanvasAccess;
import com.plot.utils.PlotI18n;

import java.util.List;
import java.util.Objects;

/** 已放置单塔：选中、定位、删除。 */
public final class PlacedSingleTowerActions {
    private final PluginContext host;
    private final PowerLinePluginState state;

    public PlacedSingleTowerActions(PluginContext host, PowerLinePluginState state) {
        this.host = Objects.requireNonNull(host, "host");
        this.state = Objects.requireNonNull(state, "state");
    }

    public void selectTower(String towerId) {
        if (towerId == null || towerId.isBlank()) {
            state.clearPlacedSingleTowerSelection();
            return;
        }
        if (state.findPlacedSingleTower(towerId) != null) {
            state.selectPlacedSingleTower(towerId);
        }
    }

    public void locateTower(PlacedSingleTower tower) {
        if (tower == null) {
            return;
        }
        Canvas canvas = CanvasAccess.get();
        if (canvas != null && canvas.getCamera() != null) {
            canvas.getCamera().setOffset(tower.getPlanPoint());
            state.selectPlacedSingleTower(tower.getId());
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.single_tower.locate_success", tower.getDesignLabel()),
                ProjectStatusSeverity.SUCCESS);
        }
    }

    public void requestDelete(String towerId) {
        if (towerId == null || state.findPlacedSingleTower(towerId) == null) {
            return;
        }
        state.setPendingDeletePlacedSingleTowerId(towerId);
        state.setPlacedSingleTowerDeleteConfirmPending(true);
    }

    public void deleteTower(String towerId) {
        PlacedSingleTower tower = state.findPlacedSingleTower(towerId);
        if (tower == null) {
            return;
        }
        List<BlockRecord> records = tower.getBlockRecords();
        if (records.isEmpty()) {
            state.removePlacedSingleTower(towerId);
            state.clearPlacedSingleTowerSelectionIf(towerId);
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.single_tower.deleted", tower.getDesignLabel()),
                ProjectStatusSeverity.INFO);
            return;
        }
        if (PluginProjectionContext.tryCapture(host.coordinates()).isEmpty()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.projection_unavailable"),
                ProjectStatusSeverity.ERROR);
            return;
        }
        com.plot.api.world.PlacementReadiness readiness = host.projection().checkWorldModificationReadiness();
        if (!readiness.ready()) {
            state.setProjectStatus(readiness.message(), ProjectStatusSeverity.ERROR);
            return;
        }
        if (host.placement().isBusy()) {
            state.setProjectStatus(
                PlotI18n.tr("plugin.powerline.build_in_progress_wait"),
                ProjectStatusSeverity.WARNING);
            return;
        }

        SingleTowerRemoveCommand command = new SingleTowerRemoveCommand(
            tower,
            host.projection(),
            host.placement());
        state.setProjectStatus(
            PlotI18n.tr("plugin.powerline.single_tower.remove_in_progress", tower.getDesignLabel()),
            ProjectStatusSeverity.INFO);
        command.executeScheduled(() -> {
            if (command.hasAppliedRecords()) {
                host.commands().pushExecuted(command);
            }
            state.removePlacedSingleTower(towerId);
            state.clearPlacedSingleTowerSelectionIf(towerId);
            var result = command.getLastExecutionResult();
            if (result != null && result.isFullSuccess()) {
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.single_tower.deleted", tower.getDesignLabel()),
                    ProjectStatusSeverity.SUCCESS);
            } else if (result != null && result.success() > 0) {
                state.setProjectStatus(
                    PlotI18n.tr(
                        "plugin.powerline.single_tower.deleted_partial",
                        tower.getDesignLabel(),
                        result.success(),
                        result.total()),
                    ProjectStatusSeverity.WARNING);
            } else {
                state.setProjectStatus(
                    PlotI18n.tr("plugin.powerline.single_tower.delete_failed", tower.getDesignLabel()),
                    ProjectStatusSeverity.WARNING);
            }
        });
    }

    public String resolveStyleLineName(PlacedSingleTower tower, PowerLineProject project) {
        if (tower == null || project == null) {
            return "";
        }
        String styleLineId = tower.getStyleLineId();
        if (styleLineId == null || styleLineId.isBlank()) {
            return PlotI18n.tr("plugin.powerline.single_tower.style_unknown");
        }
        PowerLineFootprint line = project.getLines().get(styleLineId);
        if (line == null) {
            return PlotI18n.tr("plugin.powerline.single_tower.style_missing");
        }
        return line.getName();
    }
}
