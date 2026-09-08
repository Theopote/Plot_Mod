# Plugins

插件专题说明（道路 / 土方 / 建筑 / 电力线路等）。用户向导见 [../zh/08-plugins.md](../zh/08-plugins.md)。

宿主注入约定见 [ADR 0001](../decisions/0001-core-boundary-and-plugin-context.md)：插件经 `PluginContext` 访问 Core，禁止新增 `*.getInstance()`。

## 代码审查

| 插件 | 文档 |
|------|------|
| 电力线路 | [电力线路_代码审查.md](电力线路_代码审查.md) |
