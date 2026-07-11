# Plot Mod - 道路系统第三阶段任务书：路肩边坡 / 坡长限制 / 自相交误判修复

> 面向 Cursor 的开发任务书。本次范围严格限定为下面三项，**不要**顺带实现"高程规划预处理"或"立体交叉/立交桥"，这两项已确认是更大的架构改动，会在后续单独立项。写完之后请对照第 5 章验收标准逐条自查。

---

## 0. 背景

当前道路系统（`com.plot.plugin.road` 包）已经跑通了"认领中心线 → 拓扑打断 → 坡度找平 → 路口生成 → 预览 → 落地撤销"这条主链路。这次要补三个具体缺口：

1. **路肩没有边坡**：路面高于/低于周边地形时，现在只是铺一条固定高度的碎石带，两侧和地形之间是断崖，不是真实的填方路基/挖方路堑。
2. **坡度没有连续长度限制**：现在的找平算法只要不超过 `maxSlope` 就会一直按最大坡度爬升/下降，没有上限，可能出现一整段几十格长、全程贴着最大坡度的陡坡。
3. **同一条道路的自身线段被误判为相交**：画一条来回拐弯（比如盘山公路）的路径，认领时如果路径的两段几何上靠得很近，会被 `RoadNetworkBuilder.detectAndSplitIntersections()` 误判成"两条路相交"，强行在那里创建一个不该存在的路口节点。

---

## 任务 A：路肩边坡（填方/挖方自动找坡）

### 现状
`RoadGenerator.generateShoulderBlocks()`（约第 687 行）目前的做法：在路面两侧偏移出路肩边界，沿边界每隔 1 格采样一次目标高度 `targetY`（跟路面同高），调用 `placeSidewalkStrip(result, point, shoulderWidth, targetY, blockId, projectionHandler)` 铺一条固定宽度、固定高度的碎石带，铺完即止，不管这个高度跟旁边实际地形（`getTopHeight(world, pos)`）差多少。

### 要做的事

**A1. 新增边坡坡度比配置**

在 `RoadSystemConfig`（参考现有 `bridgeThreshold`/`tunnelThreshold`/`shoulderWidth` 字段的写法）新增：
```java
private float fillSlopeRatio = 1.5f;  // 填方边坡坡度比：水平 1.5 格 降/升 1 格
private float cutSlopeRatio = 1.0f;   // 挖方边坡坡度比
private String fillSlopeMaterial = "material.plot.gravel";  // 填方边坡材质（复用现有 material key 体系）
private String cutSlopeMaterial = "";  // 挖方边坡材质，空字符串表示"保留原地形材质，不替换"
```
带上对应的 getter/setter，setter 里做合理范围校验（比如坡度比限制在 `0.5f` 到 `5.0f` 之间，参考现有 `setShoulderWidth` 的 `Math.max/Math.min` 写法）。

`RoadEdge` 不需要加对应的按边覆盖字段——边坡坡度是工程规范性质的全局参数，不像宽度/材质那样需要逐条路单独定制，这次先只做网络级默认值。

**A2. 新增边坡剖面计算函数**

在 `RoadSlopeUtils`（这个类现在是纯函数、不依赖 World，继续保持这个风格）新增：
```java
/**
 * 计算从路肩边缘开始，按给定坡度比向外延伸、直到与地形高度相交为止的边坡剖面。
 * 返回值：每一步的 (水平偏移量, 目标高度) 序列，第一个元素是路肩边缘本身。
 * maxHorizontalRun：安全上限，超过这个水平距离还没找到交汇点就强制停止（避免地形数据异常导致死循环，比如目标高度和地面高度永远不重合的极端情况）。
 */
public static List<int[]> computeSlopeProfile(
        int shoulderEdgeHeight,
        int direction,          // +1 表示往下挖（cut），-1 表示往上填（fill）——注意实际方向需要在调用处根据 targetY 和地面高度的相对关系判断，这里的参数含义由调用方决定，写清楚注释
        java.util.function.IntUnaryOperator groundHeightAtOffset, // 输入：距离路肩边缘的水平偏移（格），输出：该处地形高度
        float slopeRatio,
        int maxHorizontalRun) {
    ...
}
```
算法：从偏移 0（路肩边缘，高度 = `shoulderEdgeHeight`）开始，每往外移动 `slopeRatio` 格，目标高度变化 1 格（填方是往地面方向下降，挖方是往地面方向上升），每一步都用 `groundHeightAtOffset` 查询该偏移处的实际地形高度，一旦"当前边坡目标高度"和"该处地形高度"相等（或者边坡高度已经越过地形高度，说明这一步已经与地形重合），就停止并返回到这一步为止的序列。超过 `maxHorizontalRun` 还没交汇，就在当前进度截断返回（避免异常地形导致边坡无限延伸）。

