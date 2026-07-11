# Plot Mod - 建筑轮廓生成器插件（BuildingPlugin）开发任务书

> 面向 Cursor 的开发任务书。这是继道路系统之后的第二个"生成类"插件，架构上尽量对齐道路系统已经跑通的模式（认领 → 参数编辑 → 预览 → 落地撤销），能直接复用的基础设施（`OffsetHandler`、`BlockPlacementScheduler`、`BlockProjectionHandler.checkWorldModificationReadiness()`、`CommandManager.pushExecuted`）都要复用，不要重新发明。

---

## 0. 背景与两个必须遵守的前提

1. **不要依赖 `EarthworkPlugin`**。该插件目前只是 UI 骨架，所有工具按钮都是空实现，没有真实的挖填计算逻辑可以调用。建筑的地基整平需要在本插件内部自行实现一套简化版逻辑（见第 3 章），不要尝试调用或等待 `EarthworkPlugin` 提供能力。
2. **多边形内部点判定必须使用 `Polygon.contains(Vec2d point)`（射线法，返回 `boolean`），禁止使用继承自 `Shape` 的 `containsPoint(Vec2d, double tolerance)`**。后者是"距边界的容差判断"，语义不同，用在楼板/屋顶填充上会导致建筑内部大片区域被误判为"不在轮廓内"。

---

## 1. 整体工作流（对齐道路系统的三阶段模式）

1. **绘制阶段**：用户用 Plot 原生的 `Polygon` 或 `RectangleShape` 画建筑轮廓，插件不参与。
2. **认领阶段**：插件读取选中的 `Polygon`/`RectangleShape`，取其 `getPoints()`，**拷贝**成插件自己的 `BuildingFootprint` 数据（认领即独立，后续编辑不回写画布，这一点和道路系统的"认领即拷贝"原则一致）。
3. **生成阶段**：整平地基 → 逐层生成墙体+楼板 → 生成屋顶 → 开窗/开门 → `GhostBlockManager` 预览 → 二次确认 → 落地（异步分帧 + 撤销）。

---

## 2. 数据模型

新建包 `com.plot.plugin.building.model`：

### 2.1 `BuildingFootprint`
```java
public class BuildingFootprint {
    private final String id;
    private List<Vec2d> outerPoints;      // 认领时拷贝的外轮廓顶点（闭合多边形）
    private boolean isRectangular;        // 认领来源是否是 RectangleShape，决定屋顶类型是否可选坡顶

    private int floors = 1;
    private int floorHeight = 3;          // 每层层高（格）
    private int wallThickness = 1;
    private String wallMaterial;
    private String floorMaterial;         // 楼板材质
    private String roofMaterial;

    public enum RoofType { FLAT, GABLE, HIP }
    private RoofType roofType = RoofType.FLAT;
    private int roofPitchRatio = 1;       // 坡屋顶：水平前进 roofPitchRatio 格，垂直升高 1 格，越小越陡

    private Integer manualBaseElevation;  // 手动指定的±0标高，null 表示按第 3 章自动计算
    private int windowSpacing = 4;        // 沿外墙周长的开窗间距（格），<=0 表示不开窗
    private int windowWidth = 1;
    private int windowHeight = 2;
    private int windowSillHeight = 1;     // 窗台距楼层地面的高度

    private List<DoorOpening> doors = new ArrayList<>();
    public static class DoorOpening {
        int wallSegmentIndex;    // 门所在的外墙线段索引（对应 outerPoints 里第几条边）
        double positionRatio;   // 门在该段墙上的位置比例 0.0-1.0（从该边起点算起）
        int floor;              // 门所在楼层（0 = 一层）
        int width = 1;
        int height = 2;
    }
    // 所有字段的 getter/setter，setter 做合理范围校验（参考 RoadEdge/RoadSystemConfig 现有的 Math.max/Math.min 写法）
}
```

### 2.2 `BuildingProject`（对应道路系统的 `RoadNetwork`，管理已认领的多个建筑）
```java
public class BuildingProject {
    private final Map<String, BuildingFootprint> buildings = new LinkedHashMap<>();
    // 增删改查、toJson/fromJson（参考 RoadNetwork 的 DTO 转换写法）、saveTo/loadFrom
}
```
建筑之间目前**不需要**像道路那样处理"相交打断"——每栋建筑是独立实体，不要在这次任务里加建筑之间的拓扑关系判断（比如两栋楼是否重叠），这个先不做校验，超出本次范围。

### 2.3 撤销栈
新建 `BuildingProjectHistory`，直接照抄 `RoadNetworkHistory` 的实现模式（JSON 快照深拷贝栈），不需要重新设计。

---

## 3. 地基整平算法（本插件自行实现，不依赖 EarthworkPlugin）

