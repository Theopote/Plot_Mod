package com.masterplanner.core.command.commands;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
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

    public OffsetCommand(Shape shape, Vec2d point, AppState appState) {
        super(List.of(shape), new ArrayList<>(), appState);
        this.shape = shape;
        this.point = point;
        this.distance = point.distance(shape.getPosition());
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
        return "偏移图形";
    }
}
