# Plot Mod - 建筑轮廓画布叠加层（Building Footprint Overlay）开发任务书

> **优先级**：P1（建筑插件下一阶段首要任务）  
> **前置验收**：2026-09 轮次已通过——`ALL`/`SELECTED_ONLY`、Footprint Repair、Circle/Ellipse 认领、D-B19 CI gate  
> **产品准绳**：见 [BuildingPlugin_产品定位.md](../BuildingPlugin_产品定位.md)  
> **参考实现**：`PowerLinePlugin` + `CanvasOverlayRegistry`（画布编辑态叠加，≠ Ghost Block 生成预览）

---

## 0. 要解决的问题

认领之后，画布缺少**建筑语义反馈**。用户面对几十/几百个轮廓时无法回答：

- 哪些已认领 / 未认领？
- 列表里选中的 Building 17 对应哪块轮廓？
- 多选的 12 栋在画布上是哪 12 个？
- 哪些轮廓被自动修复？哪栋生成失败？

**这不是再加一个建筑参数能解决的，而是空间反馈层缺失。**

电力线路插件之所以易理解，是因为「线路、杆塔、选中对象」在视觉空间里始终有对应物。Building 底层链路已成熟，缺的是同一层 **Footprint Overlay**。

---

## 1. 三层可视化（必须严格区分）

| 层 | 名称 | 时机 | 数据依赖 | 禁止依赖 |
|----|------|------|----------|----------|
| **L1** | Footprint Overlay | 插件启用且开关打开（建议默认开） | `BuildingProject`、`BuildingSelectionSet` | `BuildingGenerationResult`、`BlockRecord` |
| **L2** | Selection Highlight | L1 之上，随列表/画布选择变化 | 同上 + `BuildingOverlayState` | 同上 |
| **L3** | Massing / World Preview | 用户点击 Preview District / Preview | `BuildingGenerationResult` → Ghost | 现有逻辑，**不改语义** |

```
Canvas Shape → Adopt → BuildingProject
                              │
                              ▼
                    Footprint Overlay (L1/L2)  ← 本任务
                              │
                              ▼ (用户主动 Preview)
                    Ghost Block Preview (L3)   ← 已有，保持独立
                              │
                              ▼
                           Build
```

**硬约束**：不得把 L1/L2 与 L3 合并为同一 UI 状态或同一 Renderer。

---

## 2. 包结构

```
com.plot.plugin.building.overlay/
    BuildingOverlayState.java      // REGISTERED | SELECTED | PRIMARY | INVALID | WARNING | PREVIEWED(可选)
    BuildingOverlayEntry.java      // buildingId, outerPoints, floors, state, label(可选)
    BuildingOverlayStyle.java      // 颜色/线宽/虚线/填充透明度 per state
    BuildingOverlayController.java // 从 Project + Selection + PickSession 合成 entries
    BuildingOverlayRenderer.java   // ImDrawList + CanvasCamera 绘制闭合环与标签
```

**`BuildingOverlayEntry` 最小字段**：

```java
record BuildingOverlayEntry(
    String buildingId,
    String displayName,      // 如 B-024 或 footprint name
    List<Vec2d> outerPoints,
    int floors,
    BuildingOverlayState state
) {}
```

认领页（Adopt）可扩展**候选态**（未认领 / pick 中 / invalid / already adopted），由 Controller 第二数据源 `List<Shape> pickCandidates` 提供，不写入 `BuildingFootprint`。

---

## 3. 画布集成（对齐 PowerLine）

- 在 `BuildingPlugin.onEnable()` 注册 `CanvasOverlayRegistry.Overlay`
- 在 `onDisable()` 注销
- 仅当 **Building 插件为当前扩展面板活动插件** 时绘制（与 `PowerLinePlugin.renderCanvasPreviewOverlay` 相同守卫）
- 绘制在 `CanvasRenderer` 已有 `CanvasOverlayRegistry.renderAll` 路径上，不修改 Shape 本体

```java
// BuildingPlugin.java（目标形态）
private final CanvasOverlayRegistry.Overlay footprintOverlay = this::renderFootprintOverlay;

@Override
public void onEnable() {
    // ...
    CanvasOverlayRegistry.register(footprintOverlay);
}

private void renderFootprintOverlay(ImDrawList drawList, CanvasCamera camera) {
    if (!isEnabled() || uiContext == null) return;
    if (PluginManager.getInstance().getActivePlugin() != this) return;
    BuildingOverlayRenderer.render(drawList, camera, uiContext.overlayController().snapshot());
}
```

---

## 4. 视觉状态（L1 + L2）

| State | 用途 | 建议视觉（可微调） |
|-------|------|-------------------|
| `REGISTERED` | 已认领、未选中 | 细实线、低饱和填充 |
| `SELECTED` | 在 selection 内 | 粗实线、较高饱和 |
| `PRIMARY` | selection 主项 | 最粗线 + 角标/箭头 |
| `INVALID` | 认领校验失败候选 | 虚线 + 警告色 |
| `WARNING` | 生成 skip / site 警告（可选二期） | 虚线 + 琥珀色 |
| `PREVIEWED` | 最近一次 district preview 成功（可选） | 细点线，不遮挡 L2 |

**认领页额外态**（Adopt Tab 活跃时）：

