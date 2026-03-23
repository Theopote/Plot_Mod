package com.plot.ui.tools.impl.drawing.config;

import com.plot.api.tool.IToolConfig;

import java.util.Map;

/**
 * 工具配置适配器基类
 * 
 * <p>提供IToolConfig接口的默认实现，减少适配器代码的冗余。
 * 子类只需实现getValue和setValue方法即可。</p>
 * 
 * @author Plot Team
 * @version 1.0
 */
public abstract class BaseToolConfigAdapter implements IToolConfig {

    
    @Override
    public Map<String, Object> getAllValues() {
        return Map.of(); // 默认返回空映射
    }
    
    @Override
    public String getDescription() {
        return "Tool Config Adapter";
    }
    
    @Override
    public void setDescription(String description) {
        // 默认实现：忽略
    }
    
    @Override
    public String getTooltip() {
        return "";
    }
    
    @Override
    public void setTooltip(String tooltip) {
        // 默认实现：忽略
    }
    
    @Override
    public String getIcon() {
        return "";
    }
    
    @Override
    public void setIcon(String icon) {
        // 默认实现：忽略
    }
    
    @Override
    public String getShortcutKey() {
        return "";
    }
    
    @Override
    public void setShortcutKey(String shortcutKey) {
        // 默认实现：忽略
    }
    
    @Override
    public int getPriority() {
        return 0;
    }
    
    @Override
    public void setPriority(int priority) {
        // 默认实现：忽略
    }
    
    @Override
    public void resetToDefault() {
        // 默认实现：忽略
    }
    
    @Override
    public String saveToJson() {
        return "{}";
    }
    
    @Override
    public void loadFromJson(String json) {
        // 默认实现：忽略
    }
    
    @Override
    public IToolConfig clone() {
        return this; // 默认返回自身
    }
}
