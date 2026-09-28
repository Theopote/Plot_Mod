# Road Vertical Mode v1.0
## Terrain Adaptive / Flat 数据模型与求解规则

**适用项目：Plot_Mod / Road System**  
**版本：v1.0**

---

## 1. 目标

Road System 中，每一条逻辑 `Road` 必须独立决定自己的纵向设计方式。

用户层只需要理解两种：

### 1. Terrain Adaptive — 适应地形

道路纵断面主要根据 Minecraft 地形生成，并允许用户通过纵剖面进一步调整。

典型用途：

- 普通乡村道路
- 山区道路
- 城市道路
- 沿地形起伏的道路

### 2. Flat — 水平道路

道路拥有一个明确的基础高程：

```text
Base Y = constant
```

道路绝大部分保持在这个水平面。

只有为了满足：

- 平交高程关系
- 立交净空
- 与其他道路连接
- 端点连接

才允许在交叉点附近产生局部、平缓的纵向过渡。

Flat 道路不能像普通纵断面一样任意增加 PVI 或自由改变道路起伏。

---

# 2. 核心设计原则

## 2.1 Vertical Mode 属于每一条 Road

禁止设置：

```text
RoadNetwork.verticalMode
```

每条道路独立保存自己的纵向设计意图。

例如：

```text
Road A → Flat, Y=70
Road B → Terrain Adaptive
Road C → Flat, Y=76
Road D → Terrain Adaptive
```

整个路网可以：

- 全部 Terrain Adaptive；
- 全部 Flat；
- 两者任意混合。

---

## 2.2 Flat ≠ 永远数学水平

Flat 的定义不是：

> 每一个 Station 必须严格等于 Base Y。

而是：

> **Base Y 是道路的主要纵向约束；只有明确的网络连接约束允许产生局部偏离。**

因此允许：

```text
Base Y = 70

──────────────╮
              ╰──── Y74 ← intersection
              ╭────
──────────────╯

其余道路继续恢复到 Y70
```

不允许：

```text
Y70 ──╮
      ╰── Y73 ──╮
                 ╰── Y68
```

由用户随意插入多个普通控制点形成自由纵断面。

---

## 2.3 Terrain 对 Flat 只影响工程结果，不改变设计高程

Terrain Adaptive：

```text
Terrain
  ↓
Vertical Profile
```

Flat：

```text
Base Y + Junction Constraints
  ↓
Vertical Profile

Terrain
  ↓
Cut / Fill / Bridge / Tunnel
```

也就是说，山坡、谷地不能自动让 Flat Road 随地形上下起伏。

---

# 3. 产品层 Vertical Strategy

用户界面中只显示：

```text
纵向方式

○ 适应地形
○ 水平道路
```

建议定义产品级概念：

```java
enum RoadVerticalStrategy {
    TERRAIN_ADAPTIVE,
    FLAT
}
```

但 **v1.0 不要求必须新增一个持久化 enum**。

当前已有：

```java
RoadVerticalMode {
    FLAT,
    FIT_TERRAIN,
    AUTO_SMOOTH,
    MANUAL_PROFILE
}
```

可以继续作为 Engine Mode。

产品层映射建议：

```text
Terrain Adaptive
├ FIT_TERRAIN
├ AUTO_SMOOTH
└ MANUAL_PROFILE

Flat
└ FLAT
```

其中：

```text
MANUAL_PROFILE
```

不是用户选择的第三种道路类型。

它表示：

> Terrain Adaptive 道路已经进入人工纵断面编辑状态。

---

# 4. Road 数据模型

当前：

```java
Road {
    RoadVerticalMode verticalMode;
    RoadVerticalAlignment verticalAlignment;
}
```

保留。

Flat v1.0 建议增加明确的 authoritative 数据：

```java
Double flatBaseElevation;
```

最终：

```java
Road {
    RoadVerticalMode verticalMode;

    // Flat 权威参数
    Double flatBaseElevation;

    // Manual / derived profile
    RoadVerticalAlignment verticalAlignment;
}
```

