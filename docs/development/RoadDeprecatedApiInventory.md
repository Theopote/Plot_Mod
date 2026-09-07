# Road Deprecated API Inventory

> Phase G — Stabilization。**Batch A 已于 2026-09-07 删除**；下文保留审计记录。
>
> 生成日期：2026-09-07  
> 范围：`com.plot.plugin.road.*` 及道路相关 `RoadSystemConfig` 条目

## 删除准则

仅在同时满足以下条件时，才允许从代码库移除：

| 条件 | 说明 |
|------|------|
| 0 生产调用 | `src/main/java` 中除定义处外无引用 |
| 0 测试依赖 | 或测试已迁移至 replacement |
| 无反射 | 无 `Class.forName` / MethodHandle 动态调用 |
| 无序列化契约 | JSON / Gson 字段名或类型别名仍被旧工程使用 |
| 无外部 API 承诺 | 无第三方 mod 文档化的 public 入口 |

---

## 汇总

| 状态 | 数量 | 说明 |
|------|------|------|
| **已删除（Batch A）** | 13 | 2026-09-07 移除，见下方删除记录 |
| **保留（兼容层）** | 4 | Gson / style id / 测试或内部 alias |
| **迁移后删除（Batch B）** | 2 | 仅测试仍引用，需先改测试 |

---

## Batch A 删除记录（2026-09-07）

| # | 已删除 API | Replacement |
|---|-----------|-------------|
| 1 | `RoadNetwork.linkEdgeToRoad` | `assignEdgeToRoad` |
| 2 | `Road.applyPreset` | `applyStyle` |
| 3 | `RoadCrossSection.fromPreset` | `fromStyle` |
| 4 | `CrossSectionLayout.fromPreset` | `fromStyle` |
| 5 | `RoadPlanGeometry.planLength` | `designLength` |
| 6 | `RoadPlanGeometry.instanceChainLength` | `instanceLength` |
| 7 | `RoadStationing.totalLength` | `canonicalLength` |
| 8 | `RoadStationing.planLength` | `designLength` |
| 9 | `EdgeChainageMapper.toChainage`（4 参数） | `toChainage(OrientedRoadSegment, …)` |
| 10 | `mergeJunctionBlocksWithConfigDefaults` | `mergeJunctionBlocks` / `mergeJunctionForNode` |
| 11 | `MinecraftTerrainSampler` 两个 static 委托 | `EngineeringTerrainService` |
| 12 | `RoadSystemConfig.getPresets` | `getStyles` |
| 13 | `RoadSystemConfig.applyPreset` | `applyStyle` |

---

## 明细（历史审计）

### 1. `RoadNetwork.linkEdgeToRoad`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadNetwork.java` |
| **Replacement** | `assignEdgeToRoad(String edgeId, String roadId)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否（非 JSON 字段） |
| **Reflection** | 否 |
| **safeToRemove** | **是** |

> 注意：参数顺序与 replacement **相反**（历史 bug 来源）。

---

### 2. `Road.applyPreset`

| 字段 | 值 |
|------|-----|
| **位置** | `Road.java` |
| **Replacement** | `applyStyle(RoadStyle)` / `applyStyle(String, RoadSystemConfig)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 3. `RoadCrossSection.fromPreset`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadCrossSection.java` |
| **Replacement** | `fromStyle(RoadStyle)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 4. `RoadCrossSectionPreviewRenderer.CrossSectionLayout.fromPreset`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadCrossSectionPreviewRenderer.java` |
| **Replacement** | `fromStyle(RoadStyle)` / `fromStyle(RoadStyle, String)` |
| **Internal usages** | 0 |
| **Test usages** | 0（测试已用 `fromStyle`） |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 5. `RoadPlanGeometry.planLength`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadPlanGeometry.java` |
| **Replacement** | `designLength(RoadNetwork, Road)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 6. `RoadPlanGeometry.instanceChainLength`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadPlanGeometry.java` |
| **Replacement** | `instanceLength(RoadNetwork, Road)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 7. `RoadStationing.totalLength`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadStationing.java` |
| **Replacement** | `canonicalLength(RoadNetwork, Road)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 8. `RoadStationing.planLength`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadStationing.java` |
| **Replacement** | `designLength(RoadNetwork, Road)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 9. `EdgeChainageMapper.toChainage`（4 参数重载）

| 字段 | 值 |
|------|-----|
| **位置** | `EdgeChainageMapper.java` |
| **Replacement** | `toChainage(OrientedRoadSegment, double, double)` |
| **Internal usages** | 0（`DesignElevationSource` 已用新 API） |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 10. `RoadGenerationResultAssembler.mergeJunctionBlocksWithConfigDefaults`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadGenerationResultAssembler.java` |
| **Replacement** | `mergeJunctionBlocks(...)` / `mergeJunctionForNode(...)` |
| **Annotation** | `@Deprecated(since = "1.x", forRemoval = true)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（优先删除候选） |

---

### 11. `MinecraftTerrainSampler` 包内静态方法

| 方法 | Replacement |
|------|-------------|
| `countsAsEngineeringTerrain(BlockState)` | `EngineeringTerrainService.isEngineeringTerrain` |
| `isNaturalDecoration(BlockState)` | `EngineeringTerrainService.isNaturalDecoration` |

| 字段 | 值 |
|------|-----|
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Visibility** | package-private static |
| **safeToRemove** | **是** |

---

### 12. `RoadSystemConfig.getPresets`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadSystemConfig.java` |
| **Replacement** | `getStyles()` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | **间接**：JSON 字段仍名 `presets`（非此方法名） |
| **safeToRemove** | **是**（方法可删；`presets` 字段名保留） |

