# EarthworkSite 领域设计

> 土方平衡插件从「整平工具」演进为「建筑场地土方」的领域模型、JSON 持久化与 Design Terrain 合成规则。
>
> **状态**：Phase 16 已实施（`schemaVersion` 正式化 + v1→v2→v3 迁移链）  
> **版本**：`schemaVersion: 3`（当前）；加载时自动迁移 v1/v2  
> **架构**：Phase 17 设计定稿 → 见 [Earthwork_2.0_架构.md](./Earthwork_2.0_架构.md)

---

## 1. 问题陈述

### 1.1 当前模型

`GradingRegion` 表达的是：

```
RegionGeometry { outerRing, holes[] }
    + 设计曲面 / 排除模式
    → 有效参与挖填的格点子集
```

对应代码：`RegionGeometry` + `PolygonRegionUtils` + `DesignTerrainComposer`。

### 1.2 真实建筑场地的语义缺口

| 场地要素 | 工程含义 | 当前支持 |
|---------|---------|---------|
| Site Boundary | 施工红线 / 场地范围 | `outerPoints`（每 Region 各一份，无统一 Site） |
| Building Pad | 室外地坪 ±0.000 | ❌ |
| Basement Excavation | 基坑底 + 工作面 + 放坡 | ❌ |
| Road / Plaza / Landscape | 分区不同设计标高 | ❌（道路在 Road 插件） |
| Retaining Wall | 挡土 / 垂直界 | ❌ |
| Exclusion Zone | 不动土区 / 保留岛 / 中庭 | ✅ `ExclusionZone` + `RegionGeometry.holes` |
| Region holes | 建筑保留区、水池、已建构筑物 | ✅ `GradingZone.holes` |
| **Design Terrain** | 多分区合成后的最终设计地形 | ❌（仅单平面） |
| Existing Terrain | 可审计的现状基准 | ✅ `TerrainSnapshot`（未入持久化模型） |

**结论**：插件算量可以正确，但**产品抽象仍是整平**；要从玩具升级为建筑土方，核心是把 `GradingRegion` 提升为 `EarthworkSite` + 多 `GradingZone` + **Design Terrain 合成器**。

---

## 2. 设计目标与非目标

### 2.1 目标

1. **一个工程一个场地**（`EarthworkSite`），统一 Site Boundary 与现状地形基准。
2. **多分区设计面**（`GradingZone`），各 Zone 独立定义设计曲面/设计体。
3. **可复现的 Design Terrain 合成**，输出逐格 `targetY[x,z]` 及来源 Zone。
4. **JSON 可版本迁移**：`schemaVersion: 1`（`regions[]`）→ `2`（`sites[]`）→ `3`（完整场地模型，见 §7）。
5. **与建筑/道路插件可对接**（引用外轮廓与标高，不复制几何引擎）。

### 2.2 非目标（本阶段不做）

- 有限元沉降、渗流、压实遍数等施工仿真。
- 自动从 CAD 导入 Breakline（可预留字段）。
- 道路边坡细部（属 Road 插件；土方仅消费道路设计高程或 Breakline）。
- 实时多人协同编辑冲突解决。

---

## 3. 核心概念与层次

```
┌─────────────────────────────────────────────────────────────┐
│                      EarthworkProject                        │
│  schemaVersion · sites[] · (legacy regions[] 只读迁移)        │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      EarthworkSite                           │
│  siteBoundary · materialModel · existingTerrainRef          │
│  gradingZones[] · breaklines[] · retainingEdges[]           │
│  exclusionZones[] · compositionPolicy                         │
└───────────────────────────┬─────────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
   GradingZone[]      Breakline[]      ExclusionZone[]
   (设计分区)          (硬边界)          (不施工)
          │
          ▼
   DesignSurface (每 Zone 内)
          │
          ▼
   DesignTerrainComposer  ──→  DesignTerrainGrid
          │                      [x,z] → targetY, zoneId, flags
          ▼
   EarthworkPipeline (已有四步)
          │
          ▼
   EarthworkReport (EarthworkVolumeReport 超集)
```

### 3.1 与现有类的映射

| 新概念 | 现有实现 | 迁移策略 |
|--------|---------|---------|
| `ExistingTerrain` | `TerrainSnapshot` | 运行时捕获；持久化存 `ExistingTerrainRef` |
| `MaterialModel` | `EarthMaterialProperties` | Site 级默认；Zone 可覆盖 |
| `FlatZone` | `GradingRegion` + `FLAT/FIT_SLOPE` | 原样包装，`type: "FLAT"` |
| `SlopedZone` | `FIXED_SLOPE` / `THREE_POINT` | `type: "SLOPED"` |
| `DesignSurface` | `GradingPlane` + `GradingSurfaceResolver` | Zone 内求解，不再 Site 级单平面 |
| `EarthworkReport` | `EarthworkVolumeReport` | 增加分区明细、快照元数据 |
| `EarthworkProject` | `Map<String, GradingRegion>` | 增加 `sites[]`，`regions[]` 迁移 |

---

## 4. 实体定义

### 4.1 EarthworkSite

场地是土方工程的最高聚合根（建议 **一个 Project 默认一个 Site**；多 Site 留给大型园区后期扩展）。

```java
class EarthworkSite {
    String id;
    String name;
    List<Vec2d> siteBoundary;          // 施工红线（画布坐标）
    EarthMaterialProperties materialModel;  // Site 默认材料模型
    ExistingTerrainRef existingTerrainRef;  // 上次预览/落地的现状快照引用
    CompositionPolicy compositionPolicy;
    Map<String, GradingZone> gradingZones;
    List<Breakline> breaklines;
    List<RetainingEdge> retainingEdges;
    List<ExclusionZone> exclusionZones;
    // transient
    EarthworkReport lastReport;
}
```

**约束**

- `siteBoundary` 必须简单多边形（≥3 点），作为 footprint 采样与合成的裁剪边界。
- 所有 `GradingZone.outerPoints` 必须在 `siteBoundary` 内（允许贴边，不允许大面积越界）。
- `existingTerrainRef` 在每次「计算预览」成功后更新；落地前对比 `TerrainSnapshot.compareWithCurrentWorld()`（已实现）。

### 4.2 ExistingTerrainRef

现状地形不写入工程 JSON 的全量体素（体积过大），只存**可审计元数据** + 可选外部文件。

