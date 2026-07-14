# Plot Mod - 道路系统插件（RoadSystemPlugin）完整开发任务书

> 本文档面向 AI 编码助手（Cursor），用于在现有 `Plot_Mod` 代码库中完整实现"道路系统"插件。
> 请先通读全文再开始动手，不要跳过"约束与原则"章节——这决定了很多设计选择的理由。

---

## 0. 项目背景

`Plot` 是一个 Fabric（Minecraft 1.21.10）客户端模组，本质是一个"在 Minecraft 里画平面 CAD 图，再把图形投影成方块"的辅助规划工具。核心概念：

- **画布（Canvas）**：一个抽象的 2D 绘图平面，通过 `CoordinateTransformer` 与 Minecraft 世界坐标互相映射。
- **图层（Layer）/ 图形（Shape）**：`com.plot.core.model.Shape` 是所有图形（直线、多段线、贝塞尔曲线、圆、矩形、标注等）的抽象基类，挂在 `Layer` 下，`Layer` 挂在 `Project` 下。
- **工具系统**：`ITool`/`BaseTool` 体系，负责画布上的交互式绘制/编辑（`ui/tools/impl/drawing/*`、`ui/tools/impl/modify/*`）。
- **插件系统**：`IPlugin`/`Plugin` 体系，插件是"侧边扩展面板里的一个功能页"，**不能**注册自己的 `ITool`，只能通过 `render()` 画自己的 ImGui 面板，并读取用户当前在画布上选中的 `Shape`。
- **道路系统插件**（`com.plot.plugin.RoadSystemPlugin`）目前已有一个早期雏形：单路径地形采样 + 挖填方统计 + 桥梁/隧道检测（`com.plot.plugin.road.RoadGenerator`），但**没有路网（多条道路互相连接/相交）的概念**，本次任务就是把它扩展成完整的道路系统。

---

## 1. 约束与设计原则（务必遵守）

这些原则来自和用户的多轮讨论，是本次开发**优先级最高**的约束，任何实现细节如果和这些原则冲突，以这些原则为准：

1. **最小化对 Plot 核心的改动**。道路系统是一个独立子域，它的数据模型、持久化、撤销栈都应该尽量自成一体，只通过 `Shape`/`Plugin`/`EventBus` 已经暴露的公共接口跟核心交互。**不要**修改 `Project`、`ProjectSnapshot`、`Layer`、`ToolManager`、`CommandManager` 等核心类的既有行为（新增纯扩展性质的重载方法可以，但不能改变已有方法的语义）。
2. **"画" 和 "道路数据" 彻底解耦（三阶段工作流）**：
   - **阶段一：绘制** —— 用户用 Plot 原生的 `PolylineTool`/`LineTool`/`BezierCurveTool` 在画布上画路的走向，插件完全不参与这一步，也不需要新增任何捕捉逻辑（Plot 现有的端点/交点捕捉已经够用）。
   - **阶段二：认领（Adopt）** —— 用户在道路插件面板里选中一条或多条 `Shape`，点击"添加为道路"，插件**读取并拷贝**这些图形的几何点，生成插件自己的 `RoadEdge` 数据。**认领之后，画布上原来的 `Shape` 和道路数据不再有任何关联**——用户后续编辑/移动/删除那条原始线，不影响已经生成的道路；道路数据的后续编辑（改宽度、加坡度覆盖等）全部在插件自己的面板里做。**不要**尝试做"画布图形变化后自动同步道路数据"这种双向绑定，明确不做。
   - **阶段三：生成** —— 基于已认领的道路数据做拓扑分析（求交打断、路口分类）、坡度校验、方块生成，通过 `GhostBlockManager` 预览，用户确认后落地到世界。
