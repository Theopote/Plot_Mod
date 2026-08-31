# ADR 0006: 结束 Road 稳定化，进入 Phase 2 专业道路设计

## Status

Accepted — 2026-08-30

## Context

经过多轮稳定化（Road ↔ Edge 归属、有序分段、拓扑不变量、认领拆路、`RoadTopologyMode`、UI/验证/i18n），道路模块已具备可扩展的 **Road Kernel**：

| 已确立原则 | 含义 |
|-----------|------|
| Road ≠ Edge | 逻辑道路与几何分段分离 |
| Road has ordered segments | `RoadSegmentOrdering` + 拓扑不变量 |
| CrossSection ≠ Geometry | 工程属性与中心线几何解耦 |
| Road-level ≠ Segment-level | 继承与覆盖分层清晰 |
| Defaults ≠ Overrides | 显式覆盖 vs 全局默认 |
| Preview ≠ Build | 预览与落地分离 |
| Requested ≠ Applied writes | 写入语义可审计 |
| Topology failure ≠ silent continue | 检测 + warning / 认领后 repair |

**不再建议**继续投入：重构 manager、再拆 service、改 context、抽象层打磨。收益递减。

下一阶段目标：从「画线 + 认领 + 生成」进入 **专业道路线形设计**。

## Decision

### 1. 正式结束 Road Stabilization

- 冻结大规模架构重构，除非阻塞 Phase 2 功能。
- 允许 **小步修正**（bug、i18n、单点 invariant），不作为主线。

### 2. Phase 2 能力链（目标架构）

| 代号 | 能力 | 说明 |
|------|------|------|
| **A** | Horizontal Alignment | 直线 / 圆曲线 / 缓和曲线；Tangent → Spiral → Circular → Spiral → Tangent |
| **B** | Vertical Alignment | PVI、竖曲线（crest/sag）、K 值、设计速度、最小竖曲线半径 |
| **C** | Stationing / Chainage | Road-local 坐标系 K0+000…；横断面采样、设施、override 的统一里程 |
| **D** | Road Editing Tools | 编辑中心线、PI、圆角、拆分/合并/反向等 |

当前几何仍以 **polyline centerline** 为主；A/B 是逐步替换/增强 centerline 的语义层。

> **Superseded（平面）**：[ADR 0007](0007-road-design-derived-topology-geometry.md) — 有 `RoadHorizontalAlignment` 时设计层为平面权威；`RoadEdge.centerlinePoints` 降为派生缓存。纵断面仍以 Phase 2.3 演进为准。

### 3. 推荐实施顺序

| 阶段 | 内容 | 理由 |
|------|------|------|
| **2.1** | **Road Stationing** | 有序链已稳定；里程是平曲线、竖曲线、可变横断面的公共坐标 |
| **2.2** | Horizontal Alignment | 在 station 上挂线元（直线/圆弧/缓和） — **v1 已实现** |
| **2.3** | Vertical Alignment + Vertical Curves | 现有 slope solver 升级为 PVI + 竖曲线 |
| **2.4** | Centerline Editing Tools | 用户直接编辑道路而不仅认领 polyline |
| **2.5** | Cross Section Stations | 沿桩号可变横断面 |
| **2.6** | Retaining Wall / Guardrail / Drainage | 附属设施沿 station 布置 |

**明确延后**：桥梁、信号灯、公交站等，直到 2.1–2.4 建立线形与里程基础。

### 4. Phase 2.1 第一交付物：Road Station / Chainage System v1

**已实现（2026-08-30）**：`com.plot.plugin.road.station` — `RoadStation`、`RoadStationing`、编辑 Tab 桩号展示。

见 [Road Stationing v1 任务书](../development/task-assignments/RoadSystemPlugin_Phase2_Stationing_v1.md)。

### 5. 与现有代码的关系

| 现有 | Phase 2.1 演进 |
|------|----------------|
| `segmentId + localDistance`（坡度 override） | 并存；逐步增加 station 输入/存储 |
| `RoadEdgeListHelper.segmentIndex` | 保留；station 为更上层语义 |
| `RoadProfileSolver` 累计距离 | 可对齐到同一 chainage 定义 |
| `RoadSegmentOrdering` | Stationing 的 **前提依赖**，不再改动算法 |

## Consequences

- 新功能开发以 **Road Stationing → Horizontal Alignment** 为默认优先级。
- PR 评审问「是否属于 Phase 2 能力链」而非「是否还需要稳定化」。
- Phase 2.1 完成后，平曲线线元、竖曲线、设施布置应 **只依赖 RoadStation API**，避免再扩散 `segmentIndex` 语义。

## References

- [ADR 0003](0003-road-segment-ordering-linear-chain-assumption.md)
- [ADR 0004](0004-road-topology-invariant.md)
- [ADR 0005](0005-road-adopt-fork-split.md)
- `com.plot.plugin.road.model.RoadSegmentOrdering`
- `com.plot.plugin.road.RoadEdgeListHelper`