```json
{
  "capturedAtEpochMs": 1725176465000,
  "worldKey": "minecraft:overworld",
  "outlineFingerprint": 3847291047281,
  "contentFingerprint": 9182736451029,
  "columnCount": 1248,
  "snapshotFile": "snapshots/site-abc-20260902.json"
}
```

- `snapshotFile`：相对于工程目录的可选侧车文件（完整 `TerrainSnapshot` 列数据）。
- 无侧车文件时，预览必须重新 `captureFresh()`。
- `contentFingerprint` 算法与 `TerrainSnapshot.computeContentFingerprint()` 一致。

### 4.2.1 RegionGeometry（Phase 13）

所有平面分区（`GradingZone`、`ExclusionZone`、`DesignSurfaceFacet`）共享的几何模型：

```java
class RegionGeometry {
    List<Vec2d> outerRing;          // 外环（≥3 点）
    List<List<Vec2d>> holes;        // 内环孔洞（可空）
}
```

```json
{
  "outerRing": [
    { "x": 0, "y": 0 },
    { "x": 20, "y": 0 },
    { "x": 20, "y": 20 },
    { "x": 0, "y": 20 }
  ],
  "holes": [
    [
      { "x": 6, "y": 6 },
      { "x": 14, "y": 6 },
      { "x": 14, "y": 14 },
      { "x": 6, "y": 14 }
    ]
  ]
}
```

**语义**

- 格点 `(x,z)` 在区域内 ⟺ 位于 `outerRing` 内且不在任一 `holes` 内。
- 净面积 = `|outerRing|` − Σ`|hole|`（`GradingZone.computeArea()`、`ZoneOverlapAnalyzer` 面积裁决）。
- `outerPoints` 为 **读兼容别名**：反序列化时若 `outerRing` 为空则回退 `outerPoints`；序列化时同时写出两者。

**典型孔洞用途**

| 场景 | 建模方式 |
|------|---------|
| 建筑保留区 / 已建构筑物 | `GradingZone.holes` 或独立 `ExclusionZone` |
| 水池 / 禁挖区 | `ExclusionZone`（`PRESERVE_EXISTING` / `NO_TOUCH`） |
| 中庭（场地内岛，不整平） | `GradingZone` 外环 + `holes`，或环形 `ExclusionZone` |
| `MULTI_PLANE` 子面片 | `DesignSurfaceFacet` 同样支持 `holes` |

**边界放坡**：`ZoneEdgeSettings.edgeIndex` 仅索引 **外环** 边；孔洞边默认垂直截止（后续可扩展 `holeEdgeOverrides`）。

### 4.3 GradingZone（抽象）

所有设计分区共享：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | UUID |
| `name` | string | 显示名 |
| `type` | enum | 见 4.4 |
| `outerRing` / `outerPoints` | Vec2d[] | 外环；`outerPoints` 为兼容字段 |
| `holes` | Vec2d[][] | 内环孔洞（可选） |
| `priority` | int | 合成优先级，越大越优先 |
| `enabled` | bool | 是否参与合成 |
| `materialOverride` | MaterialModel? | 空则用 Site 默认 |
| `cutExposeMaterial` | string | 挖方裸露地表 |
| `fillMaterial` | string | 填方材质 |
| `designSurface` | DesignSurface | 设计面定义 |
| `edgeSettings` | ZoneEdgeSettings | 边界处理策略（Phase F） |

### 4.3.1 ZoneEdgeSettings（Phase F）

分区边界默认策略与逐边覆盖：

```json
{
  "defaultTreatment": "CUT_FILL_SLOPE",
  "cutSlopePitchRatio": 1,
  "fillSlopePitchNumerator": 3,
  "fillSlopePitchDenominator": 2,
  "maximumReachBlocks": 8,
  "benchWidthBlocks": 0,
  "edgeOverrides": [
    { "edgeIndex": 0, "treatment": "RETAINING_WALL" }
  ]
}
```

| `EdgeTreatment` | 行为 |
|-----------------|------|
| `VERTICAL` | 垂直切填（第一版默认） |
| `CUT_FILL_SLOPE` | 分区外侧按挖/填坡比放坡 |
| `RETAINING_WALL` | 垂直截止（实体墙由 `RetainingWallGenerator`） |
| `MATCH_EXISTING` | 边界带内贴合现状高程 |

`benchWidthBlocks > 0` 时，外侧放坡按 **坡面段（宽 = benchWidth，按坡比升高）→ 水平平台段（宽 = benchWidth，高程不变）** 周期交替（`SlopeBenchProfile`）。

`edgeIndex` 对应 `outerPoints[i] → outerPoints[i+1]`。合成后由 `ZoneBoundarySlopeApplicator` 修正 `DesignTerrainGrid`；画布上以颜色区分各边策略（见 `EarthworkEdgeTreatmentCanvasRenderer`）。

### 4.4 GradingZone 类型（MVP → 完整）

| `type` | 工程含义 | MVP | 设计面 |
|--------|---------|-----|--------|
| `FLAT` | 场地平整 / 广场 | ✅ 迁移自 `GradingRegion` | `FLAT` / `FIT_SLOPE` |
| `SLOPED` | 坡向整平 / 排水坡 | ✅ | `FIXED_SLOPE` / `THREE_POINT` |
| `BUILDING_PAD` | 建筑室外地坪 ±0.000 | Phase C | 常数标高 + 可选建筑引用 |
| `EXCAVATION_PIT` | 基坑（坑底+工作面+放坡） | Phase C | 坑底平面 + 边坡体 |
| `TERRAIN_FIT` | 过渡/贴合现状 | Phase B | `FIT_SLOPE` 或「保持现状」 |
| `ROAD_CORRIDOR` | 道路走廊土方 | Phase D | 引用 Road 纵断面采样 |
| `LANDSCAPE` | 景观微地形 | Phase D | 相对高程 + 平滑 |

**MVP 仅实现 `FLAT` + `SLOPED`**，其余类型在 JSON 中预留 `type`，反序列化时降级为 `FLAT` 并打 warning。

### 4.5 DesignSurface

从「一个 Region 选 surfaceMode」改为显式结构：

```json
{
  "kind": "FLAT",
  "autoBalance": true,
  "manualTargetElevation": null,
  "fitSlopeBalanceCutFill": true
}
```

