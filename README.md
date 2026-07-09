# Plot

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10-green.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![GitHub](https://img.shields.io/badge/GitHub-Theopote%2FPlot__Mod-blue.svg)](https://github.com/Theopote/Plot_Mod)

**Plot** 是一款面向 Minecraft 的 Fabric 模组，提供类似 CAD 的 2D 规划与建造工具。在正交相机视图中绘制几何图形、编辑设计方案，并将线条投影为方块，直接在世界里预览与建造。

**Plot** is a Fabric mod for Minecraft that brings CAD-style 2D planning and building tools into the game. Draw geometry in an orthographic view, edit your design, and project lines into blocks for in-world preview and construction.

---

## 功能特性 / Features

### 绘图工具 / Drawing Tools

- 直线、矩形、圆形、椭圆、圆弧、半圆
- 折线、多边形、样条曲线、自由绘制
- 星形、螺旋线、悬链线、正弦曲线
- 文字标注

### 编辑与变换 / Edit & Transform

- 选择、橡皮擦
- 移动、旋转、缩放、镜像、对齐、阵列、偏移
- 打断、延伸、修剪、圆角、倒角
- 标注工具
- 完整的撤销 / 重做（命令模式）

### 方块建造 / Block Building

- **线转方块**：将绘制的线条转换为方块结构
- **方块投影**：将设计投影到 Minecraft 世界中，支持幽灵方块预览
- **方块配置**：可视化选择与管理建造用方块

### 画布与视图 / Canvas & View

- 多图层管理（显示、锁定、排序）
- 网格、吸附（端点、中点、交点等）
- 正交相机视图，支持视图锁定与透明度调节
- 项目保存与加载（JSON 格式）

### 插件系统 / Plugin System

- 可扩展的插件 API
- 内置道路系统插件（Road System），支持从路径生成道路并预览

### 界面 / UI

- 基于 ImGui 的专业设计界面
- 中英文本地化（`en_us` / `zh_cn`）
- 可自定义快捷键

---

## 环境要求 / Requirements

| 项目 | 版本 |
|------|------|
| Minecraft | 1.21.10 |
| Fabric Loader | ≥ 0.18.4 |
| Fabric API | 最新兼容版 |
| Java | ≥ 21 |

---

## 安装 / Installation

1. 安装 [Fabric Loader](https://fabricmc.net/use/)（对应 Minecraft 1.21.10）
2. 将 [Fabric API](https://modrinth.com/mod/fabric-api) 放入 `mods` 文件夹
3. 下载 Plot 模组 JAR，放入 `.minecraft/mods` 目录
4. 启动游戏

---

## 快速开始 / Quick Start

📖 **完整用户文档 / Full user guide:** [docs/README.md](docs/README.md)

1. 进入任意世界后，按 **`P`** 键打开 Plot 设计器
2. 在左侧工具栏选择绘图工具，在画布上绘制图形
3. 使用选择工具选中图形，通过变换工具进行调整
4. 配置方块后，使用「线转方块」或「投影」将设计应用到世界中
5. 通过文件菜单保存项目，以便下次继续编辑

> 快捷键可在游戏设置 → 控制 → Plot 分类中查看与修改。

---

## 从源码构建 / Building from Source

```bash
# 克隆仓库
git clone https://github.com/Theopote/Plot_Mod.git
cd Plot_Mod

# 构建模组（Windows）
gradlew build

# 构建模组（Linux / macOS）
./gradlew build
```

构建产物位于 `build/libs/plot-<version>.jar`。

### 开发运行

```bash
# 启动 Minecraft 客户端进行调试
gradlew runClient
```

---

## 项目结构 / Project Structure

```
src/main/java/com/plot/
├── api/              # 公共 API 接口（插件、工具、几何、事件等）
├── core/             # 核心逻辑（命令、图层、几何、吸附、序列化）
├── infrastructure/   # 基础设施（事件总线、坐标变换、方块渲染）
├── ui/               # 用户界面（画布、工具栏、面板、对话框）
├── plugin/           # 内置插件（道路系统等）
└── PlotMod.java      # 模组入口
```

---

## 插件开发 / Plugin Development

Plot 提供插件 API，允许扩展自定义工具与功能。核心接口位于 `com.plot.api.plugin` 包：

- `IPlugin` — 插件主接口
- `PluginDescription` — 插件元数据
- `IPluginListener` — 插件生命周期监听

参考内置的 `RoadSystemPlugin` 了解插件实现方式。

---

## 依赖项 / Dependencies

| 库 | 用途 |
|----|------|
| [Fabric API](https://github.com/FabricMC/fabric) | Minecraft 模组基础 API |
| [imgui-java](https://github.com/SpaiR/imgui-java) | ImGui 界面渲染 |
| [Apache Batik](https://xmlgraphics.apache.org/batik/) | SVG 图标转码 |

---

## 贡献 / Contributing

欢迎提交 Issue 与 Pull Request。开发前请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 许可证 / License

本项目采用 [MIT License](LICENSE) 开源。

---

## 作者 / Author

**Theopote** — [GitHub](https://github.com/Theopote/Plot_Mod)
