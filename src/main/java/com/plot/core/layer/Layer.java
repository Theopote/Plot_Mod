package com.plot.core.layer;

import com.plot.core.graphics.style.LineStyle;
import com.plot.core.graphics.style.ShapeStyle;
import com.plot.core.model.Entity;
import com.plot.core.model.Shape;
import com.plot.api.model.ILayer;
import com.plot.api.model.IElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 优化后的图层实现
 * <p>
 * 主要优化：
 * 1. 使用 LayerContainer 统一管理所有类型的元素
 * 2. 使用 LayerState 管理状态属性
 * 3. 使用 LayerEventSystem 统一事件处理
 * 4. 职责分离，提高可维护性
 * 5. 线程安全设计
 * 6. 更好的性能和内存使用
 */
public class Layer extends Entity implements ILayer, LayerState.StateChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Layer.class);
    
    // 核心属性
    private final String id;
    private volatile String name;
    
    // 组件化设计
    private final LayerContainer container;
    private final LayerState state;
    private final LayerEventSystem eventSystem;
    
    // 构造函数
    
    public Layer() {
        this("新建图层");
    }
    
    public Layer(String name) {
        this(UUID.randomUUID().toString(), name);
    }
    
    public Layer(String id, String name) {
        this(id, name, new LayerEventSystem());
    }
    
    public Layer(String id, String name, LayerEventSystem eventSystem) {
        this.id = id;
        this.name = name;
        this.container = new LayerContainer();
        this.state = new LayerState();
        this.eventSystem = eventSystem;
        
        // 设置状态变更监听器
        this.state.setChangeListener(this);
        
        LOGGER.debug("创建优化图层: id={}, name={}", id, name);
    }
    
    // === StateChangeListener 实现 ===
    
    @Override
    public void onVisibilityChanged(boolean visible) {
        eventSystem.publishLayerPropertyChanged(id, this, "visibility", !visible, visible);
        LOGGER.debug("图层 '{}' 可见性变更: {}", name, visible);
    }
    
    @Override
    public void onLockStateChanged(boolean locked) {
        eventSystem.publishLayerPropertyChanged(id, this, "locked", !locked, locked);
        LOGGER.debug("图层 '{}' 锁定状态变更: {}", name, locked);
    }
    
    @Override
    public void onActiveStateChanged(boolean active) {
        eventSystem.publishLayerPropertyChanged(id, this, "active", !active, active);
        LOGGER.debug("图层 '{}' 激活状态变更: {}", name, active);
    }
    
    @Override
    public void onOpacityChanged(float opacity) {
        updateElementsOpacity(opacity);
        eventSystem.publishLayerPropertyChanged(id, this, "opacity", -1f, opacity);
        LOGGER.debug("图层 '{}' 透明度变更: {}", name, opacity);
    }
    
    @Override
    public void onZOrderChanged(int zOrder) {
        eventSystem.publishLayerPropertyChanged(id, this, "zOrder", -1, zOrder);
        LOGGER.debug("图层 '{}' Z序变更: {}", name, zOrder);
    }
    
    @Override
    public void onColorChanged(Color color) {
        updateElementsColor(color);
        eventSystem.publishLayerPropertyChanged(id, this, "color", null, color);
        LOGGER.debug("图层 '{}' 颜色变更: {}", name, color);
    }
    
    @Override
    public void onLineStyleChanged(LineStyle lineStyle) {
        updateElementsLineStyle(lineStyle);
        eventSystem.publishLayerPropertyChanged(id, this, "lineStyle", null, lineStyle);
        LOGGER.debug("图层 '{}' 线条样式变更: {}", name, lineStyle);
    }
    
    // === ILayer 接口实现 ===
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            String oldName = this.name;
            this.name = name;
            eventSystem.publishLayerPropertyChanged(id, this, "name", oldName, name);
            LOGGER.debug("图层名称变更: '{}' -> '{}'", oldName, name);
        }
    }
    
    @Override
    public boolean isVisible() {
        return state.isVisible();
    }
    
    @Override
    public void setVisible(boolean visible) {
        state.setVisible(visible);
    }
    
    @Override
    public boolean isLocked() {
        return state.isLocked();
    }
    
    @Override
    public void setLocked(boolean locked) {
        state.setLocked(locked);
    }
    
    @Override
    public double getOpacity() {
        return state.getOpacity();
    }
    
    @Override
    public void setOpacity(double opacity) {
        state.setOpacity((float) opacity);
    }
    
    @Override
    public int getZOrder() {
        return state.getZOrder();
    }
    
    @Override
    public void setZOrder(int zOrder) {
        state.setZOrder(zOrder);
    }
    
    @Override
    public Color getColor() {
        return state.getColor();
    }
    
    @Override
    public void setColor(Color color) {
        state.setColor(color);
    }
    
    @Override
    public LineStyle getLineStyle() {
        return state.getLineStyle();
    }
    
    @Override
    public void setLineStyle(LineStyle lineStyle) {
        state.setLineStyle(lineStyle);
    }
    
    // === 图形管理 ===
    
    @Override
    public List<Shape> getShapes() {
        return container.getElements(Shape.class);
    }
    
    @Override
    public void addShape(Shape shape) {
        if (shape == null) {
            LOGGER.warn("尝试添加空图形到图层 '{}'", name);
            return;
        }
        
        container.addElement(shape);
        
        // 应用图层样式到新添加的图形
        applyLayerStyleToShape(shape);
        
        eventSystem.publishLayerContentChanged(id, this, "element_added", shape);
        LOGGER.debug("添加图形到图层 '{}': {}", name, shape.getId());
    }
    
    @Override
    public boolean removeShape(Shape shape) {
        if (shape == null) {
            LOGGER.warn("尝试从图层 '{}' 移除空图形", name);
            return false;
        }
        
        boolean removed = container.removeElement(shape);
        if (removed) {
            eventSystem.publishLayerContentChanged(id, this, "element_removed", shape);
            LOGGER.debug("从图层 '{}' 移除图形: {}", name, shape.getId());
        }
        return removed;
    }
    
    @Override
    public Shape getShape(String shapeId) {
        return container.getElementById(shapeId);
    }
    
    @Override
    public boolean hasShape(String shapeId) {
        return container.containsElement(shapeId);
    }
    
    @Override
    public Set<String> getShapeIds() {
        return container.getAllElementIds();
    }
    
    @Override
    public void clear() {
        container.clear();
        eventSystem.publishLayerContentChanged(id, this, "content_cleared", null);
        LOGGER.debug("清空图层 '{}' 的内容", name);
    }
    
    // === 元素管理 ===
    
    @Override
    public List<IElement> getElements() {
        return container.getElements(IElement.class);
    }
    
    @Override
    public void addElement(IElement element) {
        if (element == null) {
            LOGGER.warn("尝试添加空元素到图层 '{}'", name);
            return;
        }
        
        container.addElement(element);
        eventSystem.publishLayerContentChanged(id, this, "element_added", element);
        LOGGER.debug("添加元素到图层 '{}': {}", name, element.getId());
    }
    
    @Override
    public boolean removeElement(IElement element) {
        if (element == null) {
            LOGGER.warn("尝试从图层 '{}' 移除空元素", name);
            return false;
        }
        
        boolean removed = container.removeElement(element);
        if (removed) {
            eventSystem.publishLayerContentChanged(id, this, "element_removed", element);
            LOGGER.debug("从图层 '{}' 移除元素: {}", name, element.getId());
        }
        return removed;
    }
    
    // === 扩展功能 ===
    
    /**
     * 获取是否为活动图层
     */
    public boolean isActive() {
        return state.isActive();
    }
    
    /**
     * 设置是否为活动图层
     */
    public void setActive(boolean active) {
        state.setActive(active);
    }
    
    /**
     * 获取图层的默认样式
     */
    public ShapeStyle getDefaultStyle() {
        return state.getDefaultStyle();
    }

    // === 私有辅助方法 ===
    
    /**
     * 将图层样式应用到图形
     * 修复：确保新图形默认跟随图层样式
     */
    private void applyLayerStyleToShape(Shape shape) {
        if (shape.getStyle() instanceof ShapeStyle shapeStyle) {
            ShapeStyle defaultStyle = getDefaultStyle();
            
            if (defaultStyle != null) {
                // 修复：标记图形为跟随图层样式
                shapeStyle.setFollowsLayerStyle(true);
                
                // 应用图层的默认样式
                if (shapeStyle.getColor() == null && defaultStyle.getColor() != null) {
                    shapeStyle.internalSetColor(defaultStyle.getColor());
                }
                if (shapeStyle.getLineStyle() == null && defaultStyle.getLineStyle() != null) {
                    shapeStyle.internalSetLineStyle(defaultStyle.getLineStyle());
                }
                if (shapeStyle.getOpacity() <= 0 && defaultStyle.getOpacity() > 0) {
                    shapeStyle.internalSetOpacity(defaultStyle.getOpacity());
                }
                
                // 确保线条颜色与图层颜色一致
                Color layerColor = getColor();
                LineStyle layerLineStyle = getLineStyle();
                if (layerColor != null) {
                    shapeStyle.setLineColor(layerColor);
                    shapeStyle.setStrokeColor(layerColor);
                }
                
                // 应用图层的线型和线宽
                if (layerLineStyle != null) {
                    if (shapeStyle.getLineStyle() instanceof LineStyle lineStyle) {
                        lineStyle.setType(layerLineStyle.getType());
                        lineStyle.setWidth(layerLineStyle.getWidth());
                        lineStyle.setColor(layerColor != null ? layerColor : lineStyle.getColor());
                    }
                    shapeStyle.setStrokeWidth(layerLineStyle.getWidth());
                }
                
                LOGGER.debug("图层 '{}' 应用样式到新图形 {}: 颜色={}, 线型={}, 线宽={}", 
                           name, shape.getId(), layerColor, 
                           layerLineStyle != null ? layerLineStyle.getType() : "null",
                           layerLineStyle != null ? layerLineStyle.getWidth() : "null");
            }
        }
    }
    
    /**
     * 更新所有元素的颜色
     */
    private void updateElementsColor(Color newColor) {
        List<Shape> shapes = getShapes();
        LOGGER.debug("图层 '{}' 颜色变更，开始更新 {} 个图形的颜色", name, shapes.size());
        
        for (Shape shape : shapes) {
            if (shape.getStyle() instanceof ShapeStyle shapeStyle) {
                // 修复：只更新跟随图层样式的图形
                if (shapeStyle.doesFollowLayerStyle()) {
                    // 使用内部方法避免触发followsLayerStyle标志
                    shapeStyle.internalSetColor(newColor);
                    shapeStyle.setLineColor(newColor);
                    shapeStyle.setStrokeColor(newColor);
                    LOGGER.debug("图层 '{}' 更新图形 {} 的颜色: {}", name, shape.getId(), newColor);
                }
            }
        }
    }
    
    /**
     * 更新所有元素的线条样式
     */
    private void updateElementsLineStyle(LineStyle newLineStyle) {
        List<Shape> shapes = getShapes();
        LOGGER.debug("图层 '{}' 线条样式变更，开始更新 {} 个图形的样式", name, shapes.size());
        
        for (Shape shape : shapes) {
            if (shape.getStyle() instanceof ShapeStyle shapeStyle) {
                // 修复：只更新跟随图层样式的图形
                if (shapeStyle.doesFollowLayerStyle()) {
                    // 使用内部方法避免触发followsLayerStyle标志
                    shapeStyle.internalSetLineStyle(newLineStyle);
                    shapeStyle.setStrokeWidth(newLineStyle.getWidth());
                    
                    // 确保图形的LineStyle也更新
                    if (shapeStyle.getLineStyle() instanceof LineStyle lineStyle) {
                        lineStyle.setType(newLineStyle.getType());
                        lineStyle.setWidth(newLineStyle.getWidth());
                        if (newLineStyle.getColor() != null) {
                            lineStyle.setColor(newLineStyle.getColor());
                        }
                    }
                    
                    LOGGER.debug("图层 '{}' 更新图形 {} 的线条样式: 类型={}, 宽度={}", 
                               name, shape.getId(), newLineStyle.getType(), newLineStyle.getWidth());
                }
            }
        }
    }
    
    /**
     * 更新所有元素的透明度
     */
    private void updateElementsOpacity(float newOpacity) {
        List<Shape> shapes = getShapes();
        LOGGER.debug("图层 '{}' 透明度变更，开始更新 {} 个图形的透明度", name, shapes.size());
        
        for (Shape shape : shapes) {
            if (shape.getStyle() instanceof ShapeStyle shapeStyle) {
                // 修复：只更新跟随图层样式的图形
                if (shapeStyle.doesFollowLayerStyle()) {
                    // 使用内部方法避免触发followsLayerStyle标志
                    shapeStyle.internalSetOpacity(newOpacity);
                    LOGGER.debug("图层 '{}' 更新图形 {} 的透明度: {}", name, shape.getId(), newOpacity);
                }
            }
        }
    }
    
    // 修复：移除了不可靠的启发式判断方法 isDefaultColor() 和 isDefaultLineStyle()
    // 这些方法基于硬编码的"魔数"进行猜测，容易出错且不可维护
    // 现在使用明确的 ShapeStyle.doesFollowLayerStyle() 标志位替代
    
    // === toString 和其他 ===
    
    @Override
    public String toString() {
        return String.format("Layer[id=%s, name=%s, visible=%s, locked=%s, elements=%d]",
            id, name, isVisible(), isLocked(), container.getTotalElementCount());
    }
    
    /**
     * 强制更新图层上所有图形的线条样式
     * 用于图层面板中手动修改线型/线宽后的立即同步
     */
    public void forceUpdateAllShapesLineStyle() {
        LineStyle currentLineStyle = getLineStyle();
        if (currentLineStyle != null) {
            LOGGER.debug("强制更新图层 '{}' 上所有图形的线条样式: 类型={}, 宽度={}", 
                       name, currentLineStyle.getType(), currentLineStyle.getWidth());
            updateElementsLineStyle(currentLineStyle);
        } else {
            LOGGER.warn("图层 '{}' 的线条样式为null，无法更新图形样式", name);
        }
    }
    
    /**
     * 强制更新图层上所有图形的颜色
     * 用于图层面板中手动修改颜色后的立即同步
     */
    public void forceUpdateAllShapesColor() {
        Color currentColor = getColor();
        if (currentColor != null) {
            LOGGER.debug("强制更新图层 '{}' 上所有图形的颜色: {}", name, currentColor);
            updateElementsColor(currentColor);
        } else {
            LOGGER.warn("图层 '{}' 的颜色为null，无法更新图形颜色", name);
        }
    }
} 