```json
{
  "kind": "FIXED_SLOPE",
  "slopeDirectionDegrees": 90.0,
  "slopePitchRatio": 4,
  "anchorCanvas": { "x": 12.0, "y": 8.0 },
  "anchorElevation": 70
}
```

```json
{
  "kind": "THREE_POINT",
  "controls": [
    { "canvas": { "x": 0, "y": 0 }, "elevation": 60 },
    { "canvas": { "x": 20, "y": 0 }, "elevation": 64 },
    { "canvas": { "x": 0, "y": 15 }, "elevation": 62 }
  ]
}
```

```json
{
  "kind": "FIT_SLOPE",
  "fitSlopeBalanceCutFill": true
}
```

**Phase C 扩展**（不在 MVP JSON 必填）：

```json
{
  "kind": "CONSTANT_ELEVATION",
  "elevation": 68,
  "buildingFootprintRef": "building-uuid"
}
```

```json
{
  "kind": "EXCAVATION_PIT",
  "bottomElevation": 52,
  "workingMarginBlocks": 1,
  "slopePitchRatio": 1
}
```

求解器：`DesignSurfaceResolver`（从 `GradingSurfaceResolver` 演进），输入 Zone + `ExistingTerrain` 采样，输出 `GradingPlane` 或 `DesignVolume`（基坑体）。

**Phase G 重命名与扩展**（`DesignSurfaceKind` / `GradingSurfaceMode`，旧 ID 反序列化兼容）：

| 旧 kind | 新 kind | 说明 |
|---------|---------|------|
| `FLAT` | `LEVEL_PAD` | 水平整平 |
| `FIXED_SLOPE` | `SINGLE_SLOPE_PLANE` | 单向放坡平面 |
| `THREE_POINT` | `THREE_POINT_PLANE` | 三点定面 |
| `FIT_SLOPE` | `BEST_FIT_PLANE` | 最佳拟合平面 |
| — | `MATCH_EXISTING` | `DesignY = ExistingY + verticalOffset` |
| — | `MULTI_PLANE` | `facets[]` 多面片，最小包含面片优先 |
| — | `DRAINAGE_SURFACE` | 排水面（当前委托 `BEST_FIT_PLANE`） |

`MULTI_PLANE` 示例：

```json
{
  "kind": "MULTI_PLANE",
  "facets": [
    {
      "outerPoints": [ {"x":0,"y":0}, {"x":10,"y":0}, {"x":10,"y":10}, {"x":0,"y":10} ],
      "kind": "LEVEL_PAD",
      "manualTargetElevation": 68
    }
  ]
}
```

`MATCH_EXISTING` 示例：

```json
{
  "kind": "MATCH_EXISTING",
  "verticalOffset": 2
}
```

### 4.6 Breakline

场地内**不可随意跨区插值**的折线（挡土墙脚、道路红线、建筑退线等）。

```json
{
  "id": "bl-001",
  "name": "Building setback",
  "points": [ {"x": 10, "y": 0}, {"x": 10, "y": 40} ],
  "role": "HARD_BOUNDARY",
  "leftZoneId": "zone-pad",
  "rightZoneId": "zone-landscape"
}
```

| `role` | 行为 |
|--------|------|
| `HARD_BOUNDARY` | 折线两侧不得跨区混合；格点归属由法向侧 Zone 决定 |
| `ELEVATION_STEP` | 两侧可不同标高，但不沿折线做坡向插值（Phase D） |
| `NO_BLENDING` | 与 `HARD_BOUNDARY` 同义，保留别名 |

### 4.7 RetainingEdge

挡土/直立界（MVP：仅标记，合成时视为 `HARD_BOUNDARY` + 垂直立面约束）。

```json
{
  "id": "ret-001",
  "polyline": [ ... ],
  "topElevation": 72,
  "bottomElevation": 64,
  "side": "CUT"
}
```

Phase D 才生成挡土墙实体；MVP 仅影响 Design Terrain 在边缘的截止方式。

### 4.8 ExclusionZone（正式排除区）

Site 级**排除区**实体：不参与（或单独处理）挖填的子区域，优先级高于 `GradingZone`（`exclusionPrecedence: ABSOLUTE`）。

适用：建筑保留区、水池、已建构筑物、中庭、禁挖区等。几何使用 `RegionGeometry`（外环 + 孔洞），例如环形保留带 = 外环减去中心孔洞。

```json
{
  "id": "ex-001",
  "name": "Courtyard preserve",
  "outerRing": [ ... ],
  "holes": [ [ ... ] ],
  "mode": "PRESERVE_EXISTING"
}
```

| `mode` | 合成行为 |
|--------|---------|
| `PRESERVE_EXISTING` | `targetY = existingGroundY`，方量 = 0 |
| `NO_TOUCH` | 不写入 `placementRecords`，也不改 Design Terrain 显示 |

### 4.9 CompositionPolicy

Site 级合成策略（可配置，MVP 用默认值）。

```json
{
  "overlapResolution": "HIGHEST_PRIORITY_WINS",
  "balanceScope": "SITE_WIDE",
  "balanceMethod": "ZONE_ALLOCATION",
  "balanceResidualUniformPolish": true,
  "outsideSiteBoundary": "IGNORE",
  "exclusionPrecedence": "ABSOLUTE",
  "breaklinePrecedence": "ABSOLUTE",
  "blendWidthBlocks": 0
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `overlapResolution` | `HIGHEST_PRIORITY_WINS` | 多 Zone 覆盖同一格时的裁决；`LARGEST_ZONE_WINS` 为面积较小者优先 |
| `balanceScope` | `SITE_WIDE` | `PER_ZONE`：各分区在设计面解析阶段自平衡；`SITE_WIDE`：合成后全场统筹 |
| `balanceMethod` | `ZONE_ALLOCATION` | 仅 `SITE_WIDE` 生效：`ZONE_ALLOCATION` 按调配矩阵分区 ΔY；`UNIFORM_OFFSET` 全场统一 ΔY |
| `balanceResidualUniformPolish` | `true` | `ZONE_ALLOCATION` 后是否对残余挖填差再做一次全场统一抛光 |
| `exclusionPrecedence` | `ABSOLUTE` | 排除区永远优先 |
| `breaklinePrecedence` | `ABSOLUTE` | Breakline 侧归属优先于纯距离 |
| `blendWidthBlocks` | `0` | MVP 不混合；>0 时在交界做高程混合（Phase D） |

### 4.10 DesignTerrainGrid（运行时，不持久化）

```java
class DesignTerrainCell {
    int targetY;
    String zoneId;           // 来源 Zone，空表示未覆盖
    boolean excluded;
    int existingGroundY;
    DesignTerrainFlags flags;
}