3. **道路网络数据模型完全是插件私有的**，`Project`/`Layer`/`Shape` 不需要知道"道路"这个概念的存在。
4. **UI 挂载点已经存在，不要重新造**：`com.plot.ui.panel.extension.ExtensionPanel` 已经实现了"点击左侧插件列表里的按钮 → 下方自动展开该插件的 `render()` 内容"这一整套折叠/展开机制（见该文件 `render()` 方法：点击 `UIUtils.iconButton` 触发 `pluginManager.setActivePlugin(...)`，随后调用 `currentActivePlugin.render()`）。**你不需要新写任何面板展开/收起的框架代码**，只需要把 `RoadSystemPlugin.render()` 的内容组织好即可，框架会自动处理"点击按钮展开配置面板"的交互。
5. 几何运算优先复用已有代码，不要重复造轮子（第 4 章有详细的可复用清单）。

---

## 2. 现有代码基础（复用清单，务必先读源码再写代码）

以下是已经确认存在、可以直接复用的接口和类，实现时请先 `view` 一遍确认签名，因为版本可能已经变化：

| 能力 | 位置 | 说明 |
|---|---|---|
| 图形端点 | `Shape.getEndpoints()` | 抽象方法，所有 Shape 子类都实现了 |
| 图形切线方向 | `Shape.getTangentAt(Vec2d point)` | 抽象方法 |
| 折线/曲线取点 | `PolylineShape.getPoints()` / `BezierCurveShape.getPoints()` | 直接拿到坐标序列 |
| 图形求交 | `Shape.getIntersectionsWith(Shape other)` | `PolylineShape`/`BezierCurveShape` 均已实现，**不要自己写线段求交算法** |
| 多段线偏移（生成路面/人行道边界） | `com.plot.ui.tools.impl.modify.helper.OffsetHandler` | 已经处理了零长度向量保护、拐角求交等边界情况 |
| 地形高度采样 / 桥梁隧道判定 | `com.plot.plugin.road.RoadGenerator` | 已有 `samplePath`/`calculateSegmentHeights`/`detectBridges`/`detectTunnels`/`generateRoadBlocks` 等私有方法，本次任务需要**重构其入口**（见第 5 章） |
| 坐标变换 | `com.plot.infrastructure.coordinate.CoordinateTransformer` | 画布坐标 ↔ 世界坐标 |
| 虚影方块预览 | `com.plot.infrastructure.event.block.GhostBlockManager` | `addGhostBlock`/`removeGhostBlock`/`clearAllGhostBlocks` |
| 插件数据目录 | `Plugin.getDataFolder()` | 返回 `config/plugins/{pluginId}/`，用作道路网络的本地存储位置 |
| 项目加载/保存事件 | `com.plot.infrastructure.event.project.ProjectLoadedEvent`（带 `getFilePath()`）、`ProjectSavedEvent`（带 `getFilePath()`） | 通过 `EventBus.getInstance().subscribe(...)` 订阅，用于按工程文件路径关联道路数据文件 |
| 插件已有配置系统 | `com.plot.plugin.config.RoadSystemConfig` | 目前是全局默认配置（宽度/材质/坡度阈值/预设），本次任务中把它降级为"新道路的默认参数来源"，具体道路网络的每条边可以覆盖这些默认值 |
| ImGui 工具组件 | `com.plot.ui.component.UIUtils`、`Icons`（已有 `Icons.ROAD`） | 面板 UI 复用现成的按钮/卡片组件风格 |

---

## 3. 数据模型设计

新建包：`com.plot.plugin.road.model`

### 3.1 `RoadNode`
```java
public class RoadNode {
    private final String id;           // UUID
    private Vec2d position;            // 画布坐标
    private Double manualElevation;    // 手动指定标高，null 表示用地形采样
    private final Set<String> connectedEdgeIds;
    // getId / getPosition / setPosition / getManualElevation / setManualElevation
    // addEdge(String edgeId) / removeEdge(String edgeId) / getDegree()
}
```

### 3.2 `RoadEdge`
```java
public class RoadEdge {
    private final String id;
    private String startNodeId;
    private String endNodeId;
    private List<Vec2d> centerlinePoints;  // 认领时拷贝的坐标，独立于原 Shape

    // 道路属性，未显式设置时回落到 RoadSystemConfig 的默认值
    private Integer width;
    private String material;
    private Boolean includeSidewalk;
    private Integer sidewalkWidth;
    private String sidewalkMaterial;
    private Integer streetlightSpacing;   // 米，null 表示不放路灯
    private Float maxSlope;               // 覆盖网络级默认最大坡度
    private List<SlopeOverride> slopeOverrides; // 按里程区间覆盖坡度

    public static class SlopeOverride {
        double startDistance;  // 沿中心线的起始弧长（米）
        double endDistance;
        float maxSlope;
    }
    // 所有字段的 getter/setter，getEffective* 系列方法用于"未设置则取默认值"的回落逻辑
}
```

