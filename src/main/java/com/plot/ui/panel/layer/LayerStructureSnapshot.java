package com.plot.ui.panel.layer;

import com.plot.core.graphics.style.LineStyle;
import com.plot.core.layer.Layer;
import com.plot.core.layer.LayerManager;
import com.plot.api.model.ILayer;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 图层结构快照，用于撤销/重做图层创建、删除、合并、复制与排序。
 */
public final class LayerStructureSnapshot {
    private final List<LayerEntry> layers;
    private final String activeLayerId;

    private LayerStructureSnapshot(List<LayerEntry> layers, String activeLayerId) {
        this.layers = List.copyOf(layers);
        this.activeLayerId = activeLayerId;
    }

    public static LayerStructureSnapshot capture() {
        AppState appState = AppState.getInstance();
        LayerManager layerManager = appState.getLayerManager();
        List<LayerEntry> entries = new ArrayList<>();
        for (ILayer layer : layerManager.getLayers()) {
            if (layer instanceof Layer concreteLayer) {
                entries.add(LayerEntry.from(concreteLayer));
            }
        }
        ILayer activeLayer = layerManager.getActiveLayer();
        String activeId = activeLayer != null ? activeLayer.getId() : null;
        return new LayerStructureSnapshot(entries, activeId);
    }

    public void apply() {
        AppState appState = AppState.getInstance();
        LayerManager layerManager = appState.getLayerManager();

        Set<String> targetIds = new HashSet<>();
        for (LayerEntry entry : layers) {
            targetIds.add(entry.layerId());
        }

        for (LayerEntry entry : layers) {
            if (layerManager.getLayerById(entry.layerId()) == null) {
                layerManager.addLayer(entry.layer());
            }
        }

        boolean removed;
        do {
            removed = false;
            for (ILayer layer : new ArrayList<>(layerManager.getLayers())) {
                if (!targetIds.contains(layer.getId()) && layerManager.getLayerCount() > 1) {
                    layerManager.removeLayer(layer);
                    removed = true;
                    break;
                }
            }
        } while (removed);

        List<ILayer> orderedLayers = new ArrayList<>();
        for (LayerEntry entry : layers) {
            ILayer layer = layerManager.getLayerById(entry.layerId());
            if (layer != null) {
                orderedLayers.add(layer);
            }
        }
        layerManager.restoreLayerOrder(orderedLayers);

        for (LayerEntry entry : layers) {
            ILayer layer = layerManager.getLayerById(entry.layerId());
            if (layer instanceof Layer concreteLayer) {
                entry.applyPropertiesTo(concreteLayer);
                syncLayerShapes(concreteLayer, entry.shapes(), layerManager);
            }
        }

        ILayer activeLayer = activeLayerId != null ? layerManager.getLayerById(activeLayerId) : null;
        if (activeLayer != null) {
            layerManager.setActiveLayer(activeLayer);
        }

        appState.rebuildShapeToLayerMap();
        appState.rebuildSpatialIndex();
    }

    public boolean sameStructureAs(LayerStructureSnapshot other) {
        if (other == null) {
            return false;
        }
        if (!Objects.equals(activeLayerId, other.activeLayerId)) {
            return false;
        }
        if (layers.size() != other.layers.size()) {
            return false;
        }
        for (int i = 0; i < layers.size(); i++) {
            if (!layers.get(i).sameAs(other.layers.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static void syncLayerShapes(Layer layer, List<Shape> targetShapes, LayerManager layerManager) {
        List<Shape> current = new ArrayList<>(layer.getShapes());
        Set<Shape> targetSet = new HashSet<>(targetShapes);

        for (Shape shape : current) {
            if (!targetSet.contains(shape)) {
                layer.removeShape(shape);
            }
        }

        for (Shape shape : targetShapes) {
            if (shape == null) {
                continue;
            }
            removeShapeFromAllLayers(shape, layerManager);
            if (!layer.getShapes().contains(shape)) {
                layer.addShape(shape);
            }
        }
    }

    private static void removeShapeFromAllLayers(Shape shape, LayerManager layerManager) {
        for (ILayer candidate : layerManager.getLayers()) {
            if (candidate instanceof Layer concreteLayer) {
                concreteLayer.removeShape(shape);
            }
        }
    }

    private record LayerEntry(
            Layer layer,
            String layerId,
            String name,
            Color color,
            LineStyle lineStyle,
            boolean visible,
            boolean locked,
            double opacity,
            int zOrder,
            List<Shape> shapes) {

        static LayerEntry from(Layer source) {
            LineStyle lineStyle = source.getLineStyle();
            LineStyle copy = lineStyle != null ? (LineStyle) lineStyle.clone() : new LineStyle();
            Color color = source.getColor();
            Color colorCopy = color != null ? new Color(color.getRGB(), true) : Color.WHITE;
            return new LayerEntry(
                    source,
                    source.getId(),
                    source.getName(),
                    colorCopy,
                    copy,
                    source.isVisible(),
                    source.isLocked(),
                    source.getOpacity(),
                    source.getZOrder(),
                    new ArrayList<>(source.getShapes())
            );
        }

        void applyPropertiesTo(Layer target) {
            target.setName(name);
            target.setColor(color != null ? new Color(color.getRGB(), true) : Color.WHITE);
            target.setLineStyle(lineStyle != null ? (LineStyle) lineStyle.clone() : new LineStyle());
            target.setVisible(visible);
            target.setLocked(locked);
            target.setOpacity(opacity);
            target.setZOrder(zOrder);
        }

        boolean sameAs(LayerEntry other) {
            if (other == null) {
                return false;
            }
            if (!Objects.equals(layerId, other.layerId)) {
                return false;
            }
            if (!Objects.equals(name, other.name)) {
                return false;
            }
            if (visible != other.visible || locked != other.locked) {
                return false;
            }
            if (Math.abs(opacity - other.opacity) > 1e-6 || zOrder != other.zOrder) {
                return false;
            }
            if (color != null && other.color != null) {
                if (color.getRGB() != other.color.getRGB()) {
                    return false;
                }
            } else if (color != other.color) {
                return false;
            }
            if (lineStyle != null && other.lineStyle != null) {
                if (lineStyle.getType() != other.lineStyle.getType()
                        || Math.abs(lineStyle.getWidth() - other.lineStyle.getWidth()) > 1e-4f) {
                    return false;
                }
            } else if (lineStyle != other.lineStyle) {
                return false;
            }
            if (shapes.size() != other.shapes.size()) {
                return false;
            }
            for (int i = 0; i < shapes.size(); i++) {
                Shape a = shapes.get(i);
                Shape b = other.shapes.get(i);
                if (a == null || b == null) {
                    if (a != b) {
                        return false;
                    }
                } else if (!Objects.equals(a.getId(), b.getId())) {
                    return false;
                }
            }
            return true;
        }
    }
}
