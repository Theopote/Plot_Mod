package com.plot.api.plugin;

/**
 * 插件生命周期状态。
 * <p>
 * 标准流转：
 * DISCOVERED → LOADED → INITIALIZED → ENABLED → ACTIVE
 *   ⇄ INACTIVE → DISABLED → UNLOADED → DISPOSED
 * </p>
 */
public enum PluginState {
    /** 已发现（实例已创建，尚未纳入管理器） */
    DISCOVERED,

    /** 正在加载 / 安装 */
    LOADING,

    /** 已纳入注册表与依赖图 */
    LOADED,

    /** initialize() 已成功 */
    INITIALIZED,

    /** 已启用（可运行，但未必是当前 UI 激活插件） */
    ENABLED,

    /** 当前 UI 激活插件 */
    ACTIVE,

    /** 已启用但非当前激活 */
    INACTIVE,

    /** 已禁用 */
    DISABLED,

    /** 正在卸载 */
    UNLOADING,

    /** 已卸载（实例仍可能短暂存在） */
    UNLOADED,

    /** 已释放资源，不可再使用 */
    DISPOSED,

    /** 加载 / 生命周期失败 */
    FAILED,

    /** 依赖缺失 */
    MISSING_DEPENDENCIES,

    /** 版本不兼容 */
    INCOMPATIBLE_VERSION
}
