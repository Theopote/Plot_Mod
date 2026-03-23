package com.plot.core.layer;

import com.plot.api.model.ILayer;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 优化后的图层管理器
 * 
 * 主要改进：
 * 1. 使用新的LayerEventSystem统一事件处理
 * 2. 更好的线程安全设计
 * 3. 性能优化的图层操作
 * 4. 简化的API设计
 * 5. 更好的错误处理和日志记录
 */
public class LayerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayerManager.class);
    
    // 常量
    private static final int MAX_LAYERS = 100;

    // 核心组件
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<ILayer> layers = new CopyOnWriteArrayList<>();
    private volatile ILayer activeLayer;
    private final LayerEventSystem eventSystem;
    private final AtomicInteger nextZOrder = new AtomicInteger(0);
    
    // 缓存和索引
    private volatile Map<String, ILayer> layerIndex = new HashMap<>();
    private volatile boolean indexDirty = true;

    /**
     * 私有构造函数
     */
    private LayerManager() {
        // 依赖
        AppState appState = AppState.getInstance();
        EventBus eventBus = EventBus.getInstance();
        this.eventSystem = new LayerEventSystem(eventBus);
        
        LOGGER.debug("创建优化图层管理器");
    }
    
    /**
     * 创建LayerManager实例
     */
    public static LayerManager create() {
        return new LayerManager();
    }

    /**
     * 图层创建结果
     */
    public static class LayerCreationResult {
        private final boolean success;
        private final ILayer layer;
        private final String message;
        
        public LayerCreationResult(boolean success, ILayer layer, String message) {
            this.success = success;
            this.layer = layer;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public ILayer getLayer() { return layer; }
        public String getMessage() { return message; }
    }
    
    /**
     * 创建新图层
     */
    public LayerCreationResult createLayer(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new LayerCreationResult(false, null, "图层名称不能为空");
        }
        
        // 添加字符编码调试信息
        String trimmedName = name.trim();
        LOGGER.info("LayerManager.createLayer - 接收到名称: '{}'", name);
        LOGGER.info("LayerManager.createLayer - 修剪后名称: '{}'", trimmedName);
        LOGGER.info("LayerManager.createLayer - 名称字节长度: {}", trimmedName.getBytes().length);
        LOGGER.info("LayerManager.createLayer - 名称字符长度: {}", trimmedName.length());
        
        lock.writeLock().lock();
        try {
            // 检查数量限制
            if (layers.size() >= MAX_LAYERS) {
                return new LayerCreationResult(false, null, "已达到最大图层数量限制");
            }
            
            // 检查名称重复
            if (isNameExists(trimmedName)) {
                return new LayerCreationResult(false, null, "图层名称已存在");
            }
            
            // 创建新图层
            Layer newLayer = new Layer(trimmedName);
            newLayer.setZOrder(nextZOrder.getAndIncrement());
            
            // 验证创建的图层名称
            LOGGER.info("LayerManager.createLayer - 新图层名称: '{}'", newLayer.getName());
            LOGGER.info("LayerManager.createLayer - 新图层名称字节长度: {}", newLayer.getName().getBytes().length);
            LOGGER.info("LayerManager.createLayer - 新图层名称字符长度: {}", newLayer.getName().length());
            
            // 添加到管理器
            addLayerInternal(newLayer);
            
            // 如果是第一个图层，设为活动图层
            if (activeLayer == null) {
                setActiveLayerInternal(newLayer);
            }
            
            // 发布事件
            eventSystem.publishLayerCreated(newLayer.getId(), newLayer);
            
            LOGGER.info("创建新图层: {}", newLayer.getName());
            return new LayerCreationResult(true, newLayer, "图层创建成功");
            
        } catch (Exception e) {
            String errorMsg = "创建图层失败: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            return new LayerCreationResult(false, null, errorMsg);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 添加已存在的图层
     */
    public void addLayer(ILayer layer) {
        if (layer == null) {
            LOGGER.warn("尝试添加空图层");
            return;
        }
        
        lock.writeLock().lock();
        try {
            if (layers.contains(layer)) {
                LOGGER.warn("图层已存在: {}", layer.getName());
                return;
            }
            
            // 检查名称冲突
            if (isNameExists(layer.getName())) {
                LOGGER.warn("图层名称已存在: {}", layer.getName());
                return;
            }
            
            addLayerInternal(layer);
            eventSystem.publishLayerCreated(layer.getId(), layer);
            
            LOGGER.info("添加图层: {}", layer.getName());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 移除图层
     */
    public boolean removeLayer(ILayer layer) {
        if (layer == null) {
            LOGGER.warn("尝试移除空图层");
            return false;
        }
        
        lock.writeLock().lock();
        try {
            if (!layers.contains(layer)) {
                LOGGER.warn("图层不存在: {}", layer.getName());
                return false;
            }
            
            // 不能删除最后一个图层
            if (layers.size() == 1) {
                LOGGER.warn("不能删除最后一个图层");
                return false;
            }
            
            // 移除图层
            boolean removed = layers.remove(layer);
            if (removed) {
                markIndexDirty();
                
                // 如果删除的是活动图层，选择新的活动图层
                if (layer == activeLayer) {
                    ILayer newActiveLayer = layers.isEmpty() ? null : layers.getFirst();
                    setActiveLayerInternal(newActiveLayer);
                }
                
                // 发布事件
                eventSystem.publishLayerRemoved(layer.getId(), layer);
                
                LOGGER.info("移除图层: {}", layer.getName());
            }
            
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // === 图层查询和访问 ===
    
    /**
     * 获取所有图层
     */
    public List<ILayer> getLayers() {
        return new ArrayList<>(layers);
    }
    
    /**
     * 根据ID查找图层
     */
    public ILayer getLayerById(String id) {
        if (id == null) return null;
        
        rebuildIndexIfNeeded();
        return layerIndex.get(id);
    }

    /**
     * 检查名称是否已存在
     */
    public boolean isNameExists(String name) {
        if (name == null) return false;
        
        return layers.stream()
                    .anyMatch(layer -> name.equals(layer.getName()));
    }
    
    /**
     * 获取图层数量
     */
    public int getLayerCount() {
        return layers.size();
    }
    
    // === 活动图层管理 ===
    
    /**
     * 获取活动图层
     */
    public ILayer getActiveLayer() {
        return activeLayer;
    }
    
    /**
     * 设置活动图层
     */
    public void setActiveLayer(ILayer layer) {
        if (layer != null && !layers.contains(layer)) {
            LOGGER.warn("尝试设置不存在的图层为活动图层: {}", layer.getName());
            return;
        }
        
        lock.writeLock().lock();
        try {
            setActiveLayerInternal(layer);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // === 图层顺序管理 ===
    
    /**
     * 移动图层到指定位置
     */
    public boolean moveLayer(String layerId, int newIndex) {
        if (layerId == null || newIndex < 0 || newIndex >= layers.size()) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            ILayer layer = getLayerById(layerId);
            if (layer == null) {
                return false;
            }
            
            int currentIndex = layers.indexOf(layer);
            if (currentIndex == -1 || currentIndex == newIndex) {
                return false;
            }
            
            // 移动图层
            layers.remove(currentIndex);
            layers.add(newIndex, layer);
            
            // 更新Z序
            updateZOrders();
            markIndexDirty();
            
            // 发布事件
            eventSystem.publishLayerOrderChanged(layerId, layer, currentIndex, newIndex);
            
            LOGGER.debug("移动图层 '{}' 从位置 {} 到位置 {}", layer.getName(), currentIndex, newIndex);
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    // === 图层属性管理 ===
    
    /**
     * 更新图层属性（兼容性方法）
     * @param layer 要更新的图层
     * @param propertyType 属性类型（字符串形式）
     * @param newValue 新值
     */
    public void updateLayerProperty(ILayer layer, String propertyType, Object newValue) {
        if (layer == null) {
            LOGGER.warn("尝试更新空图层的属性");
            return;
        }
        
        try {
            switch (propertyType.toLowerCase()) {
                case "name":
                    layer.setName((String) newValue);
                    break;
                case "visible":
                case "visibility":
                    layer.setVisible((Boolean) newValue);
                    break;
                case "locked":
                case "lock_state":
                    layer.setLocked((Boolean) newValue);
                    break;
                case "opacity":
                    if (newValue instanceof Number) {
                        layer.setOpacity(((Number) newValue).doubleValue());
                    }
                    break;
                case "color":
                    if (newValue instanceof java.awt.Color) {
                        layer.setColor((java.awt.Color) newValue);
                    }
                    break;
                case "linestyle":
                case "line_style":
                    if (newValue instanceof com.plot.core.graphics.style.LineStyle) {
                        // 先更新图层的线条样式
                        layer.setLineStyle((com.plot.core.graphics.style.LineStyle) newValue);
                        
                        // 如果是Layer类型，强制触发图层上所有图形的样式更新
                        if (layer instanceof com.plot.core.layer.Layer concreteLayer) {
                            LOGGER.debug("触发图层 '{}' 上所有图形的线条样式更新", layer.getName());
                            concreteLayer.forceUpdateAllShapesLineStyle();
                        }
                    }
                    break;
                case "zorder":
                case "z_order":
                    if (newValue instanceof Number) {
                        layer.setZOrder(((Number) newValue).intValue());
                    }
                    break;
                default:
                    LOGGER.warn("未知的图层属性类型: {}", propertyType);
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("更新图层属性失败: layer={}, property={}, value={}", 
                layer.getName(), propertyType, newValue, e);
        }
    }
    
    // === 图形管理 ===
    
    /**
     * 添加图形到活动图层
     */
    public void addShape(Shape shape) {
        if (shape == null) {
            LOGGER.warn("尝试添加空图形");
            return;
        }
        
        ILayer current = activeLayer;
        if (current == null) {
            LOGGER.warn("没有活动图层，无法添加图形");
            return;
        }
        
        if (current.isLocked()) {
            LOGGER.warn("活动图层已锁定，无法添加图形");
            return;
        }
        
        current.addShape(shape);
        LOGGER.debug("添加图形到图层 '{}': {}", current.getName(), shape.getId());
    }
    
    // === 状态管理 ===
    
    /**
     * 清空所有图层
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            layers.clear();
            activeLayer = null;
            markIndexDirty();
            nextZOrder.set(0);
            
            LOGGER.info("清空所有图层");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // === 私有辅助方法 ===
    
    private void addLayerInternal(ILayer layer) {
        layers.add(layer);
        markIndexDirty();
    }
    
    private void setActiveLayerInternal(ILayer layer) {
        ILayer oldActive = activeLayer;
        activeLayer = layer;
        
        // 更新图层状态
        if (oldActive instanceof Layer) {
            ((Layer) oldActive).setActive(false);
        }
        if (layer instanceof Layer) {
            ((Layer) layer).setActive(true);
        }
        
        // 发布事件
        eventSystem.publishLayerActivated(
            layer != null ? layer.getId() : null, 
            layer, 
            oldActive
        );
        
        LOGGER.debug("活动图层变更: {} -> {}", 
            oldActive != null ? oldActive.getName() : "无", 
            layer != null ? layer.getName() : "无");
    }
    
    private void updateZOrders() {
        for (int i = 0; i < layers.size(); i++) {
            layers.get(i).setZOrder(i);
        }
    }
    
    private void markIndexDirty() {
        indexDirty = true;
    }
    
    private void rebuildIndexIfNeeded() {
        if (indexDirty) {
            lock.writeLock().lock();
            try {
                if (indexDirty) {
                    Map<String, ILayer> newIndex = new HashMap<>();
                    for (ILayer layer : layers) {
                        newIndex.put(layer.getId(), layer);
                    }
                    layerIndex = newIndex;
                    indexDirty = false;
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
} 