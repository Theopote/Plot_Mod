# EarthworkSite 领域设计

> 土方平衡插件从「整平工具」演进为「建筑场地土方」的领域模型、JSON 持久化与 Design Terrain 合成规则。
>
> **状态**：Phase D 已实施（Breakline 侧归属 + 交界混合 + 地形拟合分区）  
> **版本**：`schemaVersion: 2`  
> **关联代码基线**：P0-1～P0-6 已完成（`TerrainSnapshot`、`EarthMaterialProperties`、`EarthworkVolumeReport`、`EngineeringTerrainService`）

---

## 1. 问题陈述

### 1.1 当前模型

`GradingRegion` 表达的是：

```
二维多边形 outerPoints
    + 单一设计曲面（GradingPlane）
    → 整块区域统一挖填到该曲面
```

对应代码：`GradingSurfaceResolver` + `EarthworkGenerator` 四步管线（Capture → Solve → Compute → Voxel）。

### 1.2 真实建筑场地的语义缺口

| 场地要素 | 工程含义 | 当前支持 |
|---------|---------|---------|
| Site Boundary | 施工红线 / 场地范围 | `outerPoints`（每 Region 各一份，无统一 Site） |
| Building Pad | 室外地坪 ±0.000 | ❌ |
| Basement Excavation | 基坑底 + 工作面 + 放坡 | ❌ |
| Road / Plaza / Landscape | 分区不同设计标高 | ❌（道路在 Road 插件） |
| Retaining Wall | 挡土 / 垂直界 | ❌ |
| Exclusion Zone | 不动土区 | ❌ |
| **Design Terrain** | 多分区合成后的最终设计地形 | ❌（仅单平面） |
| Existing Terrain | 可审计的现状基准 | ✅ `TerrainSnapshot`（未入持久化模型） |

**结论**：插件算量可以正确，但**产品抽象仍是整平**；要从玩具升级为建筑土方，核心是把 `GradingRegion` 提升为 `EarthworkSite` + 多 `GradingZone` + **Design Terrain 合成器**。

---

## 2. 设计目标与非目标

### 2.1 目标

1. **一个工程一个场地**（`EarthworkSite`），统一 Site Boundary 与现状地形基准。
2. **多分区设计面**（`GradingZone`），各 Zone 独立定义设计曲面/设计体。
3. **可复现的 Design Terrain 合成**，输出逐格 `targetY[x,z]` 及来源 Zone。
4. **JSON 可版本迁移**：`schemaVersion: 1`（现有 `regions[]`）→ `schemaVersion: 2`（`site`）。
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

### 4.3 GradingZone（抽象）

所有设计分区共享：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | UUID |
| `name` | string | 显示名 |
| `type` | enum | 见 4.4 |
| `outerPoints` | Vec2d[] | 分区平面范围 |
| `priority` | int | 合成优先级，越大越优先 |
| `enabled` | bool | 是否参与合成 |
| `materialOverride` | MaterialModel? | 空则用 Site 默认 |
| `cutExposeMaterial` | string | 挖方裸露地表 |
| `fillMaterial` | string | 填方材质 |
| `designSurface` | DesignSurface | 设计面定义 |

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

### 4.8 ExclusionZone

不参与挖填的分区（保留现状或保护构筑物）。

```json
{
  "id": "ex-001",
  "name": "Protected trees",
  "outerPoints": [ ... ],
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
  "outsideSiteBoundary": "IGNORE",
  "exclusionPrecedence": "ABSOLUTE",
  "breaklinePrecedence": "ABSOLUTE",
  "blendWidthBlocks": 0
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `overlapResolution` | `HIGHEST_PRIORITY_WINS` | 多 Zone 覆盖同一格时的裁决 |
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
  }
}
```

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

对每个 `ExclusionZone` 多边形 `E`：

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
candidates = [ z ∈ gradingZones | z.enabled && z.outerPoints.contains(x,z) ]
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

**Step 4 — 裁剪到 Site Boundary**

格点已在 Step 0 限制在 `siteBoundary` 内；`outsideSiteBoundary: IGNORE` 不生成 cell。

