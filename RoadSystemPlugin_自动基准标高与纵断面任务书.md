# Plot Mod - 道路系统任务书：自动基准标高 + 标高提示/纵断面展示

> 面向 Cursor 的开发任务书。这是"讨论 4 个问题"里的第一份（对应第 2、4 点），第 3 点（立体交叉手动标记）是独立的第二份任务书，不在这次范围内。

---

## 0. 设计核心思路（先理解这个，再看具体实现）

现在 `RoadGenerator` 算道路高度的方式是：从这条边**第一个点的地面高度**出发，然后逐段贴着最大坡度限制往前走（`RoadSlopeUtils.computeChainedTargetHeights`）。这次要改的不是这个"逐段找平"的算法本身（它已经经过多轮验证，工作正常），而是给它换一个更聪明的"要去追的目标"：

- **现在**：链式计算追的是"这一段的真实地面高度"，起点高度 = 第一个点的真实地面高度。
- **改完之后**：链式计算追的是一条**引导线（Guide Line）**——这条线是根据整条路径的地形，用平衡算法算出来的一条"尽量贴合地形整体趋势、同时让两头挖填方都不过量"的平滑高度曲线，不是逐点照抄真实地形。

**关键设计决定：`RoadSlopeUtils.computeChainedTargetHeights` 这个函数本身不需要改一行代码**。因为它的输入本来就是"一串地面高度 + 起点高度"，只要把喂给它的"地面高度"从"真实地面采样"换成"引导线采样"，它自然就会算出一条贴着引导线走的道路高度。而后续判断挖方/填方/桥梁/隧道（`RoadConstructionEvaluator`）依然要用**真实地面高度**去比较（不能拿引导线跟自己比），这部分逻辑也不用改。也就是说这次任务的改动范围，是在"算引导线"这一个新的预处理步骤，加上"把引导线接到现有链路的正确位置"，不涉及任何已经验证过的核心算法。

---

## 1. 新增：`RoadGuideLineUtils`（引导线计算，纯函数）

新建 `com.plot.plugin.road.RoadGuideLineUtils`（不依赖 World，参考 `RoadSlopeUtils`/`EarthworkBalanceUtils` 的风格）。

### 1.1 双锚点 + 线性过渡

```java
public static List<Integer> computeGuideLine(
        List<Integer> groundHeightSamples,   // 沿路径采样的真实地面高度，跟 RoadGenerator 里现有采样方式一致
        List<Double> cumulativeDistances,    // 每个采样点距路径起点的累计距离
        float fillFactor);                   // 复用 EarthworkBalanceUtils 里"压实系数"的含义
```

算法：
1. 取路径起点一侧的样本（累计距离在 `[0, anchorWindow]` 范围内，`anchorWindow = min(totalLength * 0.2, 15.0)`，不足这个窗口就用全部样本），调用 `EarthworkBalanceUtils.findBalancedElevation` 算出 `startAnchor`；
2. 同样取终点一侧的样本，算出 `endAnchor`；
3. 引导线在每个采样点的值 = `startAnchor` 和 `endAnchor` 之间按累计距离占比线性插值，四舍五入取整。

如果路径总长度小于 `anchorWindow * 2`（路很短，起点窗口和终点窗口会重叠），直接对全部样本调用一次 `findBalancedElevation`，返回一条常量引导线，不做插值——短路径没必要强行分两段算。

### 1.2 兼容 `manualElevation`（路口手动标高）

如果路径的起点或终点节点（`RoadNode`）设置了 `manualElevation`，对应那一侧的锚点直接用这个手动值，不再调用 `findBalancedElevation` 去算——手动指定的优先级最高，这个逻辑参考现有 `computeJunctionTargetHeight` 里"先看 `manualElevation` 是否为空"的判断顺序抄一遍。

---

## 2. 接入 `RoadGenerator`

在 `calculateSegmentHeightsForEdge`（现在收集 segment 数据后调用 `RoadSlopeUtils.computeChainedTargetHeights` 的地方）之前，插入一步：

```java
List<Integer> guideLine = RoadGuideLineUtils.computeGuideLine(groundHeightSamples, cumulativeDistances, config.getFillFactor());
```
`config.getFillFactor()`：`RoadSystemConfig` 里如果还没有这个字段，参考 `EarthworkConfig.fillFactor` 的写法加一个（默认 1.1，含义一致，两个插件字段重名没关系，不需要共享同一个配置对象）。

**把 `groundHeightSamples` 替换成 `guideLine` 喂给 `computeChainedTargetHeights` 的 `groundStarts`/`groundEnds` 参数**，`manualStartHeight` 参数逻辑不变（如果用户对这条边整体设置了手动起始标高，手动值优先级依然最高，直接跳过引导线，这个判断现在应该已经存在，确认一下不要动它）。

**真实地面高度 `groundHeightSamples` 不要丢弃**，后续 `RoadConstructionEvaluator.evaluatePath` 判断挖方/填方/桥梁/隧道时，依然要拿"链式计算出来的道路目标高度"去跟"真实地面高度"比较，这部分传参不变，只是"目标高度"现在是追着引导线跑出来的结果，不再是追着真实地面跑出来的结果。

---

## 3. 标高提示与纵断面展示（UI）

### 3.1 纵断面数据

