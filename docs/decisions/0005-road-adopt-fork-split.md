# ADR 0005: 认领流程分叉自动拆路

## Status

Accepted — 2026-08-30（**P1–P3 已实现**；P4 预览未做）

## Context

[ADR 0004](0004-road-topology-invariant.md) 规定：普通 `Road` 必须是连通、无分叉的有序链（`LINEAR`）或显式 `LOOP`。分叉应表达为 **多条 Road 在同一 `RoadNode` 汇合**，而非单 Road 自成 Y。

### 认领流程现状

| 阶段 | 行为 | 对分叉的处理 |
|------|------|----------------|
| `groupConnectedPathsForAdoption` | 多选路径按端点聚类分组 | **Y 形三线段 → 3 次 adopt**（不合并）✓ |
| `adoptShape` | 每组 → 1 个 `createRoadForAdopt` + 1 条初始边 | 单次认领 = 1 条逻辑 Road ✓ |
| `detectAndSplitIntersections` | 几何打断、共享节点 | **split 后子段继承原 `roadId`** |
| 连续多次 adopt | 每条 path 独立 Road | T 字 / 十字 → 不同 Road 在节点汇合 ✓ |
| 认领结束 | `RoadTopologyRoadSplitter.repairAfterAdopt` + 拓扑序同步 | 断开/分叉拆路；闭合环升为 `LOOP` ✓ |

### 仍会产生 `ROAD_BRANCHING` 的场景

1. **自交 polyline 单次认领**（figure-8、蝴蝶结）：求交打断后多段仍同一 `roadId`，路口处 road-subgraph 度数 ≥ 3。
2. **删段 / 错误 reassignment**（非认领主路径，但 adopt 后编辑会触发）。
3. **闭合环被 merge 成一条 polyline 认领**：主要是 `ROAD_CYCLE`，不是分叉；应配合 `topologyMode=LOOP`（见下文）。

正常 **两次 adopt 的 T 字路口不会产生** `ROAD_BRANCHING`——水平 Road 在节点上只有 2 条自己的边，竖向是另一条 Road。

## Decision（建议）

### 原则

1. **在认领批次的最后**做拓扑修复，而不是改 `RoadSegmentOrdering`。
2. **只拆 `Road` 内部违规**，不把「多 Road 在节点汇合」误判为分叉。
3. **默认保守**：自动拆路 + 状态栏摘要；不阻断认领；单次 undo 可回滚整批修复。
4. **手工 `assignEdgeToRoad` 不自动拆**（用户意图优先）；提供与编辑 Tab 一致的「同步/修复拓扑」入口（可选，后续）。

### 建议插入点

```
RoadNetworkManager.adoptSelectedPaths
  → groupConnectedPaths + adoptShape（现有）
  → detectAndSplitIntersections 重试（现有）
  → 【已实现】`RoadTopologyRoadSplitter.repairAfterAdopt(network)`（P1 断开 + P2 分叉 + P3 闭合环 → LOOP）
  → applyTopologicalOrderToAllRoads（可并入 repair 末尾）
  → notifyNetworkChanged
```

### 算法 A：断开分量（`ROAD_DISCONNECTED`）— 优先实现

对每条违规 Road：

1. 在 road-subgraph 上做连通分量 BFS。
2. 第一个分量保留原 `roadId` 与名称。
3. 其余分量：`createRoad` + `copyEngineeringFrom` + `assignEdgeToRoad` 各段。
4. 空 Road 删除。

风险低，与「删中间段」场景直接对齐。

### 算法 B：内部分叉（`ROAD_BRANCHING`）— 核心

对每条违规 Road，找 road-subgraph 内度数 **> 2** 的节点（fork）。

在每个 fork 节点 `J`：

1. 收集 incident 的 road 内边 `e1…ek`。
2. 每条 incident 边沿 road-subgraph **向外延伸**为一条 maximal chain（直到下一 fork 或端点）。
3. 每条 chain → 一条 `LINEAR` Road：
   - **保留原 Road**：最长 chain，或「与认领前主路径重合最多」的 arm（tie-break：段数多 → 总长长）。
   - **其余 arm**：`createRoad` + `copyEngineeringFrom(parent)` + 迁移 segment。
4. 名称：保留原名 vs `"{name} · 支路 {n}"` / 自动标签（产品可选）。
5. 递归处理新 Road 上仍存在的 fork（罕见，但 figure-8 可能需要两轮）。

**不在此步骤处理** network 级 junction（多 Road 共享节点）——那是正确语义。

### 算法 C：闭合环认领（配合 ADR 0004 `LOOP`）

在 `adoptShape` 或 grouping 阶段：

- 若 polyline 首尾闭合（或 merge 后 `clusterDegrees` 全为 2 且无度 1 端点）→ 设置 `road.setTopologyMode(LOOP)`。
- 避免对真实环路误报 `ROAD_CYCLE`。

自交环（figure-8）**不能**仅用 LOOP 豁免，仍需算法 B。

### 与现有模块关系

| 模块 | 角色 |
|------|------|
| `RoadTopologyInvariantValidator` | 检测哪些 Road 需要 repair |
| `RoadNetwork.splitRoadBeforeSegment` | 仅适用于 **已有序链** 上的一点切开；**不能**替代 fork 拆路 |
| `RoadGraphEdits.splitEdgeAtNode` | 几何打断；不负责 Road 归属拆分 |
| `groupConnectedPathsForAdoption` | 认领**前**预防；保持现有 fork 不合并逻辑 |

## 产品行为建议

### 认领成功文案（示例）

- 「认领 3 条道路；其中 1 条在路口自动拆分为 3 条（符合 open chain 规则）。」
- 无修复时不额外提示。

### 不建议自动做的

- 跨 `sourceRoadId` 合并 Road（与「一次认领 = 一组边」语义冲突）。
- 在 fork 处自动改道路名称为路口名（留给命名工具）。
- 认领时静默丢弃自交 arm（应拆路而非删几何）。

## 实现分期

| 阶段 | 内容 | 价值 |
|------|------|------|
| **P1** | `repairDisconnectedRoads` | 删段后 adopt 批次末尾自愈 |
| **P2** | `splitBranchingRoadsAtForks` | 自交 polyline、错误合并的主修复 |
| **P3** | adopt 时自动 `topologyMode=LOOP` | 环形路认领零 warning |
| **P4** | 认领前预览「将拆分为 N 条道路」（可选） | 降低意外感 |

每阶段独立 PR + 场景测试（T 字、十字、figure-8、三岔选、删段）。

## Consequences

- 认领后工程检查 `road_branching` warning 应显著减少。
- undo 栈：repair 与 adopt 同事务（一次 `pushHistory` 在 repair 前）或 repair 单独一步（需 UX 决定）。
- 纵坡 / 里程语义在 repair 后立即合法（每条 Road 独立链）。

## References

- [ADR 0004](0004-road-topology-invariant.md)
- `RoadGeometryUtils.groupConnectedPathsForAdoption`
- `RoadNetworkBuilder.adoptShape`
- `RoadNetworkManager.adoptSelectedPaths`