**Step 5 — 输出校验**

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
  "schemaVersion": 2,
  "sites": [ { ... EarthworkSite ... } ],
  "activeSiteId": "site-uuid",
  "regions": []
}
```

| 字段 | 说明 |
|------|------|
| `schemaVersion` | `1` = 仅 `regions[]`（当前）；`2` = `sites[]` |
| `sites` | 场地列表 |
| `activeSiteId` | UI 当前编辑的 Site |
| `regions` | **只写不读**（迁移后清空）或只读兼容 v1 导入 |

### 6.2 EarthworkSite 完整示例

```json
{
  "schemaVersion": 2,
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
FLAT | FIXED_SLOPE | THREE_POINT | FIT_SLOPE
CONSTANT_ELEVATION | EXCAVATION_PIT    (Phase C+)
```

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

## 7. 从 schemaVersion 1 迁移

### 7.1 当前 v1 形状

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

（无 `schemaVersion` 字段时视为 `1`。）

### 7.2 自动迁移规则

加载 v1 时：

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

### 7.3 保存策略

- 首次以 v2 保存：写 `schemaVersion: 2`，`regions: []`。
- 保留 v1 只读导入至少两个大版本周期。

---

## 8. 管线集成

### 8.1 新管线类职责

| 类 | 职责 |
|----|------|
| `TerrainSnapshotCapture` | Step 1（已有） |
| `DesignSurfaceResolver` | 每 Zone 解析 `GradingPlane`（自 `GradingSurfaceResolver`） |
| `DesignTerrainComposer` | 第 5 节合成算法 |
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
| 生成 | 显示 `existingTerrainRef` 时间 + 全场合计 + 分 Zone 方量 |

单 Zone 时 UI 与现版几乎相同，降低迁移成本。

---

## 10. 实施阶段

| 阶段 | 交付 | 风险 |
|------|------|------|
| **A** | `EarthworkSite` 模型 + v1→v2 迁移 + 单 Zone 委托旧 Generator | ✅ 已完成 |
| **B** | `DesignTerrainComposer` + 多 Zone priority 合成 + 分 Zone 方量 | ✅ 已完成 |
| **C** | `BUILDING_PAD` / `EXCAVATION_PIT` + 建筑引用 | ✅ 已完成 |
| **D** | Breakline 侧归属、交界混合、`TERRAIN_FIT` 分区 | ✅ 已完成 |
| **D+** | `ROAD_CORRIDOR` 道路引用、`LANDSCAPE` UI、`RetainingEdge` 实体 | 待定 |

**建议下一步（Phase D+）**：道路走廊纵断面采样、`ROAD_CORRIDOR` 与 Breakline 联动。

---

## 11. 开放问题

1. **一个 Project 多个 Site？** 建议 MVP 限制为 1 个，JSON 仍用数组。
2. **Zone 允许重叠吗？** 允许，靠 `priority` 裁决；UI 需显示重叠警告。
3. **侧车 snapshot 是否强制？** 建议预览成功后写入；无侧车时禁止离线查看断面，但允许重新捕获。
4. **竖向基准 ±0.000 如何定义？** 建议与建筑 `baseElevation` 共用整数 Y，文档化「工程标高 = 方块 Y」。
5. **基坑放坡与道路边坡边界？** 土方放坡改变 Design Terrain；道路边坡改变路面结构 — 两者在 `ROAD_CORRIDOR` 交界用 Breakline 分开。

---

## 12. 参考

- 现有任务书：`docs/development/task-assignments/EarthworkPlugin_开发任务书.md`
- 代码入口：`EarthworkProject`、`GradingRegion`、`TerrainSnapshot`、`EarthworkGenerator`
- 建筑对接：`BuildingFootprint`、`BuildingFoundationUtils.computeBaseElevation`
- 道路对接：`TerrainSampler.sampleSurfaceY`、`RoadGenerator`

---

*文档维护：土方插件领域演进；实施 Phase A 时同步更新本节与 JSON 示例。*
