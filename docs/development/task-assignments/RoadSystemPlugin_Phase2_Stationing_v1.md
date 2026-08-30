# Road System Plugin — Phase 2.1 Road Stationing v1

## 目标

建立 **Road-local 里程（chainage）** 计算与展示，作为平曲线、竖曲线、可变横断面、道路设施的唯一沿程坐标基础。

## 背景

- 有序分段与拓扑不变量已稳定（ADR 0003–0005）。
- 当前沿程位置分散在：`segmentIndex`、`SlopeOverride.startDistance`（边内局部）、profile 累计距离。
- v1 **不替换**现有存储，先提供统一 API 与 UI 只读展示。

## 范围（v1）

### 包含

1. **`RoadStation` 值对象**  
   - `roadId` + `chainageMeters`（从道路链起点沿拓扑序累计，单位：米，与 `RoadEdge.getLength()` 一致）。

2. **`RoadStationing` 工具类 / 小服务**（`com.plot.plugin.road.station` 或 `model` 包）
   - `totalLength(network, road)`
   - `orderedSegments(network, road)` — 委托 `RoadSegmentOrdering`
   - `segmentStartStation(network, road, segmentId)` → double
   - `resolve(network, road, station)` → `SegmentStation(segmentId, localDistance)` 或 empty
   - `stationAt(network, road, segmentId, localDistance)` → `RoadStation`
   - `format(station, style)` → `"K0+000.0"` / `"0+020.0"`（中英文 i18n 可共用数字格式）
   - `isValid(network, station)` — road 存在且为可维护链（LINEAR 或 LOOP；分叉/断开返回 invalid）

3. **LOOP 道路**
   - v1：chainage 范围 `[0, totalLength)`，不自动 wrap；超出为 invalid。
   - 文档注明 Phase 2.2+ 可选「环状桩号」策略。

4. **UI（只读）**
   - 编辑 Tab 道路摘要：显示总里程 `K0+000 – K0+xxx`
   - 选中分段：显示该段起终点桩号
   - 坡度 override 行：旁注桩号范围（只读，仍编辑 localDistance）

5. **测试**
   - 多段折线累计
   - `station ↔ segment+offset` 往返
   - 无序 storage + `applyTopologicalOrder` 后 station 与几何一致
   - LOOP 道路总长度
   - 分叉/断开 road → `isValid` false

### 不包含（留给后续阶段）

- 持久化 `RoadStation` 到 JSON / override 改存 station
- 反向桩号（从终点计）
- 平曲线线元（PI、圆弧、缓和曲线）
- 竖曲线 / PVI
- 中心线编辑工具
- 横断面桩号变化

## 技术要点

### 链长累计

```
station(0) = 0
station(i) = sum(length(edge[0..i-1]))
```

边长使用 `RoadEdge.getLength()`（canvas/世界单位与现有 profile 一致；若 profile 有 `canvasUnitsPerBlock` 换算，station API 文档写清与 profile 对齐方式）。

### 与 SlopeOverride 的关系（迁移路径）

| 现在 | Phase 2.3 可选 |
|------|----------------|
| `startDistance`, `endDistance` 边内米 | `startStation`, `endStation` 或双存 |

v1 仅提供 `stationAt` / `resolve` 供显示与校验。

### 依赖

- `RoadSegmentOrdering.orderedSegmentIds`
- `RoadTopologyInvariantValidator` — 仅对 valid topology 保证 station 语义
- `RoadTopologyMode.LOOP` — 允许无端点链

## 验收标准

- [x] `RoadStationing` 单元测试覆盖上述场景
- [x] 编辑 UI 显示道路总桩号与当前段桩号
- [x] 现有 slope override / profile / build **行为不变**
- [x] ADR 0006 引用本任务书

## 建议后续（2.2 预览）

- `HorizontalAlignment`：线元列表挂到 Road，每元带 `startStation` / `length`
- `RoadPI` / `CircularCurve` / `Spiral` 数据结构草案与 station 对齐
