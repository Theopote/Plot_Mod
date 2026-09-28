# Road Vertical Mode v1.0：Terrain Adaptive / Flat 数据模型与求解规则

> **状态**：Draft v1.0（产品定义 + 实现边界）  
> **日期**：2026-09-28  
> **相关 ADR**：[0007 道路三层几何模型](../decisions/0007-road-design-derived-topology-geometry.md)、[0008 AUTO_SMOOTH v1 语义](../decisions/0008-auto-smooth-v1-semantics.md)  
> **前置 UI 收口**：Path / Edit / Generate 职责已在 2026-09 信息架构重组中分离

---

## 1. 问题陈述（我们理解的用户意图）

在 Minecraft 道路建造中，**多数道路需要适应地形**；但也有常见特例：用户希望某条道路是**完全的水平道路**——整条路在一个基准水平面上，只需设置一个 **Y 值**。

关键语义：

| 用户说的 | 系统应理解的 |
|----------|--------------|
| 「水平道路」 | 纵断面由 **固定基准高程** 主导，不是自由曲线 |
| 「也有纵断面」 | 预览/生成仍沿桩号采样标高，图形上仍是 profile |
| 「不能随意加 PVI」 | **禁止**在路径任意位置插入/拖动自由变坡点做起伏 |
| 「交叉只能在交叉点调 Y」 | 偏离基准的唯一显式入口是 **路口约束** |
| 「要有缓慢过渡」 | 路口约束与基准高程之间自动 **坡度受限的平滑过渡**，禁止台阶 |
| 「每条路独立」 | 按 **Road** 选择模式与 Y，不做全网统一 Flat 模式 |
| 「地形仍影响挖填/桥隧」 | Flat 模式下 terrain **不参与纵坡塑形**，只参与施工判定 |

**核心不是「有没有纵断面」，而是：这条路的纵断面是自由设计的，还是由一个固定水平高程主导。**

---

## 2. 产品层 vs 内部层（关键调整）

### 2.1 产品层：只暴露两种纵向策略

面向普通用户，**Edit 主界面**只显示：

```
纵向方式
● 适应地形
○ 水平道路

[水平道路时]
道路高程  Y [70]

交叉口附近会自动平滑过渡
```

### 2.2 内部层：保留现有四种 `RoadVerticalMode`

**不删除**现有枚举，避免破坏已成熟的纵断面与持久化：

```java
// com.plot.plugin.road.vertical.RoadVerticalMode（已存在）
FLAT
FIT_TERRAIN
AUTO_SMOOTH
MANUAL_PROFILE
```

映射关系：

| 产品策略 | 默认内部模式 | 进入 MANUAL_PROFILE 的条件 |
|----------|--------------|---------------------------|
| **适应地形** | `FIT_TERRAIN` 或 `AUTO_SMOOTH`（见 §3.2） | 用户在 Generate 纵断面编辑器中编辑 PVI / 竖曲线 |
| **水平道路** | `FLAT` | 用户显式「允许局部过渡」升级，或 Flat×Flat 冲突解析选择「允许过渡」 |

`MANUAL_PROFILE` **不是**第三个产品选项，而是 **适应地形策略下的权威编辑态**（与 ADR 0008 一致）。

### 2.3 为何不把 FLAT 当成另一种 MANUAL_PROFILE

| | MANUAL_PROFILE | FLAT（v1 目标语义） |
|--|----------------|---------------------|
| 设计权威 | `RoadVerticalAlignment`（PVI 列表） | **`FlatVerticalIntent`**（base Y + 路口 override） |
| 用户可编辑 | 任意 PVI、竖曲线 | 仅 base Y、路口 Y（经交叉关系 UI） |
| 地形参与纵坡 | 可选（guide / target） | **不参与**纵坡塑形 |
| 派生物 | 即设计本身 | `RoadVerticalAlignment` **由编译器生成** |

把 FLAT 硬塞进自由 PVI 会导致：改 base Y 后旧 PVI 残留、纵剖面编辑器语义混乱。

