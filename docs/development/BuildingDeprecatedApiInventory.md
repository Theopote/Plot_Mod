# Building Deprecated API Inventory

> **P3 Cleanup** — 存在 Deprecated ≠ 应立即删除。本清单用于审计与分批移除决策，**非当前最高优先级**。
>
> 生成日期：2026-09-07  
> 范围：`com.plot.plugin.building.*` 及 `BuildingPlugin` 公开入口

## 删除准则

仅在同时满足以下条件时，才允许从代码库移除：

| 条件 | 说明 |
|------|------|
| 0 生产调用 | `src/main/java` 中除定义处外无引用 |
| 0 测试依赖 | 或测试已迁移至 replacement |
| 无反射 | 无 `Class.forName` / MethodHandle 动态调用 |
| 无序列化契约 | JSON / Gson 字段名或类型别名仍被旧工程使用 |
| 无外部 API 承诺 | 无第三方 mod 文档化的 public 入口 |

与 [RoadDeprecatedApiInventory.md](RoadDeprecatedApiInventory.md) 相同：**先清单、后分批、再守卫测试**。

---

## 汇总

| 状态 | 数量 | 说明 |
|------|------|------|
| **保留（兼容层）** | 10 | 全部 `@Deprecated`；当前 **0 生产调用**（定义处除外） |
| **已删除** | 0 | 尚无 Batch 删除记录 |

**优先级**：P3 Cleanup — 报告若将其标为「唯一中优先级问题」，属排序错误；应在 Massing UI、Persistence Facade、Seed 等 P1/P2 项稳定后再处理。

---

## 明细

### 1. `BuildingDefinition` 六参数构造器

| 字段 | 值 |
|------|-----|
| **位置** | `model/spec/BuildingDefinition.java` |
| **符号** | `BuildingDefinition(footprint, massing, envelope, facade, roof, foundation)` |
| **Replacement** | 七参数构造器，末参 `AccessorySpec`（无附属时用 `AccessorySpec.none()`） |
| **Production usages** | 0 — `BuildingDefinitionMapper`、`BuildingPresetCatalog` 已用七参数 |
| **Test usages** | 0 — 测试 helper 均已传入 `AccessorySpec.none()` |
| **Persistence** | 否（JSON 经 `BuildingFootprint` / `BuildingProjectPersistence`） |
| **Reflection** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 2. `GenerationSiteResolver.ResolvedSiteElevation.baseElevation()`

| 字段 | 值 |
|------|-----|
| **位置** | `generation/resolve/GenerationSiteResolver.java` |
| **Replacement** | `actualFoundationElevation()` |
| **Production usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 3. `GenerationSiteResolver.ResolvedSiteElevation.earthworkPadElevation()`

| 字段 | 值 |
|------|-----|
| **位置** | `generation/resolve/GenerationSiteResolver.java` |
| **Replacement** | `resolvedPadElevation()` |
| **Production usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

> 注意：`BuildingFoundationUtils` 等方法参数名 `earthworkPadElevation` 是局部命名，**不是**对此 accessor 的调用。

---

### 4. `GenerationSiteResolver.ResolvedSiteElevation.usedEarthworkPad()`

| 字段 | 值 |
|------|-----|
| **位置** | `generation/resolve/GenerationSiteResolver.java` |
| **Replacement** | `source() == FoundationElevationSource.EARTHWORK_PAD` |
| **Production usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 5. `BuildingGenerationContext.getFootprint()`

| 字段 | 值 |
|------|-----|
| **位置** | `generation/BuildingGenerationContext.java` |
| **Replacement** | `getDefinition()`（语义源）；需 footprint 几何时用 `getOuterPoints()` / `BuildingDefinition.fromFootprint` |
| **Production usages** | 0 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 6. `BuildingRoofGenerator.generate`（无 `roofPitchRatio` 重载）

| 字段 | 值 |
|------|-----|
| **位置** | `BuildingRoofGenerator.java` |
| **Replacement** | `generate(..., int roofPitchRatio, ...)` |
| **Production usages** | 0 — `RoofGenerationStage` 已用带 pitch 重载 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 7. `BuildingGeometryUtils.isAxisAlignedSlopedRoofEligible`