class DesignTerrainGrid {
    Map<BlockColumnKey, DesignTerrainCell> cells;
    int minTargetY, maxTargetY;
}
```

### 4.11 EarthworkReport

`EarthworkVolumeReport` 的超集：

```json
{
  "siteId": "...",
  "existingTerrainRef": { ... },
  "resolvedAtEpochMs": 1725176500000,
  "totals": {
    "geometricCutVolume": 12430,
    "geometricFillVolume": 11820,
    "reusableCutVolume": 10940,
    "compactedFillDemand": 11820,
    "importVolume": 880,
    "exportVolume": 1490,
    "cutChangedBlocks": 9312,
    "fillChangedBlocks": 9312,
    "totalChangedBlocks": 18624
  },
  "byZone": {
    "zone-pad": { "geometricCutVolume": 8000, "..." : "..." }
  },
  "allocationMatrix": {
    "transfers": [
      { "sourceZoneId": "zone-cut", "destinationZoneId": "zone-fill", "volume": 6000 },
      { "sourceZoneId": "zone-cut", "destinationZoneId": "__EXPORT__", "volume": 1000 }
    ]
  },
  "balanceScope": "SITE_WIDE",
  "siteWideVerticalOffset": 0,
  "zoneVerticalOffsets": {
    "zone-cut": 10,
    "zone-fill": -12
  }
}
```

`EarthworkProjectReport`（Phase 12）在 `EarthworkVolumeReport` 基础上增加：

| 字段 | 说明 |
|------|------|
| `totalCut` / `totalFill` | 全场几何挖填合计 |
| `reusableCut` / `importRequired` / `exportRequired` | 材料调配后的可再利用挖方、缺方外借、余方外运 |
| `byZone` | 分区分项方量 |
| `overlaps` | 分区重叠冲突摘要（`ZoneOverlapAnalyzer`） |
| `allocationMatrix` | 贪心调配矩阵 A→B / 进出口（`EarthworkAllocationMatrix`） |
| `balanceScope` | 本次合成使用的平衡范围 |
| `siteWideVerticalOffset` | 全场统一竖向调整量（`UNIFORM_OFFSET` 或残余抛光） |
| `zoneVerticalOffsets` | 分区竖向调整量（`ZONE_ALLOCATION`） |

---

## 5. Design Terrain 合成规则

### 5.1 总览

```
输入:
  siteBoundary
  existingTerrain: TerrainSnapshot
  gradingZones[] (enabled, with resolved DesignSurface → plane/volume)
  exclusionZones[]
  breaklines[]
  compositionPolicy

输出:
  DesignTerrainGrid: ∀ (x,z) ∈ footprint(siteBoundary):
      targetY, zoneId, excluded
```

### 5.2 算法（MVP：无混合、无基坑体）

**Step 0 — 初始化**

对 `siteBoundary` 内每个 footprint 格点 `(x,z)`：

```
cell.existingGroundY = snapshot.groundY(x,z)
cell.targetY         = cell.existingGroundY   // 默认保持现状
cell.zoneId          = null
cell.excluded        = false
```

**Step 1 — 排除区（最高优先级）**

对每个 `ExclusionZone` 区域 `E`（`RegionGeometry`，含孔洞）：

```
if E.contains(x,z):
    if mode == PRESERVE_EXISTING:
        cell.targetY = cell.existingGroundY
        cell.excluded = true
        cell.zoneId = E.id
    if mode == NO_TOUCH:
        cell.excluded = true
        cell.zoneId = E.id
```

排除区之后格点不再参与 Zone 覆盖（`excluded == true` 跳过 Step 2）。

**Step 2 — Breakline 侧归属（可选，有 breakline 时）**

对每个未排除格点，若存在 `HARD_BOUNDARY` breakline：

```
side = classifySide(x,z, breakline)   // 左/右半平面
cell.zoneId = side == LEFT ? breakline.leftZoneId : breakline.rightZoneId
```

若无 breakline 或格点不在任何 breakline 影响带内，进入 Step 3。

> MVP 简化：无 breakline 时跳过本步。有 breakline 时，**先**确定候选 Zone 集合，再在 Step 3 只从候选 Zone 中取 design elevation。

**Step 3 — Zone 覆盖（按 priority 裁决）**

对每个未排除格点 `(x,z)`：

```
candidates = [ z ∈ gradingZones | z.enabled && z.geometry.contains(x,z) ]
if candidates empty:
    keep cell.targetY = existingGroundY
else:
    winner = max(candidates, by priority, then by area asc as tie-break)
    plane  = resolveDesignSurface(winner, existingTerrain)
    cell.targetY = plane.evaluateAt(x, z)
    cell.zoneId  = winner.id
```

**重叠规则** `HIGHEST_PRIORITY_WINS`：

- 取 `priority` 最大者；
- 若并列，取面积较小者（更具体的分区赢）；
- 若仍并列，取 `id` 字典序（确定性）。

**Step 4 — 全场土方平衡（Phase 12，可选）**

在 Zone 覆盖之后、交界混合之前，根据 `compositionPolicy.balanceScope`：

```
if balanceScope == PER_ZONE:
    // 各 Zone 已在 DesignSurfaceResolver 内自平衡，跳过
    pass
else if balanceScope == SITE_WIDE && zoneCount >= 2:
    if balanceMethod == ZONE_ALLOCATION:
        matrix = EarthworkAllocationMatrix.fromZoneReports(byZoneVolumes)
        ∀ zone: ΔY_zone = round(allocationIntent_zone / cellCount_zone)
        apply ΔY_zone to cells where cell.zoneId == zone.id
        if balanceResidualUniformPolish:
            δ = SiteWideBalanceAdjuster.findBalancedVerticalOffset(all cells)
            apply uniform δ
    else if balanceMethod == UNIFORM_OFFSET:
        δ = SiteWideBalanceAdjuster.findBalancedVerticalOffset(all cells)
        apply uniform δ to all participating cells