---

## 3. 数据模型

### 3.1 三层权威（对齐 ADR 0007）

```
Road
├── Design Layer — 纵向设计意图（按模式不同）
│     ├── TerrainAdaptiveIntent   （可选，v1 可隐式）
│     │     └── RoadVerticalAlignment  （MANUAL_PROFILE 时权威）
│     └── FlatVerticalIntent      （FLAT 时权威）
│           ├── baseElevation
│           └── intersectionOverrides: Map<nodeId, elevation>
│
├── Derived Layer — 生成用纵断面缓存
│     └── compiledVerticalAlignment: RoadVerticalAlignment
│           （FLAT 时由 FlatProfileCompiler 每次重算）
│
└── Topology Layer — 路口节点
      └── RoadNode.manualElevation / gradeSeparated / elevatedRoadId
```

**规则**：

- `FLAT` 道路：**不得**把用户手改的 PVI 当作权威；`verticalAlignment` 为 **派生缓存**，`verticalMode == FLAT` 时 UI 与求解器读 `FlatVerticalIntent`。
- `MANUAL_PROFILE` 道路：`verticalAlignment` 仍为权威（现状不变）。
- `FIT_TERRAIN` / `AUTO_SMOOTH`：无 PVI 权威；纵坡由 `RoadProfileSolver` 离散链求解（现状不变）。

### 3.2 新增类型（建议包：`com.plot.plugin.road.vertical`）

```java
/** FLAT 道路的纵向设计意图（权威）。 */
public final class FlatVerticalIntent {
    double baseElevation;
    Map<String, Double> intersectionOverrides; // nodeId → 该路口处目标 Y
}

/** 将 FlatVerticalIntent 编译为可求值的 RoadVerticalAlignment（含过渡段 PVI）。 */
public final class FlatProfileCompiler {
    public static RoadVerticalAlignment compile(
        RoadNetwork network,
        Road road,
        FlatVerticalIntent intent,
        double maxGradePercent);
}
```

**持久化（v1）**：在 `Road` JSON 中增加：

```json
{
  "verticalMode": "FLAT",
  "flatVerticalIntent": {
    "baseElevation": 70.0,
    "intersectionOverrides": { "node-abc": 73.0 }
  },
  "verticalAlignment": null   // 或仅存编译缓存，加载时若 FLAT 则重编译
}
```

向后兼容：现有 `FLAT` 道路若只有两端等高的 `verticalAlignment`，迁移时：

```java
baseElevation = alignment.getPvis().getFirst().getElevation();
intersectionOverrides = {}; // 从 node.manualElevation 与 base 差值回填（可选）
```

### 3.3 路口 override 与 `RoadNode.manualElevation` 的关系

| 场景 | 权威写入 | 说明 |
|------|----------|------|
| 用户在 Path 交叉关系面板拖 Y | `node.manualElevation` + 同步到相连道路的 `intersectionOverrides` | 平交/立交净空仍走现有 `RoadGradeSeparationControls` |
| FLAT 道路编译 | 读取 junction 约束 → 合并进 `intersectionOverrides` | `FlatRoadJunctionConflictResolver` 升级为基于 Intent |
| MANUAL_PROFILE | 现有 `VerticalAlignmentJunctionSynchronizer` | 不变 |

**原则**：路口标高仍是 **Topology 层可见约束**；FLAT 道路的 Intent 是 **Road 级编译输入**，不是第二套路口数据库。

---

## 4. 水平道路 ≠ 全程数学常数 Y

### 4.1 定义

```
水平道路 = 基准高程固定 + 仅约束触发的局部平滑过渡
```

允许偏离 `baseElevation` 的原因（**仅此列表**）：

1. 平交：共享路口标高约束  
2. 立交：净空 / 上跨方强制标高  
3. 端点：链式道路端点连接标高（若未来需要）  
4. 网络传播：相邻 FLAT 路在共享路口达成一致（v1 以显式 UI 为主，不静默改两条路的 base Y）

**禁止**因 `terrain hill/valley` 改变 FLAT 道路的纵坡目标。