| 态 | 含义 |
|----|------|
| `CANDIDATE` | 画布上可认领、尚未认领 |
| `PICK_ACTIVE` | 当前 pick 会话选中 |
| `ALREADY_ADOPTED` | 与已有 footprint 重叠或已认领（几何匹配策略见 §6） |

---

## 5. 列表 ↔ 画布双向联动（P1）

统一现有分散能力：

| 操作 | 行为 |
|------|------|
| Overview 单击建筑行 | `selection.select(id)` + 画布 PRIMARY/SELECTED 高亮 |
| Ctrl 多选 | 画布多 footprint 高亮 |
| Hover 列表行（可选） | 临时 hover 高亮，不改变 selection |
| 双击行 | `camera.setOffset(centroid)` + zoom（扩展 `locateBuilding`） |
| Locate 按钮 | 保留，行为与双击一致 |

`BuildingActions.locateBuilding` 已有 `centroid` + `camera.setOffset` + `selection.select`——Overlay 应订阅同一 `BuildingSelectionSet`，避免两套选中状态。

---

## 6. 分阶段交付

### Phase 1 — MVP（本任务最小可交付）

- [ ] `BuildingOverlayController` + `BuildingOverlayRenderer`
- [ ] L1：已认领建筑 footprint 闭合环
- [ ] L2：`REGISTERED` / `SELECTED` / `PRIMARY` 三档
- [ ] `CanvasOverlayRegistry` 注册/注销
- [ ] Toolbar：`☑ 显示建筑轮廓`（**默认开启**，持久化到 `BuildingPluginState`）
- [ ] Overview 列表点击与 overlay 同步（验证 selection 已有则只补绘制）
- [ ] 单元测试：Controller 状态映射（given project + selection → entries 与 state 正确）

**不在 Phase 1**：层数标签、Adopt 候选态、WARNING/PREVIEWED、Repair 跳转。

### Phase 2 — Adopt + 修复反馈

- [ ] Adopt Tab 下显示 `CANDIDATE` / `PICK_ACTIVE` / `ALREADY_ADOPTED` / `INVALID`
- [ ] 认领成功提示「N 个轮廓已自动修复」→ `[Show]` 选中并高亮该 N 栋
- [ ] 几何判断「画布 Shape 是否已被某 footprint 覆盖」（可用 bounds + 点集近似，不要求完美 GIS）

### Phase 3 — Massing 辅助（仍属 L1，非 Ghost）

- [ ] 轮廓中心轻量标签：`B12` 或 `8F`（可开关）
- [ ] `applyHeightDistribution` 后**无需 Ghost**即可在画布看到层数变化
- [ ] 与 Height Distribution RANDOM/GRADIENT 联动刷新标签

### 明确不做（本任务）

- 不把 Overlay 画进 `BuildingGeneratePanel` 内部
- 不依赖 `BuildingGenerationResult` 做常驻显示
- 不替换现有 Ghost Preview 流程
- 不在 Phase 1 做 District Variation 算法

---

## 7. UI 文案（i18n 预留）

| Key | 中文 |
|-----|------|
| `plugin.building.overlay.show_footprints` | 显示建筑轮廓 |
| `plugin.building.overlay.show_labels` | 显示编号 |
| `plugin.building.overlay.show_floors` | 显示层数 |
| `plugin.building.adopt_repaired_show` | 查看 |

---

## 8. 测试

| 类型 | 内容 |
|------|------|
| 单元 | `BuildingOverlayControllerTest`：selection 变化 → entry state |
| 单元 | `BuildingOverlayRendererTest`（可选）：空 project 不绘制 |
| 手工 | Overview 选 12 栋 → 画布 12 环高亮；Primary 更粗 |
| 手工 | 插件禁用/切换扩展 → overlay 消失 |
| 回归 | Ghost Preview / D-B19 CI gate 不受影响 |

---

## 9. 与当前代码的锚点

| 已有 | 用途 |
|------|------|
| `CanvasOverlayRegistry` | 注册入口 |
| `PowerLinePlugin.renderCanvasPreviewOverlay` | 活动插件守卫 + synchronized 模式 |
| `BuildingActions.locateBuilding` | centroid + camera |
| `BuildingSelectionSet` | 唯一 selection 真相源 |
| `BuildingGeometryUtils.computeCentroid` | 标签锚点 |
| `BuildingFootprintValidator` + Repair | Phase 2 INVALID / repaired 列表 |

---

## 10. 优先级对照（2026-09 更新）

| 优先级 | 项 |
|--------|-----|
| **P1** | Footprint Overlay + Selection Highlight（本任务） |
| **P1** | List ↔ Canvas 双向选择/定位 |
| **P1** | Adopt 状态可视化（Phase 2） |
| P2 | District Variation（`DistrictMassingSpec`，勿塞入 `BuildingFootprint`） |
| P2 | Preset 卡片化 UI |
| 保持 | Fail-soft、Pipeline、`BuildingDefinition` 模型、Footprint 冻结线 |

---

## 11. 完成标准（Phase 1）

用户打开建筑插件、画布有 ≥2 栋已认领建筑时：

1. **默认**能在画布看到所有已认领 footprint 轮廓
2. 在 Overview 点击任一建筑，对应轮廓立即高亮，且 primary 可区分
3. 多选时多个轮廓同时高亮
4. 关闭「显示建筑轮廓」后 overlay 消失，Ghost Preview 仍独立工作
5. 不启用建筑插件时无 overlay 残留
