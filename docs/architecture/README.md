# Architecture

架构说明与模块边界文档放在此目录。

现行约定见 [ADR 0001](../decisions/0001-core-boundary-and-plugin-context.md)。

代码入口：

- `com.plot.core.context.ApplicationContext` — 组合根
- `com.plot.core.context.PluginContext` — 注入给插件的宿主服务
- `com.plot.core.persistence`
- `com.plot.core.command.CommandService`
- `com.plot.core.plugin.PluginManager`
