# Plot Mod - 道路系统重构任务书：施工决策层拆分 + 桥梁/隧道成本函数

> 面向 Cursor 的开发任务书。这是一次架构性重构，不是新功能开发，**核心要求是不改变现有生成结果的整体行为**（除了桥梁/隧道的触发条件从"固定阈值"变成"成本比较"这一点是有意为之的改动，其它部分——挖填坡度找平、路面/人行道/路肩边坡生成——都不动）。

---

## 0. 背景

`RoadGenerator.java` 现在 1247 行、40 多个方法，混杂了"要不要架桥/挖隧道"这种决策逻辑和"怎么摆方块"这种执行逻辑。这次任务把"决策"从这个类里拆出来，做成一个独立、纯函数、可单测的评估层，替换掉现在 `detectBridges`/`detectTunnels` 里"高度差超过固定阈值就架桥/挖隧道"的写法，改成成本比较。

**这次任务的范围明确限定为本文档描述的内容，不要顺带做以下事情**（这些是更大的架构讨论过的方向，但都不在这次任务范围内）：
- 不要重构整个道路系统成"六层管线"架构；
- 不要引入跨插件的通用框架（`LinearInfrastructureEngine` 之类）；
- 不要改动挖填坡度找平（`RoadSlopeUtils`）、路面/边坡/路口生成的现有逻辑；
- 不要改动 `TerrainSampler` 接口的现有方法签名（可以新增方法，不要动已有的）。

---

## 1. 新增：`RoadConstructionType` 枚举

新建 `com.plot.plugin.road.RoadConstructionType`：
```java
public enum RoadConstructionType {
    ROAD,     // 正常贴地铺设
    CUT,      // 挖方（地面高于目标高度，但差值不足以触发隧道）
    FILL,     // 填方（地面低于目标高度，但差值不足以触发架桥）
    BRIDGE,   // 架桥
    TUNNEL    // 挖隧道
}
```
这个类型目前主要用于**统计和判断**（比如生成结果统计里显示"这条路 X 米是桥梁、Y 米是隧道、Z 米是普通路面"），暂时不需要每种类型对应完全不同的方块生成方法——`ROAD`/`CUT`/`FILL` 三种目前的方块生成方式是一样的（都是走现有的贴地找平+挖填逻辑，只是叫法不同），只有 `BRIDGE`/`TUNNEL` 需要触发现有的 `generateBridgeStructures`/`generateTunnelStructures`。不要为了"看起来更完整"而给 `ROAD`/`CUT`/`FILL` 三种类型分别实现不同的方块生成路径，这次先不做这个区分，避免过度设计。

---

## 2. 新增：`RoadConstructionEvaluator`（纯函数决策层）

新建 `com.plot.plugin.road.RoadConstructionEvaluator`，风格参照 `RoadSlopeUtils`（不依赖 World，方便单测）。

### 2.1 成本函数

```java
public static RoadConstructionType evaluateSegment(
        double segmentDistance,
        int groundHeight,
        int targetHeight,
        RoadConstructionCostConfig costConfig);
```

成本比较逻辑：
- `heightDifference = targetHeight - groundHeight`（正数表示目标比地面高，需要考虑架桥或填方；负数表示目标比地面低，需要考虑挖隧道或挖方）；
- **架桥/填方的选择**（`heightDifference > 0` 时）：
  - `fillCost = |heightDifference| * segmentDistance * costConfig.getFillCostPerVolume()`（近似体积 × 单位填方成本）；
  - `bridgeCost = costConfig.getBridgeBaseCost() + segmentDistance * costConfig.getBridgeCostPerLength()`（架桥有一个固定基础成本 + 按长度线性增加的成本，模拟"桥梁哪怕很短也有起步成本，但填方成本会随高度差线性甚至更快增长"这个直觉）；
  - 如果 `heightDifference <= costConfig.getMinimumConsiderationHeight()`（新配置项，默认给个较小的值比如 2，表示"高度差很小的时候不值得比较成本，直接判 FILL 或 ROAD，避免噪声"），跳过成本比较，直接返回 `heightDifference > 某个更小的阈值(比如1) ? FILL : ROAD`；
  - 否则比较 `fillCost` 和 `bridgeCost`，谁小选谁（`fillCost <= bridgeCost` → `FILL`，否则 → `BRIDGE`）。
