# Plot Mod - 土方平衡插件（EarthworkPlugin）开发任务书

> 面向 Cursor 的开发任务书。`EarthworkPlugin`/`EarthworkConfig` 目前已经存在，但只是 UI 骨架——所有工具按钮都是空函数体，`cutVolume`/`fillVolume`/`targetElevation`/`autoBalance`/`fillFactor` 这些字段也没有任何真实计算逻辑喂给它们。这次任务是把它做成一个真正能用的插件，架构上对齐道路系统、建筑插件已经跑通的模式（认领 → 编辑 → 预览 → 落地撤销），复用已经验证过的基础设施，不要重新发明。

---

## 0. 背景与两个必须遵守的前提

1. **多边形/区域内部点判定统一使用 `Polygon.contains(Vec2d)`，禁止使用 `Shape.containsPoint(Vec2d, double)`**。原因和建筑插件任务书里说的一样：后者是"离边界的容差判断"，不是真正的"是否在内部"，会导致区域中心被误判成"不在范围内"。
2. **认领的区域来源不限于矩形/多边形**，`CircleShape`、`EllipseShape`、`RectangleShape`、`Polygon` 都要支持——这几个类都已经有 `getPoints()`，统一做法是：认领时不管来源是哪种 Shape，都调用 `getPoints()` 拿到边界点序列，再构造成一个 `Polygon` 对象（参考建筑插件里 `BuildingGeometryUtils.toPolygon` 的写法），后续所有几何计算统一在这个 `Polygon` 上进行，不要为每种 Shape 类型分别写一套判断逻辑。

---

## 1. 核心概念：土方平衡算法

"平衡"指的是：给一片区域，选一个目标整平标高 `Z`，使得该区域内**挖方体积**和**填方体积（考虑压实系数换算后）**尽量相等，从而不需要从场地外部运入或运出额外的土方。

- **挖方体积** `cutVolume(Z) = Σ max(0, groundHeight_i - Z)`，对区域内所有采样格点求和；
- **填方体积（几何量）** `fillVolumeGeometric(Z) = Σ max(0, Z - groundHeight_i)`；
- **填方所需体积（松散方）** `fillVolumeRequired(Z) = fillVolumeGeometric(Z) × fillFactor`（`fillFactor` 就是 `EarthworkConfig` 里已经有的字段，默认 1.1，表示压实后的填方比松散状态收缩，所以填 1 格实际密实体积需要略多于 1 格的松散材料，这是现有字段的既定含义，直接沿用，不要改字段语义）；
- **平衡目标**：找到 `Z` 使得 `cutVolume(Z)` 尽量接近 `fillVolumeRequired(Z)`。

`cutVolume(Z)` 随 `Z` 增大单调递减，`fillVolumeGeometric(Z)`（进而 `fillVolumeRequired(Z)`）随 `Z` 增大单调递增，所以 `cutVolume(Z) - fillVolumeRequired(Z)` 是一个关于 `Z` 单调递减的函数，只需要在 `[区域最低点, 区域最高点]` 范围内做**整数二分查找**，找到这个差值最接近 0（由正变负）的整数 `Z` 即可，不需要复杂的优化算法。

新建 `com.plot.plugin.earthwork.EarthworkBalanceUtils`（纯函数、不依赖 World，参考 `RoadSlopeUtils`/`BuildingFoundationUtils` 的风格）：
```java
public static int findBalancedElevation(List<Integer> groundHeightSamples, float fillFactor);

public static long computeCutVolume(List<Integer> groundHeightSamples, int targetElevation);

public static long computeFillVolume(List<Integer> groundHeightSamples, int targetElevation); // 几何量，不乘 fillFactor
```
`autoBalance = true` 时用 `findBalancedElevation` 算出的标高；`autoBalance = false` 时直接用用户手动指定的 `targetElevation`（`EarthworkConfig`/`GradingRegion` 里已有的字段）。

