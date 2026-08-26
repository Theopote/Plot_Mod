# ADR 0001: Core 边界与 PluginContext

## Status

Accepted — 2026-08-26

## Context

Plot 正确方向是 **Core ← Plugin**（Road / Building / Earthwork …）。但 Core 同时承载状态、命令、工具、事件、图层等，且热路径普遍通过：

`AppState` / `EventBus` / `ToolManager` / `CommandService` / `PluginManager.getInstance()`

形成 **Singleton Service Locator**。再叠加 Parcel / Terrain / Rail 等插件时，跨模块隐性耦合、事件重复订阅、Undo 与插件缓存不一致的调试成本会指数上升。

已有 `ApplicationContext` 作为组合根，但插件几乎不使用它。

## Decision

1. **Core 职责（允许）**  
   Project / Layer / Selection / SpatialIndex / Command / Snap（几何语义）/ Plugin host / Persistence。

2. **Core 禁止新增**  
   领域专用模型（道路、土方、地块…）、ImGui / Canvas 实现、具体绘制 Tool UI。

3. **插件获取宿主服务**  
   唯一合法入口是注入的 **`PluginContext`**（由 `ApplicationContext` 组装），包括：  
   `appState` / `commands` / `events` / `tools` / `coordinates` / `ghosts` / `placement` / `projection`。  
   **禁止**在 `com.plot.plugin/**` 对这些服务调用 `*.getInstance()`。

4. **生命周期**  
   `PluginManager.installPlugin` 在 `initialize()` 之前 `bind(PluginContext)`。  
   事件订阅优先使用 owner 作用域（`EventBus.subscribe(owner, …)` + `unsubscribeOwner`），避免 disable 后残留监听器。

5. **API 接口**  
   现存 `ICommandManager` / `IEventManager` 与实现语义不一致，**暂不强制实现**；待对齐后再把 `PluginContext` 访问面迁到 `com.plot.api`。短期内 `PluginContext` 位于 `com.plot.core.context`，对 builtins 提供 typed 访问。

## Consequences

- 新插件（Parcel 等）必须经 `PluginContext` 拿宿主能力，不能再扩大定位器面。
- 世界服务（`GhostBlockManager` / `BlockPlacementScheduler` / `BlockProjectionHandler`）已纳入 `PluginContext`（`ghosts()` / `placement()` / `projection()`）。实现层暂仍为单例，但对 `com.plot.plugin/**` 禁止再直接 `getInstance()`。
- UI / Core 命令默认构造仍可回退单例；插件创建落地命令时应传入 Context 中的实例。
- UI 仍可临时使用 `AppState` 门面；新 Core/Plugin 代码不得新增 `AppState.getInstance()`。