| 字段 | 值 |
|------|-----|
| **位置** | `BuildingGeometryUtils.java` |
| **Replacement** | `isSlopedRoofEligible(List)` / `isSlopedRoofEligible(List, int pitchRatio)`（Straight Skeleton v2） |
| **Production usages** | 0 — `RoofGenerationStage` 已用 `isSlopedRoofEligible` |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 8. `BuildingSiteAnalyzer.analyze`（三参数，无 `buildingId`）

| 字段 | 值 |
|------|-----|
| **位置** | `site/BuildingSiteAnalyzer.java` |
| **Replacement** | `analyze(String buildingId, ResolvedMassingGeometry, World, ICoordinateService)` |
| **Production usages** | 0 — `GenerationSiteResolver` 已用四参数 |
| **Test usages** | 0 |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 9. `RoofGenerationStage.resolveRoofType(BuildingFootprint, BuildingGenerationResult)`

| 字段 | 值 |
|------|-----|
| **位置** | `generation/stage/RoofGenerationStage.java` |
| **Replacement** | `resolveRoofType(BuildingDefinition, List<Vec2d>, BuildingGenerationResult)` |
| **Production usages** | 0 |
| **Test usages** | 0 — 测试已用 `BuildingDefinition.fromFootprint` + 三参数 API |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选） |

---

### 10. `BuildingHeightDistribution.Settings.of(Mode, int, int)`（三参数，无 seed）

| 字段 | 值 |
|------|-----|
| **位置** | `BuildingHeightDistribution.java` |
| **Replacement** | `Settings.of(Mode, int minFloors, int maxFloors, long seed)` |
| **Production usages** | 0 — `BuildingEditPanel` 已传显式 seed |
| **Test usages** | 0 — 测试均用四参数（含 `0L` 或固定 seed） |
| **Persistence** | 否 |
| **safeToRemove** | **是**（Batch A 候选；与 Height Distribution Seed 功能同期引入） |

> 历史：`System.nanoTime()` 作 seed 的版本已在 2026-09-07 改为 `0L` 并标记 `@Deprecated`；RANDOM 模式须显式 seed 才可复现。

---

## 相关但未标记 `@Deprecated` 的持久化 / 兼容逻辑

| 项 | 位置 | 说明 |
|----|------|------|
| `doors[]` → `OpeningSpec.DOOR` | `BuildingProject.ProjectData` | legacy JSON 字段映射；删字段需 migration |
| `BuildingFootprint` 直写 JSON | `BuildingProjectPersistence` → `toJson`/`fromJson` | 聚合根仍内嵌 Gson DTO；Facade 已建立，DTO 迁移见 [BuildingProjectPersistence](../main/java/com/plot/plugin/building/model/persistence/BuildingProjectPersistence.java) |
| `ResolvedBuilding` 注释 | `model/resolve/ResolvedBuilding.java` | 标注为 legacy DTO 名称，类本身未 `@Deprecated` |

这些**不在**本次 Batch A 范围；移除前需单独 persistence 审计。

---

## 建议删除顺序（P3，未排期）

**Batch A（当前全部 safeToRemove = 是）** — 10 项可一次性删除，前提是：

1. 运行全量 `com.plot.plugin.building.*` 测试
2. 添加 `BuildingDeprecatedApiInventoryTest` 守卫（删除后验证符号未重现）
3. 无 golden / dev project fixture 依赖旧 accessor（当前审计：无）

**暂不建议**：

- 在未完成 `BuildingProject` DTO 外迁前，改动 JSON 字段名或 legacy door 映射
- 为「清零 Deprecated 计数」而删除仍可能被旧存档隐式依赖的 Gson 路径

---

## 自动化守卫

删除 Batch A 后新增 `BuildingDeprecatedApiInventoryTest`（模式同 `RoadDeprecatedApiInventoryTest`），验证已删符号未重新进入 `src/main/java`。

当前阶段：**仅本文档审计，不执行删除**。