### 3.3 `RoadNetwork`
```java
public class RoadNetwork {
    private final Map<String, RoadNode> nodes = new LinkedHashMap<>();
    private final Map<String, RoadEdge> edges = new LinkedHashMap<>();

    // 增删改查
    public RoadNode createNode(Vec2d position);
    public RoadEdge createEdge(String startNodeId, String endNodeId, List<Vec2d> points);
    public void removeEdge(String edgeId);   // 同时清理孤立节点
    public void removeNode(String nodeId);   // 前置校验：度数必须为 0

    // 序列化（Gson，纯 POJO，参考 ProjectSnapshot 的写法风格）
    public String toJson();
    public static RoadNetwork fromJson(String json);
    public void saveTo(Path file) throws IOException;
    public static RoadNetwork loadFrom(Path file) throws IOException; // 文件不存在返回空网络，不抛异常
}
```

### 3.4 轻量撤销栈：`RoadNetworkHistory`
不要接入核心 `CommandManager`。设计阶段的编辑量小，用最简单的"深拷贝快照栈"即可：
```java
public class RoadNetworkHistory {
    private final Deque<String> undoStack = new ArrayDeque<>(); // 存 JSON 快照
    private final Deque<String> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    public void push(RoadNetwork current);  // 编辑前调用，把当前状态推入 undoStack
    public RoadNetwork undo(RoadNetwork current);
    public RoadNetwork redo(RoadNetwork current);
}
```
每次插件面板里发生"认领新边 / 修改属性 / 删除边 / 打断"等操作前，调用 `history.push(network)`。

---

## 4. 拓扑处理：`RoadNetworkBuilder`

新建 `com.plot.plugin.road.RoadNetworkBuilder`，职责：

### 4.1 认领（Adopt）
```java
public RoadEdge adoptShape(RoadNetwork network, Shape shape, RoadSystemConfig defaults);
```
- 用 `shape.getEndpoints()` 取首尾点，用 `PolylineShape.getPoints()`（若 shape 是 `PolylineShape`）或对 `BezierCurveShape` 按现有 `RoadGenerator.sampleBezierCurve` 的采样逻辑（需要把该方法从 `RoadGenerator` 挪到一个公共工具类，比如 `RoadGeometryUtils`，两边都能用）取得完整点序列。
- 在起点/终点容差范围内（比如 0.5 格）查找网络里是否已有 `RoadNode` 坐标重合，重合则复用该节点（这样用户只要画线时端点吸附到已认领的道路端点，认领时会自动识别为同一路口，不需要额外交互）；不重合则新建节点。
- 生成 `RoadEdge`，属性字段先留空（后续在面板里编辑，或直接使用 `defaults` 填充初始值）。

### 4.2 求交与打断
```java
public void detectAndSplitIntersections(RoadNetwork network);
```
- 两两遍历 `RoadEdge`，用第 2 章里提到的方式构造临时 `PolylineShape` 调用 `getIntersectionsWith`，取得交点。
- 对每个交点：如果交点不与任一条边的端点重合，则在交点处把两条边都打断成两段，新建一个 `RoadNode`。
- 注意去重（同一交点可能被多次检测到）和自相交（同一条边不需要跟自己求交）。
- 这个方法设计成**幂等**：多次调用应该得到同样的结果，不会重复打断同一交点。建议在每次"认领新边"之后自动调用一次，而不是要求用户手动触发。

### 4.3 路口分类
```java
public enum JunctionType { ENDPOINT, THROUGH, T_JUNCTION, CROSSROAD, COMPLEX }
public JunctionType classify(RoadNode node); // 按 node.getDegree() 返回：0/1→ENDPOINT，2→THROUGH（直连，不算路口），3→T_JUNCTION，4→CROSSROAD，5+→COMPLEX
```