给这个函数配一个不依赖 World 的单元测试（放进 `RoadSlopeUtilsTest`），用手工构造的 `IntUnaryOperator`（比如返回一个固定斜率的假地形）验证：填方情况下能正确找到下降交汇点，挖方情况下能正确找到上升交汇点，以及"地形完全平坦、边坡目标高度和路肩齐平"的退化情况（此时应该 0 步就返回，只有起点一个元素）。

**A3. 改造 `generateShoulderBlocks`，替换成边坡版本**

新增一个方法 `generateSlopeBatterBlocks`（保留原 `generateShoulderBlocks` 不动或者改造成调用新方法，你判断哪种改法对现有调用方影响更小），在原来"铺完固定宽度碎石带"之后，对路肩外侧边缘再做一次边坡展开：

- 对边界线上每个采样点（复用现有 `RoadGeometryUtils.interpolatePolylineByNormalizedDistance` 采样逻辑），先判断这一点路肩外缘的 `targetY` 和 `getTopHeight(world, 该点对应的BlockPos)` 的关系：
  - `targetY > groundHeight`（路面比地形高，填方）→ 用 `fillSlopeRatio` + `fillSlopeMaterial` 调 `computeSlopeProfile`，从路肩边缘往外，按剖面结果逐格垂直堆方块（从地面到目标高度之间整段填实，不是只放表层一层，参考现有 `placeSidewalkStrip`/`generateRoadBlocksFromBoundaries` 里对"整列填充"的写法）；
  - `targetY < groundHeight`（路面比地形低，挖方）→ 用 `cutSlopeRatio`，同样逐格垂直方向从目标高度到地面高度之间**清空**（挖空，替换成空气或者保留原样具体看 `cutSlopeMaterial` 是否为空——为空表示不铺任何材质、只是把多余方块挖掉露出原地形），边坡侧面如果 `cutSlopeMaterial` 非空则铺一层该材质模拟护坡面；
  - `targetY == groundHeight`（基本齐平）→ 跳过，不需要边坡。
- 边坡范围内新增/清除的方块也要走 `recordBlock`（参考现有 `RoadGenerator.recordBlock` 的调用方式），保证落地时能正确纳入 `GenerateRoadCommand` 的撤销记录。

**A4. UI**

在 `RoadSystemPlugin.renderAdvancedEngineeringSettings()` 里，`include_shoulder` 复选框展开后的区域，新增两个滑条（参考同一方法里 `shoulderWidth` 滑条的写法）：填方边坡坡度比、挖方边坡坡度比，范围 `0.5f` 到 `5.0f`，格式化成 `"1:%.1f"` 这种直观的比例展示。材质选择复用已有的 `renderConfigMaterialCombo` 模式。语言文件（`en_us.json`/`zh_cn.json`）记得补上新增文案 key。

---

## 任务 B：单一坡度最长连续长度限制

### 现状
`RoadSlopeUtils.computeChainedTargetHeights()` 和 `RoadGenerator.calculateSegmentHeightsForEdge()` 里的找平循环，只要某一段的坡度不超过 `edge.getEffectiveMaxSlope(...)`，就会一直贴着这个上限爬升/下降，没有"爬了多远"的累计概念。

### 要做的事

**B1. 新增配置**

`RoadSystemConfig` 新增：
```java
private double maxContinuousSlopeLength = 30.0; // 单一方向连续爬坡/下坡的最大长度（米），超过后强制插入缓坡段
private double relaxedSlopeLength = 5.0;         // 缓坡段的长度（米）
private float relaxedSlopePercent = 1.0f;        // 缓坡段允许的坡度上限（百分比），应明显小于 maxSlope
```
setter 校验：`relaxedSlopePercent` 必须小于当前 `maxSlope`，否则缓坡段起不到"喘口气"的效果，不满足时可以直接钳制成 `maxSlope` 的一半。

**B2. 改造 `RoadSlopeUtils.computeChainedTargetHeights`**

这是纯函数层，改动收敛在这一个方法里最干净。当前签名不带"连续爬坡长度"的状态跟踪，需要新增一个重载（**保留原方法签名不变，新增一个带累计长度控制参数的重载**，避免破坏已有调用方和已有测试）：