- **挖隧道/挖方的选择**（`heightDifference < 0` 时）：对称的逻辑，`cutCost` vs `tunnelCost`，`tunnelBaseCost`/`tunnelCostPerLength` 同理新增到配置里。
- `heightDifference` 接近 0（在 `minimumConsiderationHeight` 内）→ `ROAD`。

### 2.2 连续段平滑（重要，避免决策抖动）

如果只是逐段独立比较成本，地形稍微有点起伏就可能导致"这段 FILL、下一段 BRIDGE、再下一段又变回 FILL"这种来回跳变，实际生成出来会是一段路面里插了几个孤立的桥墩，很难看。所以需要加一个**最小连续长度**限制：

```java
public static List<RoadConstructionType> evaluatePath(
        List<Double> segmentDistances,
        List<Integer> groundHeights,
        List<Integer> targetHeights,
        RoadConstructionCostConfig costConfig,
        double minimumRunLength);
```

算法：先逐段调用 `evaluateSegment` 得到一个初步序列，然后做一次平滑处理——如果某个类型（尤其是 `BRIDGE`/`TUNNEL`）连续出现的累计长度小于 `minimumRunLength`，就把这一小段"吸收"进相邻更长的那一段类型里（比如前后都是 `FILL`，中间夹了一小段 `BRIDGE` 判定但累计长度不够，就把这一小段也判成 `FILL`）。这个平滑算法思路上和我们之前给坡度限制做的"连续爬坡长度"处理是同一类问题（参考 `RoadSlopeUtils.computeChainedTargetHeights` 里状态累计+回溯调整的写法），可以借鉴那个实现思路，但这次是新写一个独立的函数，不要直接调用坡度那边的函数（两者数据结构不一样，不要硬凑）。

### 2.3 `RoadConstructionCostConfig`

新增到 `RoadSystemConfig`（不要新建一个独立的配置类，直接在现有配置里加字段，保持配置项集中在一处）：
```java
private double fillCostPerVolume = 1.0;
private double bridgeBaseCost = 15.0;
private double bridgeCostPerLength = 0.8;
private double cutCostPerVolume = 1.2;   // 挖方通常比填方成本略高（要清理更多方块）
private double tunnelBaseCost = 25.0;    // 隧道基础成本比桥更高
private double tunnelCostPerLength = 1.5;
private double minimumConsiderationHeight = 2.0;
private double minimumConstructionRunLength = 3.0; // 米，对应 2.2 节的平滑参数
```
这几个具体数值只是给个合理的初始默认值，不需要你去论证"到底应该是多少"，重点是**结构**要对（有这几个可调参数、能影响决策结果），具体数值以后可以再调。setter 做基本的非负校验即可，参考现有字段的写法。

**保留兼容性**：`bridgeThreshold`/`tunnelThreshold` 这两个现有字段**不要删除**，可以保留作为"极端情况下的硬性上限"——比如不管成本比较结果如何，`heightDifference` 超过 `bridgeThreshold`/`tunnelThreshold` 的绝对值时强制判定为 `BRIDGE`/`TUNNEL`（避免成本函数在极端地形下算出反直觉的结果，比如高度差几十格却因为某种参数设置被判成"填方"，现实中不可能填这么高）。这是一个安全兜底，不是核心逻辑。

---

## 3. 集成到 `RoadGenerator`

`RoadGenerator` 里现有的 `detectBridges`/`detectTunnels` 两个方法**替换成调用新的 `RoadConstructionEvaluator.evaluatePath`**，得到每段的 `RoadConstructionType` 后，在 `RoadGenerator` 内部把标记为 `BRIDGE`/`TUNNEL` 的连续段重新组装成现有的 `BridgeSegment`/`TunnelSegment` 列表（这两个类不用动），继续喂给原有的 `generateBridgeStructures`/`generateTunnelStructures`，这两个方法本身不需要改。

也就是说：**这次改动只影响"怎么判断该不该架桥/挖隧道"这一步，"架桥/挖隧道具体怎么摆方块"完全不动**，把改动面控制在最小范围。