新建 `com.plot.plugin.building.BuildingFoundationUtils`（纯函数风格，参考 `RoadSlopeUtils` 的写法，方便测试）：

```java
public static int computeBaseElevation(List<Integer> groundHeightSamples, Integer manualOverride);
```
- 如果 `manualOverride` 不为空，直接返回。
- 否则：对轮廓范围内采样到的一批地面高度取**众数**（出现次数最多的高度值），如果众数不唯一（多个高度出现次数相同），取其中较高的那个（宁可后续多挖一点，也不要让建筑大面积悬空）。这个策略比取平均值更符合"大部分地基已经是这个高度、少数凸起/凹陷需要处理"的直觉，也比简单取最低点/最高点更不容易产生过量挖方或过量填方。

`BuildingGenerator`（见第 4 章）在生成前：
1. 用 `Polygon.contains()` 判断轮廓内所有 `(x,z)` 整数格点（复用道路系统里"逐格采样"的写法，参考 `RoadGenerator` 里遍历路面区域方块的方式）；
2. 对这些格点各自采样地面高度（复用 `getTopHeight`/`BlockProjectionHandler` 现有的地形高度查询方式）；
3. 调用 `computeBaseElevation` 得到 `baseElevation`；
4. 逐格比较该格地面高度和 `baseElevation`：高于则挖平（清到 `baseElevation`），低于则填平（填到 `baseElevation`），正好持平则跳过。挖填材质：挖方裸露部分保留原地形（不替换材质，只是把多余方块清空成空气），填方部分用可配置的 `foundationFillMaterial`（新增字段，加进 `BuildingFootprint`，默认 `"minecraft:stone"` 之类的地基材质）。

**这次不做边坡处理**（不像道路那样在地基边缘做斜坡过渡），建筑地基整平范围就是轮廓本身，如果范围边缘和周围地形有落差，允许出现一个垂直的挡土墙面（用 `foundationFillMaterial` 铺一层side wall 即可，逻辑简单，不需要引入道路系统那套边坡算法）。这个如果你后面想做得更精细，可以作为增强项，这次先不做。

---

## 4. 主体生成：`BuildingGenerator`

新建 `com.plot.plugin.building.BuildingGenerator`（参考 `RoadGenerator` 的结构：一个统一入口 + 内部拆分成若干私有方法，返回一个类似 `RoadGenerationResult` 的结果对象，改名 `BuildingGenerationResult`，里面维护 `List<BlockRecord>`，`BlockRecord` 直接复用 `com.plot.core.command.commands.GenerateRoadCommand.BlockRecord` 的结构，不需要重新定义一遍——如果这个类目前是 `RoadCommand` 私有内部类不方便跨包引用，就把它挪成一个独立的公共类 `com.plot.core.command.BlockRecord`，两边共用，这是一个允许的小范围重构）。

生成顺序：

1. **地基整平**（第 3 章）。
2. **逐层墙体**：对 `outerPoints` 用 `OffsetHandler.offsetPolyline`（现有方法，注意这是围绕开放折线设计的，建筑轮廓是闭合的，需要确认现有实现对闭合输入的处理是否正确——前面查过 `OffsetHandler` 内部已经有 `closed` 分支逻辑，构造调用时把对应的 `PolylineShape`/`Polygon` 设置成 `closed=true` 再传入）向内偏移 `wallThickness` 格得到内墙线，外墙线就是 `outerPoints` 本身。墙体方块 = 外墙线和内墙线之间的环形区域，在 `[baseElevation, baseElevation + floors*floorHeight)` 高度范围内逐层填充 `wallMaterial`。
3. **每层楼板**：在每一层的地面高度（`baseElevation + floor*floorHeight`），对轮廓内墙线包围的区域（用 `Polygon.contains()` 判断，注意用的是内墙线构造的临时 `Polygon`，不是外轮廓，避免把墙体本身算重）逐格铺 `floorMaterial`。顶层还需要在最高楼层顶部铺一层楼板（如果是平顶屋顶，这层楼板同时充当屋顶基层）。
4. **屋顶**（见第 5 章）。
5. **开窗**：沿外墙周长（`outerPoints` 依次连接的每条边）按 `windowSpacing` 等距布置窗户位置（可以直接复用 `RoadGeometryUtils.sampleAlongPath` 的实现思路，但**不要跨插件依赖 `com.plot.plugin.road` 包**——在 `com.plot.plugin.building` 下新建一个自己的轻量版等距采样函数，逻辑可以参考但代码要独立，保持两个插件互不依赖）。每个采样点位置，在对应墙体上从 `windowSillHeight` 开始往上挖空 `windowWidth × windowHeight` 的洞（挖空即用空气替换，不需要放玻璃类方块，除非你想加一个可选的窗户材质字段，这个可以作为增强项）。每层独立计算一次开窗（多层建筑每层都会有窗，不是只有一层有窗）。
6. **开门**：遍历 `BuildingFootprint.doors`，按 `wallSegmentIndex`/`positionRatio`/`floor` 定位，在对应位置挖空 `width × height` 的洞，从该层楼板高度开始往上挖（门底部贴着地面，不像窗户有窗台高度）。