```java
public static List<Integer> computeChainedTargetHeights(
        List<Double> segmentDistances,
        List<Integer> groundStarts,
        List<Integer> groundEnds,
        List<Float> maxSlopePercents,
        Integer manualStartHeight,
        double maxContinuousSlopeLength,
        double relaxedSlopeLength,
        float relaxedSlopePercent) {
    ...
}
```

算法思路：在原来逐段调用 `computeTargetEndHeight` 的循环基础上，维护两个状态量——`continuousDirection`（当前连续爬坡的方向：+1 上坡 / -1 下坡 / 0 无方向）和 `continuousRunLength`（当前方向已经累计的长度）。每算完一段：
- 如果这一段的实际升降方向和 `continuousDirection` 一致，`continuousRunLength += segmentDistance`；方向变化（比如由上坡转下坡，或者变成基本水平）则重置 `continuousRunLength = 0` 并更新 `continuousDirection`。
- 如果 `continuousRunLength` 达到或超过 `maxContinuousSlopeLength`，接下来不能再传入原来那个 `maxSlopePercents.get(i)`，而是要**强制插入一段缓坡**：在当前位置往后 `relaxedSlopeLength` 的距离内，把允许坡度压到 `relaxedSlopePercent`（如果这一段距离比一个 segment 短，可能需要把该 segment 拆分成"缓坡部分 + 剩余部分"两段来分别计算，具体拆分粒度你可以按现有 segment 的采样密度来定，不需要做到米级精确，允许有一点近似）；缓坡段跑完之后 `continuousRunLength` 清零，重新开始累计。

**这个算法本身有一定复杂度，建议先写好纯函数版本 + 覆盖典型场景的单元测试（长距离持续上坡应该被正确打断成"陡坡+缓坡+陡坡..."的交替序列；短距离爬坡不应该触发缓坡段；下坡和上坡的连续长度应该分开累计，不能混算），确认逻辑正确后再接入 `RoadGenerator`。**

**B3. 接入 `RoadGenerator`**

`calculateSegmentHeightsForEdge` 目前是自己在方法内部逐段调用 `computeTargetEndHeight`（没有直接用 `computeChainedTargetHeights`，两者是重复实现的关系）。这次顺手把 `calculateSegmentHeightsForEdge` 改成基于新的 `computeChainedTargetHeights` 重载来算（先把所有 segment 的距离/起止地面高度/坡度上限收集成 List，一次性调用链式计算拿到 `targetEnds`，再回填进 `SegmentHeightInfo` 列表），避免两处独立维护找平逻辑、以后改一处忘了改另一处。

**B4. UI**

`renderAdvancedEngineeringSettings()` 里新增这三个参数的滑条，放在坡度相关设置附近（如果编辑 Tab 已经有单独的"坡度"分组就放那里，没有的话放高级设置里）。

---

## 任务 C：同一条道路自身线段的误判修复

### 现状
`RoadEdge` 没有任何字段记录"这条边最初是从哪一次认领动作产生的"。`RoadNetworkBuilder.adoptShape()` 认领一条路径时，如果这条路径自身几何上有两段靠得很近（比如盘山公路来回拐弯），认领后紧接着调用的 `detectAndSplitIntersections()` 会把这两段也当成"两条不同的路相交"处理，错误生成一个路口节点。

### 要做的事

**C1. 给 `RoadEdge` 加来源分组标识**
```java
private String sourceRoadId;  // 认领时生成的分组 ID，同一次 adoptShape() 产生的所有边共享同一个值
```
带 getter/setter。**这个字段要参与 JSON 序列化**（`RoadNetwork` 现有的 DTO 转换逻辑那里记得加上，否则存盘读盘之后这个分组信息会丢，后续的求交逻辑又会失效）。

**C2. `RoadNetworkBuilder.adoptShape()` 里生成并传播这个 ID**

`adoptShape` 一开始 `network.createEdge(...)` 之后，生成一个 `UUID.randomUUID().toString()` 赋给 `sourceRoadId`。这次认领内部调用 `detectAndSplitIntersections` 时，如果这条新边自己因为跟别的边（不管是别的道路还是自己认领的其它段）相交被打断，`splitEdgeAtNode` 产生的两个子边都要继承同一个 `sourceRoadId`（现有的 `copyProperties(source, target)` 方法里加一行 `target.setSourceRoadId(source.getSourceRoadId())`）。

**C3. `detectAndSplitIntersections` 里跳过同源边**

在 `findIntersections(edgeA, edgeB)` 拿到交点、准备处理之前，加一个前置判断：
```java
if (edgeA.getSourceRoadId() != null
        && edgeA.getSourceRoadId().equals(edgeB.getSourceRoadId())) {
    continue; // 同一次认领产生的边，不视为需要连接的路口
}
```
放在现有 `isNearEdgeEndpoint`/`alreadyConnectedAt` 判断的前面即可。

