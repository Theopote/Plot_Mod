package com.plot.plugin.powerline.placement;

import com.plot.api.world.IBlockPlacementService;
import com.plot.api.world.IBlockProjectionService;
import com.plot.core.command.BlockRecord;
import com.plot.core.command.Command;
import com.plot.core.command.commands.PowerLineGenerateCommand;
import com.plot.utils.PlotI18n;

import java.util.Date;
import java.util.List;

/** 单塔落地命令：委托 {@link PowerLineGenerateCommand}，独立撤销历史描述。 */
public final class SingleTowerPlaceCommand implements Command {
    private final PowerLineGenerateCommand delegate;

    public SingleTowerPlaceCommand(
            List<BlockRecord> records,
            IBlockProjectionService projection,
            IBlockPlacementService placement) {
        this.delegate = new PowerLineGenerateCommand(records, projection, placement);
    }

    public void executeScheduled(Runnable onComplete) {
        delegate.executeScheduled(onComplete);
    }

    public PowerLineGenerateCommand.ExecutionResult getLastExecutionResult() {
        return delegate.getLastExecutionResult();
    }

    public boolean hasAppliedRecords() {
        return delegate.hasAppliedRecords();
    }

    @Override
    public void execute() {
        delegate.execute();
    }

    @Override
    public void undo() {
        delegate.undo();
    }

    @Override
    public void redo() {
        delegate.redo();
    }

    @Override
    public String getDescription() {
        int count = delegate.hasAppliedRecords()
            ? delegate.getAppliedRecordCount()
            : delegate.getLastExecutionResult() != null
                ? delegate.getLastExecutionResult().total()
                : 0;
        return PlotI18n.tr("plugin.powerline.single_tower.history.place", count);
    }

    @Override
    public String getDetailedDescription() {
        int count = delegate.hasAppliedRecords()
            ? delegate.getAppliedRecordCount()
            : delegate.getLastExecutionResult() != null
                ? delegate.getLastExecutionResult().total()
                : 0;
        return PlotI18n.tr("plugin.powerline.single_tower.history.place.detail", count);
    }

    @Override
    public Date getTimestamp() {
        return delegate.getTimestamp();
    }
}
