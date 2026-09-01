# ADR 0007: 道路三层几何模型 — Design / Derived / Topology

## Status

Accepted — 2026-08-31

## Context

Phase 2.2 引入 `RoadHorizontalAlignment`（平面线形）后，道路模块出现典型的 **双几何真相**：

| 来源 | 角色（过渡前） |
|------|----------------|
| `RoadHorizontalAlignment` | UI 展示、持久化、工程校验；**未驱动生成** |
| `RoadEdge.centerlinePoints` | 生成、profile、横断面、预览、持久化 |

用户编辑 HA（Tangent / Spiral / R=100）与编辑折线中心线（Insert PI / Fillet）可各自变更，彼此 **不保证同步**。生成管线长期直接读取 `edge.getCenterlinePoints()`，导致 Minecraft 落地与编辑 Tab 所示设计线形可能不一致。

[ADR 0006](0006-road-phase-2-direction.md) 将 HA 定位为「逐步替换 centerline 的语义层」，但未明确 **谁是权威来源**、三层之间如何同步。Phase 2.2b 实现已落地一套可运行方案，需要正式写入 ADR 以免后续 PR 再次引入平级双轨几何。

### 已拒绝的方案

**方案 B — RoadEdge 折线为唯一权威**：HA 仅作 metadata / 分析。Spiral、圆曲线、PI 无法成为真实设计约束，不符合 Phase 2 专业道路目标。

## Decision

### 1. 采用三层模型（自上而下）

```
Road
├── Design Layer（设计真相）
│     ├── RoadHorizontalAlignment   — 平面线形（Tangent / Arc / Spiral）
│     └── RoadVerticalAlignment     — 纵断面（PVI / 竖曲线）
│
├── Derived Layer（派生几何缓存）
│     └── RoadEdge.centerlinePoints — 由设计采样写回的折线；持久化 / 示意图 / 无 HA 时 fallback
│
└── Topology Layer（拓扑图）
      ├── RoadNode
      └── RoadEdge            — 分段 ID、起止节点、roadId、横断面/坡度 override；不单独定义设计线形
```

**层级关系（单向依赖）**：

```
Design Alignment
      │  sample / materialize
      ▼
Derived Centerline (per OrientedRoadSegment)
      │  split by edge
      ▼
Topology (RoadNode / RoadEdge)
      │  generation / profile / station queries
      ▼
Road Generation Pipeline
```

三层 **不得平级互当权威**。有设计线形时，平面与纵断面查询、生成采样以 Design 为准；`centerlinePoints` 是缓存，不是第二条设计真相。

### 2. 权威来源规则

| 场景 | 平面权威 | 纵断面权威 |
|------|----------|------------|
| 有 HA 且 `RoadStationing.isStationable` | `RoadHorizontalAlignment` | `RoadVerticalAlignment`（若已定义） |
| 无 HA / 不可桩号化 | `RoadEdge.centerlinePoints` | 地形 / 坡度 solver |

枚举：`RoadGeometryAuthority` — `DESIGN_HORIZONTAL_ALIGNMENT` vs `INSTANCE_CENTERLINE`。

**统一查询入口**：`RoadPlanGeometry`（禁止生成与沿程模块直接 `edge.getCenterlinePoints()` 作平面设计查询）。

| API | 用途 |
|-----|------|
| `RoadPlanGeometry.pointAtStation` | 桩号处平面坐标（设计优先） |
| `RoadPlanGeometry.instancePointAtStation` | 实例折线坐标（一致性校验、双轨检测） |
| `RoadPlanGeometry.resolveEdgeCenterline` | 单边生成用折线采样 |
| `RoadPlanGeometry.designLength` | 设计平面线形总长（无 HA 时为 0） |
| `RoadPlanGeometry.instanceLength` | 实例折线链长（`RoadEdge` 派生几何累计） |
| `RoadPlanGeometry.canonicalLength` | 工程桩号域权威链长（有有效 HA → design，否则 → instance） |
| `RoadStationing.*` | 桩号拓扑与格式化；平面坐标查询 **委托** `RoadPlanGeometry` |

### 3. 同步策略（消除双轨的操作路径）

| 方向 | 触发 | 实现 | 写入 undo |
|------|------|------|-----------|
| Design → Derived | 生成 / 预览前 | `DerivedCenterlineSynchronizer`（auto-materialize） | 否 |
| Design → Derived | 用户手动 | `HorizontalAlignmentCenterlineMaterializer` + UI「同步到中心线」 | 是 |
| Derived → Design | centerline 编辑后 | `CenterlineHorizontalAlignmentSync`（refit 或 invalidate HA） | 随 centerline 编辑 |
| 双轨检测 | 工程检查 / 编辑 Tab | `HorizontalAlignmentCenterlineConsistency`（instance vs design） | — |