`RoadGenerationResult` 增加一个字段：
```java
public Map<PathSegmentKey, RoadConstructionType> constructionTypes; // 或者你觉得更合适的 key 方式，用于后续统计展示
```
具体 key 的类型你可以按现有 `RoadGenerationResult` 里怎么标识路径段来定，只要能在 UI 层统计出"这条路多少米是桥、多少米是隧道、多少米是普通路面"就行，不强制要求具体数据结构长什么样。

UI 层（`RoadSystemPlugin` 生成预览的统计区域）加一行展示这几个数字，参考现有"总挖方/总填方"统计的展示写法。

---

## 4. 需要新建/修改的文件清单

**新建：**
```
com/plot/plugin/road/RoadConstructionType.java
com/plot/plugin/road/RoadConstructionEvaluator.java
```

**修改：**
```
com/plot/plugin/config/RoadSystemConfig.java   -- 新增 7 个成本相关配置字段（第 2.3 节）
com/plot/plugin/road/RoadGenerator.java        -- detectBridges/detectTunnels 替换成调用新评估层，其它方法不动
com/plot/plugin/RoadSystemPlugin.java          -- 生成预览统计区域新增桥/隧道/路面里程展示
src/main/resources/assets/plot/lang/en_us.json -- 新增文案
src/main/resources/assets/plot/lang/zh_cn.json -- 新增文案
```

**不允许修改**：`RoadSlopeUtils.java`/`RoadGeometryUtils.java`/`RoadJunctionGenerator.java`/`RoadJunctionGeometry.java`/`TerrainSampler.java`（不新增方法之外的任何改动）、`generateBridgeStructures`/`generateTunnelStructures`/`generateRoadBlocksFromBoundaries`/`generateSlopeBatterBlocks` 这几个方法体本身。

---

## 5. 验收标准

1. 用一批地形数据（高度差在 `minimumConsiderationHeight` 以内），验证 `evaluateSegment` 返回 `ROAD`。
2. 高度差明显但成本比较后 `fillCost < bridgeCost` 的场景，验证返回 `FILL`；反过来调大 `bridgeCostPerLength`（或者调小 `fillCostPerVolume`）应该让原本判 `FILL` 的场景改判 `BRIDGE`——这个测试用来验证成本比较确实在起作用，不是摆设。
3. 构造一段"大部分是 FILL，中间夹一小段（累计长度小于 `minimumConstructionRunLength`）被判成 BRIDGE"的输入，验证 `evaluatePath` 平滑处理后，这一小段被吸收成了 `FILL`，不再是孤立的桥梁段。
4. 高度差超过 `bridgeThreshold`/`tunnelThreshold` 的极端场景，不管成本比较结果如何，验证最终判定强制是 `BRIDGE`/`TUNNEL`（第 2.3 节的安全兜底）。
5. 回归测试：用一段之前（旧的固定阈值逻辑下）会触发桥梁的地形数据，配上跟旧阈值等价的成本配置（比如把 `bridgeBaseCost`/`bridgeCostPerLength` 调到几乎总是选桥梁），验证新逻辑在等价配置下和旧逻辑判断结果一致，确认这次重构没有在默认场景下产生令人意外的结果变化。
6. 现有的道路系统测试套件（`RoadSlopeUtilsTest`、`RoadNetworkBuilderTest`、`RoadNetworkGeneratorTest` 等）全部保持通过。

---

## 6. 实施顺序

1. 第 1、2 章：`RoadConstructionType` + `RoadConstructionEvaluator` 纯函数层，写完先跑测试确认成本比较和平滑逻辑都对，这一步完全不需要碰 `RoadGenerator`。
2. 第 3 章：接入 `RoadGenerator`，替换 `detectBridges`/`detectTunnels` 的调用方式，跑一遍现有测试确认没有破坏正常生成流程。
3. UI 统计展示，最后做，不影响核心逻辑。

完成后实机测试一下：画一条明显有起伏的路（有几段该架桥、有几段该挖隧道的那种），对比这次改动前后的生成效果，确认桥梁/隧道的位置合理、没有出现"一路上散布着一堆孤立小桥墩"这种因为决策抖动导致的难看结果。
