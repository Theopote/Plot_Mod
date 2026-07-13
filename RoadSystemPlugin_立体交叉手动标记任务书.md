# Plot Mod - 道路系统任务书：立体交叉（跨越/穿越）手动标记

> 面向 Cursor 的开发任务书。这是"讨论 4 个问题"里的第二份，对应第 3 点（平面上相交但实际是一条路跨过另一条路，用户手动标记，系统自动算出所需净空高度）。第一份任务书（自动基准标高 + 纵断面展示）已经实现完成，这次任务可以在其基础上继续，但**不依赖**第一份任务书的引导线机制，是相对独立的功能。

---

## 0. 设计思路

现在的拓扑规则是"两条路只要在平面上相交，就一定打断、生成共享路口节点，所有汇聚道路在这个节点取平均高度拼接"（`RoadGenerator.computeJunctionTargetHeight`）。这次要加一个例外：**用户可以把某个路口节点标记为"立体交叉"，指定其中一条道路在这个点应该跨越（架高）通过，不参与正常的路口高度拼接**。

**关键设计决定**：不改变现有的拓扑打断逻辑（相交处依然会打断、生成共享节点，这部分继续复用现有机制，不需要新增"不打断"的例外路径），只是在**高度计算**这一步，让被标记的那条道路在这个节点绕开正常的"取平均值"逻辑，改成"取其它道路在此处的高度 + 净空 = 强制标高"。至于"跨越处要不要长得像座桥"，直接依赖现有的桥梁判定机制（`RoadConstructionEvaluator`）——强制拉高之后，目标高度和真实地面/其它道路的高度差自然会变大，现有的桥梁生成流程会自动接管，**不需要新写"如何画一座跨线桥"这种造型代码**。

---

## 1. 数据模型：`RoadNode` 新增字段

**范围收窄（这次讨论后确定）**：只处理"标准四路交叉"——一个节点正好连接 4 条边，这 4 条边正好属于 2 个不同的 `roadId`，每个 `roadId` 正好贡献 2 条边（一条道路直行穿过，两侧各一条子边）。度数不是 4、或者涉及的 `roadId` 种类不是 2 的节点，一律不提供立体交叉功能，不需要考虑更复杂的多路汇聚场景。

```java
private boolean gradeSeparated = false;  // 是否把这个节点标记为立体交叉（而不是正常路口）
private String elevatedRoadId;           // 哪条道路（roadId）在此处跨越通过；null 表示"已标记交叉，但由程序自动判断哪条在上"
private Double crossingClearance;        // 净空高度（格），null 表示使用 RoadSystemConfig 里的默认值
```
**注意 `gradeSeparated` 和 `elevatedRoadId` 是两个独立的状态**，不要合并成一个字段：`gradeSeparated=false` 表示这是正常路口（沿用现有平均值拼接逻辑）；`gradeSeparated=true` 且 `elevatedRoadId=null` 表示"用户确定要做立体交叉，但没有指定哪条在上，交给程序按第 2.1a 节的规则自动判断"；`gradeSeparated=true` 且 `elevatedRoadId` 非空表示用户手动指定了具体哪条道路在上。

带 getter/setter，参考现有 `manualElevation` 字段的写法。`RoadNetwork` 的 JSON 序列化（DTO 转换那部分）记得把这三个新字段都加进去，参考之前加 `sourceRoadId`/后来改名 `roadId` 时"顺手把序列化也一起补上"的做法。

`RoadSystemConfig` 新增：
```java
private double defaultCrossingClearance = 3.0; // 默认净空高度，setter 做基本范围校验（比如 1-10）
```

### 校验规则（在 setter 或者赋值入口做，不要在生成阶段才发现问题）
- `gradeSeparated` 只能在节点满足"度数正好 4、`roadId` 种类正好 2、每种正好 2 条边"这个条件时才能设为 `true`，不满足直接拒绝（返回失败或者忽略，不要抛异常打断整个流程）。这个判断逻辑建议写成一个独立的静态方法 `RoadGraphQueries.isSimpleCrossing(RoadNode node, RoadNetwork network)`，UI 层和数据层都用这一个方法判断，不要各自写一份。
- `elevatedRoadId` 非空时，必须是这个节点实际连接的两个 `roadId` 之一，不能是任意字符串。
- 如果 `gradeSeparated=false`，`elevatedRoadId`/`crossingClearance` 应该被忽略（不需要强制清空，但生成逻辑要保证只看 `gradeSeparated` 这一个开关，不要因为 `elevatedRoadId` 残留了旧值就误判成开启状态）。

