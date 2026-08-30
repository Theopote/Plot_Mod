# ADR 0003: Road 分段排序的「线性链」假设

## Status

Accepted — 2026-08-30（记录已知边界，**暂不实现**拓扑扩展）

## Context

`Road` 是逻辑道路（工程对象）：横断面、材质、最大坡度等属性绑定在一组 `RoadEdge` 分段上。分段在 `Road.segmentIds` 中有存储顺序，但打断、拆分、重分配后可能与几何拓扑不一致。

`RoadSegmentOrdering` 负责把同一 `Road` 内的分段排成「沿拓扑可走」的顺序，供 UI、里程、`splitRoadBeforeSegment`、链式纵坡等使用。当前算法（简化）：

1. 在 road 子图里找一个 **endpoint**（度数为 1 的节点）；若无则取坐标最小的节点作 fallback；
2. 从该点出发：`edge → node → next edge → …` 贪心行走；
3. 未访问的分段 **追加在列表末尾**（保留原 storage 相对顺序）。

对典型认领结果 `A──B──C──D──E`（简单路径）完全正确。

但 `Road` 的数据模型 **并不禁止** 以下拓扑仍归属同一条逻辑道路：

| 形态 | 示例 | 当前排序行为 |
|------|------|--------------|
| **闭合环路** | `A──B──C──A` | 无 endpoint，从 fallback 节点出发可绕完一圈；顺序依赖起始点选择，**无唯一起终点**，里程非线性 |
| **分叉（树/叉）** | `A──B──C──E` 且 `C──D` | 主链走完后，**另一支路被 append 到末尾**，与主链在拓扑上不连续 |
| **断开分量** | 删段后 `A──B` 与 `D──E` 同 Road | 先走其中一个连通分量，**另一分量 append 在末尾** |

`RoadNetworkInvariantValidator` 只校验 Road ↔ Edge 双向归属，**不 enforce**「同一 Road 的分段构成简单路径」。`assignEdgeToRoad` 可任意挂接。

### 下游依赖「有序链」语义的代码

- `RoadEdgeListHelper.computeRoadLength` / `segmentIndex` — 顺序影响分段序号展示
- `RoadNetwork.splitRoadBeforeSegment` — 按有序列表切 head/tail
- `RoadSlopeUtils.computeChainedTargetHeights` + `RoadProfileSolver` — **假定分段顺序即纵坡传递方向**；分叉/断开时语义错误（高程无法沿真实拓扑传递）
- `RoadNetworkEngineeringValidator` 的 `disconnected_components` — **全网**连通性警告，非 per-Road 简单路径检查

### 当前为何未立即出问题

- 认领（adopt）流程通常产生 **polyline 简单链**；
- 用户删段、手动 reassignment 才可能制造非链拓扑；
- UI 对 append 段仍可读，**短期不会崩溃**，但工程语义已模糊。

## Decision

**现阶段维持现状**，并明确以下产品/工程语义：

1. **文档化假设**：`RoadSegmentOrdering` 及依赖它的纵坡链式计算，其正确性前提是 *同一 Road 的分段诱导子图应为简单路径（open chain）*。
2. **不扩展 `Road` 为通用图/森林模型**（环路、分叉、多分量）——留作后续架构议题。
3. **短期可选增强（非必须）**：在验证层增加 *per-Road* 警告（非简单路径 / 多连通分量 / 分叉节点），仅提示、不阻断；与全网 `disconnected_components` 互补。  
   **已实现（2026-08-30）**：见 [ADR 0004](0004-road-topology-invariant.md) — `RoadTopologyInvariantValidator` 四类 violation。
4. **若未来支持环路或分叉**，需先选定语义再改模型，例如：
   - **方案 A**：收紧 invariant — `Road` 只允许简单路径；环路拆成多条 Road 或单独 `LoopRoad` 类型；
   - **方案 B**：`Road` 升级为 **ordered walk / Eulerian trail / 森林**，排序与纵坡改为图算法；
   - **方案 C**：工程属性仍绑 `Road`，几何纵坡改绑 **path component** 或 **edge 局部求解**，弱化 Road 级链式传递。

## Consequences

- 贡献者与 AI 助手应知：`orderedSegmentIds` ≠ 「几何上唯一正确的工程遍历顺序」，在非链拓扑下是 **best-effort**。
- 新功能若依赖「沿 Road 的累计里程」或「从首段到末段纵坡传递」，必须先确认拓扑前提或显式处理分叉/断开。
- 测试覆盖仍以线性链为主（`RoadSegmentOrderingTest`）；非链 case 可作为未来 warning/拓扑 API 的回归用例，**本次不追加实现**。

## References

- `com.plot.plugin.road.model.RoadSegmentOrdering`
- `com.plot.plugin.road.model.Road` — `getOrderedSegmentIds()` / `reorderSegments()`
- `com.plot.plugin.road.RoadSlopeUtils#computeChainedTargetHeights`
- `com.plot.plugin.road.model.RoadNetwork#splitRoadBeforeSegment`