---

# 5. Flat Base Elevation 是权威数据

当：

```text
verticalMode == FLAT
```

权威数据必须是：

```text
flatBaseElevation
```

例如：

```text
FLAT
baseElevation = 70
```

不能继续把：

```java
verticalAlignment.getPvis().getFirst().getElevation()
```

当作 Flat 的唯一权威来源。

当前代码中的：

```java
FlatRoadJunctionConflictResolver
```

目前通过读取一个完全水平的 `RoadVerticalAlignment` 判断 Flat 高程。

v1.0 建议逐步改成：

```text
flatBaseElevation
        ↓
Flat profile solver
        ↓
Derived RoadVerticalAlignment
```

即：

> **Flat Intent → Derived Profile**

而不是：

> Flat 本身就是一条普通 Manual PVI 曲线。

---

# 6. Flat Road 的第二类数据：Intersection Constraint

Flat Road 不允许普通用户创建自由 PVI。

但允许交叉点产生局部高程约束。

权威数据优先复用现有：

```java
RoadNode.manualElevation
RoadNode.gradeSeparated
RoadNode.elevatedRoadId
RoadNode.crossingClearance
```

不要创建第二套：

```text
FlatIntersectionOverride
```

除非以后证明 `RoadNode.manualElevation` 语义不足。

v1.0 优先保持：

```text
Road
    flatBaseElevation

RoadNode
    manualElevation
    gradeSeparated
    elevatedRoadId
    crossingClearance
```

求解器负责将两者组合。

---

# 7. Flat Derived Profile

Flat Road 最终仍然必须生成可供现有 Pipeline 使用的：

```java
RoadVerticalAlignment
```

但这条 Alignment 是：

> Derived Data

不是：

> User Authored Manual Profile

概念：

```text
FlatVerticalIntent
├ Base Y
├ Junction A → Y73
├ Junction B → Y68
└ maxSlope
       ↓
FlatVerticalProfileSolver
       ↓
RoadVerticalAlignment
```

例如：

```text
Base = Y70

Station
0       80       120       200

Y70 ─────────╮
             ╰─Y73─╮
                   ╰──────── Y70
```

---

# 8. Flat Profile 的控制点类型

Flat Profile 内部可以生成 PVI，但必须区分：

```text
DERIVED
```

与：

```text
USER_EDITABLE
```

用户不应该在 Flat 模式下看到普通 PVI 编辑。

Flat 的内部控制点可以包括：

```text
BASELINE_ANCHOR
TRANSITION_START
JUNCTION_TARGET
TRANSITION_END
```

例如：

```text
Y70 ──────────────
       ↑
Transition Start

             ◇ Junction Y74

                     ↓
             Transition End
──────────────── Y70
```

这些可以继续编译成现有 `RoadVerticalAlignment`。

---

# 9. Flat Profile Solver

建议新增：

```java
FlatRoadVerticalProfileSolver
```

职责：

```text
Road
+ RoadNetwork
+ Junction constraints
+ maxSlope
        ↓
Derived RoadVerticalAlignment
```

它不负责：

- Terrain fitting
- Cut/fill
- Bridge generation
- Tunnel generation

---

# 10. Flat Solver 基本算法

对 Road：

```text
Base Y = B
```

收集所有该 Road 上需要修改高程的约束：

```text
Constraint {
    station
    targetElevation
    sourceNodeId
}
```

排序：

```text
C1, C2, C3...
```

对每一个 Constraint：

```text
ΔY = targetY - baseY
```

根据最大坡度：

```text
requiredDistance >= |ΔY| / maxSlope
```

例如：

```text
Base Y = 70
Target Y = 74
ΔY = 4

maxSlope = 8%
requiredDistance >= 50 blocks
```

然后建立：

```text
transitionStart
junctionTarget
transitionEnd
```

---

# 11. 平滑过渡

v1.0 不要求用户控制：

```text
Vertical Curve Radius
K Value
Transition Ease
```

这些应内部自动处理。

过渡求解遵循：