---

## 5. 道路生成：重构 `RoadGenerator`

现有 `RoadGenerator.generateRoad(Shape path)` 是单路径入口，本次任务需要新增一个不依赖 `Shape` 的入口，供路网批量生成使用（**保留原方法不动，新增重载，向后兼容**）：

```java
public RoadGenerationResult generateEdge(RoadEdge edge, RoadNode startNode, RoadNode endNode, World world);
```

内部处理链（按顺序）：

1. **地形高度采样**：复用现有 `calculateSegmentHeights`（如果是 `private`，改成 `protected`/包内可见，不要改签名语义）。
2. **坡度校验链**（新增逻辑，这是本次任务的核心新功能）：
   对每个采样区间计算实际坡度 `= |Δ高度| / 水平距离`，与 `edge.getEffectiveMaxSlope(distance)`（会去查 `slopeOverrides`，命中区间用覆盖值，否则用边级/网络级默认值）比较：
   - 坡度达标 → 按地形起伏铺路。
   - 超标但挖填方量在 `bridgeThreshold`/`tunnelThreshold` 以内 → 对该段做找平：生成一个满足坡度约束的渐变高度序列（线性插值即可，不需要复杂的竖曲线算法）。
   - 挖填方超过阈值 → 交给现有 `detectBridges`/`detectTunnels` 逻辑处理。
3. **路面/人行道边界生成**：调用 `OffsetHandler`（见第 6 章的接口调整）分别对中心线做 `±width/2` 和 `±(width/2+sidewalkWidth)` 偏移，得到路面和人行道边界。
4. **方块生成**：复用现有 `generateRoadBlocks`，扩展参数支持传入路面/人行道的边界点集合，分别用 `material`/`sidewalkMaterial` 填充。
5. **路灯放置**（新增）：见第 7 章。

路网级的生成入口：
```java
public class RoadNetworkGenerator {
    public Map<String, RoadGenerationResult> generateAll(RoadNetwork network, World world);
}
```
遍历所有 `RoadEdge` 调用 `RoadGenerator.generateEdge`，再调用 `RoadJunctionGenerator`（见第 6.2 节）处理路口拼接。

---

## 6. 路口与偏移

### 6.1 `OffsetHandler` 的扩展
检查 `OffsetHandler` 现有方法是否是围绕 `Shape` 设计的。如果是，新增一个纯函数重载：
```java
public static List<Vec2d> offsetPolyline(List<Vec2d> points, double distance);
```
只处理 `List<Vec2d>` 输入输出，不依赖 `Shape`/图层上下文，方便道路系统直接调用。**这是唯一允许对核心几何工具类做的改动，且必须是新增重载，不能修改原有方法的行为**。

### 6.2 `RoadJunctionGenerator`（新建）
```java
public class RoadJunctionGenerator {
    public JunctionBlocks generateJunction(RoadNode node, RoadNetwork network, RoadGenerator generator);
}
```
- `degree == 2`（直连）：不需要特殊处理，两条边的路面边界在此处应该本来就近似重合，跳过。
- `degree == 3` 或 `4`：把汇聚到该节点的每条边的路面偏移线，在节点附近截取一段，依次求相邻两条偏移线的交点作为路口多边形的顶点，围成一个简单多边形，作为路口路面。人行道边界同理再做一圈，转角处理不需要做到工业级平滑，够用即可（比如直接连接相邻人行道偏移线的最近点）。
- `degree >= 5`：先不生成精细路口造型，只把交汇范围内的路面做并集覆盖（简单地取所有相关边在该范围内的路面区域的包络矩形或圆形区域），记录一条 `LOGGER.warn` 提示"复杂路口（{}条道路汇聚）暂不支持精细造型，已用简化处理"。

---

## 7. 附属设施：路灯等距布置