配一组单元测试：构造一批明显偏高的地形样本，验证算出的平衡标高比简单取平均值更低（因为高处贡献的挖方"更好用"，平衡点通常会比算术平均更靠近较低的一侧，具体数值以二分查找的实际结果为准，测试里验证"结果落在最低点和最高点之间"以及"在该标高上下微调 1 格，差值绝对值不会变得更小"这种性质，而不是断言一个写死的具体数字）。

---

## 2. 数据模型

新建包 `com.plot.plugin.earthwork.model`。**这次不要把区域数据塞进 `EarthworkConfig`**——`EarthworkConfig` 保留作为插件的全局默认偏好（默认网格大小、默认压实系数、默认是否自动平衡），跟道路系统/建筑插件一样，真正的"某个工程里有哪些整平区域"这类数据要走独立的、按工程持久化的模型：

### 2.1 `GradingRegion`
```java
public class GradingRegion {
    private final String id;
    private String name;
    private List<Vec2d> outerPoints;      // 认领时拷贝的边界点
    private boolean autoBalance = true;
    private Integer manualTargetElevation; // autoBalance=false 时使用
    private float fillFactor = 1.1f;
    private String cutExposeMaterial;      // 挖方裸露材质，空字符串表示保留原地形不替换
    private String fillMaterial = "minecraft:dirt";
    private int gridSize = 5;              // 采样/预览网格粒度，仅影响统计计算和网格可视化的精细程度，不影响最终逐格生成

    // 生成后回填的只读统计（不参与编辑，仅用于面板展示）
    private transient long lastCutVolume;
    private transient long lastFillVolume;
    private transient int lastResolvedElevation;

    // getter/setter，范围校验参考 RoadEdge/BuildingFootprint 现有写法
}
```

### 2.2 `EarthworkProject`（对应道路系统的 `RoadNetwork`、建筑插件的 `BuildingProject`）
```java
public class EarthworkProject {
    private final Map<String, GradingRegion> regions = new LinkedHashMap<>();
    // 增删改查、toJson/fromJson（照抄 BuildingProject 的 DTO 转换写法）、saveTo/loadFrom
}
```

### 2.3 撤销栈
`EarthworkProjectHistory`，直接照抄 `RoadNetworkHistory`/`BuildingProjectHistory` 的实现（JSON 快照深拷贝栈），不需要重新设计。

---

## 3. 生成逻辑：`EarthworkGenerator`

新建 `com.plot.plugin.earthwork.EarthworkGenerator`，结构参考 `BuildingGenerator`：一个统一入口 + 内部拆分成若干私有方法，返回结果对象 `EarthworkGenerationResult`（内部维护 `List<BlockRecord>`，**直接复用 `com.plot.core.command.BlockRecord`**，这个类建筑插件那次任务已经从 `GenerateRoadCommand` 里抽出来了，土方插件和道路、建筑三边共用，不要再建一个重复的类）。

生成步骤：

1. 用 `Polygon.contains()` 收集 `outerPoints` 范围内所有整数格点（参考 `BuildingGeometryUtils.collectFootprintCellCenters` 的写法，可以直接复用或者照抄一份到 `EarthworkGeometryUtils`）；
2. 对每个格点采样当前地面高度（复用现有的地形高度查询方式，参考 `RoadGenerator`/`BuildingGenerator` 里 `getTopHeight` 的调用方式）；
3. 调用 `EarthworkBalanceUtils` 算出/取得 `targetElevation`；
4. 逐格比较：地面高度 > 目标标高 → 从目标标高+1 到地面高度，逐格清空（`cutExposeMaterial` 为空则清成空气，非空则替换成该材质，模拟裸露地表处理，比如切出来的坡面刷一层沙土）；地面高度 < 目标标高 → 从地面高度+1 到目标标高，逐格填充 `fillMaterial`；地面高度等于目标标高则跳过；
5. 统计真实挖填方量（不是理论值，是实际逐格执行时累加的数字），回填进 `GradingRegion` 的 `lastCutVolume`/`lastFillVolume`/`lastResolvedElevation` 三个只读字段供 UI 展示。