**注意边界情况**：如果用户后续手动认领了另一条路径，这条新路径恰好也经过了老路径自相交的那个位置——只要这条新路径的 `sourceRoadId` 跟老路径不同，仍然应该正常打断生成路口，这是符合预期的行为，`sourceRoadId` 只用来排除"同一条路和自己"的误判，不影响正常的路网相交逻辑。

**已知的显式不做的事**（避免 Cursor 过度设计）：不需要支持"同一条路首尾相接形成环岛"这种场景——如果用户确实想要一条路自己首尾相连，这次先允许它被当成两个独立端点处理（不强行连接，也不强行阻止），环岛作为独立功能以后再说。

---

## 1. 需要修改/新建的文件清单

**修改：**
```
com/plot/plugin/config/RoadSystemConfig.java     -- 新增 6 个配置字段（边坡比×2+材质×2、坡长限制×3）
com/plot/plugin/road/RoadSlopeUtils.java          -- 新增 computeSlopeProfile、computeChainedTargetHeights 重载
com/plot/plugin/road/RoadGenerator.java           -- 边坡生成逻辑、calculateSegmentHeightsForEdge 改造
com/plot/plugin/road/model/RoadEdge.java          -- 新增 sourceRoadId 字段
com/plot/plugin/road/model/RoadNetwork.java       -- DTO 序列化新增 sourceRoadId
com/plot/plugin/road/RoadNetworkBuilder.java      -- adoptShape 生成 ID、copyProperties 传播 ID、求交前置跳过判断
com/plot/plugin/RoadSystemPlugin.java             -- 新增配置项对应的 UI 滑条
src/main/resources/assets/plot/lang/en_us.json    -- 新增文案
src/main/resources/assets/plot/lang/zh_cn.json    -- 新增文案
```

**不允许修改**（跟之前几轮任务书的约束一致）：`Shape.java`/`Project.java`/`ProjectSnapshot.java`/`Layer.java`/`ToolManager.java`/`CommandManager.java` 的既有行为；`RoadJunctionGenerator`/`RoadNetworkGenerator` 的路口高度计算逻辑（任务 A/B/C 都不涉及路口，不要顺带改动）。

---

## 2. 验收标准

1. `computeSlopeProfile`：给一个固定坡度的假地形（比如 `offset -> 64 - offset`），路肩边缘高度 70，填方坡度比 2.0，验证返回的剖面序列每一步高度差 1、水平间隔 2，且在正确的偏移量处与假地形高度重合并停止。
2. 平坦地形（`groundHeightAtOffset` 恒定等于 `shoulderEdgeHeight`）时，`computeSlopeProfile` 应该只返回起点一个元素，不产生多余的边坡方块。
3. `computeChainedTargetHeights` 新重载：构造一段连续 50 米、地形持续抬升、坡度稳定贴着 `maxSlope` 上限的输入，验证在累计到 `maxContinuousSlopeLength` 附近，实际坡度会被压到 `relaxedSlopePercent` 附近持续 `relaxedSlopeLength` 左右，然后恢复可以贴 `maxSlope` 继续爬升。
4. 短距离（小于 `maxContinuousSlopeLength`）的爬坡不应该触发任何缓坡段，跟旧行为（不带这几个新参数的调用）结果一致。
5. 画一条自身会近距离拐回来两次的折线（模拟盘山公路的发夹弯），认领后 `network` 里不应该在拐弯靠近处出现额外生成的 `RoadNode`；同样两条**不同**折线在同一位置相交，认领后应该照常生成路口节点（回归测试，确认没有把正常求交也一起跳过了）。
6. `RoadNetwork.toJson()`/`fromJson()` 往返后，`sourceRoadId` 字段应该完整保留。
7. 现有的 `RoadNetworkBuilderTest`/`RoadNetworkGeneratorTest`/`RoadSlopeUtilsTest` 等已有测试全部保持通过，不因为这次改动出现回归。

---

## 3. 实施顺序

1. 任务 C（自相交修复）：改动范围最小、风险最低，建议第一个做，做完跑一遍现有测试确认没有破坏正常求交逻辑。
2. 任务 B（坡长限制）：纯函数层先写完并测好，再接入 `RoadGenerator`。
3. 任务 A（路肩边坡）：依赖 B 的高度计算结果（边坡要基于找平之后的 `targetY`），放在最后做。

每完成一项，运行一次现有构建/测试套件，确认没有破坏之前已经验收通过的功能，再进入下一项。