新建工具方法（可以放进 `RoadGeometryUtils`）：
```java
public static List<Vec2d> sampleAlongPath(List<Vec2d> pathPoints, double spacing, double skipNearEndsDistance);
```
- 按弧长把路径重采样为等间距点。
- `skipNearEndsDistance`：距离两端（路口附近）多少米内不放置，避免路灯怼在路口正中间，建议默认值等于道路宽度。

路灯的具体世界坐标 = 采样点沿中心线法向偏移 `(width/2 + sidewalkWidth + 0.5)` 格，两侧交替或都放（做成可配置项）。

---

## 8. UI 设计：`RoadSystemPlugin.render()`

**重要提醒**：不需要自己实现"点击展开面板"的机制，`ExtensionPanel` 已经处理了插件级别的展开/收起。这里要做的是把 `render()` 内部的内容组织清楚。建议的面板结构（用 ImGui 的 `CollapsingHeader` 或 Tab 分区，避免所有内容堆在一屏）：

```
[道路系统]
├─ 顶部工具条：[撤销] [重做] [保存到工程] 当前工程状态提示
├─ Tab: 网络概览
│   ├─ 路网统计（节点数/边数/路口数/总长度）
│   ├─ 节点/边列表（可点击定位、可删除）
├─ Tab: 认领道路
│   ├─ 提示文字："请先用画图工具画出道路走向，然后在此选择图形认领"
│   ├─ 当前选中图形信息（复用现有 findAvailablePaths/updateSelectedPath 逻辑）
│   ├─ [默认参数]：宽度/材质/人行道/坡度 等，取自 RoadSystemConfig 预设
│   ├─ [认领为新道路] 按钮
├─ Tab: 编辑道路属性
│   ├─ 选择一条已认领的 RoadEdge（下拉框，展示"起点→终点，长度 X 米"）
│   ├─ 逐项编辑宽度/材质/人行道/路灯间距/最大坡度
│   ├─ 坡度分段覆盖的简单列表编辑（起始里程/结束里程/坡度值，增删行）
├─ Tab: 生成与预览
│   ├─ [生成预览] → 调用 RoadNetworkGenerator，结果丢进 GhostBlockManager
│   ├─ 统计信息：总挖方/填方/桥梁数/隧道数/路灯数
│   ├─ [确认生成] → 落地到世界，包一个 Command 交给 CommandManager（见第 9 章）
│   ├─ [清除预览]
```

样式上参考现有 `RoadSystemPlugin.render()` 里已经用到的 `UIUtils.selectableCard`/`ImGui.textColored` 等写法保持一致，不要引入新的视觉风格。

---

## 9. 落地阶段的撤销集成

生成阶段往世界里放方块时，参考代码库里其他工具生成图形/方块时接入 `CommandManager` 的写法（在 `core/command/commands/` 下找一个结构类似的现有 Command 类作为模板），新建：
```java
public class GenerateRoadCommand implements Command {
    // 记录本次落地涉及的所有 BlockPos 及其落地前的原始方块状态
    // execute()：写入新方块
    // undo()：恢复原始方块状态
}
```
构造时需要的原始方块状态在"生成预览"阶段用 `world.getBlockState(pos)` 采集好，随 `RoadGenerationResult` 一起传给这个 Command，避免 `undo()` 时再去查（世界状态可能已经变了）。

---

## 10. 持久化接线

在 `RoadSystemPlugin.onEnable()` 里：
```java
EventBus.getInstance().subscribe(ProjectLoadedEvent.class, this::onProjectLoaded);
EventBus.getInstance().subscribe(ProjectSavedEvent.class, this::onProjectSaved);
```
在 `onDisable()` 里对称地 `unsubscribe`（参考 `EventBus.unsubscribe` 签名）。

文件名生成策略：对 `event.getFilePath()` 做一个稳定 hash（比如 SHA-256 取前 16 位十六进制）作为文件名，保存到 `getDataFolder().toPath().resolve("networks").resolve(hash + ".json")`，避免路径分隔符/特殊字符直接拼进文件名导致的问题。

