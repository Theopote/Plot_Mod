# Plot Mod - 道路系统任务书：立体交叉（跨越/穿越）手动标记

> 面向 Cursor 的开发任务书。这是"讨论 4 个问题"里的第二份，对应第 3 点（平面上相交但实际是一条路跨过另一条路，用户手动标记，系统自动算出所需净空高度）。第一份任务书（自动基准标高 + 纵断面展示）已经实现完成，这次任务可以在其基础上继续，但**不依赖**第一份任务书的引导线机制，是相对独立的功能。

---

## 0. 设计思路

现在的拓扑规则是"两条路只要在平面上相交，就一定打断、生成共享路口节点，所有汇聚道路在这个节点取平均高度拼接"（`RoadGenerator.computeJunctionTargetHeight`）。这次要加一个例外：**用户可以把某个路口节点标记为"立体交叉"，指定其中一条道路在这个点应该跨越（架高）通过，不参与正常的路口高度拼接**。

**关键设计决定**：不改变现有的拓扑打断逻辑（相交处依然会打断、生成共享节点，这部分继续复用现有机制，不需要新增"不打断"的例外路径），只是在**高度计算**这一步，让被标记的那条道路在这个节点绕开正常的"取平均值"逻辑，改成"取其它道路在此处的高度 + 净空 = 强制标高"。至于"跨越处要不要长得像座桥"，直接依赖现有的桥梁判定机制（`RoadConstructionEvaluator`）——强制拉高之后，目标高度和真实地面/其它道路的高度差自然会变大，现有的桥梁生成流程会自动接管，**不需要新写"如何画一座跨线桥"这种造型代码**。

---

## 1. 数据模型：`RoadNode` 新增字段

```java
private String elevatedRoadId;      // 哪条道路（RoadEdge.getRoadId()）在此处跨越通过；null 表示这是正常路口
private Double crossingClearance;   // 净空高度（格），null 表示使用 RoadSystemConfig 里的默认值
```
带 getter/setter，参考现有 `manualElevation` 字段的写法。`RoadNetwork` 的 JSON 序列化（DTO 转换那部分）记得把这两个新字段也加进去，参考之前加 `sourceRoadId`/后来改名 `roadId` 时"顺手把序列化也一起补上"的做法。

`RoadSystemConfig` 新增：
```java
private double defaultCrossingClearance = 3.0; // 默认净空高度，setter 做基本范围校验（比如 1-10）
```

### 校验规则（在 setter 或者赋值入口做，不要在生成阶段才发现问题）
- 一个节点要能被标记为立体交叉，前提是这个节点连接的道路（按 `roadId` 去重）**至少有 2 条**——如果只有一条道路自己打断（比如同一条路中间被另一条同源道路误判打断，理论上不应该发生，但加个防御性检查），设置 `elevatedRoadId` 应该被拒绝或者直接忽略，不要产生没有意义的状态。
- `elevatedRoadId` 必须是这个节点实际连接的某条边的 `roadId`，不能是任意字符串——赋值的地方要做这个校验（可以是简单的 `network.getEdgesAtNode(nodeId)` 遍历检查，具体用现有的图查询工具类 `RoadGraphQueries` 里有没有现成的"某节点连接哪些道路"的方法，有就直接用，没有就加一个）。

---

## 2. 高度计算：`RoadGenerator` 改造

### 2.1 `computeJunctionTargetHeight` / `getTargetHeightAtNode`

在现有的判断顺序（先看 `manualElevation`，再走平均值逻辑）里插入立体交叉分支，新的判断顺序：
1. `node.getManualElevation() != null` → 直接用（不变，优先级最高，用户显式指定了具体标高就不要再套用立体交叉逻辑，两者冲突时以 `manualElevation` 为准，这个可以在 UI 里提示用户）；
2. `node.getElevatedRoadId() != null` 且当前正在计算的这条边的 `roadId` **等于** `elevatedRoadId` → 这是"跨越方"，目标高度 = 基准高度（见下面）+ `crossingClearance`（或者 `config.getDefaultCrossingClearance()`）；
3. `node.getElevatedRoadId() != null` 且当前边的 `roadId` **不等于** `elevatedRoadId` → 这是"被跨越方"，按现有逻辑正常计算（如果这个节点除了跨越方还有多条"被跨越方"道路交汇，它们之间继续走原来的平均值拼接逻辑，只是把跨越方排除在外）；
4. 都不满足 → 现有逻辑不变。

