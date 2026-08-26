package com.plot.core.layer;

import com.plot.api.model.ILayer;
import com.plot.core.model.Shape;
import com.plot.core.spatial.SpatialIndexService;
import com.plot.core.state.SelectionState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.base.Event;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 图层与图形写入服务：拥有 LayerManager、活动图层、shape→layer 映射。
 */
public final class LayerService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/LayerService");

    private final ConcurrentMap<Shape, ILayer> shapeToLayerMap = new ConcurrentHashMap<>();
    private final AtomicReference<ILayer> activeLayer = new AtomicReference<>();
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final SpatialIndexService spatialIndexService;
    private final SelectionState selectionState;

    private LayerManager layerManager;

    public LayerService(SpatialIndexService spatialIndexService, SelectionState selectionState) {
        this.spatialIndexService = spatialIndexService;
        this.selectionState = selectionState;
        subscribeToLayerEvents();
    }

    private void subscribeToLayerEvents() {
        EventBus eventBus = EventBus.getInstance();
        eventBus.subscribe(LayerEventSystem.LayerActivatedEvent.class, this::handleLayerActivated);
        eventBus.subscribe(LayerEventSystem.LayerRemovedEvent.class, this::handleLayerRemoved);
        eventBus.subscribe(LayerEventSystem.SelectAllElementsInLayerEvent.class, this::handleSelectAllElementsInLayer);
    }

    private void handleLayerActivated(Event event) {
        if (event instanceof LayerEventSystem.LayerActivatedEvent layerEvent) {
            ILayer activatedLayer = layerEvent.getLayer();
            this.activeLayer.set(activatedLayer);
            LOGGER.debug("活动图层已同步更新为: {}",
                activatedLayer != null ? activatedLayer.getName() : "null");
        }
    }

    private void handleLayerRemoved(Event event) {
        if (!(event instanceof LayerEventSystem.LayerRemovedEvent layerEvent)) {
            return;
        }
        ILayer removedLayer = layerEvent.getLayer();
        ILayer currentActive = this.activeLayer.get();
        if (currentActive == removedLayer) {
            this.activeLayer.set(null);
        }
        shapeToLayerMap.entrySet().removeIf(entry -> entry.getValue() == removedLayer);
    }

    private void handleSelectAllElementsInLayer(Event event) {
        if (!(event instanceof LayerEventSystem.SelectAllElementsInLayerEvent selectEvent)) {
            return;
        }
        ILayer targetLayer = selectEvent.getLayer();
        if (targetLayer == null) {
            return;
        }
        try {
            List<Shape> shapesInLayer = targetLayer.getShapes().stream()
                .filter(shape -> shape != null && !shape.isDeleted())
                .collect(Collectors.toList());
            selectionState.setSelectedShapes(shapesInLayer);
        } catch (Exception e) {
            LOGGER.error("处理图层全部图元选择事件失败: {}", e.getMessage(), e);
        }
    }

    public void initialize() {
        if (this.layerManager == null) {
            LOGGER.info("初始化 Layer 系统...");
            this.layerManager = LayerManager.create();
        }
    }

    public void ensureDefaultLayer() {
        if (this.layerManager == null) {
            initialize();
        }
        if (this.layerManager.getLayerCount() == 0) {
            this.layerManager.createLayer(PlotI18n.defaultLayerName());
        } else {
            resolveStoredLayerNameKeys();
        }
        if (this.activeLayer.get() == null) {
            this.activeLayer.set(this.layerManager.getActiveLayer());
        }
    }

    private void resolveStoredLayerNameKeys() {
        for (ILayer layer : this.layerManager.getLayers()) {
            String storedName = layer.getName();
            if (storedName == null || !storedName.startsWith("layer.plot.")) {
                continue;
            }
            String resolvedName = PlotI18n.tr(storedName);
            if (resolvedName.equals(storedName) || layerManager.isNameExists(resolvedName)) {
                continue;
            }
            layerManager.updateLayerProperty(layer, "name", resolvedName);
        }
    }

    public LayerManager getLayerManager() {
        return layerManager;
    }

    public void addShape(Shape shape) {
        if (shape == null) {
            return;
        }

        ILayer layer = getActiveLayer();
        if (layer == null) {
            LOGGER.error("无法添加图形：没有活动的图层！");
            return;
        }
        if (layer.isLocked()) {
            LOGGER.warn("无法添加图形：活动图层 '{}' 已被锁定。", layer.getName());
            return;
        }

        layer.addShape(shape);
        shapeToLayerMap.put(shape, layer);
        spatialIndexService.update(shape);
        EventBus.getInstance().publish(
            new LayerEventSystem.LayerContentChangedEvent(layer.getId(), layer, "shape_added", shape));
    }

    public void removeShape(Shape shape) {
        if (shape == null) {
            return;
        }

        ILayer layer = shapeToLayerMap.get(shape);
        if (layer != null) {
            boolean removed = layer.removeShape(shape);
            if (removed) {
                shapeToLayerMap.remove(shape);
                spatialIndexService.remove(shape);
                EventBus.getInstance().publish(
                    new LayerEventSystem.LayerContentChangedEvent(layer.getId(), layer, "shape_removed", shape));
            } else {
                LOGGER.error("严重状态不一致！shapeToLayerMap 中记录图形 {} 在图层 '{}' 中，但图层中实际不存在",
                    shape.getId(), layer.getName());
                shapeToLayerMap.remove(shape);
            }
        } else {
            LOGGER.error("严重状态不一致！尝试移除不存在于 shapeToLayerMap 的图形: {} (ID: {})",
                shape.getClass().getSimpleName(), shape.getId());
        }
    }

    public List<ILayer> getLayers() {
        if (layerManager != null) {
            return layerManager.getLayers();
        }
        return Collections.emptyList();
    }

    public List<Shape> getShapes() {
        if (layerManager == null) {
            return Collections.emptyList();
        }
        stateLock.readLock().lock();
        try {
            return layerManager.getLayers().stream()
                .flatMap(layer -> layer.getShapes().stream())
                .collect(Collectors.toList());
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void setActiveLayer(ILayer layer) {
        if (layer != null && layerManager != null) {
            layerManager.setActiveLayer(layer);
            this.activeLayer.set(layer);
            syncActiveLayerFromManager(layer);
        }
    }

    public void syncActiveLayerFromManager(ILayer layer) {
        if (layer != null) {
            this.activeLayer.set(layer);
        }
    }

    public ILayer getActiveLayer() {
        return activeLayer.get();
    }

    public String getActiveLayerName() {
        ILayer layer = getActiveLayer();
        return layer != null ? PlotI18n.layerDisplayName(layer.getName()) : "无图层";
    }

    public void rebuildShapeToLayerMap() {
        shapeToLayerMap.clear();
        if (layerManager == null) {
            return;
        }
        for (ILayer layer : layerManager.getLayers()) {
            if (!(layer instanceof Layer concreteLayer)) {
                continue;
            }
            for (Shape shape : concreteLayer.getShapes()) {
                if (shape != null) {
                    shapeToLayerMap.put(shape, layer);
                }
            }
        }
    }

    public void clearMappings() {
        shapeToLayerMap.clear();
    }
}