```text
首先满足：
1. Junction target elevation
2. Max slope

然后：
3. 尽可能平滑
4. 尽可能少偏离 Base Y
```

建议逻辑：

```text
transition length =
max(
    minimum slope distance,
    configured minimum transition distance
)
```

内部可以复用现有：

```text
VerticalProfileCurveFitter
VerticalProfileAutoFixer
```

或提取公共算法。

---

# 12. Transition overlap

如果两个交叉点非常靠近：

```text
Junction A
    ↓
----/\-----

        Junction B
            ↓
-------\/---
```

两个 transition 区域发生重叠时，不允许分别独立求解。

必须将它们作为一个 constraint cluster：

```text
C1
C2
C3
    ↓
solve together
```

最终生成连续 profile。

建议：

```java
FlatRoadConstraintCluster
```

但 v1.0 可以内部实现，不必持久化。

---

# 13. 无法满足坡度时

例如：

```text
Base = Y70
Junction = Y80

距离只有 20 blocks
Max slope = 8%
```

理论至少需要：

```text
125 blocks
```

此时不能偷偷突破坡度。

求解结果必须返回：

```java
FlatProfileSolveResult {
    alignment;
    violations;
}
```

Violation：

```text
INSUFFICIENT_TRANSITION_DISTANCE
GRADE_LIMIT_EXCEEDED
CONFLICTING_JUNCTION_CONSTRAINTS
```

UI：

```text
⚠ 交叉点附近没有足够距离完成平滑过渡
```

并提供建议：

```text
降低/提高交叉点高程
改变上下关系
增加允许坡度
```

不要自动把道路变成 Terrain Adaptive。

---

# 14. Flat × Terrain Adaptive：平交

设：

```text
Road A
Flat
Y70

Road B
Terrain Adaptive
```

如果：

```text
A × B = AT_GRADE
```

默认优先级：

```text
Flat Base Elevation
>
Adaptive Terrain Target
```

因此默认目标：

```text
Junction Y = 70
```

Road B 调整纵断面与 Y70 相接。

如果 B 无法在坡度限制内到达 Y70：

```text
⚠ 平交导致 Road B 坡度过大
```

然后系统允许：

```text
1. 修改交叉高程
2. 改成立交
```

---

# 15. Flat × Terrain Adaptive：立交

例如：

```text
Flat Road A = Y70
Road B Adaptive
A 在上
Clearance = 4
```

优先策略：

```text
A 尽量保持 Y70
B ≤ Y66
```

即：

> Adaptive Road 优先承担调整。

如果 B 无法合理下降：

自动评估：

```text
A 上
vs
B 上
```

可以继续使用：

```java
RoadGradeSeparationProfileEvaluator
```

---

# 16. Flat Road 什么时候允许偏离 Base Y

只有以下情况：

### A. 用户明确调整 Junction Elevation

例如：

```text
Base = 70
Node = 74
```

### B. Flat × Flat 平交冲突

### C. 网络连续性需要

### D. 明确的连接端点约束

### E. 用户明确选择某种立交方案，而该方案需要局部过渡

其他情况：

```text
Terrain
Cut/Fill
Bridge
Tunnel
```

不得改变 Flat profile。

---

# 17. Flat × Flat：同一高程

```text
Road A = Y70
Road B = Y70
```

平交：

```text
Junction = Y70
```

无需 transition。

---

# 18. Flat × Flat：不同高程

```text
Road A = Y70
Road B = Y76
```

如果是立交：

非常自然：

```text
A Y70
B Y76
```

只需要检查：

```text
actual gap >= required clearance
```

---

如果是平交：

发生冲突：

```text
A wants 70
B wants 76

AT_GRADE requires same elevation
```

系统不能自动把整条 Road A 或 B 改成另一高度。

必须产生：

```text
FLAT_AT_GRADE_ELEVATION_CONFLICT
```

---

# 19. Flat × Flat 冲突 UI

用户看到：

```text
两条水平道路高程不同

Road A   Y70
Road B   Y76
```