所有方块变更记录 `previousBlockId`（生成预览阶段读取世界当前状态），保证落地后可以正确撤销，写法照抄 `BuildingGenerator.recordBlock`。

**这次不做边坡处理**（跟建筑插件的地基整平一样，范围边缘允许出现垂直落差，不做斜坡过渡），这个和道路系统的边坡是两个不同的功能，不要混着做。

---

## 4. 落地撤销：`EarthworkGenerateCommand`

新建 `com.plot.core.command.commands.EarthworkGenerateCommand`，**直接照抄 `BuildingGenerateCommand`/`GenerateRoadCommand` 的结构**（`BlockWriter` 抽象、`executeScheduled`/`undoScheduled` 走 `BlockPlacementScheduler`、`schedulePlacement` 开关方便测试用同步路径）。这是这三个生成类插件里第三次用到同一套落地机制了，如果你在写的时候发现三份代码高度重复、只有"如何从结果对象里取出 records 列表"这一点不一样，可以考虑重构成一个通用的 `AbstractBlockGenerateCommand`，把这个判断留给你自己看代码重复度决定要不要做，**这不是本次任务的强制要求**，做不做都可以，不要因为纠结要不要重构而拖慢进度。

---

## 5. UI：`EarthworkPlugin` 改造

现有 `EarthworkPlugin.java` 目前是单页面、没有 Tab 分区的写法（网格设置 / 计算设置 / 土方统计 / 工具按钮，四个 `beginChild` 区块）。这次改造成和道路系统/建筑插件一致的**四段式 Tab 结构**：

```
[土方平衡]
├─ Tab 概览：已认领的整平区域列表（名称/面积/当前挖填方统计），点击定位、删除（带二次确认弹窗）
├─ Tab 认领：提示"请先用多边形/矩形/圆形/椭圆工具画出需要整平的区域范围"，显示当前选中图形信息，支持多选批量认领（参考建筑插件/道路系统的多选累加模式）
├─ Tab 编辑：选择一个已认领的区域，编辑名称/自动平衡开关/手动标高（仅 autoBalance=false 时可编辑，UI 上根据开关状态置灰）/压实系数/挖填材质/网格粒度
├─ Tab 生成：计算预览（调用 `EarthworkGenerator` 算出统计但不落地，展示 `cutVolume`/`fillVolume`/`resolvedElevation`）→ 投影虚影（`GhostBlockManager`，挖方部分用一种偏红的材质提示、填方部分用偏蓝的材质提示，方便用户直观区分哪里要挖哪里要填——具体材质可以选现有的彩色玻璃/羊毛类方块，不需要新增资源，这个虚影提示只是预览阶段的视觉辅助，不代表最终生成材质）→ 生成确认弹窗（复用 `checkWorldModificationReadiness()` 做权限预检，写法照抄建筑插件/道路系统这部分现有代码）→ 异步落地 → 完成回调里登记撤销栈
```

网格可视化（`EarthworkConfig` 里已经有的 `showGrid`/`gridSize` 字段对应的功能）：在"编辑"或"生成"Tab 里，如果 `showGrid` 开启，用 `GhostBlockManager` 或者简单的 ImGui 2D 示意图（参考建筑插件横断面预览 `RoadCrossSectionPreviewRenderer` 的思路，这次做一个"区域网格采样点+对应挖填方向"的俯视示意图）展示网格采样点，不强制要求做成 3D 世界叠加，ImGui 里画一个简化的俯视网格图（每个格子按挖/填方向着色）实现成本更低，优先做这个版本。

所有新增/改造的文案记得同步补充 `en_us.json`/`zh_cn.json`，**已有的 `plugin.earthwork.*` 文案 key 尽量复用**（比如 `plugin.earthwork.cut_volume`/`plugin.earthwork.fill_volume`/`plugin.earthwork.target_elevation` 这些已经存在，不需要重新起名字，除非语义对不上）。

---

## 6. 持久化

