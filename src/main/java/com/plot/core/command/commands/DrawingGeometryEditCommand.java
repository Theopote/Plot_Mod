package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.ui.tools.impl.drawing.helper.DrawingGeometrySnapshot;
import com.plot.ui.tools.impl.drawing.helper.PolylineDrawingSession;
import com.plot.utils.PlotI18n;

public class DrawingGeometryEditCommand implements Command {
    private final DrawingGeometrySnapshot beforeSnapshot;
    private final DrawingGeometrySnapshot afterSnapshot;

    public DrawingGeometryEditCommand(DrawingGeometrySnapshot beforeSnapshot, DrawingGeometrySnapshot afterSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
    }

    @Override
    public void execute() {
    }

    @Override
    public void undo() {
        PolylineDrawingSession.apply(beforeSnapshot);
    }

    @Override
    public void redo() {
        PolylineDrawingSession.apply(afterSnapshot);
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.drawing_geometry");
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.drawing_geometry.detail", beforeSnapshot.getKind().name());
    }
}