提供简单选项：

```text
交叉高程

○ Y70
○ Y76
● 自定义 Y [73]
○ 改为立交
```

选择：

```text
Y73
```

结果：

```text
Road A:
70 → 73 → 70

Road B:
76 → 73 → 76
```

两条 Road 都仍然保持：

```text
verticalMode = FLAT
```

绝对不要像当前：

```java
FlatRoadJunctionConflictResolver.allowConflictingRoadsToSlope()
```

一样，为解决冲突直接：

```java
road.setVerticalMode(MANUAL_PROFILE)
```

v1.0 中应逐步废弃这种行为。

---

# 20. 当前 FlatRoadJunctionConflictResolver 的调整

当前存在：

```java
makeRoadsFlatAtJunctionElevation()
```

和：

```java
allowConflictingRoadsToSlope()
```

v1.0 建议：

### 保留检测职责

```java
find()
```

可以继续使用。

### 弱化 / 废弃

```java
allowConflictingRoadsToSlope()
```

因为它破坏：

> Flat 是用户设计意图。

新的解决路径应该是：

```text
Flat Conflict
→ Intersection Override
→ Flat Profile Re-solve
```

而不是：

```text
Flat
→ MANUAL_PROFILE
```

---

# 21. Flat 与 Manual Profile 的关系

必须明确：

```text
FLAT
≠ MANUAL_PROFILE
```

即使 Flat 内部最终生成：

```java
RoadVerticalAlignment
```

也不能：

```text
因为存在多个 PVI
→ 自动认为它是 MANUAL_PROFILE
```

建议规则：

```text
RoadVerticalMode 是权威 intent。
verticalAlignment 的形状不能反向推翻显式 verticalMode。
```

只有旧存档：

```text
verticalMode == null
```

才允许通过 Alignment 推断模式。

---

# 22. 现有 legacy inference

当前 `RoadVerticalModeTest` 中存在：

```text
legacy Road
no verticalMode
+
has alignment
→ MANUAL_PROFILE
```

保留用于旧数据。

但是新增规则：

```text
if verticalMode != null
    never infer from alignment
```

尤其：

```text
verticalMode == FLAT
+
derived alignment contains transition PVIs
```

必须仍然是：

```text
FLAT
```

---

# 23. Terrain Adaptive 规则

产品层：

```text
适应地形
```

内部默认建议：

```text
AUTO_SMOOTH
```

或者当前产品默认 mode。

求解：

```text
Terrain samples
→ Target profile
→ Max slope constraints
→ Junction constraints
→ Smooth
```

允许用户打开 Profile Editor 后调整 PVI。

用户手工编辑后：

```text
AUTO_SMOOTH
→ MANUAL_PROFILE
```

这符合当前架构。

---

# 24. Terrain Adaptive 的 Manual Profile

`MANUAL_PROFILE` 仍属于：

```text
Terrain Adaptive family
```

用户界面不显示：

```text
纵向方式：Manual Profile
```

而显示：

```text
纵向方式：适应地形
纵断面：已手动调整
```

这样用户无需理解 Engine Mode。

---

# 25. 模式转换：Adaptive → Flat

用户选择：

```text
适应地形
→ 水平道路
```

系统必须询问/建议：

```text
道路高程 Y
```

默认推荐值：

优先：

```text
median(current design profile)
```

备选：

```text
average current profile
```

不推荐直接用：

```text
start elevation
```

因为长道路可能产生很大偏差。

转换：

```text
verticalMode = FLAT
flatBaseElevation = suggestedY
```

原来的 `RoadVerticalAlignment`：

可以立即重新生成：

```text
Flat derived alignment
```

而不是保留旧自由 profile。

---

# 26. 模式转换：Flat → Terrain Adaptive

切换：

```text
FLAT
→ Terrain Adaptive
```

建议：

```text
verticalMode = AUTO_SMOOTH
```

Flat 当前实际 derived profile 可以：

### v1 推荐

不作为新的 Manual Profile。

重新：