参考道路系统/建筑插件的做法：`EarthworkPlugin.onEnable()` 订阅 `ProjectLoadedEvent`/`ProjectSavedEvent`，用 `getDataFolder()` + 工程文件路径哈希存取 `EarthworkProject` 的 JSON，`onDisable()` 对称取消订阅（同时 `onDisable()` 里原来就有的 `config.save()` 继续保留，那个存的是插件全局偏好，跟这次新增的按工程持久化是两回事，不要合并成一个文件）。这部分逻辑直接参考 `RoadSystemPlugin`/`BuildingPlugin` 对应代码抄一遍。

---

## 7. 需要新建/修改的文件清单

**新建：**
```
com/plot/plugin/earthwork/model/GradingRegion.java
com/plot/plugin/earthwork/model/EarthworkProject.java
com/plot/plugin/earthwork/model/EarthworkProjectHistory.java
com/plot/plugin/earthwork/EarthworkBalanceUtils.java
com/plot/plugin/earthwork/EarthworkGenerator.java
com/plot/plugin/earthwork/EarthworkGeometryUtils.java
com/plot/core/command/commands/EarthworkGenerateCommand.java
```

**修改：**
```
com/plot/plugin/EarthworkPlugin.java   -- 从单页面改造成四段式 Tab，接入上面新建的模型和生成器
src/main/resources/assets/plot/lang/en_us.json
src/main/resources/assets/plot/lang/zh_cn.json
```

**不允许修改**：`com.plot.plugin.road` 包、`com.plot.plugin.building` 包下的任何文件；`Shape.java`/`Polygon.java`/`Project.java`/`ProjectSnapshot.java`/`CommandManager.java`（`pushExecuted`/`BlockRecord` 直接复用，不需要改）。

---

## 8. 验收标准

1. `EarthworkBalanceUtils.findBalancedElevation`：构造一批地形数据（比如大部分在 64、少数几个在 70），验证算出的平衡标高在 `[64,70]` 区间内，且在该标高上下各偏移 1 格重新计算 `cutVolume - fillVolumeRequired` 的绝对值，都不应该比原标高处的结果更小（验证确实是差值最接近 0 的点，而不是随便一个中间值）。
2. `fillFactor` 变大时（比如从 1.0 调到 1.5），同一批地形数据算出的平衡标高应该相应下降（压实损耗更大，意味着需要更多挖方去换填方，平衡点会往"挖得更多"的方向偏移）——写一个测试验证这个单调关系。
3. 认领 `CircleShape`/`EllipseShape`/`RectangleShape`/`Polygon` 四种来源，都能正确生成 `GradingRegion`，`outerPoints` 数量和形状轮廓对得上。
4. 生成一个明显起伏的地形区域，验证生成结果里高于目标标高的格点全部被清空/替换，低于的全部被填平，等于的不受影响。
5. 落地撤销：完整走一遍 `execute → undo`，方块状态正确恢复（复用已经验证过的 `BlockPlacementScheduler` 机制，写一个端到端冒烟测试确认 `EarthworkGenerateCommand` 接入没接错）。
6. `EarthworkProject.toJson()/fromJson()` 往返，所有字段保持一致。
7. 现有的 `EarthworkConfig` 全局偏好加载/保存（`gridSize`/`showGrid`/`autoBalance`/`fillFactor` 默认值）不受这次改造影响，`onDisable()` 里原有的 `config.save()` 继续正常工作。

---

## 9. 实施顺序建议

1. 第 1 章 `EarthworkBalanceUtils` 纯函数 + 单元测试，先把平衡算法验证正确，这是整个插件的核心，务必先跑通。
2. 第 2 章数据模型 + 第 3 章 `EarthworkGenerator`（先只做整平生成，不做网格可视化），跑通"认领 → 生成一片整平区域"这条最短链路。
3. 第 5 章 UI 四段式改造，接上前两步。
4. 第 5 章的网格可视化（挖填方向着色的俯视示意图），作为增量功能加上。
5. 第 4 章落地撤销 + 第 6 章持久化，参考道路系统/建筑插件现成实现，工作量不大，放最后做。

每完成一步跑一次现有构建/测试，确认没有破坏道路系统、建筑插件或者土方插件原有的全局配置功能。
