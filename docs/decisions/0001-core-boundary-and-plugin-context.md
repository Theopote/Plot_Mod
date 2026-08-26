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
- `ApplicationContext` 是 UI/Core 服务访问面：`getAppState` / `getCommandService` / `getToolManager` / `getSnapManager` / `getEventBus` / 世界服务等。
- `AppState.getInstance()` / `CommandService.getInstance()` 已 `@Deprecated`（组合根内部解析除外）。
- `ViewTransform` 位于 `com.plot.api.render`；`DrawContext` / `Shape.renderImGui` / `IRenderVisitor` 均使用它，Core 几何不再依赖 `CanvasCamera`。
- `IShapeVisitor` 迁至 `com.plot.core.geometry.visitor`（UI 包保留废弃转发）。
- `LineType` 升至 `com.plot.api.graphics`；`IPluginManager` 不再暴露 core 的 `PluginDependencyGraph`。
- `api` 已引入 `IShape` 并迁移 `IAppState.addShape`、`ISnapManager`、`IExtendableShape` 到接口抽象；`Shape` 实现 `IShape` 作为兼容桥接。
- `ILayer`/`ICanvas.getShapes` 已改为返回 `List<? extends IShape>`；core/ui 调用点经 `ShapeLists.of` 桥接回 `List<Shape>`。
- `ILayer` 线型 API 已改为 `ILineStyle`（不再直接依赖 `core.graphics.style.LineStyle`）；`Layer` 内部仍桥接到具体实现。
- `api.render.IDrawContext` 已抽出；`ITool.render` / `IRenderer.begin` 改为依赖它，`core.graphics.DrawContext` 实现该接口。
- `IRenderVisitor` 已迁至 `com.plot.core.geometry.visitor`（与 `IShapeVisitor` 同层），因其依赖具体 Shape 与 ImGui，不宜留在 api。
- Core 组合根已持有并注入 `EventBus`：`CommandService` / `LayerService` / `SnapManager` 经构造注入，不再各自 `EventBus.getInstance()`；`ApplicationContext` 拥有 `snapManager` 字段。
- `ToolManager` 同样由组合根持有：`initialize(AppState, EventBus)` 注入事件总线，工具变更/配置事件不再经 `ApplicationContext.getEventBus()` 回查。
- `api.plugin.PluginDescription` 已改为使用 API 层 `PropertiesPluginConfig`，去除对 `core.plugin.PluginConfig` 的直接依赖。
- `api` 包对 core 的直接依赖已清空（含 FQCN）；后续可减少 `LogManager.getInstance()` 散落，并固化一次架构提交 + 主路径手测。