### 4.2 过渡段求解（v1 不暴露参数）

内部复用已有工程规则（`VerticalProfileDesignRules`）：

```java
requiredRun = |ΔY| / (maxGrade% / 100)
transitionLength = suggestedTransitionLength(incomingRun, outgoingRun)
// MIN_VERTICAL_TRANSITION_LENGTH = 8 blocks（已存在）
```

编译器在相邻 **基准段** 与 **路口 override 点** 之间插入过渡 PVI（或竖曲线），保证：

- 坡度 ≤ `road.getEffectiveMaxSlope(config)`  
- 过渡长度不足 → 验证警告：`⚠ 距离不足，无法在当前坡度限制内完成过渡`

**v1 不向用户暴露**：transition length、K 值、ease 参数。

### 4.3 纵剖面视觉

FLAT 模式图表：

- 虚线：`Base Y = 70`  
- 实线：编译后的实际 target profile（含过渡弯）  
- 路口标记：◇ / ●（现有 `RoadProfileIntersection`）  
- **不显示**可拖动的自由 PVI 控制点（除路口上下文）

---

## 5. 适应地形模式（Terrain Adaptive）

### 5.1 内部行为（现状 + 收口）

| 阶段 | 内部模式 | 纵坡来源 |
|------|----------|----------|
| 认领后默认 | `AUTO_SMOOTH` 或 `FIT_TERRAIN` | `RoadProfileSolver` + 地形采样 |
| 用户打开纵断面编辑器并编辑 PVI | `MANUAL_PROFILE` | `VerticalAlignmentProfileSolver` |
| 有完整 PVI 设计 | `MANUAL_PROFILE` | 设计纵断面为准，地形仅作对比线 |

产品文案统一为 **「适应地形」**；高级折叠可显示当前内部子状态（自动控坡 / 贴合地形 / 已手动编辑纵断面）。

### 5.2 求解优先级（Adaptive）

```
1. 用户锁定的路口标高 / 立交净空
2. MANUAL_PROFILE 时的 RoadVerticalAlignment（PVI + 竖曲线）
3. 最大坡度限制
4. 地形贴合（FIT_TERRAIN 权重大；AUTO_SMOOTH 为平衡 guide line）
5. 平滑 / 自动修坡（编辑器工具）
```

---

## 6. 交叉关系行为矩阵

### 6.1 Adaptive × Adaptive

最自由：两边 profile + 路口约束联合求解（现状）。

### 6.2 Flat × Adaptive

**优先保护 Flat 基准**（除非用户显式改路口 Y）：

- **平交**：默认让 Adaptive 路在路口落到 `Flat.baseElevation`  
- **立交 Flat 上跨**：Flat 主体保持 base Y；Adaptive 适应净空  
- **立交 Flat 下穿**：Flat 在路口局部下凹 → 过渡回 base Y

约束优先级：

```
Flat baseline > Adaptive profile（默认）
用户显式路口 Y > 上述默认
```

### 6.3 Flat × Flat（重点）

```
Road A: FLAT Y=70
Road B: FLAT Y=76
平交 → 冲突
```

**禁止**静默修改任一条路的 `baseElevation`。

UI 必须展示：

```
⚠ 两条水平道路高程不同，无法直接平交
Road A：Y=70    Road B：Y=76

[统一到 Y=70]  [统一到 Y=76]  [改为立交]
```

用户若在纵剖面/交叉面板设置 **交叉高程 Y=73**：

```
A: 70 → 73 → 70   （intersectionOverrides[node]=73）
B: 76 → 73 → 76
```

实现路径：写入 `intersectionOverrides` + `node.manualElevation`，`FlatProfileCompiler` 重编译。

### 6.4 与现有 `FlatRoadJunctionConflictResolver` 的关系

