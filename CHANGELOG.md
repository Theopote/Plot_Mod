# Changelog

本文件记录 Plot 模组的主要版本变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [语义化版本](https://semver.org/)。

## [1.0.0] - 2026-07-09

### Added

- Plot 设计器主界面（ImGui），默认快捷键 `P` 打开
- 完整绘图工具集：直线、矩形、圆/椭圆、折线、多边形、样条、自由绘制、星形、螺旋、悬链线、正弦曲线、圆弧、半圆、文字
- 编辑与变换工具：选择、橡皮擦、移动、旋转、缩放、镜像、对齐、阵列、偏移、打断、延伸、修剪、圆角、倒角、标注
- 多图层管理与项目保存/加载（JSON）
- 吸附系统（端点、中点、交点等）
- 线转方块与方块投影，支持幽灵方块世界预览
- 方块配置面板
- 撤销/重做命令历史
- 插件系统与内置道路系统插件
- 中英文本地化（`en_us` / `zh_cn`）

### Technical

- 基于 Fabric 1.21.10，要求 Java 21
- 使用 imgui-java 进行 UI 渲染
- 模块化架构：`api` / `core` / `ui` / `infrastructure` / `plugin`

[1.0.0]: https://github.com/Theopote/Plot_Mod/releases/tag/v1.0.0