---

### 13. `RoadSystemConfig.applyPreset`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadSystemConfig.java` |
| **Replacement** | `applyStyle(RoadStyle)` |
| **Internal usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是** |

---

### 14. `RoadStyleCatalog.citySecondary`

| 字段 | 值 |
|------|-----|
| **位置** | `RoadStyleCatalog.java` |
| **Replacement** | `residential()`（id 重写为 `city_secondary`） |
| **Internal usages** | 1 — `resolveLegacyStyleId("city_secondary")` |
| **Test usages** | 1 — `RoadStyleTest.citySecondaryAliasStillResolvable` |
| **Persistence** | **是** — 旧工程 / lang key `preset.road.city_secondary` |
| **safeToRemove** | **否** — 保留 style id 别名 |

---

### 15. `RoadSystemConfig.RoadPreset`（Gson 类型）

| 字段 | 值 |
|------|-----|
| **位置** | `RoadSystemConfig.java` 内部类 |
| **Replacement** | `RoadStyle` |
| **Internal usages** | Gson 反序列化 `presets` 列表 |
| **Test usages** | 0 |
| **Persistence** | **是** — 旧 `road_system.json` 可能含 `RoadPreset` 形状 |
| **safeToRemove** | **否** — 需 format 迁移或 major 版本 |

---

### 16. `RoadSegmentTopologyAnalyzer`（整类）

| 字段 | 值 |
|------|-----|
| **位置** | `RoadSegmentTopologyAnalyzer.java` |
| **Replacement** | `RoadTopologyInvariantValidator` + `RoadTopologyViolationKind` |
| **Internal usages** | 0 |
| **Test usages** | 1 — `RoadSegmentTopologyAnalyzerTest` |
| **Persistence** | 否 |
| **ADR** | `docs/decisions/0004-road-topology-invariant.md` |
| **safeToRemove** | **否** — 先迁移测试，再删类 |

---

### 17. `VoxelGradeDiscretizer`（整类）

| 字段 | 值 |
|------|-----|
| **位置** | `VoxelGradeDiscretizer.java` |
| **Replacement** | `com.plot.core.geometry.VoxelElevationDiscretizer` |
| **Internal usages** | 0 |
| **Test usages** | 1 — `VoxelGradeDiscretizerTest` |
| **Persistence** | 否 |
| **safeToRemove** | **否** — 先迁移测试 |

---

## 相关但未标记 @Deprecated 的持久化字段

| 字段 | 位置 | 说明 |
|------|------|------|
| `selectedPreset` | `RoadSystemConfig` | 仍写入 JSON；语义同 `selectedStyle`，改名需 migration |
| `presets` | `RoadSystemConfig` | JSON 数组字段名；内容为 `RoadStyle`，非 Deprecated 方法 |

---

## 建议删除顺序（剩余）

1. **Batch B（测试迁移）**：#16、#17 — 改测试 import 后删类  
2. **Batch C（持久化）**：#15 — 需 `road_system.json` v2 或兼容层  
3. **保留 indefinitely**：#14 `city_secondary` 别名

---

## 自动化守卫

`RoadDeprecatedApiInventoryTest` 验证 Batch A 符号未重新引入生产代码，并监控 Batch B 委托类未被生产引用。
