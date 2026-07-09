package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;
import java.util.ArrayList;
import java.util.List;

/**
 * 偏移命令
 */
public class OffsetCommand extends ModifyCommand {
    private final Shape shape;
    private final double distance;
    private final Vec2d point;
    private Shape offsetShape;

    public OffsetCommand(Shape shape, double distance, AppState appState) {
        super(List.of(shape), new ArrayList<>(), appState);
        this.shape = shape;
        this.distance = distance;
        this.point = null;
    }


    @Override
    public void execute() {
        offsetShape = shape.createOffset(distance);
        if (offsetShape != null) {
            newShapes.add(offsetShape);
        }
    }

    @Override
    public void undo() {
        if (offsetShape != null) {
            newShapes.remove(offsetShape);
        }
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.offset");
    }
}