```text
Terrain
→ AUTO_SMOOTH
```

因为用户明确选择了：

> 重新适应地形。

如果以后需要：

```text
“以当前纵断面为起点”
```

可以作为高级功能。

v1 不做。

---

# 27. Edit Tab UI

单选 Road：

```text
纵向方式

[ 适应地形 ] [ 水平道路 ]
```

如果 Adaptive：

```text
最大坡度
[标准 ▼]
```

不显示 PVI。

---

如果 Flat：

```text
纵向方式
[ 水平道路 ]

道路高程
Y [70]

交叉点附近将自动生成平缓过渡
```

不要在 Edit 显示：

```text
PVI
Curve
Transition Station
```

---

# 28. Multi-select UI

多个 Road：

```text
纵向方式
[ Mixed ▼ ]
```

可以批量：

```text
设为适应地形
设为水平道路
```

如果全部 Flat：

```text
道路高程

Mixed
```

用户输入 Y72：

```text
所有选中 Road
baseElevation = 72
```

如果只是点击：

```text
设为水平道路
```

而不输入 Y：

每条 Road 使用各自推荐的 Base Y。

不要默认把全部 Road 强制成同一个 Y。

---

# 29. Generate Tab

Generate 不负责选择 Vertical Strategy。

它只显示当前 Road 的状态：

Adaptive：

```text
纵向方式
适应地形
```

Flat：

```text
纵向方式
水平道路 · Y70
```

然后：

```text
[计算 / 重新计算]
```

---

# 30. Terrain Adaptive Profile Editor

显示：

```text
Terrain
Guide
Design
Intersections
PVI
```

允许：

```text
拖 PVI
修改 Elevation
修改 Station
Auto Smooth
Auto Fix Grade
调整 Intersection
```

---

# 31. Flat Profile Editor

显示：

```text
Terrain
Base Y
Derived Road Profile
Intersection
Transition
```

视觉：

```text
Terrain    /^^^^^\____

Base Y  - - - - - - - - -  Y70

Road    ───────╮
               ╰──◇──╮
                    ╰────────
```

其中：

```text
Base Y
```

建议用独立虚线表示。

---

# 32. Flat Profile Editor 禁止的操作

Flat 下禁止：

```text
Add PVI
Delete PVI
Move arbitrary PVI
Auto Smooth PVI
Edit arbitrary control point
```

也不要显示普通 PVI 列表。

允许：

```text
修改 Base Y
选择交叉点
拖交叉点 target Y
改变平交/立交关系
改变谁在上
```

---

# 33. Flat Intersection Drag

纵剖面拖动：

```text
◇
```

不是在创建 PVI。

它代表：

> 修改该 Junction 的目标高程约束。

如果平交：

```text
node.manualElevation = new Y
```

Flat solver 重新生成局部 transition。

---

# 34. Flat 立交拖动

如果：

```text
A Flat Y70
A 上跨 B
required clearance = 4
```

拖 B：

```text
B <= 66
```

仍遵守：

```text
required clearance
```

而且：

```text
crossingClearance
```

永远代表：

> minimum required clearance

不能改成 actual gap。

显示：

```text
要求净空：4
当前净空：7
```

---

# 35. 交叉关系决策优先级

v1.0：

```text
1. 用户明确锁定的交叉关系
2. 用户明确设置的 Junction Y
3. Flat Base Y
4. Required Clearance
5. Max Slope
6. Smooth Transition
7. Terrain Adaptation
```

Terrain Adaptive Road 中：

```text
Terrain Adaptation
```

权重较高。

Flat Road 中：

```text
Terrain Adaptation
```

不参与纵断面设计。

---

# 36. Auto Grade Separation

现有：

```java
RoadGradeSeparationProfileEvaluator
```

继续保留。

但评估两种方案时必须识别：

```text
RoadVerticalMode.FLAT
```

对于 Flat Road：

### Cost 中重点惩罚

```text
偏离 Base Y 的幅度
偏离 Base Y 的长度
最大坡度
```

因此一般情况下：