---

## 2. 高度计算：`RoadGenerator` 改造

### 2.1 `computeJunctionTargetHeight` / `getTargetHeightAtNode`

在现有的判断顺序（先看 `manualElevation`，再走平均值逻辑）里插入立体交叉分支，新的判断顺序：
1. `node.getManualElevation() != null` → 直接用（不变，优先级最高，用户显式指定了具体标高就不要再套用立体交叉逻辑，两者冲突时以 `manualElevation` 为准，这个可以在 UI 里提示用户）；
2. `node.isGradeSeparated()` → 先按 2.1a 节确定"这次谁是跨越方"（`elevatedRoadId` 手动指定了就用它，没指定就自动判断），确定之后：当前边的 `roadId` 等于跨越方 → 目标高度 = 基准高度（见下面）+ `crossingClearance`（或 `config.getDefaultCrossingClearance()`）；不等于 → 按现有逻辑正常计算（这个节点上另一条道路的两条子边继续走原来的高度计算，不受影响）；
3. 都不满足 → 现有逻辑（平均值拼接）不变。

"基准高度"的计算：**排除掉跨越方对应的边之后**，对节点上剩下的边（被跨越方，正好 2 条，属于同一条道路）跑一遍现有的平均值逻辑（`RoadSlopeUtils.averageJunctionHeight`），得到的值作为基准，再加净空。

### 2.1a 自动判断哪条道路是跨越方

`elevatedRoadId == null` 时（用户标记了立体交叉但没有手动指定哪条在上），按以下规则自动判断：

对这个节点上的两条道路，**分别假装这个节点不是交叉点、只是各自路径上的一个普通点**，各自独立算出"如果不考虑另一条道路存在，这个点本来应该是什么高度"——具体做法：对每条道路，取它在这个节点两侧的两条子边，把 `gradeSeparated` 当作 `false` 临时计算一次目标高度（也就是走现有的、不涉及立体交叉的正常链式计算逻辑，两条子边在节点处应该本来就是同一个值，因为打断之前这本来就是同一条连续的路径）。两条道路各自算出一个"自然高度"，取**更高**的那条自动设为跨越方。

这个自动判断建议做成一个独立方法（不要直接埋在 `computeJunctionTargetHeight` 里，方便单测）：
```java
public String resolveElevatedRoadId(RoadNode node, RoadNetwork network, TerrainSampler terrain);
// 返回自动判断出的 roadId；如果 node.getElevatedRoadId() 非空，直接返回这个手动值，不用再算
```
**每次生成都重新计算**，不要把自动判断的结果缓存写回 `RoadNode`（用户没有手动选择，就应该一直是"自动"状态，如果地形被后续编辑改变了，重新生成时应该能跟着重新判断，而不是固定死第一次算出来的结果）。

### 2.2 让跨越方在节点两侧的子边都拿到这个强制高度

一条道路在打断之后，穿过某个路口节点的部分实际上是**两条独立的子边**（一条在这个节点结束、另一条从这个节点开始）。两条子边都需要在这个节点这一端拿到同一个"基准+净空"的强制值，才能保证跨越处两侧平滑衔接、不会出现台阶。

现在 `RoadSlopeUtils.computeChainedTargetHeights` 只支持强制**起点**高度（`manualStartHeight`），不支持强制**终点**高度。这次任务需要给它加一个对称的能力：

```java
public static List<Integer> computeChainedTargetHeights(
        List<Double> segmentDistances,
        List<Integer> groundStarts,
        List<Integer> groundEnds,
        List<Float> maxSlopePercents,
        Integer manualStartHeight,
        Integer manualEndHeight,   // 新增参数，非空时强制链条最后一个高度等于这个值
        ...（其它已有参数保持不变）);
```
**这是允许对 `RoadSlopeUtils` 做的改动**（跟第一份任务书里"不允许改 `RoadSlopeUtils`"这条不一样，这次任务明确需要这个新能力，属于合理扩展，新增重载，不要改动已有的不带 `manualEndHeight` 的重载的行为）。