在 `RoadGenerationResult`（或者你觉得更合适的地方，只要能被 UI 层读到）里新增字段，把生成预览计算过程中已经算出来的三条曲线暴露出去，不需要重新计算：
```java
public List<Double> profileDistances;      // 沿路径累计距离
public List<Integer> profileGroundHeights; // 真实地面高度
public List<Integer> profileGuideLine;     // 引导线
public List<Integer> profileTargetHeights; // 最终道路目标高度（链式计算结果）
```

### 3.2 纵断面图（新增 UI 组件）

新建 `com.plot.plugin.road.RoadLongitudinalProfileRenderer`，参考 `RoadCrossSectionPreviewRenderer` 的 `ImDrawList` 绘制风格：横轴是沿路径的累计距离，纵轴是高度，画三条线（地面高度用一种颜色、引导线用虚线或者另一种颜色、最终道路目标高度用第三种颜色/加粗），可以参考常见"纵断面图"的视觉习惯（地面线用棕色系、道路线用灰色/道路材质对应色）。

接入位置：`RoadGeneratePanel`（生成 Tab），在"计算预览"之后，`lastGenerationResult` 有值时，展示这个纵断面图（放在已有的挖填方/桥隧统计文字下方，用 `ImGui.collapsingHeader` 包一下，默认展开或收起都行，你自己判断哪个体验更好）。

### 3.3 当前地形/建议标高的文字提示

在 `RoadEditPanel`（编辑 Tab）里，选中一条道路边之后，加一行文字展示这条边**起点**和**终点**的"当前地形标高 / 引导线建议标高"对比，比如：
```
起点：现状地形 Y=64，建议道路标高 Y=66
终点：现状地形 Y=70，建议道路标高 Y=68
```
这两个数字直接从 3.1 节暴露的 `profileGroundHeights`/`profileGuideLine` 数组的首尾取值即可，不需要额外计算。**这一步依赖"计算预览"已经跑过一次**（数据来自 `lastGenerationResult`），如果用户还没点过"计算预览"，这里显示提示文字"请先计算预览查看标高建议"，不要报错或者显示空白。

---

## 4. 需要新建/修改的文件清单

**新建：**
```
com/plot/plugin/road/RoadGuideLineUtils.java
com/plot/plugin/road/RoadLongitudinalProfileRenderer.java
```

**修改：**
```
com/plot/plugin/config/RoadSystemConfig.java   -- 新增 fillFactor 字段（如果还没有）
com/plot/plugin/road/RoadGenerator.java        -- calculateSegmentHeightsForEdge 接入引导线计算，RoadGenerationResult 新增纵断面数据字段
com/plot/plugin/road/ui/RoadGeneratePanel.java -- 接入纵断面图展示
com/plot/plugin/road/ui/RoadEditPanel.java     -- 接入起点/终点标高提示文字
src/main/resources/assets/plot/lang/en_us.json
src/main/resources/assets/plot/lang/zh_cn.json
```

**不允许修改**：`RoadSlopeUtils.java`（不新增方法之外的任何改动，这次不需要动它）、`RoadConstructionEvaluator.java`、`EarthworkBalanceUtils.java`（只调用，不修改）、`RoadJunctionGenerator.java`/`RoadJunctionGeometry.java`（路口高度计算逻辑本次不涉及）。

---

## 5. 验收标准

1. `computeGuideLine`：构造一段地形数据，起点区域普遍偏低、终点区域普遍偏高，验证算出的引导线在起点附近接近起点区域的平衡标高、终点附近接近终点区域的平衡标高，且中间是平滑过渡（相邻采样点之间的引导线高度变化应该是单调的，不应该有忽高忽低的抖动）。
2. 路径总长度小于 `anchorWindow * 2` 时，验证退化成整条路径统一用一个平衡标高（常量引导线）。
3. 起点/终点节点设了 `manualElevation` 时，验证对应锚点直接使用这个值，不再调用平衡算法。
4. 回归验证：用一批之前测试过的地形数据（现有 `RoadSlopeUtilsTest`/`RoadGeneratorTest` 里已经有的用例），确认接入引导线之后，`RoadConstructionEvaluator` 判断挖方/填方/桥梁/隧道的结果依然是拿"最终道路目标高度 vs 真实地面高度"比较，没有误用引导线去比较。
5. 生成预览之后，`RoadGenerationResult` 的纵断面三个数组长度应该一致，且和实际生成的方块范围能对上（抽查几个点，`profileTargetHeights` 里的值应该等于该点实际落地的道路方块高度）。
6. UI 层：`RoadEditPanel` 在没有计算过预览时显示提示文字而不是报错；计算预览之后正确显示起点/终点标高对比。

---

## 6. 实施顺序

1. `RoadGuideLineUtils` 纯函数 + 单元测试，独立开发独立验证，不涉及 `RoadGenerator`。
2. 接入 `RoadGenerator`，用现有测试套件做一次完整回归，确认引导线接入之后现有道路生成的效果（尤其是挖填方统计、桥梁隧道判断）没有出现意外的大幅变化——理论上引导线只是让目标高度曲线更平滑，不应该显著改变"要不要架桥/挖隧道"这类决策的结果，如果实机测试发现变化很大，需要回头检查是不是引导线算法本身有问题。
3. UI 部分（纵断面图 + 标高提示文字），最后做，不影响核心计算逻辑。

第 2 步做完之后建议你自己在游戏里对比一下改动前后同一条路的生成效果，这是这次任务里唯一需要人工判断"感觉对不对"的地方，其余都可以靠单元测试和构建验证。