所有方块变更都要记录 `previousBlockId`（生成预览阶段读取世界当前状态，不是生成之后再读，参考 `RoadGenerator.recordBlock` 的写法），保证落地后可以正确撤销。

---

## 5. 屋顶生成：`BuildingRoofGenerator`

新建 `com.plot.plugin.building.BuildingRoofGenerator`。

- **`RoofType.FLAT`**：不需要额外处理，顶层楼板本身就是屋顶（第 4 章步骤 3 已经生成），这里只需要可选地把顶层楼板材质换成 `roofMaterial`（如果和 `floorMaterial` 不同的话）。
- **`RoofType.GABLE`（双坡顶）/ `RoofType.HIP`（四坡顶）**：**只支持 `isRectangular == true` 的建筑**（即认领来源是 `RectangleShape`，或者 `outerPoints` 经检测正好是 4 个点组成的轴对齐矩形）。非矩形轮廓选择了坡屋顶类型时，生成阶段自动降级为 `FLAT` 并在结果里附一条警告信息（不要抛异常中断整个生成流程，参考道路系统里"复杂路口降级处理+警告"的处理方式）。
  - 双坡顶：取矩形较长的一边方向作为屋脊方向，屋脊线位于矩形中轴线，从两侧墙顶到屋脊线按 `roofPitchRatio` 逐格升高，两侧对称。
  - 四坡顶：四个方向都往屋脊线（如果矩形接近正方形，屋脊退化成一个点，即攒尖顶的特殊情况）方向按坡度升高，可以用"取该点到最近一条短边的距离和到屋脊线的距离，谁小算谁"这种简化算法，不需要引入完整的多边形骨架（straight skeleton）算法，矩形场景下这个简化足够。

**明确不做**（v1 范围外，不要展开）：任意多边形（非矩形）的攒尖顶/复杂坡屋顶、屋顶天窗、老虎窗、多重屋檐这些细节造型，都留作后续增强。

---

## 6. UI：`BuildingPlugin`

新建 `com.plot.plugin.BuildingPlugin`，直接对照 `com.plot.plugin.RoadSystemPlugin` 的结构复刻（构造函数注册方式参考现有 `super(id, nameKey, descKey, icon)` 写法，图标新建一个 `ExtensionPanelIcons.BUILDING`，仿照现有 `EARTHWORK` 常量的写法加一个 `building.svg`，没有现成图标素材的话先用一个占位方块图标，不要卡在这上面）。

Tab 结构（照抄道路系统的四段式）：
```
[建筑]
├─ 概览：已认领建筑列表（名称/占地面积/层数），点击定位、删除（带二次确认弹窗，参考 renderDeleteConfirmPopup）
├─ 认领：提示"请先用多边形/矩形工具画出建筑轮廓"，显示当前选中图形信息，支持多选批量认领（参考道路系统 RoadPathPickSession 的多选累加模式，允许一次选中多个轮廓批量认领成多栋建筑）
├─ 编辑：选择一栋已认领的建筑，编辑层数/层高/墙厚/材质/屋顶类型/开窗参数；门的增删单独做一个小列表（选墙段下拉 + 位置比例滑条 + 楼层选择）
├─ 生成：计算预览（统计方块数量/挖填方量）→ 投影虚影 → 生成确认弹窗（复用 `checkWorldModificationReadiness()` 做权限预检，按钮不满足条件时禁用，参考道路系统这部分的现有写法）→ `BuildingGenerateCommand.executeScheduled(...)` 异步落地 → 完成回调里 `CommandManager.pushExecuted(command)`
```

所有新增文案记得同步补充 `en_us.json`/`zh_cn.json`。

---

## 7. 落地撤销：`BuildingGenerateCommand`

新建 `com.plot.core.command.commands.BuildingGenerateCommand`，**直接照抄 `GenerateRoadCommand` 的结构**（`BlockWriter` 抽象、`executeScheduled`/`undoScheduled` 走 `BlockPlacementScheduler`、`schedulePlacement` 开关方便测试用同步路径），只是把内部字段类型换成建筑生成结果，不需要重新设计落地机制。

---

## 8. 持久化

