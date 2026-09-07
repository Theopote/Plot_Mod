# Plugins

插件专题说明（道路 / 土方 / 建筑等）。用户向导见 [../zh/08-plugins.md](../zh/08-plugins.md)。

土方插件产品定义：[../development/Earthwork_产品定位.md](../development/Earthwork_产品定位.md)（[ADR 0009](../decisions/0009-earthwork-minecraft-tool-not-civil-cad.md)）。

宿主注入约定见 [ADR 0001](../decisions/0001-core-boundary-and-plugin-context.md)：插件经 `PluginContext` 访问 Core，禁止新增 `*.getInstance()`。

## 代码审查（2026-09-07）

只读审查，按严重度列出当前代码中的问题与改造建议。历史报告见 [../historical-reports/](../historical-reports/)，不作为现行规范。

| 插件 | 文档 |
|------|------|
| 道路系统 | [道路系统_代码审查.md](道路系统_代码审查.md) |
| 土方平衡 | [土方平衡_代码审查.md](土方平衡_代码审查.md) |
| 建筑轮廓生成器 | [建筑轮廓生成器_代码审查.md](建筑轮廓生成器_代码审查.md) |