**注意**：请先确认当前代码库里 `ProjectLoadedEvent`/`ProjectSavedEvent` 是否已经有真实的触发源（搜索谁 `new ProjectLoadedEvent(...)`/`new ProjectSavedEvent(...)`）。如果目前还没有 UI 入口真正触发这两个事件（工程保存/加载功能可能还没接通），那么：
- 这部分订阅逻辑仍然要写好（面向未来），
- 但同时在插件里加一个兜底：`onEnable()` 时如果没有收到过 `ProjectLoadedEvent`，用一个固定的默认文件名（比如 `default.json`）加载/保存，保证插件在工程系统完全打通之前也能正常使用，不会因为事件没触发而导致道路数据完全无法持久化。

---

## 11. 需要新建/修改的文件清单

**新建：**
```
com/plot/plugin/road/model/RoadNode.java
com/plot/plugin/road/model/RoadEdge.java
com/plot/plugin/road/model/RoadNetwork.java
com/plot/plugin/road/model/RoadNetworkHistory.java
com/plot/plugin/road/RoadNetworkBuilder.java
com/plot/plugin/road/RoadNetworkGenerator.java
com/plot/plugin/road/RoadJunctionGenerator.java
com/plot/plugin/road/RoadGeometryUtils.java
com/plot/core/command/commands/GenerateRoadCommand.java
```

**修改（只做新增重载/扩展，不改变既有行为）：**
```
com/plot/plugin/RoadSystemPlugin.java          -- 重写 render()，接入 RoadNetwork
com/plot/plugin/road/RoadGenerator.java        -- 新增 generateEdge(...) 入口，抽取可复用的私有方法为包内可见
com/plot/ui/tools/impl/modify/helper/OffsetHandler.java  -- 新增 offsetPolyline(List<Vec2d>, double) 重载
```

**不允许修改：**
```
Shape.java / Project.java / ProjectSnapshot.java / Layer.java / ToolManager.java 的既有方法签名和行为
```

---

## 12. 验收标准

请在实现完成后，用以下场景自查（有条件的话写成简单的单元测试，几何/数据模型部分不依赖 Minecraft 运行时，可以脱离 Fabric 环境单测）：

1. 画一条折线，认领为道路，`RoadNetwork` 里应该恰好新增 2 个 `RoadNode`（首尾）和 1 个 `RoadEdge`。
2. 画两条相交的折线，分别认领后调用 `detectAndSplitIntersections`，交点处应该正确生成新节点，原来 2 条边应该变成 4 条边，且重复调用该方法结果不再变化（幂等）。
3. 画三条线在同一点汇合，认领后该节点 `classify()` 应返回 `T_JUNCTION`；四条线汇合返回 `CROSSROAD`。
4. 给一条边设置 `maxSlope = 5`，构造一段地形高度差明显超过 5% 坡度的路径，生成结果里对应区间应该被找平（不应该有单点坡度超过设定值，除非触发了桥梁/隧道）。
5. `RoadNetwork.toJson()` → `fromJson()` 往返后，节点/边的所有字段应完全一致。
6. 插件禁用再重新启用（`onDisable`→`onEnable`），不应该出现 `EventBus` 重复订阅（每次 `enable` 前应该先确保没有残留的旧订阅）。
7. 生成道路落地世界后执行撤销，方块应完全恢复到落地前的状态。

---

## 13. 实施顺序建议

1. 第 3 章的数据模型（`RoadNode`/`RoadEdge`/`RoadNetwork`/`RoadNetworkHistory`）+ 单元测试，这一层不依赖任何 Minecraft/ImGui 代码，最容易验证。
2. 第 4 章的 `RoadNetworkBuilder`（认领 + 求交打断 + 路口分类），继续保持可脱离运行时单测。
3. 第 8 章的 UI，先接入前两步的成果，让"画线 → 认领 → 看到路网列表"这条链路能跑通并肉眼可见。
4. 第 5、6、7 章的生成逻辑（坡度校验、偏移边界、路口造型、路灯）。
5. 第 9 章的落地 Command + 撤销集成。
6. 第 10 章的持久化接线（可以放最后，不影响前面功能的开发和测试）。

请严格按此顺序推进，每一步完成后运行一次现有的构建（`./gradlew build` 或对应任务）确认没有破坏原有功能，再进入下一步。
