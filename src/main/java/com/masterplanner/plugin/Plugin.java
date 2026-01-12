package com.masterplanner.plugin;

/**
 * 插件基类，所有插件都需要继承此类
 */
public abstract class Plugin {
    private final String id;
    private final String name;
    private final String description;
    private final String icon;
    private boolean enabled;
    
    public Plugin(String id, String name, String description, String icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.enabled = true;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    // 插件生命周期方法
    public abstract void onEnable();
    public abstract void onDisable();
    public abstract void render();
}