实现思路：先按现有逻辑正向算一遍（从 `manualStartHeight` 出发逐段贴地形/坡度限制往前走），如果算出来的最后一个值和 `manualEndHeight` 不一致，从后往前再做一次修正——用现有的 `clampTowardTarget`（"将目标高度向 fromHeight 拉近，并遵守坡度限制"，这个函数已经存在，正好是为这种"手动标高修正"场景准备的）反向传播调整，确保最后一格精确等于 `manualEndHeight`，同时尽量不违反坡度限制（如果起点和终点的强制值之间的距离，按最大坡度根本走不到，说明这条边太短、净空设置太大，这种极端情况允许坡度限制被突破以保证两端强制值精确匹配，但要打一条 `LOGGER.warn` 提示"该边长度不足以在坡度限制内连接两端强制标高"，不要静默出现连接不上的情况）。

配一组单元测试验证：给定 `manualStartHeight`/`manualEndHeight`，算出来的链条首尾应该精确匹配这两个值；不设置 `manualEndHeight`（传 null）时，结果应该和现有的不带这个参数的重载完全一致（回归测试，确认新增参数不影响原有调用方）。

### 2.3 `RoadJunctionGenerator` 排除跨越方

生成路口路面拼接（第 6 章设计里提到的"取相邻道路偏移线交点连接"那部分逻辑）时，如果节点标了 `elevatedRoadId`，**跳过属于这条道路的边**，只用"被跨越方"的边去生成路口路面——跨越方不参与路口路面融合，它会在自己的边生成流程里，因为目标高度和地面/其它道路高度差变大，自然触发现有的桥梁判定和桥梁方块生成（`RoadConstructionEvaluator`/`generateBridgeStructures`），不需要在 `RoadJunctionGenerator` 里额外处理跨越方的方块。

---

## 3. UI：`RoadOverviewPanel` 节点编辑器扩展

现有 `renderNodeElevationEditor()` 已经是一个"遍历所有节点、每个节点可以设置手动标高"的列表，这次在这个基础上扩展，不要新建一个平行的 UI 区块。**只对满足 `RoadGraphQueries.isSimpleCrossing()` 条件（度数正好 4、正好 2 个 roadId、各贡献 2 条边）的节点**，在现有的"自动/手动标高"复选框旁边，加一个"立体交叉"下拉框——不满足条件的节点不显示这个下拉框，节省界面空间，也避免用户在不该标记的地方乱标。这样筛选之后，可选的节点数量本身就不会很多，不需要再额外做什么"精简可选点数量"的处理。

下拉框选项：
- 默认"无"（`gradeSeparated = false`）；
- "自动判断"（`gradeSeparated = true`，`elevatedRoadId = null`，交给 2.1a 节的规则决定）；
- 这个节点上两条道路各一个选项（`gradeSeparated = true`，`elevatedRoadId` 设为对应值，显示道路名称，如果道路没有单独的名字属性，用"道路 + roadId 前 6 位"这种简单标识即可）；
- 选中"自动判断"或某条具体道路后，下面出现一个净空高度的滑条（范围 1-10，默认读 `config.getDefaultCrossingClearance()`），修改后 `pushHistory()` 记录撤销栈（跟这个面板里其它编辑操作保持一致的撤销接入方式）；选了"自动判断"时，建议在旁边用灰色小字标注一下当前实际算出来是哪条道路在上（调用 `resolveElevatedRoadId` 实时显示），让用户能看到自动判断的结果，而不是选完看不出程序到底判了哪条。

如果用户同时设置了"手动标高"和"立体交叉"，在旁边给一个文字提示"已设置手动标高，立体交叉设置将被忽略"（对应第 2.1 节的优先级说明，让用户知道两者冲突时是什么行为，不要什么提示都没有让用户自己猜）。

---

## 4. 需要新建/修改的文件清单