参考道路系统的做法：`BuildingPlugin.onEnable()` 订阅 `ProjectLoadedEvent`/`ProjectSavedEvent`，用 `getDataFolder()` + 工程文件路径哈希存取 `BuildingProject` 的 JSON，`onDisable()` 对称取消订阅。这部分逻辑和道路系统里的实现几乎一样，可以直接参考 `RoadSystemPlugin` 对应代码抄一遍，不需要重新设计。

---

## 9. 需要新建的文件清单

```
com/plot/plugin/BuildingPlugin.java
com/plot/plugin/building/model/BuildingFootprint.java
com/plot/plugin/building/model/BuildingProject.java
com/plot/plugin/building/model/BuildingProjectHistory.java
com/plot/plugin/building/BuildingFoundationUtils.java
com/plot/plugin/building/BuildingGenerator.java
com/plot/plugin/building/BuildingRoofGenerator.java
com/plot/plugin/building/BuildingGeometryUtils.java   -- 等距开窗采样等纯函数工具
com/plot/core/command/commands/BuildingGenerateCommand.java
com/plot/ui/component/ExtensionPanelIcons.java         -- 新增 BUILDING 图标常量（在现有文件基础上加一行，不是新建文件）
src/main/resources/assets/plot/lang/en_us.json         -- 新增文案
src/main/resources/assets/plot/lang/zh_cn.json         -- 新增文案
```

**允许的小范围重构**：如果 `GenerateRoadCommand.BlockRecord` 目前是私有内部类，需要挪成 `com.plot.core.command.BlockRecord` 独立公共类供两个插件共用（第 4 章提到过），这是本次任务里唯一允许触碰道路系统既有文件的地方，改动仅限于"把内部类提出来变成同名的独立类"，不要顺带改动 `GenerateRoadCommand` 的其它逻辑。

**不允许修改**：`Shape.java`/`Polygon.java`/`RectangleShape.java`/`Project.java`/`ProjectSnapshot.java`/`Layer.java`/`ToolManager.java`/`CommandManager.java`（除了已有的 `pushExecuted` 直接复用，不需要改它）、`com.plot.plugin.road` 包下的任何文件。

---

## 10. 验收标准

1. 认领一个矩形轮廓和一个非矩形多边形轮廓，分别生成 `BuildingFootprint`，`isRectangular` 标记正确。
2. `BuildingFoundationUtils.computeBaseElevation`：构造一批带明显众数的假高度数据（比如 `[64,64,64,64,65,63]`），应该返回 `64`；构造众数并列的数据（比如 `[64,64,65,65]`），应该返回较高的 `65`。
3. 用 `Polygon.contains()` 而非 `Shape.containsPoint()` 做楼板填充的回归检查：构造一个较大的矩形轮廓，验证其几何中心点被正确判定为"在内部"（如果误用了 `containsPoint()`，这个测试会失败，因为中心点离边界的距离通常远大于默认容差 5.0）。
4. 双坡顶生成：给一个矩形轮廓生成 `GABLE` 屋顶，验证屋脊线位置和两侧对称的坡度渐变符合 `roofPitchRatio` 设定。
5. 非矩形轮廓选择 `GABLE`/`HIP` 屋顶类型时，生成结果应自动降级为 `FLAT` 并带有对应的警告信息，不应该抛异常。
6. 开窗：给一个四边形轮廓、`windowSpacing=4`，验证每条外墙边上生成的窗户数量和间距符合预期，且相邻两扇窗之间的距离误差在合理范围内（不需要精确到整数格，允许类似道路灯柱采样那样的近似）。
7. 落地撤销：完整走一遍 `execute → undo`，验证方块状态能正确恢复（复用道路系统那一套已经验证过的 `BlockPlacementScheduler` 机制，这部分不需要重新测，但建议至少写一个端到端的冒烟测试确认 `BuildingGenerateCommand` 接入没有接错）。
8. `BuildingProject.toJson()/fromJson()` 往返，所有字段（包括 `doors` 列表）保持一致。

---

## 11. 实施顺序建议

1. 第 2 章数据模型 + 第 3 章地基整平纯函数，先写测试跑通（不依赖 World，参考 `RoadSlopeUtilsTest` 的风格）。
2. 第 4 章墙体+楼板生成（依赖 World，只做平顶，先不做坡屋顶），跑通"认领 → 生成一个盒子楼"这条最短链路。
3. 第 6 章 UI，接上前两步，让整个流程在游戏里跑起来。
4. 第 5 章坡屋顶、开窗、开门，作为在盒子楼基础上的增量功能逐个加。
5. 第 7 章落地撤销 + 第 8 章持久化，参考道路系统现成实现，工作量不大，放最后做。

每完成一步跑一次现有构建/测试确认没有破坏道路系统或其它已有功能。
