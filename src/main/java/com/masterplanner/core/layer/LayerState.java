package com.masterplanner.core.layer;

import com.masterplanner.core.graphics.style.LineStyle;
import com.masterplanner.core.graphics.style.ShapeStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 图层状态管理器
 * 负责管理图层的所有状态属性，包括可见性、锁定状态、样式等
 */
public class LayerState {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayerState.class);
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 基本属性
    private volatile boolean visible = true;
    private volatile boolean locked = false;
    private volatile boolean active = false;
    private volatile float opacity = 1.0f;
    private volatile int zOrder = 0;
    
    // 样式属性
    private volatile Color color = new Color(255, 255, 255);
    private volatile LineStyle lineStyle = new LineStyle();
    private volatile ShapeStyle defaultStyle;
    
    // 状态变更监听器
    public interface StateChangeListener {
        void onVisibilityChanged(boolean visible);
        void onLockStateChanged(boolean locked);
        void onActiveStateChanged(boolean active);
        void onOpacityChanged(float opacity);
        void onZOrderChanged(int zOrder);
        void onColorChanged(Color color);
        void onLineStyleChanged(LineStyle lineStyle);
    }
    
    private volatile StateChangeListener changeListener;
    
    public LayerState() {
        updateDefaultStyle();
    }

    
    /**
     * 设置状态变更监听器
     */
    public void setChangeListener(StateChangeListener listener) {
        this.changeListener = listener;
    }
    
    // 可见性管理
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            lock.writeLock().lock();
            try {
                boolean oldValue = this.visible;
                this.visible = visible;
                LOGGER.debug("图层可见性变更: {} -> {}", oldValue, visible);
                
                if (changeListener != null) {
                    changeListener.onVisibilityChanged(visible);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // 锁定状态管理
    
    public boolean isLocked() {
        return locked;
    }
    
    public void setLocked(boolean locked) {
        if (this.locked != locked) {
            lock.writeLock().lock();
            try {
                boolean oldValue = this.locked;
                this.locked = locked;
                LOGGER.debug("图层锁定状态变更: {} -> {}", oldValue, locked);
                
                if (changeListener != null) {
                    changeListener.onLockStateChanged(locked);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // 激活状态管理
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        if (this.active != active) {
            lock.writeLock().lock();
            try {
                boolean oldValue = this.active;
                this.active = active;
                LOGGER.debug("图层激活状态变更: {} -> {}", oldValue, active);
                
                if (changeListener != null) {
                    changeListener.onActiveStateChanged(active);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // 透明度管理
    
    public float getOpacity() {
        return opacity;
    }
    
    public void setOpacity(float opacity) {
        opacity = Math.max(0.0f, Math.min(1.0f, opacity)); // 限制范围
        
        if (Math.abs(this.opacity - opacity) > 0.001f) { // 使用浮点数比较容忍度
            lock.writeLock().lock();
            try {
                float oldValue = this.opacity;
                this.opacity = opacity;
                updateDefaultStyle();
                LOGGER.debug("图层透明度变更: {} -> {}", oldValue, opacity);
                
                if (changeListener != null) {
                    changeListener.onOpacityChanged(opacity);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // Z序管理
    
    public int getZOrder() {
        return zOrder;
    }
    
    public void setZOrder(int zOrder) {
        if (this.zOrder != zOrder) {
            lock.writeLock().lock();
            try {
                int oldValue = this.zOrder;
                this.zOrder = zOrder;
                LOGGER.debug("图层Z序变更: {} -> {}", oldValue, zOrder);
                
                if (changeListener != null) {
                    changeListener.onZOrderChanged(zOrder);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // 颜色管理
    
    public Color getColor() {
        return new Color(color.getRGB()); // 返回副本以避免外部修改
    }
    
    public void setColor(Color color) {
        if (color == null) {
            LOGGER.warn("尝试设置空颜色，忽略操作");
            return;
        }
        
        if (!Objects.equals(this.color, color)) {
            lock.writeLock().lock();
            try {
                Color oldValue = this.color;
                this.color = new Color(color.getRGB()); // 创建副本
                updateDefaultStyle();
                LOGGER.debug("图层颜色变更: {} -> {}", oldValue, this.color);
                
                if (changeListener != null) {
                    changeListener.onColorChanged(this.color);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // 线条样式管理
    
    public LineStyle getLineStyle() {
        lock.readLock().lock();
        try {
            LineStyle copy = new LineStyle(lineStyle.getType(), lineStyle.getWidth());
            copy.setColor(lineStyle.getColor());
            return copy;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void setLineStyle(LineStyle lineStyle) {
        if (lineStyle == null) {
            LOGGER.warn("尝试设置空线条样式，忽略操作");
            return;
        }
        
        lock.writeLock().lock();
        try {
            LineStyle oldValue = this.lineStyle;
            this.lineStyle = new LineStyle(lineStyle.getType(), lineStyle.getWidth());
            this.lineStyle.setColor(lineStyle.getColor());
            updateDefaultStyle();
            LOGGER.debug("图层线条样式变更: {} -> {}", oldValue, this.lineStyle);
            
            if (changeListener != null) {
                changeListener.onLineStyleChanged(this.lineStyle);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // 默认样式管理
    
    public ShapeStyle getDefaultStyle() {
        lock.readLock().lock();
        try {
            return defaultStyle != null ? (ShapeStyle) defaultStyle.clone() : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void updateDefaultStyle() {
        if (defaultStyle == null) {
            defaultStyle = new ShapeStyle();
        }
        
        // 使用新的接口方法设置线条样式
        defaultStyle.setLineStyle((com.masterplanner.api.graphics.ILineStyle) lineStyle);
        defaultStyle.setColor(color);
        defaultStyle.setOpacity(opacity);
    }

    /**
     * 重置为默认状态
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            this.visible = true;
            this.locked = false;
            this.active = false;
            this.opacity = 1.0f;
            this.zOrder = 0;
            this.color = new Color(255, 255, 255);
            this.lineStyle = new LineStyle();
            updateDefaultStyle();
            
            LOGGER.debug("重置图层状态为默认值");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取状态摘要
     */
    public String getStateSummary() {
        lock.readLock().lock();
        try {
            return String.format("LayerState[visible=%s, locked=%s, active=%s, opacity=%.2f, zOrder=%d, color=%s]",
                visible, locked, active, opacity, zOrder, color);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        LayerState that = (LayerState) obj;
        return visible == that.visible &&
               locked == that.locked &&
               active == that.active &&
               Float.compare(that.opacity, opacity) == 0 &&
               zOrder == that.zOrder &&
               Objects.equals(color, that.color) &&
               Objects.equals(lineStyle, that.lineStyle);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(visible, locked, active, opacity, zOrder, color, lineStyle);
    }
    
    @Override
    public String toString() {
        return getStateSummary();
    }
} 