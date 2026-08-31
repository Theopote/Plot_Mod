# Road System Plugin — Phase 2.2 Horizontal Alignment v1

## 目标

在 Phase 2.1 桩号基础上，为 `Road` 增加 **平面线形（horizontal alignment）** 数据模型与几何求值，支持 Tangent → Spiral → Circular → … 工程线形链。

## 范围（v1）

### 包含

- `HorizontalAlignmentElementType`：`TANGENT` / `CIRCULAR_ARC` / `SPIRAL`
- `RoadHorizontalAlignment`：原点、起始方位角、有序线元列表
- `HorizontalAlignmentGeometry`：沿里程求 `AlignmentPose`、采样点列、线元描述
- `Road.horizontalAlignment` 字段 + sidecar JSON 持久化
- 编辑 Tab 只读展示线元列表

### 线元参数

| 类型 | 参数 |
|------|------|
| TANGENT | `length` |
| CIRCULAR_ARC | `length`, `radius`（正）, `direction` LEFT/RIGHT |
| SPIRAL | `length`, `spiralParameterA`（clothoid A；κ(s)=κ₀+s/A²） |

### 不包含（v1 范围外 / 后续）

- 中心线编辑工具完整 UX（Phase 2.4；基础 PI/Fillet 已有）
- Spiral / fillet 圆弧的精确反算拟合（v1 为 T-A-T PI 链近似）
- 竖曲线（Phase 2.3）

## 验收标准

- [x] 线元模型 + 几何求值 + 单元测试
- [x] JSON 往返
- [x] 编辑 UI 线形摘要
- [x] 线形与 polyline 偏差检查（`HorizontalAlignmentCenterlineConsistency`）
- [x] 线形写回中心线（`HorizontalAlignmentCenterlineMaterializer`）
- [x] materialize 后 HA 原点对齐链起点（`HorizontalAlignmentChainOriginAligner`）
- [x] 生成管线读取设计线形（`RoadPlanGeometry` → `RoadEdgeBuildOrchestrator`）
- [x] 生成前自动 materialize 派生中心线（`DerivedCenterlineSynchronizer`）
- [x] centerline 编辑后 refit / invalidate HA（`CenterlineHorizontalAlignmentSync`）
- [x] `RoadStationing` 平面查询委托 `RoadPlanGeometry`

## References

- [ADR 0006](../../decisions/0006-road-phase-2-direction.md)
- [ADR 0007](../../decisions/0007-road-design-derived-topology-geometry.md) — 三层几何模型
- [Phase 2.1 Stationing](RoadSystemPlugin_Phase2_Stationing_v1.md)
