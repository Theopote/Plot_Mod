package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.ui.panel.layer.LayerStructureSnapshot;
import com.plot.utils.PlotI18n;

public class LayerStructureEditCommand implements Command {
    private final LayerStructureSnapshot beforeSnapshot;
    private final LayerStructureSnapshot afterSnapshot;
    private final String operationKey;

    public LayerStructureEditCommand(
            LayerStructureSnapshot beforeSnapshot,
            LayerStructureSnapshot afterSnapshot,
            String operationKey) {
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.operationKey = operationKey != null ? operationKey : "history.plot.layer_structure";
    }

    @Override
    public void execute() {
    }

    @Override
    public void undo() {
        beforeSnapshot.apply();
    }

    @Override
    public void redo() {
        afterSnapshot.apply();
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr(operationKey);
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.layer_structure.detail", PlotI18n.tr(operationKey));
    }
}
