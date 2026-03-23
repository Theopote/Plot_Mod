package com.plot.ui.container;

import com.plot.ui.component.UIComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * UI组件容器
 * 负责管理所有UI组件的生命周期
 */
public class UIContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIContainer.class);
    private static UIContainer instance;
    private final Map<Class<? extends UIComponent>, UIComponent> components;

    private UIContainer() {
        this.components = new HashMap<>();
    }

    public static UIContainer getInstance() {
        if (instance == null) {
            instance = new UIContainer();
        }
        return instance;
    }

    /**
     * 注册UI组件
     */
    public <T extends UIComponent> void register(Class<T> type, T component) {
        LOGGER.debug("Registering component: {}", type.getSimpleName());
        components.put(type, component);
    }

    /**
     * 获取UI组件
     */
    @SuppressWarnings("unchecked")
    public <T extends UIComponent> T get(Class<T> type) {
        return (T) components.get(type);
    }

    /**
     * 释放所有组件资源
     */
    public void dispose() {
        LOGGER.debug("Disposing all UI components...");
        for (UIComponent component : components.values()) {
            try {
                component.close();
            } catch (Exception e) {
                LOGGER.error("Error disposing component: {}", component.getClass().getSimpleName(), e);
            }
        }
        components.clear();
    }
} 