```text
Flat
vs
Adaptive
```

会倾向让 Adaptive Road 承担更多高程变化。

但不是绝对禁止 Flat 偏移。

---

# 37. 建议的方案评分

概念：

```text
cost =
    slopeViolationPenalty
  + flatDeviationPenalty
  + elevationChangePenalty
  + transitionLengthPenalty
  + structurePenalty
```

其中：

```text
slopeViolationPenalty
```

最高。

Flat：

```text
flatDeviationPenalty > 0
```

Adaptive：

```text
flatDeviationPenalty = 0
```

---

# 38. Flat Baseline 的连续区间

Flat Road 有多个 Intersection override 时，求解器应尽可能恢复 Baseline。

例如：

```text
Base = 70

     J1=74          J2=68

────╮     ╭────────╮
    ╰─────╯        ╰────╮
                        ╰──── Base
```

不能简单连接：

```text
J1 → J2
```

导致道路很长一段永远偏离 Base Y。

求解目标应该包含：

```text
minimum total deviation from baseline
```

---

# 39. Endpoint Constraint

如果 Flat Road 的端点必须与另一条已连接 Road 共高程：

```text
endpointElevation != Base Y
```

允许形成：

```text
endpoint
   ↓
Y74 ╲
     ╲
      ───────── Y70
```

端点同样作为 Constraint。

---

# 40. Terrain Sampling

两种模式都应该采 Terrain。

原因：

### Adaptive

用于：

```text
vertical profile
```

### Flat

用于：

```text
cut
fill
bridge
tunnel
visibility
```

所以：

```text
Flat
```

不是：

> 不需要 Terrain Sampling。

只是 Terrain 不参与决定设计 Y。

---

# 41. Preview Invalidation

以下变化必须 invalidate：

```text
Vertical Mode
Flat Base Y
Junction manual Y
平交/立交
elevatedRoadId
required clearance
maxSlope
Road geometry
```

流程：

```text
修改
→ Preview stale
→ 清 Ghost
→ [重新计算]
```

不要在每次 Slider drag 中运行完整 generation。

---

# 42. Flat 编辑过程中的临时预览

拖 Base Y 或 Junction Y 时：

可以即时更新：

```text
Profile graphical overlay
```

但不立即重新计算：

```text
Blocks
Cut/Fill
Bridge
Tunnel
Ghost
```

松开操作：

```text
mark stale
```

---

# 43. 数据权威总结

## Terrain Adaptive

```text
Road Vertical Intent
    mode = AUTO/FIT/MANUAL

Terrain / Manual Alignment
        ↓
Final Vertical Profile
```

## Flat

```text
Road
    verticalMode = FLAT
    flatBaseElevation

RoadNode
    junction elevation
    grade separation
    clearance
        ↓
FlatRoadVerticalProfileSolver
        ↓
Derived RoadVerticalAlignment
        ↓
Generation
```

---

# 44. 不允许的数据流

禁止：

```text
Flat generated PVI
→ 用户直接编辑
→ Road 仍显示 Flat
```

禁止：

```text
Flat conflict
→ 自动切 MANUAL_PROFILE
```

禁止：

```text
Terrain changes
→ 自动改变 flatBaseElevation
```

禁止：

```text
actual clearance
→ 覆盖 required clearance
```

禁止：

```text
Network has one Flat road
→ 强制其他 Road 也 Flat
```

---

# 45. 推荐新增类

v1.0 最小新增：

```text
FlatRoadVerticalProfileSolver
FlatRoadVerticalConstraint
FlatRoadVerticalSolveResult
FlatRoadVerticalViolation
```

可选：

```text
FlatRoadVerticalModeUi
```

不建议再建大型 manager。

---

# 46. 建议修改现有类

### `Road`

新增：

```java
Double flatBaseElevation;
```

及：

```java
getFlatBaseElevation()
setFlatBaseElevation()
```

---

### `RoadVerticalMode`

保留：

```text
FLAT
FIT_TERRAIN
AUTO_SMOOTH
MANUAL_PROFILE
```

