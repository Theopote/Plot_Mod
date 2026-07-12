package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.panel.layer.LayerEditHistory;
import com.plot.utils.PlotI18n;
import com.plot.api.model.ILayer;

import java.util.List;

public class LayerLineStyleEditCommand implements Command {
    private final String layerId;
    private final LayerEditHistory.LayerLineStyleState beforeState;
    private final LayerEditHistory.LayerLineStyleState afterState;

    public LayerLineStyleEditCommand(
            String layerId,
            LayerEditHistory.LayerLineStyleState beforeState,
            LayerEditHistory.LayerLineStyleState afterState) {
        this.layerId = layerId;
        this.beforeState = beforeState;
        this.afterState = afterState;
    }

    @Override
    public void execute() {
    }

    @Override
    public void undo() {
        applyState(beforeState);
    }

    @Override
    public void redo() {
        applyState(afterState);
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.layer_line_style");
    }

    @Override
    public String getDetailedDescription() {
        LineStyle before = beforeState.lineStyle();
        LineStyle after = afterState.lineStyle();
        return PlotI18n.tr(
                "history.plot.layer_line_style.detail",
                before != null ? before.getType() : "-",
                before != null ? before.getWidth() : 0.0f,
                after != null ? after.getType() : "-",
                after != null ? after.getWidth() : 0.0f);
    }

    private void applyState(LayerEditHistory.LayerLineStyleState state) {
        Layer layer = findLayer();
        if (layer == null || state == null || state.lineStyle() == null) {
            return;
        }

        LineStyle restored = (LineStyle) state.lineStyle().clone();
        layer.setLineStyle(restored);
        restoreShapeStyles(state.shapeStyles());
    }

    private Layer findLayer() {
        LayerManager layerManager = AppState.getInstance().getLayerManager();
        if (layerManager == null) {
            return null;
        }
        ILayer layer = layerManager.getLayerById(layerId);
        return layer instanceof Layer concreteLayer ? concreteLayer : null;
    }

    private void restoreShapeStyles(List<LayerEditHistory.ShapeStyleRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Shape shape : AppState.getInstance().getShapes()) {
            if (shape == null) {
                continue;
            }
            for (LayerEditHistory.ShapeStyleRecord record : records) {
                if (record.shapeId().equals(shape.getId())) {
                    shape.setStyle((ShapeStyle) record.style().clone());
                    break;
                }
            }
        }
    }
}