```

`SITE_WIDE` 时 `DesignSurfaceResolver` 传入 `deferBalanceToSite=true`：水平分区用平均地面代替逐区 `autoBalance`，拟合坡面跳过逐区截距平衡。

**Step 5 — 交界混合（Phase D）**

`TerrainBoundaryBlender` 在 `blendWidthBlocks > 0` 时，对 overlap 格点在 winner / runner-up 标高间插值。

**Step 6 — 分区边界放坡（Phase F）**

`ZoneBoundarySlopeApplicator` 按 `ZoneEdgeSettings` 处理挖填放坡、挡土边等。

**Step 7 — 裁剪到 Site Boundary**

格点已在 Step 0 限制在 `siteBoundary` 内；`outsideSiteBoundary: IGNORE` 不生成 cell。

**Step 8 — 输出校验**

```
∀ cell: cell.targetY ∈ [world.bottomY, world.topY]（或软警告）
统计: coveredCells, excludedCells, unchangedCells
```

### 5.3 挖填方量计算（接 EarthworkGenerator）

对每个 `DesignTerrainGrid` cell（`excluded == false` 且 `mode != NO_TOUCH`）：

```
ΔY = cell.targetY - cell.existingGroundY
ΔY > 0 → 填方几何量 += ΔY
ΔY < 0 → 挖方几何量 += |ΔY|
```

材料调配：Site 级 `materialModel`（`EarthMaterialProperties`），公式同 `EarthworkVolumeReport.fromMetrics()`。

**分区方量**：仅统计 `cell.zoneId == zone.id` 的格点贡献。

### 5.4 Phase C 扩展：基坑体（EXCAVATION_PIT）

对 `EXCAVATION_PIT` Zone，Design Surface 不是单一平面，而是：

```
坑底: z_bottom(x,z) = constant 或 局部平面
边坡: 从坑底向外 workingMargin + slopePitch 形成放坡面
```

合成时：

```
if point inside pit footprint:
    targetY = max(z_bottom, z_slopeSurface(x,z))   // 坑内
else if point in slope band:
    targetY = z_slopeSurface(x,z)
