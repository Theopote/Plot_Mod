package com.plot.plugin.powerline.placement;

import com.plot.core.command.Command;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.plugin.powerline.ui.PowerLinePluginState;

/** 世界命令撤销/重做与单塔登记簿同步。 */
public final class PowerLineWorldCommandSync {
    private PowerLineWorldCommandSync() {
    }

    public static void afterUndo(Command command, PowerLinePluginState state) {
        if (state == null || command == null) {
            return;
        }
        if (command instanceof SingleTowerPlaceCommand placeCommand) {
            PlacedSingleTower tower = placeCommand.getRegisteredTower();
            if (tower == null) {
                tower = placeCommand.getTower();
            }
            if (tower != null) {
                state.removePlacedSingleTower(tower.getId());
                state.clearPlacedSingleTowerSelectionIf(tower.getId());
            } else {
                state.removeLastPlacedSingleTower();
            }
            return;
        }
        if (command instanceof SingleTowerRemoveCommand removeCommand) {
            PlacedSingleTower tower = removeCommand.getTower();
            if (tower != null) {
                state.addPlacedSingleTower(tower);
                state.selectPlacedSingleTower(tower.getId());
            }
        }
    }

    public static void afterRedo(Command command, PowerLinePluginState state) {
        if (state == null || command == null) {
            return;
        }
        if (command instanceof SingleTowerPlaceCommand placeCommand) {
            PlacedSingleTower tower = placeCommand.getRegisteredTower();
            if (tower != null) {
                state.addPlacedSingleTower(tower);
                state.selectPlacedSingleTower(tower.getId());
            }
            return;
        }
        if (command instanceof SingleTowerRemoveCommand removeCommand) {
            PlacedSingleTower tower = removeCommand.getTower();
            if (tower != null) {
                state.removePlacedSingleTower(tower.getId());
                state.clearPlacedSingleTowerSelectionIf(tower.getId());
            }
        }
    }
}
