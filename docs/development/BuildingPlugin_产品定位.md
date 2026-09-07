# Building：2D Footprint → Minecraft Massing

> **状态**：现行产品定义（2026-09）  
> **代码入口**：`com.plot.plugin.BuildingPlugin` / `com.plot.plugin.building.*`

本文件是建筑插件的**产品准绳**。引擎能力（门窗、立面、OpeningSpec 等）可以存在，但默认 UI 与片区工具以 **Massing / Blockout** 为第一优先级。

---

## 一句话

把几十栋、几百栋 **2D 建筑轮廓** 批量变成 **Minecraft 城市体量**（Massing / Blockout）。

## 是 / 不是

| 是 | 不是 |
|----|------|
| 轮廓体块生成器 | 简化 BIM 编辑器 |
| 片区批量 Massing（高度分布、预设、later-wins 合并） | 单栋失败即整片 abort |
| 2D footprint → 3D blockout | 默认暴露门窗 / 立面细部 |

---

## 片区生成：Fail-soft（核心约束）

**这是 Massing 工具比「SOLID 星级」更重要的产品特性，必须保持。**

```
100 buildings
3 invalid
→ 97 generated + 3 reported
```

| 规则 | 说明 |
|------|------|
| 单栋 invalid / empty / exception | 记入 `skipped`，**不阻断**其余 |
| 整片 abort | **禁止** — 不得因一栋异常抛出到片区外层 |
| 合并策略 | 体素冲突 **later-wins**（后生成覆盖先写） |
| UI | 明确展示 generated / skipped 数量，不只用泛化 warning |

**实现边界**：`DistrictMassingGenerator` — 每栋 `try/catch`，`BuildingFootprintValidator` 前置校验 invalid 轮廓。

**唯一允许整片失败的情况**：World 不可用、输入为空、或 `generateDistrict` 外层 infrastructure 故障（非单栋生成失败）。

---

## 默认 UI 两层（Edit Tab）

**基础体量（默认展开）**  
Floors / Height、层高、墙厚、墙材、屋顶、基准标高

**片区多选**  
Height Distribution、Preset、Batch Apply

**高级细节（折叠）**  
Windows、Doors、Facade、FloorPlate、Parapet、Balcony、Canopy

---

## 相关文档

- [BuildingDeprecatedApiInventory.md](BuildingDeprecatedApiInventory.md) — P3 Deprecated 清理
- [task-assignments/BuildingPlugin_开发任务书.md](task-assignments/BuildingPlugin_开发任务书.md) — 历史任务书