```

边坡带与相邻 `FLAT` Zone 的交界用 Breakline 或 priority 处理。

### 5.5 与道路的衔接（Phase D）

不复制 Road 纵断面求解器。两种集成方式（二选一或并存）：

1. **引用模式**：`ROAD_CORRIDOR` Zone 存 `roadEdgeRef`，运行时向 Road 插件查询 `sampleSurfaceY(planPoint)`。
2. **烘焙模式**：用户「从道路导入设计高程」→ 生成 `TERRAIN_FIT` Zone + 采样网格缓存。

Breakline 可沿道路红线导入。

### 5.6 与建筑的衔接（Phase C）

`BUILDING_PAD` Zone：

```json
{
  "type": "BUILDING_PAD",
  "buildingFootprintRef": "fp-uuid",
  "designSurface": {
    "kind": "CONSTANT_ELEVATION",
    "elevationSource": "BUILDING_BASE_ELEVATION"
  }
}
```

`elevationSource` 解析顺序：

1. 建筑 `manualBaseElevation`（若设置）
2. `BuildingFoundationUtils.computeBaseElevation(...)`
3. Site 默认标高

`EXCAVATION_PIT` 可引用同一 `buildingFootprintRef` 自动生成坑底（基础底 - 埋深）。

---

## 6. JSON Schema

### 6.1 文件顶层

```json
{
  "schemaVersion": 3,
  "sites": [ { ... EarthworkSite ... } ],
  "activeSiteId": "site-uuid"
}
```

| 字段 | 说明 |
|------|------|
| `schemaVersion` | `1` = 仅 `regions[]`；`2` = `sites[]`；`3` = 当前完整模型（见 §7.3） |
| `sites` | 场地列表 |
| `activeSiteId` | UI 当前编辑的 Site |
| `regions` | **v1 遗留**；加载后迁移并丢弃，保存时不写出 |

### 6.2 EarthworkSite 完整示例

```json
{
  "schemaVersion": 3,
  "sites": [
    {
      "id": "site-main",
      "name": "Main Building Pad",
      "siteBoundary": [
        { "x": 0, "y": 0 },
        { "x": 120, "y": 0 },
        { "x": 120, "y": 80 },
        { "x": 0, "y": 80 }
      ],
      "materialModel": {
        "reusableRatio": 0.90,
        "cutToCompactedFillRatio": 0.92
      },
      "existingTerrainRef": {
        "capturedAtEpochMs": 1725176465000,
        "worldKey": "minecraft:overworld",
        "outlineFingerprint": 3847291047281,
        "contentFingerprint": 9182736451029,
        "columnCount": 9600,
        "snapshotFile": "snapshots/site-main-20260902.json"
      },
      "compositionPolicy": {
        "overlapResolution": "HIGHEST_PRIORITY_WINS",
        "outsideSiteBoundary": "IGNORE",
        "exclusionPrecedence": "ABSOLUTE",
        "breaklinePrecedence": "ABSOLUTE",
        "blendWidthBlocks": 0
      },
      "gradingZones": [
        {
          "id": "zone-pad",
          "name": "Building Pad",
          "type": "FLAT",
          "priority": 100,
          "enabled": true,
          "outerPoints": [
            { "x": 20, "y": 20 },
            { "x": 60, "y": 20 },
            { "x": 60, "y": 50 },
            { "x": 20, "y": 50 }
          ],
          "previewGridSize": 5,
          "cutExposeMaterial": "",
          "fillMaterial": "minecraft:dirt",
          "materialOverride": null,
          "designSurface": {
            "kind": "FLAT",
            "autoBalance": true,
            "manualTargetElevation": null
          }
        },
        {
          "id": "zone-yard",
          "name": "Yard Grading",
          "type": "SLOPED",
          "priority": 50,
          "enabled": true,
          "outerPoints": [
            { "x": 0, "y": 0 },
            { "x": 120, "y": 0 },
            { "x": 120, "y": 80 },
            { "x": 0, "y": 80 }
          ],
          "previewGridSize": 5,
          "cutExposeMaterial": "",
          "fillMaterial": "minecraft:dirt",
          "designSurface": {
            "kind": "FIXED_SLOPE",
            "slopeDirectionDegrees": 90.0,
            "slopePitchRatio": 4,
            "anchorCanvas": { "x": 0, "y": 0 },
            "anchorElevation": 68
          }
        }
      ],
      "breaklines": [
        {
          "id": "bl-pad-edge",
          "name": "Pad edge",
          "points": [
            { "x": 20, "y": 20 },
            { "x": 60, "y": 20 },
            { "x": 60, "y": 50 },
            { "x": 20, "y": 50 },
            { "x": 20, "y": 20 }
          ],
          "role": "HARD_BOUNDARY",
          "leftZoneId": "zone-pad",
          "rightZoneId": "zone-yard"
        }
      ],
      "retainingEdges": [],
      "exclusionZones": [
        {
          "id": "ex-tree-island",
          "name": "Tree island",
          "outerPoints": [
            { "x": 90, "y": 60 },
            { "x": 100, "y": 60 },
            { "x": 100, "y": 70 },
            { "x": 90, "y": 70 }
          ],
          "mode": "PRESERVE_EXISTING"
        }
      ]
    }
  ],
  "activeSiteId": "site-main"
}
```

### 6.3 GradingZone.type 枚举

```
FLAT | SLOPED | BUILDING_PAD | EXCAVATION_PIT | TERRAIN_FIT | ROAD_CORRIDOR | LANDSCAPE
```

### 6.4 DesignSurface.kind 枚举

```
LEVEL_PAD | SINGLE_SLOPE_PLANE | THREE_POINT_PLANE | BEST_FIT_PLANE
MATCH_EXISTING | MULTI_PLANE | DRAINAGE_SURFACE          (Phase G)
CONSTANT_ELEVATION | EXCAVATION_PIT                    (Phase C+)
```

旧 JSON 值 `FLAT` / `FIXED_SLOPE` / `THREE_POINT` / `FIT_SLOPE` 反序列化时自动映射为新枚举。

### 6.5 侧车文件：`snapshots/*.json`

```json
{
  "schemaVersion": 1,
  "siteId": "site-main",
  "capturedAtEpochMs": 1725176465000,
  "worldKey": "minecraft:overworld",
  "outlineFingerprint": 3847291047281,
  "contentFingerprint": 9182736451029,
  "columns": [
    {
      "worldX": 10,
      "worldZ": 20,
      "groundY": 68,
      "surfaceBlockId": "minecraft:grass_block",
      "chunkLoaded": true
    }
  ]
}
```

---

## 7. Schema 版本迁移

实现：`EarthworkProjectSchema`（版本常量）+ `EarthworkProjectMigrator`（`normalizeJson` / `load` 链式升级）。

加载任意历史 JSON 时：

```
detect version → while (version < CURRENT) migrate(version → version+1) → parse
```

高于 `CURRENT` 的版本直接拒绝（避免静默丢字段）。

### 7.1 v1 形状（legacy `regions[]`）

```json
{
  "regions": [
    {
      "id": "...",
      "name": "...",
      "outerPoints": [ ... ],
      "surfaceMode": "FLAT",
      "autoBalance": true,
      ...
    }
  ]
}
```

（无 `schemaVersion` 且无 `sites[]` 时视为 `1`；有 `sites[]` 无版本号时视为 `2`。）

### 7.2 v1 → v2

```
site = new EarthworkSite()
site.id = UUID()
site.name = projectName 或 "Imported Site"
site.siteBoundary = convexHull(union(all region.outerPoints)) 或 最大 region 的 outerPoints
site.materialModel = first(region.material) 或 DEFAULT

for each region in regions:
    zone = new GradingZone()
    zone.id = region.id
    zone.name = region.name
    zone.type = mapSurfaceMode(region.surfaceMode):
        FLAT, FIT_SLOPE → FLAT
        FIXED_SLOPE, THREE_POINT → SLOPED
    zone.outerPoints = region.outerPoints
    zone.priority = 50  // 统一默认
    zone.designSurface = map from region fields

site.gradingZones = zones
project.sites = [site]
project.activeSiteId = site.id
// 不删除 regions[] 直到用户显式保存 v2
```

**注意**：v1 多 Region 互不相交时，`siteBoundary` 取并集包围盒矩形或凸包；UI 提示用户修正红线。

### 7.3 v2 → v3

v3 为**当前写入版本**，在 v2 基础上规范化：

| 步骤 | 动作 |
|------|------|
| 清理 | 移除顶层 `regions[]` |
| 几何 | `outerPoints` → 补齐 `outerRing`；`holes` 保持 |
| 分区 | 确保 `designSurface`、`edgeSettings`、`materialModel` 对象存在 |
| 场地 | 确保 `compositionPolicy`、`materialModel` 对象存在 |
| 元数据 | `existingTerrainRef` 侧车引用、`compositionPolicy.balanceMethod` 等已在 v2 DTO 中，v3 起视为稳定字段 |

**v3 涵盖的持久化能力**（后续增量字段应触发 v4+）：

- 孔洞几何（`outerRing` / `holes`）
- 材料换算（`materialModel` / `materialOverride`）
- 边界处理（`edgeSettings`）
- 坡度 / 设计面（`designSurface`）
- 现状快照元数据（`existingTerrainRef`）
- 全场平衡策略（`compositionPolicy`）

### 7.4 保存策略

- 新保存始终写 `schemaVersion: 3`，不写出 `regions[]`。
- 加载 v1/v2 后内存模型为 v3；用户下次保存即完成持久化升级。
- 保留 v1 只读导入至少两个大版本周期。

---

## 8. 管线集成

> **2.0 目标架构**（包结构、Manager 分层、迁移步骤）见 [Earthwork_2.0_架构.md](./Earthwork_2.0_架构.md)。

### 8.1 新管线类职责

| 类 | 职责 |
|----|------|
| `TerrainSnapshotCapture` | Step 1（已有） |
| `DesignSurfaceResolver` | 每 Zone 解析 `GradingPlane`（自 `GradingSurfaceResolver`） |
| `DesignTerrainComposer` | 第 5 节合成算法 |
| `SiteWideBalanceAdjuster` | 全场统一 ΔY 平衡（Phase 12b） |
| `ZoneAllocationBalanceAdjuster` | 按调配矩阵分区 ΔY + 可选残余抛光（Phase 12c） |
| `EarthworkAllocationMatrix` | 分区→分区 / 进出口贪心调配（Phase 12） |
| `ZoneOverlapAnalyzer` | 重叠检测与 priority 裁决（Phase 12） |
| `EarthworkProjectReport` | 项目级平衡报告与调配摘要（Phase 12） |
| `EarthworkVolumeCalculator` | 几何方量 + `EarthworkVolumeReport` |
| `EarthworkVoxelGenerator` | `BlockRecord`（自 `EarthworkGenerator` Step 4） |
| `SiteEarthworkPipeline` | 编排上述步骤 |

### 8.2 与现有 EarthworkGenerator 的关系

**MVP（单 Zone Site）**：`SiteEarthworkPipeline` 检测到 `gradingZones.size() == 1` 且类型为 `FLAT/SLOPED` 时，**直接委托**现有 `EarthworkGenerator.generate()`，零行为变化。

**多 Zone**：走 `DesignTerrainComposer` → 新 voxel 生成（按 cell.targetY 挖填）。

### 8.3 预览 / 落地 / 过期检测

保持 P0-6 语义：

1. `captureFresh()` → 更新 `existingTerrainRef` + 侧车文件。
2. 合成 + 算量 → `lastReport`。
3. 落地前 `compareWithCurrentWorld()` → 弹窗「重新计算？」。

---

## 9. UI 演进（概要）

| 现 Tab | 演进 |
|--------|------|
| 概览 | Site 列表 + Zone 树状结构 |
| 认领 | 认领进当前 Site（不再裸建 Region） |
| 编辑 | Site 红线 / Zone 边界 / Breakline / Exclusion |
| 生成 | 显示 `existingTerrainRef` 时间 + 全场合计 + 分 Zone 方量 + 调配矩阵 + 分区 ΔY |

单 Zone 时 UI 与现版几乎相同，降低迁移成本。

**Phase 13 UI**：编辑 Tab 可为当前分区「从选区添加孔洞」；场地合成区可管理 `ExclusionZone`（从选区添加、模式、孔洞）。画布叠加显示孔洞与排除区轮廓。

---

## 10. 实施阶段

| 阶段 | 交付 | 风险 |
|------|------|------|
| **A** | `EarthworkSite` 模型 + v1→v2 迁移 + 单 Zone 委托旧 Generator | ✅ 已完成 |
| **B** | `DesignTerrainComposer` + 多 Zone priority 合成 + 分 Zone 方量 | ✅ 已完成 |
| **C** | `BUILDING_PAD` / `EXCAVATION_PIT` + 建筑引用 | ✅ 已完成 |
| **D** | Breakline 侧归属、交界混合、`TERRAIN_FIT` 分区 | ✅ 已完成 |
| **D+** | `ROAD_CORRIDOR` 道路引用、挡土界混合抑制、折线/挡土界选区导入 | ✅ 已完成 |

| **E** | 道路标高烘焙、`BakedElevationGrid`、挡土墙实体生成 | ✅ 已完成 |
| **E+** | 道路走廊轮廓导入、中心线折线、挡土墙分区材质联动 | ✅ 已完成 |
| **F** | 分区边界处理（`EdgeTreatment`、挖填放坡、逐边覆盖） | ✅ 已完成 |
| **F+** | `RETAINING_WALL` 边界联动、虚拟挡土边、按格墙高、同步 UI | ✅ 已完成 |
| **G** | 设计面分类重命名、`MATCH_EXISTING` / `MULTI_PLANE` / `DRAINAGE_SURFACE`、体素离散统一 | ✅ 已完成 |
| **12** | 分区重叠检测、`EarthworkProjectReport`、调配矩阵 A→B / 进出口 | ✅ 已完成 |
| **12b** | `SITE_WIDE` 合成阶段全场统一 ΔY（`SiteWideBalanceAdjuster`） | ✅ 已完成 |
| **12c** | 按调配矩阵分区 ΔY（`ZoneAllocationBalanceAdjuster`）+ 残余抛光 + UI/报告 | ✅ 已完成 |
| **13** | `RegionGeometry`（`outerRing` + `holes`）、孔洞感知合成/面积/JSON、`ExclusionZone` 正式几何 | ✅ 已完成 |
| **13b** | 孔洞/排除区 UI（选区添加孔洞、排除区管理、画布轮廓叠加） | ✅ 已完成 |
| **14** | 解析解基准（8 场景）+ 管线 E2E（3 场景 apply/undo）；见 §10.1 | ✅ 已完成 |
| **15** | `EarthworkGenerateCommand` 取消后仅撤销 `appliedRecords` | ✅ 已完成 |
| **16** | `schemaVersion` 正式化 + `EarthworkProjectMigrator` v1→v2→v3 链 | ✅ 已完成 |
| **17** | Earthwork 2.0 架构：管线拆分 + Manager 层 + Plugin 瘦身；见 [Earthwork_2.0_架构.md](./Earthwork_2.0_架构.md) | 🚧 17a–17b 已完成 |

### 10.1 Phase 14 测试矩阵

**目标**：用可手算期望覆盖 Solve → 方量 → `BlockRecord` → Apply → Undo 全链路，不依赖真实 `World`。

**测试夹具**（`EarthworkTestFixtures`）：

| 夹具 | 用途 |
|------|------|
| `rectangleOutline` / `rectangleTerrain` | 规则矩形区域与现状快照 |
| `levelPadRegion` | `LEVEL_PAD` + `previewGridSize=1` |
| `solidColumnSampler` | y=1..groundY 实心柱，供无 World 挖填检测 |
| `generateLegacy` | 单 Region 委托 `EarthworkGenerator.generate(region, null, terrain)` |
| `donutZone` / `twoZoneSiteForCompose` | 带孔洞多 Zone 合成场景 |

**可测试性改动**（生产代码，仅支撑测试）：

- `EarthworkGenerator.BlockSampler` + `withBlockSampler()`：无 World 采样方块 ID
- `generate()` / `generateSite()`：提供 `TerrainSnapshot` 时允许 `world == null`
- `EarthworkGenerateCommand.BlockWriter` 公开；测试构造 `(records, BlockWriter)`

#### 10.1.1 解析解基准（`EarthworkAnalyticalBenchmarkTest`，8 项）

| # | 场景 | 输入要点 | 期望（几何方量 / 行为） |
|---|------|----------|-------------------------|
| 1 | 平地同标高 | 4×4 @ Y=64，目标 64 | Cut=0，Fill=0，无 `placementRecords` |
| 2 | 平地抬高 1 格 | 4×4 @ 64，目标 65 | Cut=0，Fill=16（=面积），16 条落地方块 |
| 3 | 平地降低 1 格 | 4×4 @ 64，目标 63 | Cut=16，Fill=0，16 条落地方块 |
| 4 | 对称坡面自动平衡 | 单行 4 格地面 62/64/66/68，`autoBalance=true` | 平衡标高 65；Cut=4，Fill=4 |
| 5 | 固定坡度离散台阶 | pitch 4 沿 +X，锚点 (0,0)@64，现状单行 @64 | `elevationMin`=64，`elevationMax`=66；Fill=10（x=2..5 各 1，x=6..8 各 2），Cut=0 |
| 6 | 三点定面 | 控制点 (0,0,60)/(10,0,64)/(0,10,62) | 平面过三点；`hasGeometricVolume()` |
| 7 | 孔洞分区 | 甜甜圈 Zone + 伴生 Zone，`generateSite` | 孔洞格 (3..6)² 无落地方块；`DesignTerrainGrid` 孔内 targetY=现状、无 zoneId |
| 8 | 重叠分区优先级 | 大 Zone@60 + 小 Zone@70，`priority` 100>50 | 重叠区 (5,5)→70/`high`；外围 (1,1)→60/`low`；`ZoneOverlapAnalyzer` 裁决一致 |

#### 10.1.2 管线 E2E（`EarthworkPipelineE2ETest`，3 项）

`InMemoryBlockWorld` 实现 `BlockWriter`，快照 apply 前后方块 ID，`EarthworkGenerateCommand.undo()` 须逐格还原。

| # | 场景 | 管线 | 断言要点 |
|---|------|------|----------|
| 1 | 单 Region 填方 | Snapshot → `generate` → `execute` → `undo` | Fill=16；undo 后每格与 apply 前一致 |
| 2 | 多 Zone 孔洞场地 | `generateSite`（donut + companion）→ apply/undo | `siteGeneration`；孔洞内无 placement；undo 全量还原 |
| 3 | 混合挖填 | 4×4 棋盘地面 ±1，目标 64 | Cut=8，Fill=8，16 placement；undo 全量还原 |

**运行**：`.\gradlew.bat test --tests "com.plot.plugin.earthwork.*"`

**Phase 17c 已完成**：`RegionSurfaceEvaluator` / `DesignTerrainBuilder` / `DefaultSiteEarthworkOperations` / `LegacyRegionPipeline`。详见 [Earthwork_2.0_架构.md](./Earthwork_2.0_架构.md) §7。

**Phase 17d 已完成**：`EarthworkPreviewManager` / `EarthworkBuildManager`；Plugin 预览与构建路径已委托 Manager。

**Phase 17e 已完成**：`EarthworkUIManager` + `EarthworkUiContext`；Plugin 降至 ~260 行。

**建议下一步（Phase 17f）**：`EarthworkGenerator` 标记 `@Deprecated`，外部改 `SiteEarthworkPipeline`。

---

## 11. 开放问题

1. **一个 Project 多个 Site？** 建议 MVP 限制为 1 个，JSON 仍用数组。
2. **Zone 允许重叠吗？** 允许，靠 `priority`（或 `LARGEST_ZONE_WINS`）裁决；UI 在概览/编辑 Tab 显示重叠警告与 `EarthworkProjectReport.overlaps`。
3. **侧车 snapshot 是否强制？** 建议预览成功后写入；无侧车时禁止离线查看断面，但允许重新捕获。
4. **竖向基准 ±0.000 如何定义？** 建议与建筑 `baseElevation` 共用整数 Y，文档化「工程标高 = 方块 Y」。
5. **基坑放坡与道路边坡边界？** 土方放坡改变 Design Terrain；道路边坡改变路面结构 — 两者在 `ROAD_CORRIDOR` 交界用 Breakline 分开。
6. **全场平衡算法选型？** 已实现 `UNIFORM_OFFSET` 与 `ZONE_ALLOCATION`；更复杂的分区平面优化（非整格 ΔY）留作后续。
7. **`MULTI_PLANE` 编辑体验？** 当前 JSON 可配置分面，画布分面绘制 UI 未实现。
8. **孔洞边放坡？** 当前 `edgeIndex` 仅外环；孔洞边界默认垂直截止，复杂内边界放坡留作后续。

---

## 12. 参考

- 现有任务书：`docs/development/task-assignments/EarthworkPlugin_开发任务书.md`
- 代码入口：`EarthworkProject`、`GradingRegion`、`TerrainSnapshot`、`EarthworkGenerator`
- 合成与平衡：`DesignTerrainComposer`、`DesignSurfaceResolver`、`SiteWideBalanceAdjuster`、`ZoneAllocationBalanceAdjuster`、`EarthworkAllocationMatrix`、`EarthworkProjectReport`
- 区域几何：`RegionGeometry`、`PolygonRegionUtils`（孔洞感知 `contains` / 面积 / 格点采样）
- 体素离散：`com.plot.core.geometry.VoxelElevationDiscretizer`（道路 `VoxelGradeDiscretizer` 委托）
- 建筑对接：`BuildingFootprint`、`BuildingFoundationUtils.computeBaseElevation`
- 道路对接：`TerrainSampler.sampleSurfaceY`、`RoadGenerator`
- 集成测试：`DesignTerrainComposerTest`（重叠、排除区、孔洞分区、全场平衡端到端）、`PolygonRegionUtilsTest`、`EarthworkProjectTest`（`holes` JSON 往返）
- Phase 14 基准：`EarthworkAnalyticalBenchmarkTest`、`EarthworkPipelineE2ETest`、`EarthworkTestFixtures`
- 持久化迁移：`EarthworkProjectSchema`、`EarthworkProjectMigrator`、`EarthworkProjectMigratorTest`
- 2.0 架构：[Earthwork_2.0_架构.md](./Earthwork_2.0_架构.md)

---

*文档维护：土方插件领域演进；Phase G / 12 / 13 / 14 / 16 实施时同步更新本节与 JSON 示例。*
