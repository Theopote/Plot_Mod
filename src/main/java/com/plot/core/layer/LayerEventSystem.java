package com.plot.core.layer;

import com.plot.api.event.EventType;
import com.plot.api.model.ILayer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.base.Event;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 图层事件系统
 * 统一管理所有图层相关的事件，避免重复定义和复杂的事件处理逻辑
 */
public class LayerEventSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayerEventSystem.class);
    
    private final EventBus eventBus;
    private final Map<String, List<Consumer<LayerEvent>>> eventListeners = new ConcurrentHashMap<>();
    
    // 事件类型常量
    public static final String LAYER_CREATED = "layer.created";
    public static final String LAYER_REMOVED = "layer.removed";
    public static final String LAYER_PROPERTY_CHANGED = "layer.property.changed";
    public static final String LAYER_ORDER_CHANGED = "layer.order.changed";
    public static final String LAYER_ACTIVATED = "layer.activated";
    public static final String LAYER_CONTENT_CHANGED = "layer.content.changed";
    
    public LayerEventSystem() {
        this(EventBus.getInstance());
    }
    
    public LayerEventSystem(EventBus eventBus) {
        this.eventBus = eventBus;
    }
    
    /**
     * 统一的图层事件基类
     */
    public abstract static class LayerEvent extends Event {
        protected final String layerId;
        protected final ILayer layer;
        protected final long timestamp;
        
        protected LayerEvent(String layerId, ILayer layer) {
            super(EventType.LAYER_CHANGED);
            this.layerId = layerId;
            this.layer = layer;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getLayerId() { return layerId; }
        public ILayer getLayer() { return layer; }

        @Override
        public String getSource() {
            return "LayerEventSystem";
        }
        
        public abstract String getEventType();
        public abstract String getDescription();
    }
    
    /**
     * 图层创建事件
     */
    public static class LayerCreatedEvent extends LayerEvent {
        public LayerCreatedEvent(String layerId, ILayer layer) {
            super(layerId, layer);
        }
        
        @Override
        public String getEventType() { return LAYER_CREATED; }
        
        @Override
        public String getDescription() {
            return PlotI18n.tr("layer.plot.event.created",
                    layer != null ? layer.getName() : layerId);
        }
    }
    
    /**
     * 图层移除事件
     */
    public static class LayerRemovedEvent extends LayerEvent {
        private final String layerName;
        
        public LayerRemovedEvent(String layerId, ILayer layer) {
            super(layerId, layer);
            this.layerName = layer != null ? layer.getName() : PlotI18n.tr("status.plot.unknown");
        }
        
        @Override
        public String getEventType() { return LAYER_REMOVED; }
        
        @Override
        public String getDescription() {
            return PlotI18n.tr("layer.plot.event.removed", layerName);
        }
        
    }
    
    /**
     * 图层属性变更事件
     */
    public static class LayerPropertyChangedEvent extends LayerEvent {
        private final String propertyName;
        private final Object oldValue;
        private final Object newValue;
        
        public LayerPropertyChangedEvent(String layerId, ILayer layer, String propertyName, 
                                       Object oldValue, Object newValue) {
            super(layerId, layer);
            this.propertyName = propertyName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        
        @Override
        public String getEventType() { return LAYER_PROPERTY_CHANGED; }
        
        @Override
        public String getDescription() {
            return PlotI18n.tr("layer.plot.event.property_changed",
                    layer != null ? layer.getName() : layerId,
                    PlotI18n.layerPropertyLabel(propertyName),
                    oldValue, newValue);
        }
        
        public String getPropertyName() { return propertyName; }
    }
    
    /**
     * 图层顺序变更事件
     */
    public static class LayerOrderChangedEvent extends LayerEvent {
        private final int fromIndex;
        private final int toIndex;
        
        public LayerOrderChangedEvent(String layerId, ILayer layer, int fromIndex, int toIndex) {
            super(layerId, layer);
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }
        
        @Override
        public String getEventType() { return LAYER_ORDER_CHANGED; }
        
        @Override
        public String getDescription() {
            return PlotI18n.tr("layer.plot.event.order_changed",
                    layer != null ? layer.getName() : layerId, fromIndex, toIndex);
        }

    }
    
    /**
     * 图层激活事件
     */
    public static class LayerActivatedEvent extends LayerEvent {
        private final ILayer previousActiveLayer;
        
        public LayerActivatedEvent(String layerId, ILayer layer, ILayer previousActiveLayer) {
            super(layerId, layer);
            this.previousActiveLayer = previousActiveLayer;
        }
        
        @Override
        public String getEventType() { return LAYER_ACTIVATED; }
        
        @Override
        public String getDescription() {
            String prevName = previousActiveLayer != null
                    ? previousActiveLayer.getName()
                    : PlotI18n.tr("status.plot.none");
            String currentName = layer != null ? layer.getName() : layerId;
            return PlotI18n.tr("layer.plot.event.activated", prevName, currentName);
        }
        
    }
    
    /**
     * 图层内容变更事件
     */
    public static class LayerContentChangedEvent extends LayerEvent {
        private final String changeType; // "element_added", "element_removed", "content_cleared" 等

        public LayerContentChangedEvent(String layerId, ILayer layer, String changeType, Object affectedElement) {
            super(layerId, layer);
            this.changeType = changeType;
        }
        
        @Override
        public String getEventType() { return LAYER_CONTENT_CHANGED; }
        
        @Override
        public String getDescription() {
            return PlotI18n.tr("layer.plot.event.content_changed",
                    layer != null ? layer.getName() : layerId,
                    PlotI18n.layerContentChangeLabel(changeType));
        }
    }
    
    /**
     * 选择图层所有元素事件
     */
    public static class SelectAllElementsInLayerEvent extends LayerEvent {
        public static final String SELECT_ALL_ELEMENTS = "layer.select_all_elements";
        
        public SelectAllElementsInLayerEvent(String layerId, ILayer layer) {
            super(layerId, layer);
        }
        
        @Override
        public String getEventType() { return SELECT_ALL_ELEMENTS; }
        
        @Override
        public String getDescription() {
            return PlotI18n.tr("layer.plot.event.select_all",
                    layer != null ? layer.getName() : layerId);
        }
    }
    
    // 事件发布方法
    
    public void publishLayerCreated(String layerId, ILayer layer) {
        LayerCreatedEvent event = new LayerCreatedEvent(layerId, layer);
        publishEvent(event);
        LOGGER.debug("发布图层创建事件: {}", event.getDescription());
    }
    
    public void publishLayerRemoved(String layerId, ILayer layer) {
        LayerRemovedEvent event = new LayerRemovedEvent(layerId, layer);
        publishEvent(event);
        LOGGER.debug("发布图层移除事件: {}", event.getDescription());
    }
    
    public void publishLayerPropertyChanged(String layerId, ILayer layer, String propertyName, 
                                          Object oldValue, Object newValue) {
        LayerPropertyChangedEvent event = new LayerPropertyChangedEvent(layerId, layer, propertyName, oldValue, newValue);
        publishEvent(event);
        LOGGER.debug("发布图层属性变更事件: {}", event.getDescription());
    }
    
    public void publishLayerOrderChanged(String layerId, ILayer layer, int fromIndex, int toIndex) {
        LayerOrderChangedEvent event = new LayerOrderChangedEvent(layerId, layer, fromIndex, toIndex);
        publishEvent(event);
        LOGGER.debug("发布图层顺序变更事件: {}", event.getDescription());
    }
    
    public void publishLayerActivated(String layerId, ILayer layer, ILayer previousActiveLayer) {
        LayerActivatedEvent event = new LayerActivatedEvent(layerId, layer, previousActiveLayer);
        publishEvent(event);
        LOGGER.debug("发布图层激活事件: {}", event.getDescription());
    }
    
    public void publishLayerContentChanged(String layerId, ILayer layer, String changeType, Object affectedElement) {
        LayerContentChangedEvent event = new LayerContentChangedEvent(layerId, layer, changeType, affectedElement);
        publishEvent(event);
        LOGGER.debug("发布图层内容变更事件: {}", event.getDescription());
    }
    
    // 私有方法
    
    private void publishEvent(LayerEvent event) {
        // 首先通过EventBus发布
        if (eventBus != null) {
            eventBus.publish(event);
        }
        
        // 然后通知本地监听器
        List<Consumer<LayerEvent>> listeners = eventListeners.get(event.getEventType());
        if (listeners != null) {
            for (Consumer<LayerEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    LOGGER.error("执行图层事件监听器时发生错误: {}", e.getMessage(), e);
                }
            }
        }
    }
} 