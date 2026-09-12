package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.Command;
import com.plot.core.command.commands.PowerLineGenerateCommand;
import com.plot.plugin.powerline.model.PlacedSingleTower;
import com.plot.utils.PlotI18n;

import java.util.Date;
import java.util.List;

/** 移除已落地单塔：恢复方块；撤销/重做可往返。 */
public final class SingleTowerRemoveCommand implements Command {
    private final PlacedSingleTower tower;
    private final PowerLineGenerateCommand delegate;

    public SingleTowerRemoveCommand(
            PlacedSingleTower tower,
            IBlockProjectionService projection,
            IBlockPlacementService placement) {
        this.tower = tower;
        List<BlockRecord> records = tower != null ? tower.getBlockRecords() : List.of();
        this.delegate = new PowerLineGenerateCommand(records, projection, placement);
    }

    public PlacedSingleTower getTower() {
        return tower;
    }

    public void executeScheduled(Runnable onComplete) {
        delegate.restoreBlocksScheduled(onComplete);
    }

    public PowerLineGenerateCommand.ExecutionResult getLastExecutionResult() {
        return delegate.getLastExecutionResult();
    }

    public boolean hasAppliedRecords() {
        return delegate.hasAppliedRecords();
    }

    @Override
    public void execute() {
        delegate.restoreBlocksScheduled(() -> { });
    }

    @Override
    public void undo() {
        delegate.reapplyBlocksScheduled(() -> { });
    }

    @Override
    public void redo() {
        delegate.restoreBlocksScheduled(() -> { });
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr(
            "plugin.powerline.single_tower.history.remove",
            tower != null ? tower.getDesignLabel() : "");
    }

    @Override
    public String getDetailedDescription() {
        int blocks = tower != null ? tower.getBlockCount() : 0;
        return PlotI18n.tr("plugin.powerline.single_tower.history.remove.detail", blocks);
    }

    @Override
    public Date getTimestamp() {
        return delegate.getTimestamp();
    }
}
