package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.core.layer.LayerManager;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;
import com.plot.api.model.ILayer;

public class LayerPropertyEditCommand implements Command {
    private final String layerId;
    private final String property;
    private final Object beforeValue;
    private final Object afterValue;

    public LayerPropertyEditCommand(String layerId, String property, Object beforeValue, Object afterValue) {
        this.layerId = layerId;
        this.property = property;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    @Override
    public void execute() {
        // 属性面板已实时应用，首次执行无需重复写入。
    }

    @Override
    public void undo() {
        applyValue(beforeValue);
    }

    @Override
    public void redo() {
        applyValue(afterValue);
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.layer_property", propertyLabel(property));
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.layer_property.detail", propertyLabel(property), beforeValue, afterValue);
    }

    private void applyValue(Object value) {
        LayerManager layerManager = AppState.getInstance().getLayerManager();
        if (layerManager == null) {
            return;
        }
        ILayer layer = layerManager.getLayerById(layerId);
        if (layer == null) {
            return;
        }
        layerManager.updateLayerProperty(layer, property, value);
    }

    private static String propertyLabel(String property) {
        return switch (property) {
            case "name" -> PlotI18n.tr("history.plot.layer_property.name");
            case "locked" -> PlotI18n.tr("history.plot.layer_property.locked");
            case "visible", "visibility" -> PlotI18n.tr("history.plot.layer_property.visible");
            default -> property;
        };
    }
}
