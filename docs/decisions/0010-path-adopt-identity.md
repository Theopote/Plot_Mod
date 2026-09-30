# ADR 0010: 路径认领保持源路径身份

## 状态

已采纳（2026-03）

## 背景

认领阶段将几何邻近、线段相交、端点吸附过早写成拓扑连接，导致多选路径被合并、T 字/十字自动打断、端点意外共享节点。纵断面与道路身份依赖稳定的 1 Shape = 1 Road 语义。

## 决策

> **认领保持源路径身份；相交产生 Crossing；只有显式 Connect 才产生 Connection。**

### Phase 1（认领隔离）

1. **1 Shape = 1 Road**：`groupConnectedPathsForAdoption` 不再按端点聚类合并简单链；每个可认领片段独立输出。
2. **私有端点节点**：`adoptShape` 使用 `createNode`，不 `findOrCreateNode` 吸附已有网络；几何闭合 polyline 首尾仍共用一个节点。
3. **认领不打断**：认领路径与认领批次末尾均不调用 `detectAndSplitIntersections`；求交打断保留给显式 `reconcileIntersections`（Phase 2 将迁移为 `reconcileCrossings`）。

### 后续阶段（见实施计划）

- Phase 2：`RoadCrossing` 注册表替代认领期拓扑 split
- Phase 3：闭环 `RoadLoopSeam` 与剖面开口点

## 与 ADR 0005 的关系

ADR 0005 侧重认领后分叉拆路与 `repairAfterAdopt`。本 ADR 补充：**认领本身不改变道路数量与拓扑连接**；分叉拆路仍在 repair 阶段处理，但不再因端点邻近或相交在认领时合并/打断。

## 后果

- 多选近端路径 → 多条独立 Road（用户需显式连接或 reconcile）
- 旧测试期望「认领即 T 字」改为「认领独立 + reconcile 才 T 字」
- `detectAndSplitIntersections` 保留但标记 deprecated，供 reconcile 与 snapshot 物化过渡使用
