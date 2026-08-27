# ADR 0002: ClientServices 与 ApplicationContext 边界

## Status

Accepted — 2026-08-26

## Context

`PlotMod` / `PlotClient` 入口已拆分，但通用 `ApplicationContext` 仍暴露：

- `GhostBlockManager`
- `BlockPlacementScheduler`
- `BlockProjectionHandler`
- `CoordinateTransformer`

其中 `BlockProjectionHandler` 依赖 `MinecraftClient`，使 dedicated server 路径在架构上仍可能触及 client 类型。

## Decision

1. **`ApplicationContext`** 仅持有逻辑共享服务：Project / Layer / Selection / Command / SpatialIndex / Tool / Snap / Event。
2. **`com.plot.client.ClientServices`** 作为客户端组合根，持有世界服务具体实现。
3. **`PluginContextFactory`** 默认产出逻辑侧 `PluginContext`；`ClientServices.initialize()` 后接管，产出带世界服务的完整句柄。
4. **`PluginManager`** 改为懒加载单例，并经 `PluginContextFactory.create()` 绑定，保证 `PlotClient` 先初始化 `ClientServices`。
5. **`PluginContext` 世界侧仅暴露 `com.plot.api.world.*` 接口**（`ICoordinateService` / `IGhostBlockService` / `IBlockPlacementService` / `IBlockProjectionService`），字段类型链不引用 `MinecraftClient` 或 concrete client 基础设施类；`ClientServices` 注入实现类实例。

## Consequences

- UI / 插件世界操作经 `ClientServices` 组装的接口句柄，不再经 `ApplicationContext` getter。
- 逻辑侧 `PluginContext` 访问 `ghosts()`/`projection()` 等在无世界服务时会明确失败。
- Core / Plugin 编译期类型依赖止于 `api.world`，dedicated-server 加载链路不再因 `PluginContext` 字段类型拉入 `BlockProjectionHandler` → `MinecraftClient`。
