# ADR 0004: Road 拓扑不变量（Topology Invariant）

## Status

Accepted — 2026-08-30（**定义原则 + 检测框架**；enforce 与 `RoadTopologyMode` 属下一阶段）

## Context

[ADR 0003](0003-road-segment-ordering-linear-chain-assumption.md) 记录了 `RoadSegmentOrdering` 对「简单链」的隐含依赖，以及环路 / 分叉 / 断开时的 best-effort 行为。

下一阶段不应优先改排序算法，而应 **先明确 Road 的产品语义**：

> **普通 Road 是否必须是一条连续、不分叉的有序链？**

建议答案：**YES**（默认 `LINEAR` 模式）。

### 设计原则

一条普通 `Road` 内：

1. **connected components = 1**（分段诱导子图连通）
2. **Road-subgraph 节点度数 ≤ 2**（无内部分叉）

**允许**（按模式）：

| 模式 | 拓扑 | 示例 |
|------|------|------|
| `LINEAR` | open chain（两个端点，度数 1 的节点恰为 2） | Main Street |
| `LOOP`（下一阶段） | closed loop（所有节点度数 2） | Ring Road |

**不允许**（应通过 **多条 Road 在 `RoadNode` 汇合** 表达，而非单 Road 自成 Y）：

```
      branch
        │
────────┼────────   ← 应为 Road A + Road B + Road C 在节点汇合
```

### 收益

明确不变量后，以下能力都有统一的「链」语义，无需在分叉/森林上打补丁：

- 里程（stationing）、方向、segment index
- longitudinal profile、streetlight spacing
- road naming、split、merge

## Decision

### 1. 不变量类型（`RoadTopologyViolationKind`）

| 代码 | 含义 | 当前级别 |
|------|------|----------|
| `ROAD_DISCONNECTED` | 同一 Road 多连通分量 | warning |
| `ROAD_BRANCHING` | 子图内度数 > 2 | warning |
| `ROAD_CYCLE` | 闭合环 | warning（隐含 `LINEAR` 模式） |
| `ROAD_ORDER_MISMATCH` | 存储顺序 ≠ `RoadSegmentOrdering` 拓扑序 | warning |

实现：`RoadTopologyInvariantValidator`（**只检测、不修复、不阻断 build**）。

### 2. 下一阶段（非 blocker）

- 在 `Road` 上增加 `RoadTopologyMode { LINEAR, LOOP }`
- `LOOP` 模式下 `ROAD_CYCLE` 不再报 violation
- **仍禁止** `ROAD_BRANCHING` / `ROAD_DISCONNECTED`（分叉永远属于多 Road 汇合）
- 可选：将 `ROAD_*` 从 warning 升级为 error / 阻断生成（产品决策）

### 3. 明确不做（当前）

- 不改 `RoadSegmentOrdering` 算法
- 不 enforce 用户操作（`assignEdgeToRoad` 仍可制造非法拓扑）
- 不实现 `RoadTopologyMode` 字段持久化

## Consequences

- 工程检查 UI 按 violation kind 分项提示（见 `plugin.road.validation.road_*` i18n）。
- `RoadSegmentTopologyAnalyzer` 标记 `@Deprecated`，新代码使用 `RoadTopologyInvariantValidator`。
- 认领 / 编辑流程可在后续迭代中 **主动维护不变量**（例如 adopt 后 `applyTopologicalOrder`、分叉时自动拆 Road）。

## References

- [ADR 0003](0003-road-segment-ordering-linear-chain-assumption.md)
- `com.plot.plugin.road.model.RoadTopologyInvariantValidator`
- `com.plot.plugin.road.model.RoadTopologyMode`
- `com.plot.plugin.road.RoadNetworkEngineeringValidator#addRoadTopologyItems`

## Supersedes / Updates

- 更新 ADR 0003：per-Road 检测由 `RoadSegmentTopologyAnalyzer` 汇总 warning，升级为 ADR 0004 四类不变量。