**原则**：

- **不允许静默双写**：编辑一侧必须显式同步或失效另一侧。
- centerline 编辑后：若道路原有 HA → `HorizontalAlignmentPolylineFitter` refit；失败则 **清除 HA**（退回 polyline 模式）。
- 生成前 auto-materialize **不** push history（派生缓存刷新）；用户主动 materialize **要** push history。
- auto-materialize 期间 **不** `notifyNetworkChanged()`，避免刚计算的预览被 `invalidatePreview` 清掉。

### 4. 桩号（Chainage）坐标系

- **Canonical 桩号域**：`RoadStationing.canonicalLength` — 工程桩号权威上界；有有效 HA 时等于 `designLength`，否则等于 `instanceLength`。`RoadStation`、VA、VCS、设施、`isValid`、`resolve`、`stationAt`、节点桩号均在此域。
- **Design length**：`RoadStationing.designLength` / `RoadPlanGeometry.designLength` — HA 线形总长（无 HA 时为 0）。
- **Instance 桩号域**：`RoadStationing.instanceLength` / `orientedSegments` 分段弧长累计（与 `OrientedRoadSegment` 一致）；内部几何换算基于此域，对外 API 在边界按比例换算。
- **设计桩号查询**：`RoadPlanGeometry.pointAtStation(chainage)` 在有 HA 时读 `HorizontalAlignmentGeometry.poseAt`。
- 沿程模块（纵断面、可变横断面、设施、标线）换算桩号 **只经 `RoadStationing`**，禁止自行 `segmentStart + localDistance`。

### 5. 代码审查约束

新增或修改道路几何消费时：

1. 平面点 / 切向 / 采样 → `RoadPlanGeometry` 或 `RoadStationing`（已委托）。
2. 生成管线 → `RoadPlanGeometry.resolveEdgeCenterline`（`RoadEdgeBuildOrchestrator` 已切换）。
3. 比较设计 vs 实例 → `instancePointAtStation` / `HorizontalAlignmentCenterlineConsistency`，**不得**用 `pointAtStation` 自检。
4. 不得在无 HA 道路上静默创建 HA；不得在有 HA 道路上只改 `centerlinePoints` 而不触发 `CenterlineHorizontalAlignmentSync`。

## Consequences

### 正面

- Spiral / R / PI 等设计参数可驱动真实生成（有 HA 时）。
- 预览、持久化、生成共用同一套设计几何；`centerlinePoints` 为可重建缓存。
- 双轨问题可检测（consistency）、可修复（materialize）、可单向回流（centerline refit / invalidate）。

### 代价与限制

- v1 polyline fit（`HorizontalAlignmentPolylineFitter`）为切线 + 圆曲线 PI 链近似；fillet 后的真实圆弧 / 缓和曲线 **尚未** 反算回 Spiral 线元。
- 拓扑 instance 域与 HA 总长不一致时（未 materialize、共享路口节点未移动），`resolve` 与 `poseAt` 可能在极端桩号 diverge；工程检查应报告；边界通过 `toCanonicalChainage` / `toInstanceChainage` 比例换算。
- `orientedSegments.length` 仍来自实例折线；canonical 域由 `canonicalLength` 统一提供。

### 对 ADR 0006 的修订

[ADR 0006](0006-road-phase-2-direction.md) §2「当前几何仍以 polyline centerline 为主」**已由本 ADR  supersede**：有 HA 时设计层为平面权威；polyline 降为 Derived + fallback。

## Core engineering model（2026-08-31）

逻辑道路工程视图（`Road` + `RoadStationing`）：

```
Road
├ DesignDirection          entryNode → exitNode（derive）
├ Oriented Segments        edgeId, forward, startStation, length（derive）
├ HorizontalAlignment
├ VerticalAlignment
├ TypicalCrossSection
├ VariableCrossSections
└ StationFacilities
```

- `segmentIds` 仅几何段归属；链方向与桩号不依赖 edge start/end 存储。
- `OrientedRoadSegment` 与 `RoadDesignDirection` 可不持久化，但概念与 API 必须存在。
- 生成管线用 `PathSegmentGeometry.chainLeftNormal(segment, forward)` 解析链相对 LEFT/RIGHT。

测试：`RoadOrientationInvariantTest` — 8 种边存储方向组合下桩号 / VA / VCS / 设施 / 链左向不变。

