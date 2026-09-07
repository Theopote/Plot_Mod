# 建筑轮廓生成器 · 审查问题 triage

> **来源**：[建筑轮廓生成器_代码审查.md](../plugins/建筑轮廓生成器_代码审查.md)（2026-09-07）  
> **核对**：当前 master + Massing v1 产品定位  
> **原则**：只执行高价值项；不为了「报告对齐」做过度重构。

审查报告 **8.7/10** — 比纯整洁度报告更接近工程审查：关注「用户点按钮后发生什么」「测试是否锁住生产语义」「Building ↔ Earthwork 边界」。

---

## P1 — 第一批必须修

| ID | 问题 | 状态 | 说明 |
|----|------|------|------|
| P1-1 | 加门滑条每帧重置 | ✅ | `BuildingPluginState.DoorEditorDraft` 按 building id |
| P1-3 | 片区 Preview 同步跑完整管线 | ✅ | `DistrictPreviewJob` 每帧 8 栋 + progress UI |
| P1-4 | Golden 屋顶被墙材质污染 | ✅ | Golden 分材质 + B03/B06/B12 语义断言 |
| P2-1 ↑ | 认领 `isRectangular` ← `isSlopedRoofEligible` | ✅ | `detectRectangular()` |
| P2-8 ↑ | 场地分析失败仍 Y=64 生成 | ✅ | 无 manual/pad → skip + `SITE_ANALYSIS_FAILED` |
| P1-2 ↓ | Pad unresolved silent terrain | ✅ | UI warning + `warn.earthwork_pad_unresolved_using_terrain` |

---

## P2 — 第二批正确性与契约

| ID | 问题 | 建议 |
|----|------|------|
| P2-2 | JSON 坏栋静默跳过 | `BuildingProjectLoadResult` + visible diagnostics |
| P2-3 | FloorPlate UI 缺口 | 轻量 Setback/Tower UI，或文档标注 Advanced Engine |
| P2-4 | FacadeEdgeResolver 同顶点数 ≠ 同拓扑 | edge direction + midpoint 继承 |
| P2-5 | 窗高 UI 与 generation clamp 不一致 | slider max 动态 = `floorHeight - sill - 1` |
| P2-6 | 阳台/雨篷 history `isItemActivated` 错位 | Edit UI state/history 统一 audit |
| P2-7 | Building ↔ Earthwork 编译环 | `IBuildingPadElevationService` 接口解耦 |
| P2-10 | 真实重叠 Pipeline 无测试 | `DistrictRealOverlapGenerationTest` |

---

## P3 — 后续增强

- 最小 FloorPlate / Setback UI
- 带 fake-world sampling 的 District benchmark
- persistence `schemaVersion` / migration hook
- 按 benchmark 决定 overlap spatial index

---

## 明确不做（当前阶段）

| 报告项 | 理由 |
|--------|------|
| 再拆 `BuildingActions` | 刚完成 Plugin/State/Actions/Panels 拆分，~500 行仍属 application 层合理集中 |
| 新建 `BuildingConfig` | Massing 产品简单，无大量全局默认参数需求 |
| 重构 Pipeline / Spec | 与 v1 冻结策略冲突 |
| 立即删除 `BuildingFootprint` | 刻意双模型迁移策略；Freeze + 新语义进 Definition |
| O(n²) overlap 提前 R-tree | 500 栋 ≈ 125k pairs，先 benchmark |
| 建筑 Ghost 简单抽稀 | 与 Earthwork 点云不同；体块即用户要看的结果 |

---

## P1-3 实现方向（备忘）

**不要**简单丢到普通后台线程（Minecraft World 线程安全）。

可选方案：

1. **分帧 preview job**：每 tick 5–20 栋，UI 显示 `Building preview 47 / 120`
2. **几何解析后台化 + World sampling 主线程**

---

## 相关文档

- [BuildingPlugin_产品定位.md](BuildingPlugin_产品定位.md)
- [BuildingMassingScenarioMatrix.md](BuildingMassingScenarioMatrix.md)
- [BuildingBenchmarkBaseline.md](BuildingBenchmarkBaseline.md)
