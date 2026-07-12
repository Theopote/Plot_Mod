package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.ui.panel.layer.LayerEditHistory;
import com.plot.utils.PlotI18n;
import com.plot.api.model.ILayer;

import java.awt.Color;
import java.util.List;

public class LayerColorEditCommand implements Command {
    private final String layerId;
    private final LayerEditHistory.LayerColorState beforeState;
    private final LayerEditHistory.LayerColorState afterState;

    public LayerColorEditCommand(
            String layerId,
            LayerEditHistory.LayerColorState beforeState,
            LayerEditHistory.LayerColorState afterState) {
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
        return PlotI18n.tr("history.plot.layer_color");
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr(
                "history.plot.layer_color.detail",
                formatColor(beforeState.layerColor()),
                formatColor(afterState.layerColor()));
    }

    private void applyState(LayerEditHistory.LayerColorState state) {
        Layer layer = findLayer();
        if (layer == null || state == null) {
            return;
        }

        LayerManager layerManager = AppState.getInstance().getLayerManager();
        if (layerManager != null) {
            layerManager.updateLayerProperty(layer, "color", state.layerColor());
        }
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

    private static String formatColor(Color color) {
        if (color == null) {
            return "-";
        }
        return String.format("#%08X", color.getRGB());
    }
}