## Implementation map（Phase 2.2b，2026-08-31）

| 组件 | 层 |
|------|-----|
| `RoadHorizontalAlignment`, `HorizontalAlignmentGeometry` | Design |
| `RoadVerticalAlignment`, `VerticalAlignmentGeometry` | Design |
| `RoadPlanGeometry`, `RoadGeometryAuthority` | 查询门面 |
| `DerivedCenterlineSynchronizer`, `HorizontalAlignmentCenterlineMaterializer` | Design → Derived |
| `CenterlineHorizontalAlignmentSync`, `HorizontalAlignmentPolylineFitter` | Derived → Design |
| `HorizontalAlignmentCenterlineConsistency` | 双轨检测 |
| `HorizontalAlignmentChainOriginAligner` | Design 原点 ↔ 链起点 |
| `RoadEdge.centerlinePoints` | Derived |
| `RoadNode`, `RoadEdge`（拓扑字段） | Topology |
| `RoadStationing`, `OrientedRoadSegment` | 桩号 / 链方向（跨层坐标系） |
| `CenterlinePhase2ConsistencyPolicy` | 中心线编辑 → Phase 2 联动规则 |

### 6. 中心线编辑 → Phase 2 一致性（2026-08-31）

中心线编辑不得静默破坏沿桩号工程数据或 HA 双轨。统一规则由 `CenterlinePhase2ConsistencyPolicy` 执行：

| 操作 | HA | VA / VCS / 设施 |
|------|----|-----------------|
| Insert PI / Fillet | refit 或清除 | 编辑段内仿射重映射（保持物理链位置） |
| Graph split / merge | refit 或清除 | 不变（总链长不变） |
| Road split / merge | 清除 + 折线 refit | `RoadStationDataTransforms` 分区/拼接 |
| Reverse road | 线元逆序 | 整路镜像 |
| Reverse edge | refit 或清除 | **不变**（`OrientedRoadSegment.forward` 取反；LEFT/RIGHT 由 `chainLeftNormal` 解析） |

**Canonical chainage**：模型仅存自链起点 K0+000 的桩号；`ChainageDisplayMode.FROM_END`（EK…）仅为 UI 展示变换。

**Canonical vs instance length**（2026-09-01）：三层长度 API — `instanceLength`（`RoadEdge` 派生折线）、`designLength`（HA 线形总长）、`canonicalLength`（工程桩号域权威：有有效 HA 时 = design，否则 = instance）。`RoadStation`、VA、VCS、设施统一使用 `canonicalLength`。`totalLength` / `planLength` / `instanceChainLength` 已弃用。instance 与 design 不一致时，边界通过 `toCanonicalChainage` / `toInstanceChainage` 比例换算。

**OrientedRoadSegment（Phase 2 基础设施）**：`forward == false` 时几何方向与链相反；生成管线须用 `PathSegmentGeometry.chainLeftNormal(segment, forward)` 解析相对链的 LEFT/RIGHT，禁止直接用几何 `leftNormal`。

**物理位置原则**：沿桩号数据绑定链上位置，而非列表索引；段长变化用仿射 rescale，整路反向用 mirror，单段几何反向不改变 canonical 桩号。

### Station policy 枚举（2026-08-31）

| 策略 | 含义 |
|------|------|
| `PRESERVE_STATION` | 桩号不变 |
| `REPARAMETERIZE_STATION` | 编辑段内弧长比例重映射 + 段后平移 ΔL（**非**绝对 chainage） |
| `PARTITION_AND_RESET_TAIL` | 逻辑拆路：tail 桩号归零 |
| `OFFSET_BY_HEAD_LENGTH` | 并路：B 桩号 += A 长度 |
| `MIRROR_IN_RANGE` / `MIRROR_FULL_ROAD` | 反向镜像 |

实现：`CenterlineEditStationPolicy`、`CenterlineEditOperation`。

## References

- [ADR 0006](0006-road-phase-2-direction.md) — Phase 2 能力链
- [ADR 0003](0003-road-segment-ordering-linear-chain-assumption.md) — 有序分段链
- [Phase 2.2 Horizontal Alignment v1](../development/task-assignments/RoadSystemPlugin_Phase2_HorizontalAlignment_v1.md)
- [Phase 2.1 Stationing v1](../development/task-assignments/RoadSystemPlugin_Phase2_Stationing_v1.md)
- `com.plot.plugin.road.alignment.RoadPlanGeometry`
- `com.plot.plugin.road.station.RoadStationing`