"基准高度"的计算：**排除掉 `elevatedRoadId` 对应的边之后**，对节点上剩下的边（被跨越方）跑一遍现有的平均值逻辑（`RoadSlopeUtils.averageJunctionHeight`），得到的值作为基准，再加净空。也就是说 `computeJunctionTargetHeight` 内部收集 `heights` 列表那段循环，需要先把属于 `elevatedRoadId` 的边过滤掉，算完基准之后，再单独给跨越方的边返回"基准+净空"这个值。

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

现有 `renderNodeElevationEditor()` 已经是一个"遍历所有节点、每个节点可以设置手动标高"的列表，这次在这个基础上扩展，不要新建一个平行的 UI 区块。对每个 `degree >= 4` 或者"连接的道路数量（按 `roadId` 去重）>= 2"的节点（度数小于这个条件的节点没必要展示立体交叉选项，正常的直连点或者只有一条路经过的点用不上），在现有的"自动/手动标高"复选框旁边，加一个"立体交叉"下拉框：

- 默认选项"无"（`elevatedRoadId = null`）；
- 其它选项列出这个节点连接的各条道路（按 `roadId` 去重，显示道路名称，如果道路没有单独的名字属性，用"道路 + roadId 前 6 位"这种简单标识即可）；
- 选中某条道路后，下面出现一个净空高度的滑条（范围 1-10，默认读 `config.getDefaultCrossingClearance()`），修改后 `pushHistory()` 记录撤销栈（跟这个面板里其它编辑操作保持一致的撤销接入方式）。

如果用户同时设置了"手动标高"和"立体交叉"，在旁边给一个文字提示"已设置手动标高，立体交叉设置将被忽略"（对应第 2.1 节的优先级说明，让用户知道两者冲突时是什么行为，不要什么提示都没有让用户自己猜）。

---

## 4. 需要新建/修改的文件清单

**修改：**
```
com/plot/plugin/road/model/RoadNode.java       -- 新增 elevatedRoadId/crossingClearance 字段
com/plot/plugin/road/model/RoadNetwork.java    -- DTO 序列化新增这两个字段
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
2. 一个 4 度节点（两条路交叉），把其中一条标记为 `elevatedRoadId`，验证：跨越方在这个节点的目标高度 = 被跨越方在这个节点的基准高度 + 净空；被跨越方的目标高度不受影响，跟没有标记立体交叉时一样。
3. 同一条边的跨越方，在这个节点两侧的两条子边，验证节点处的高度精确一致（衔接平滑，不会有台阶）。
4. `RoadJunctionGenerator` 生成的路口路面范围，验证不包含跨越方道路的方块，跨越方应该在自己的边生成流程里正常触发桥梁判定（可以构造净空足够大的场景，验证 `RoadConstructionEvaluator` 判定这一段是 `BRIDGE`）。
5. 校验规则：给一个只有单条道路经过的节点（没有真正的交叉），尝试设置 `elevatedRoadId`，验证被拒绝或者忽略，不产生无意义的状态。
6. UI：手动标高和立体交叉同时设置时，验证生成阶段以手动标高为准，且 UI 上有对应的提示文字。
7. `RoadNetwork.toJson()/fromJson()` 往返，`elevatedRoadId`/`crossingClearance` 字段保持一致。

---

## 6. 实施顺序

1. 第 1 章数据模型 + 校验规则，先写测试跑通。
2. 第 2.2 节 `computeChainedTargetHeights` 的 `manualEndHeight` 扩展，独立开发独立测试，这是后面高度计算的基础能力。
3. 第 2.1 节接入 `RoadGenerator`，第 2.3 节 `RoadJunctionGenerator` 排除跨越方，跑通"标记之后生成结果里出现一座跨线桥"这条最短链路。
4. 第 3 章 UI，最后接上。

第 3 步做完之后建议实机测试：画两条相交的路，把其中一条标记为跨越方，生成之后进游戏看一下——跨越处应该是一条路架桥跨过另一条路，桥墩不会插进被跨越的那条路的路面里（净空是否给够，可能需要根据实际观感调一下默认值）。这是这次任务里最值得肉眼验证的地方，因为"净空够不够看起来舒服"这种事情单元测试测不出来。