不需要为产品 UI 简化而删除内部模式。

---

### `VerticalAlignmentProfileSupport`

当前：

```text
FLAT
MANUAL_PROFILE
→ use VerticalAlignment
```

未来改成：

```text
MANUAL_PROFILE
→ authoritative RoadVerticalAlignment

FLAT
→ FlatRoadVerticalProfileSolver produced alignment
```

避免把 Flat derived alignment 误认为 Manual。

---

### `FlatRoadJunctionConflictResolver`

逐步转成：

```text
Conflict Detector
```

不再负责：

```text
FLAT → MANUAL_PROFILE
```

---

### `VerticalProfileEditor`

增加：

```text
switch vertical strategy
```

Adaptive：

```text
renderAdaptiveProfileEditor()
```

Flat：

```text
renderFlatProfileEditor()
```

不要在同一个方法中堆大量 `if (flat)`。

---

### `RoadProfileIntersectionDragEditor`

识别 Flat。

Flat 下：

```text
drag junction
→ modify junction constraint
→ solve flat profile
```

不是：

```text
directly move arbitrary Road PVI
```

---

### `RoadGradeSeparationProfileEvaluator`

方案评估加入 Flat baseline deviation。

---

# 47. Persistence

新增字段：

```json
{
  "verticalMode": "FLAT",
  "flatBaseElevation": 70.0
}
```

兼容旧 Flat 数据：

如果：

```text
verticalMode == FLAT
flatBaseElevation == null
verticalAlignment is flat
```

则加载时推断：

```text
flatBaseElevation =
verticalAlignment.firstPvi.elevation
```

然后继续兼容读取。

不要立即要求 migration 文件重写。

---

# 48. Legacy Data

规则：

```text
mode == null
alignment == null
→ existing default mode

mode == null
alignment != null
→ MANUAL_PROFILE

mode == FLAT
flatBaseElevation == null
alignment flat
→ infer Flat Base Y

mode == FLAT
flatBaseElevation != null
alignment contains transitions
→ still FLAT
```

最后一条尤其重要。

---

# 49. Validator

增加：

```text
FLAT_BASE_ELEVATION_MISSING
FLAT_TRANSITION_GRADE_EXCEEDED
FLAT_JUNCTION_CONFLICT
FLAT_CONSTRAINT_UNSATISFIABLE
```

严重程度：

### Block Build

```text
FLAT_BASE_ELEVATION_MISSING
严重 constraint contradiction
```

### Warning

```text
transition exceeds preferred slope
```

具体是否 blocking 应根据现有 maxSlope validation 统一。

---

# 50. Tests

必须增加：

### Flat 基础

```text
flatRoadWithoutJunctionsStaysAtBaseElevation
```

### Flat 单交叉

```text
flatRoadCreatesLocalTransitionForJunctionOverride
```

### 恢复水平

```text
flatRoadReturnsToBaselineAfterIntersection
```

### Flat × Adaptive

```text
adaptiveRoadYieldsToFlatAtAtGradeJunction
```

### Flat × Flat 相同 Y

```text
equalFlatRoadsConnectWithoutTransition
```

### Flat × Flat 不同 Y

```text
differentFlatRoadsReportAtGradeConflict
```

### 自定义 Junction Y

```text
flatRoadsTransitionToExplicitSharedJunctionElevation
```

### Grade separation

```text
flatRoadKeepsBaselineWhenAdaptiveRoadCanSatisfyClearance
```

### 不足距离

```text
flatTransitionReportsInsufficientDistance
```

### 数据语义

```text
derivedFlatPvisDoNotConvertModeToManual
```

### Persistence

```text
flatBaseElevationRoundTrips
```

---

# 51. MVP 范围

v1.0 必须完成：

```text
✓ 每 Road 独立选择 Adaptive / Flat
✓ Flat Base Y
✓ Flat 不允许普通 PVI 编辑
✓ 平交 Junction Y override
✓ 立交上下关系
✓ 自动局部 transition
✓ Max slope 检查
✓ Flat Profile 可视化
✓ Flat × Adaptive
✓ Flat × Flat
✓ Preview stale
✓ Persistence
```