| 现有方法 | v1 演进 |
|----------|---------|
| `find()` | 扩展：检测 Flat×Flat 平交冲突、过渡长度不足 |
| `makeRoadsFlatAtJunctionElevation()` | 保留为「统一到路口标高」快捷操作 |
| `allowConflictingRoadsToSlope()` | 改名为产品动作「允许局部过渡」→ 写 override + 编译，**不默认**切 MANUAL_PROFILE |

---

## 7. UI 规范

### 7.1 Edit Tab（选模式）

```
纵向方式    [适应地形 ▼]
            ├ 适应地形（默认）
            └ 水平道路

[水平道路]
道路高程    Y [70]
（提示）交叉口附近会自动平滑过渡

[适应地形]
最大坡度    …（现有 RoadRouteQuickTune / 坡度预设）
```

- **不放** Path 级交叉编辑  
- **不放** PVI 列表（已移至 Generate）

### 7.2 Generate Tab（处理纵断面）

| 模式 | 纵断面区块行为 |
|------|----------------|
| 适应地形 + 未手动编辑 | 地形/引导线/目标线预览；可进入交互编辑器 |
| 适应地形 + MANUAL_PROFILE | 完整 PVI 编辑 + 交互纵剖面（现状 `VerticalAlignmentEditor` + `VerticalProfileEditor`） |
| 水平道路 | 显示 Base Y 虚线 + 编译 profile；**仅可拖路口 Y**；隐藏「添加 PVI」 |

多选道路：

```
纵向方式  [水平道路]
Y         [Mixed]  →  输入 72 统一全部
```

### 7.3 Path Tab（交叉关系）

不变：平交/立交/谁在上/净空的主入口。  
FLAT 路口拖 Y → 更新 `intersectionOverrides` 并触发重编译。

---

## 8. 模式切换规则

### 8.1 Adaptive → Flat

1. 计算推荐 `baseElevation`（默认：**当前设计 profile 沿桩号中位数或均值**；备选：地形中位数）  
2. 弹窗确认：`转为水平道路 — 推荐高程 Y=68`  
3. 清空权威 PVI（或存档到历史）；写入 `FlatVerticalIntent`；`verticalMode = FLAT`  
4. 从现有路口 `manualElevation` 生成初始 `intersectionOverrides`（若与 base 不同）

### 8.2 Flat → Adaptive

1. `FlatProfileCompiler.compile()` → 得到初始 `RoadVerticalAlignment`（含过渡形状）  
2. `verticalMode = MANUAL_PROFILE`  
3. 进入 Generate 纵断面编辑器继续微调  

### 8.3 批量

- 全选 → 设「水平道路」：每条路 **独立**取各自推荐 Y，不强制同值  
- 全选 → 统一 Y=70：批量写 `baseElevation`

---

## 9. 求解管线改动范围

### 9.1 生成 Preview 时

```
RoadProfileSolveCoordinator.solveForEdge(...)
  │
  ├─ verticalMode == FLAT
  │     └─ alignment = FlatProfileCompiler.compile(intent, maxGrade)
  │     └─ VerticalAlignmentProfileSolver.solveForEdge(alignment, ...)
  │           （地形只写入 profileGroundHeights，不驱动 target）
  │
  ├─ verticalMode == MANUAL_PROFILE && has alignment
  │     └─ VerticalAlignmentProfileSolver（现状）
  │
  └─ AUTO_SMOOTH / FIT_TERRAIN
        └─ RoadProfileSolver（现状）
```

### 9.2 约束优先级（Flat）

```
1. 用户锁定的路口标高（含 intersectionOverrides）
2. Flat baseElevation
3. 立交净空（grade separation）
4. maxSlope → 过渡段几何
5. （不参与）terrain fit
```

### 9.3 施工层

`RoadConstructionEvaluator` / 桥隧判定：**继续**使用 `profileGroundHeights` vs `profileTargetHeights` 高差，与纵坡模式无关。

---

## 10. 代码落点清单（实现阶段）

