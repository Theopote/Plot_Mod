package com.masterplanner.api.plugin;

/**
 * 插件状态枚举
 */
public enum PluginState {
    /** 未加载 */
    UNLOADED,
    
    /** 已加载 */
    LOADED,
    
    /** 已启用 */
    ENABLED,
    
    /** 已禁用 */
    DISABLED,
    
    /** 加载失败 */
    FAILED,
    
    /** 依赖缺失 */
    MISSING_DEPENDENCIES,
    
    /** 版本不兼容 */
    INCOMPATIBLE_VERSION,
    
    /** 正在加载 */
    LOADING,
    
    /** 正在卸载 */
    UNLOADING
}
