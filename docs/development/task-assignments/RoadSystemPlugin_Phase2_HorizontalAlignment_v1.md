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

### 不包含

- 中心线编辑工具（Phase 2.4）
- 从 polyline 自动拟合线形
- 线形驱动 `RoadEdge` 几何替换（仍用现有 centerline）
- 竖曲线

## 验收标准

- [x] 线元模型 + 几何求值 + 单元测试
- [x] JSON 往返
- [x] 编辑 UI 线形摘要
- [ ] 线形与 polyline 偏差检查（后续）

## References

- [ADR 0006](../../decisions/0006-road-phase-2-direction.md)
- [Phase 2.1 Stationing](RoadSystemPlugin_Phase2_Stationing_v1.md)