| 优先级 | 模块 | 动作 |
|--------|------|------|
| P0 | `FlatVerticalIntent` | 新增；`Road` 持久化字段 |
| P0 | `FlatProfileCompiler` | 新增；base + overrides → PVI 链 |
| P0 | `RoadVerticalStrategy`（或 UI helper） | 产品二选一 ↔ 内部 `RoadVerticalMode` 映射 |
| P0 | `VerticalProfileEditor` | FLAT 时禁用自由 PVI；仅路口拖 Y |
| P0 | `VerticalAlignmentEditor` | Edit 只 `renderModeOnly`；Generate 保留完整版 |
| P1 | `FlatRoadJunctionConflictResolver` | 改为 Intent 模型；Flat×Flat 冲突 UI |
| P1 | `RoadProfileIntersectionDragEditor` | FLAT 路拖路口 → 写 override 而非 MANUAL_PROFILE |
| P1 | `VerticalProfileNetworkPropagator` | FLAT 分支调用 compiler 而非直接拒绝传播 |
| P2 | 多选批量 Y / 模式 | `RoadEditWorkspace.renderMulti` |
| P2 | 模式切换向导 | Adaptive↔Flat 推荐高程对话框 |
| P2 | 验证项 | `过渡距离不足`、`flat_flat_at_grade_mismatch` |

**不宜改动的核心**（仅扩展）：

- `RoadVerticalAlignment` / `PointOfVerticalIntersection` 求值（`VerticalAlignmentGeometry`）  
- `RoadStationing` 桩号域  
- Path 交叉关系面板结构（已收口）

---

## 11. v1 明确不做（Non-goals）

- 全网 `Network Mode = Flat`  
- 用户可调的过渡长度 / 竖曲线 K 值（Flat 模式）  
- FLAT 道路因地形自动起伏  
- 删除 `AUTO_SMOOTH` / `FIT_TERRAIN` / `MANUAL_PROFILE` 枚举  
- 在 Edit Tab 编辑交叉关系（已移至 Path）  
- 把 FLAT 和「短于 20 格必须水平」混为一谈（短路规则仍独立，见 `VerticalProfileDesignRules.MIN_ROAD_LENGTH_FOR_SLOPE`）

---

## 12. 验收标准（Golden）

1. **单条 FLAT Y=70**：直线段 target 恒 70；profile 图有 Base Y 虚线。  
2. **FLAT 与 Adaptive 平交**：默认 Adaptive 在路口降至/升至 70；过渡段坡度 ≤ maxSlope。  
3. **FLAT Y=70 × FLAT Y=76 平交**：阻断生成或强提示；用户设路口 Y=73 后两边呈 70↔73↔70 与 76↔73↔76。  
4. **FLAT 立交**：主体保持 base；下穿处在路口有局部过渡坑/台。  
5. **改 base Y**：重编译后无旧 PVI 残留台阶。  
6. **Adaptive 编辑 PVI**：行为与重构前一致。  
7. **桥隧统计**：FLAT 路在丘陵地形仍可出现桥/隧，但纵坡目标不因地形波动。

---

## 13. 开放问题（v1.1+）

1. `FIT_TERRAIN` 与 `AUTO_SMOOTH` 是否在产品层合并为单一「适应地形」，仅高级显示子策略？  
2. FLAT 道路端点（非路口）是否允许锁高？  
3. 编译缓存 `verticalAlignment` 是否持久化，或每次加载重编译？  
4. 循环路网 / 不可桩号化道路的 FLAT 语义。

---

## 14. 结论

该设计与当前仓库方向一致：

- **产品**：每条 Road 独立选择「适应地形 / 水平道路」  
- **内部**：保留四种 `RoadVerticalMode`，`MANUAL_PROFILE` 作为 Adaptive 的权威编辑态  
- **FLAT**：新权威 `FlatVerticalIntent` → 派生 `RoadVerticalAlignment`，路口 Y + 自动过渡  
- **UI**：Edit 选模式，Generate 编辑纵断面，Path 编辑交叉关系  

建议 **先实现 P0（Intent + Compiler + 纵剖面编辑器分支）**，再改交叉冲突与批量 UI，避免在现有自由 PVI 上继续堆叠 FLAT 特殊逻辑。
