package com.plot.ui.canvas;

import com.plot.api.model.ILayer;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.base.Event;
import com.plot.infrastructure.event.view.GridToggleEvent;
import com.plot.infrastructure.event.view.OpacityChangeEvent;
import com.plot.infrastructure.event.shape.ShapeAddedEvent;
import com.plot.core.layer.LayerEventSystem;
import com.plot.ui.grid.GridSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 画布事件处理类
 * 
 * 负责处理所有事件监听和响应，包括图层、工具和视图事件
 */
public class CanvasEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasEventHandler.class);

    private final CanvasCore core;
    private final EventBus eventBus;
    private final CanvasGrid grid;
    private boolean showGrid = true;
    private GridSettings gridSettings = new GridSettings();

    /**
     * 构造函数
     * @param core 画布核心对象
     * @param grid 画布网格对象
     */
    public CanvasEventHandler(CanvasCore core, CanvasGrid grid) {
        LOGGER.debug("初始化CanvasEventHandler，core={}, grid={}", core, grid);
        
        if (core == null) {
            LOGGER.error("CanvasCore对象为null，这可能导致事件处理异常");
        }
        
        if (grid == null) {
            LOGGER.error("CanvasGrid对象为null，这可能导致网格相关事件处理异常");
        }
        
        this.core = core;
        this.grid = grid;
        this.eventBus = EventBus.getInstance();
        
        LOGGER.debug("开始注册事件监听器");
        registerEventListeners();
        LOGGER.debug("事件监听器注册完成");
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        eventBus.subscribe(OpacityChangeEvent.class, this::handleOpacityChange);
        eventBus.subscribe(GridToggleEvent.class, this::handleGridToggle);
        eventBus.subscribe(LayerEventSystem.LayerCreatedEvent.class, this::handleLayerCreated);
        eventBus.subscribe(LayerEventSystem.LayerRemovedEvent.class, this::handleLayerRemoved);
        eventBus.subscribe(LayerEventSystem.LayerActivatedEvent.class, this::handleLayerActivated);
        eventBus.subscribe(LayerEventSystem.LayerPropertyChangedEvent.class, this::handleLayerPropertyChanged);
        eventBus.subscribe(LayerEventSystem.LayerOrderChangedEvent.class, this::handleLayerOrderChanged);
        eventBus.subscribe(LayerEventSystem.LayerContentChangedEvent.class, this::handleLayerContentChanged);
        eventBus.subscribe(ShapeAddedEvent.class, this::handleShapeAdded);
    }

    /**
     * 处理透明度变更事件
     */
    private void handleOpacityChange(Event event) {
        if (event instanceof OpacityChangeEvent opacityEvent) {
            core.setOpacity(opacityEvent.getOpacity());
            LOGGER.debug("处理透明度变更事件: {}", opacityEvent.getOpacity());
        }
    }

    /**
     * 处理网格显示切换事件
     */
    private void handleGridToggle(Event event) {
        try {
            LOGGER.debug("====== 收到网格切换事件 ======");
            
            if (!(event instanceof GridToggleEvent gridEvent)) {
                LOGGER.warn("收到的事件不是GridToggleEvent: {}", event.getClass().getName());
                return;
            }
            
            // 输出事件详情
            LOGGER.debug("GridToggleEvent详情: 启用状态={}, 来源={}", 
                gridEvent.isEnabled(), gridEvent.getSource());
            
            // 更新本地状态
            boolean oldState = showGrid;
            showGrid = gridEvent.isEnabled();
            LOGGER.debug("更新本地网格显示状态: {} -> {}", oldState, showGrid);
            
            // 更新网格设置（如果有）
            if (gridEvent.getSettings() != null) {
                gridSettings = gridEvent.getSettings();
                LOGGER.debug("更新网格设置: {}", gridSettings);
            }
            
            // 更新网格对象
            if (grid != null) {
                LOGGER.debug("更新CanvasGrid可见性: {} -> {}", grid.isVisible(), showGrid);
                grid.setVisible(showGrid);
                
                // 更新网格设置
                if (gridEvent.getSettings() != null) {
                    grid.setGridSettings(gridSettings);
                    LOGGER.debug("应用网格设置到CanvasGrid");
                }
                
                // 标记需要重绘
                core.markDirty(CanvasCore.DirtyType.CONTENT);
                LOGGER.debug("标记画布需要重绘");
            } else {
                LOGGER.error("网格对象为null，无法更新网格状态");
            }
        } catch (Exception e) {
            LOGGER.error("处理网格切换事件时发生错误", e);
        }
    }

    /**
     * 处理图层创建事件
     */
    private void handleLayerCreated(Event event) {
        if (event instanceof LayerEventSystem.LayerCreatedEvent layerEvent) {
            LOGGER.debug("处理图层创建事件: {}", layerEvent.getDescription());
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 处理图层移除事件
     */
    private void handleLayerRemoved(Event event) {
        if (event instanceof LayerEventSystem.LayerRemovedEvent layerEvent) {
            LOGGER.debug("处理图层移除事件: {}", layerEvent.getDescription());
            // 如果当前图层被移除，需要更新当前图层引用
            if (core.getCurrentLayer() != null && layerEvent.getLayer() == core.getCurrentLayer()) {
                ILayer newActiveLayer = core.getLayers().isEmpty() ? null : core.getLayers().getFirst();
                core.setCurrentLayer(newActiveLayer);
                LOGGER.debug("当前图层被移除，更新为: {}", 
                    newActiveLayer != null ? newActiveLayer.getName() : "null");
            }
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 处理图层激活事件
     */
    private void handleLayerActivated(Event event) {
        if (event instanceof LayerEventSystem.LayerActivatedEvent layerEvent) {
            LOGGER.debug("处理图层激活事件: {}", layerEvent.getDescription());
            String layerId = layerEvent.getLayerId();
            for (ILayer layer : core.getLayers()) {
                if (layer.getId().equals(layerId)) {
                    if (core.getCurrentLayer() != layer) {  // 只在图层实际改变时更新
                        core.setCurrentLayer(layer);
                        LOGGER.debug("当前图层已更改为: {}", layer.getName());
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * 处理图层顺序变更事件
     */
    private void handleLayerOrderChanged(Event event) {
        if (event instanceof LayerEventSystem.LayerOrderChangedEvent orderEvent) {
            LOGGER.debug("处理图层顺序变更事件: {}", orderEvent.getDescription());
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 处理图层内容变更事件
     */
    private void handleLayerContentChanged(Event event) {
        if (event instanceof LayerEventSystem.LayerContentChangedEvent contentEvent) {
            LOGGER.debug("处理图层内容变更事件: {}", contentEvent.getDescription());
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 处理图层属性变更事件
     */
    private void handleLayerPropertyChanged(Event event) {
        if (event instanceof LayerEventSystem.LayerPropertyChangedEvent propEvent) {
            LOGGER.debug("处理图层属性变更事件: {}", propEvent.getDescription());
            String propertyName = propEvent.getPropertyName();
            switch (propertyName.toLowerCase()) {
                case "visibility":
                case "visible":
                    LOGGER.debug("图层可见性已更改");
                    core.markDirty(CanvasCore.DirtyType.CONTENT);
                    break;
                case "opacity":
                    LOGGER.debug("图层透明度已更改");
                    core.markDirty(CanvasCore.DirtyType.CONTENT);
                    break;
                case "locked":
                case "lock_state":
                    LOGGER.debug("图层锁定状态已更改");
                    core.markDirty(CanvasCore.DirtyType.CONTENT);
                    break;
                case "linestyle":
                case "line_style":
                    LOGGER.debug("图层线型已更改");
                    core.markDirty(CanvasCore.DirtyType.CONTENT);
                    break;
                case "color":
                    LOGGER.debug("图层颜色已更改");
                    core.markDirty(CanvasCore.DirtyType.CONTENT);
                    break;
                case "name":
                    LOGGER.debug("图层名称已更改");
                    break;
                case "zorder":
                case "z_order":
                    LOGGER.debug("图层Z序已更改");
                    core.markDirty(CanvasCore.DirtyType.CONTENT);
                    break;
                case "active":
                    LOGGER.debug("图层激活状态已更改");
                    break;
                default:
                    LOGGER.debug("未处理的图层属性变更类型: {}", propertyName);
                    break;
            }
        }
    }

    /**
     * 处理图形添加事件
     */
    private void handleShapeAdded(Event event) {
        if (event instanceof ShapeAddedEvent shapeEvent) {
            LOGGER.debug("处理图形添加事件: {}", shapeEvent);
            core.markDirty(CanvasCore.DirtyType.CONTENT);  // 标记画布需要重绘
        }
    }

    /**
     * 取消订阅所有事件
     */
    public void unsubscribeAll() {
        eventBus.unsubscribe(OpacityChangeEvent.class, this::handleOpacityChange);
        eventBus.unsubscribe(GridToggleEvent.class, this::handleGridToggle);
        eventBus.unsubscribe(LayerEventSystem.LayerCreatedEvent.class, this::handleLayerCreated);
        eventBus.unsubscribe(LayerEventSystem.LayerRemovedEvent.class, this::handleLayerRemoved);
        eventBus.unsubscribe(LayerEventSystem.LayerActivatedEvent.class, this::handleLayerActivated);
        eventBus.unsubscribe(LayerEventSystem.LayerPropertyChangedEvent.class, this::handleLayerPropertyChanged);
        eventBus.unsubscribe(LayerEventSystem.LayerOrderChangedEvent.class, this::handleLayerOrderChanged);
        eventBus.unsubscribe(LayerEventSystem.LayerContentChangedEvent.class, this::handleLayerContentChanged);
        eventBus.unsubscribe(ShapeAddedEvent.class, this::handleShapeAdded);
    }

    /**
     * 关闭资源
     */
    public void close() {
        LOGGER.debug("释放CanvasEventHandler资源...");
        unsubscribeAll();
    }
} 