**修改：**
```
com/plot/plugin/road/model/RoadNode.java       -- 新增 gradeSeparated/elevatedRoadId/crossingClearance 字段
com/plot/plugin/road/model/RoadNetwork.java    -- DTO 序列化新增这三个字段
com/plot/plugin/config/RoadSystemConfig.java   -- 新增 defaultCrossingClearance 字段
com/plot/plugin/road/RoadSlopeUtils.java       -- computeChainedTargetHeights 新增 manualEndHeight 重载
com/plot/plugin/road/RoadGenerator.java        -- computeJunctionTargetHeight/getTargetHeightAtNode 接入立体交叉判断逻辑
com/plot/plugin/road/RoadJunctionGenerator.java -- 生成路口路面时排除跨越方的边
com/plot/plugin/road/ui/RoadOverviewPanel.java -- 节点编辑器扩展立体交叉下拉框
src/main/resources/assets/plot/lang/en_us.json
src/main/resources/assets/plot/lang/zh_cn.json
```

**不允许修改**：`RoadNetworkBuilder.java`（拓扑打断逻辑本次不涉及，相交依然正常打断生成共享节点，这次任务只改高度计算和路口路面生成，不改拓扑结构本身）、`RoadConstructionEvaluator.java`（桥梁判定逻辑不用改，让它按现有规则自然识别出跨越方那一段该架桥即可）、`RoadGuideLineUtils.java`（上一份任务书的引导线机制，这次不涉及）。

---

## 5. 验收标准

1. `computeChainedTargetHeights` 新重载：给定 `manualStartHeight=60`、`manualEndHeight=70`，一段距离足够、坡度限制宽松的路径，验证结果首尾精确等于 60 和 70，中间平滑过渡；`manualEndHeight=null` 时结果和旧重载完全一致（回归）。
2. `RoadGraphQueries.isSimpleCrossing()`：构造一个标准四路交叉节点（度数 4、2 个 roadId、各 2 条边），验证返回 `true`；构造一个度数为 3 的 T 形节点、一个度数为 4 但 3 条道路汇聚的节点，都应该返回 `false`。
3. 一个标准四路交叉节点，手动把其中一条设为 `elevatedRoadId`，验证：跨越方在这个节点的目标高度 = 被跨越方在这个节点的基准高度 + 净空；被跨越方的目标高度不受影响，跟没有标记立体交叉时一样。
4. `resolveElevatedRoadId` 自动判断：构造两条道路在交叉点附近"自然高度"明显不同的地形，`elevatedRoadId` 留空，验证自动判断选出的是自然高度更高的那条；反过来调换两条道路的地形高度关系，验证判断结果也跟着反过来。
5. 同一条边的跨越方，在这个节点两侧的两条子边，验证节点处的高度精确一致（衔接平滑，不会有台阶）。
6. `RoadJunctionGenerator` 生成的路口路面范围，验证不包含跨越方道路的方块，跨越方应该在自己的边生成流程里正常触发桥梁判定（可以构造净空足够大的场景，验证 `RoadConstructionEvaluator` 判定这一段是 `BRIDGE`）。
7. 校验规则：给一个不满足标准四路交叉条件的节点（比如只有单条道路经过、或者 T 形节点），尝试设置 `gradeSeparated = true`，验证被拒绝或者忽略，不产生无意义的状态。
8. UI：手动标高和立体交叉同时设置时，验证生成阶段以手动标高为准，且 UI 上有对应的提示文字；选择"自动判断"时，界面应该能实时显示当前判断出的是哪条道路在上。
9. `RoadNetwork.toJson()/fromJson()` 往返，`gradeSeparated`/`elevatedRoadId`/`crossingClearance` 三个字段保持一致。

---

## 6. 实施顺序

1. 第 1 章数据模型 + 校验规则，先写测试跑通。
2. 第 2.2 节 `computeChainedTargetHeights` 的 `manualEndHeight` 扩展，独立开发独立测试，这是后面高度计算的基础能力。
3. 第 2.1 节接入 `RoadGenerator`，第 2.3 节 `RoadJunctionGenerator` 排除跨越方，跑通"标记之后生成结果里出现一座跨线桥"这条最短链路。
4. 第 3 章 UI，最后接上。

第 3 步做完之后建议实机测试：画两条相交的路，把其中一条标记为跨越方，生成之后进游戏看一下——跨越处应该是一条路架桥跨过另一条路，桥墩不会插进被跨越的那条路的路面里（净空是否给够，可能需要根据实际观感调一下默认值）。这是这次任务里最值得肉眼验证的地方，因为"净空够不够看起来舒服"这种事情单元测试测不出来。