---

# 52. v1.0 明确不做

暂不做：

```text
用户手动编辑 Transition Length
Vertical Curve K-value
多个自定义 Flat baseline 区段
Road 一半 Flat、一半 Adaptive
Superelevation
复杂立交 ramp vertical solver
自动多方案全网优化
```

这些以后再扩展。

---

# 53. 最终用户工作流

## Terrain Adaptive

```text
选择 Road
↓
纵向方式：适应地形
↓
生成
↓
计算地形
↓
纵剖面
↓
需要时调整 PVI
↓
重新计算
↓
Build
```

## Flat

```text
选择 Road
↓
纵向方式：水平道路
↓
Y = 70
↓
生成
↓
计算
↓
纵剖面显示：
Terrain
Base Y
实际道路
Intersection
↓
必要时拖交叉点 Y
↓
系统自动生成平缓过渡
↓
重新计算
↓
Build
```

---

# 54. 最终架构

```text
                     ROAD
                       │
              Vertical Strategy
                 /           \
                /             \
       TERRAIN ADAPTIVE       FLAT
              │                │
         Terrain Samples       │
              │           Base Elevation
              │                +
              │        Junction Constraints
              │                │
        Auto / Manual      Flat Profile Solver
              │                │
              └──────┬─────────┘
                     │
             Resolved Vertical Profile
                     │
          Grade / Junction Validation
                     │
                Terrain Analysis
                     │
       Cut / Fill / Bridge / Tunnel
                     │
                  Preview
                     │
                   Build
```

---

# 55. v1.0 的核心契约

最终必须长期保持以下五条：

### Contract 1

```text
Vertical Mode 是每条 Road 自己的属性。
```

### Contract 2

```text
Flat Base Y 是 Flat Road 的权威设计数据。
```

### Contract 3

```text
Flat Road 可以因为交叉约束产生局部过渡，
但不能因此失去 Flat 身份。
```

### Contract 4

```text
Flat 的内部 PVI 是 Derived Data，
用户不能把它当普通自由控制点编辑。
```

### Contract 5

```text
Terrain Adaptive 让道路适应地形；
Flat 让地形适应道路。
```

最后这一条可以作为整个 Vertical Mode 系统最简单的产品定义：

> **Terrain Adaptive：道路适应地形。**  
> **Flat：地形适应道路。**

---

# 56. 推荐实施顺序

### Phase A — 数据契约

```text
Road.flatBaseElevation
Persistence
Legacy migration
Vertical Mode UI mapping
```

### Phase B — Flat Solver

```text
FlatRoadVerticalProfileSolver
Base profile
Junction constraints
Transition generation
Grade validation
```

### Phase C — Generation

```text
Flat derived profile
→ existing generation pipeline
```

不改现有 Block Generation 架构。

### Phase D — UI

```text
Edit:
Adaptive / Flat
Base Y

Generate:
Flat profile visualization
```

### Phase E — Junction

```text
Flat × Adaptive
Flat × Flat
At-grade conflict
Grade separation
```

### Phase F — Profile Interaction

```text
禁止 Flat 普通 PVI 编辑
允许 Junction Y drag
自动 transition rebuild
```

### Phase G — Tests / Cleanup

逐步废弃：

```text
FlatRoadJunctionConflictResolver.allowConflictingRoadsToSlope()
```

并清理任何：

```text
FLAT → MANUAL_PROFILE
```

的隐式转换。

---

## v1.0 决策

**Road Vertical Mode v1.0 正式采用“每 Road 独立纵向策略”。**

用户层：

```text
适应地形
水平道路
```

Flat Road 使用：

```text
Base Y
+
Intersection Constraints
+
Automatic Smooth Transition
```

而不是自由 PVI。

两种模式最终都编译为现有生成管线可以消费的纵向 Profile，因此不需要推翻当前 Road Generation / Station / Junction 架构。