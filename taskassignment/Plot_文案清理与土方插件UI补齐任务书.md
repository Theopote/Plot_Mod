# Plot Mod - 收尾任务书：废弃文案清理 + 土方平衡插件 UI 补齐

> 面向 Cursor 的开发任务书。这次是收尾性质的小任务，不涉及任何生成算法改动，风险很低，但每一项都要认真核对，因为"删文案"这种操作最怕手滑删错还在用的 key。

---

## 任务 A：清理 39 个已废弃的 i18n key

### 背景
逐一核实过，以下 key 在 `zh_cn.json`/`en_us.json` 里都有定义，但代码里**没有任何地方**（包括字符串拼接、枚举字段存储等间接引用方式）再引用它们了。基本都是"旧文案被替换成带 `_label`/`_result` 后缀的新 key，旧的没删"或者"插件早期是纯 UI 空壳时候的按钮文案，重做成正式功能后没清理"这两种情况。

### 要做的事
从 `src/main/resources/assets/plot/lang/zh_cn.json` 和 `en_us.json` 两个文件里，**同时删除**以下 key（两个文件都要删，保持两份文件的 key 集合一致）：

```
plugin.road.balance_percent
plugin.road.blocks_count
plugin.road.bridge_count
plugin.road.bridge_detect_count
plugin.road.bridges_count
plugin.road.cut_slope_ratio
plugin.road.cut_volume
plugin.road.edit_path
plugin.road.extra_facilities
plugin.road.fill_slope_ratio
plugin.road.fill_volume
plugin.road.max_slope
plugin.road.operations
plugin.road.path_info
plugin.road.paths_found
plugin.road.preview_first
plugin.road.relaxed_slope_percent
plugin.road.select_edge
plugin.road.select_path_hint
plugin.road.slope_adaptation
plugin.road.slope_format_percent
plugin.road.slope_format_ratio
plugin.road.streetlight_count
plugin.road.tunnel_count
plugin.road.tunnel_detect_count
plugin.road.tunnels_count
plugin.earthwork.calc_settings
plugin.earthwork.cut_area
plugin.earthwork.cut_volume
plugin.earthwork.earthwork_stats
plugin.earthwork.elevation_measure
plugin.earthwork.export_report
plugin.earthwork.fill_area
plugin.earthwork.fill_volume
plugin.earthwork.level_site
plugin.earthwork.net_volume
plugin.earthwork.tools
plugin.earthwork.unit_cubic_meter
plugin.earthwork.volume_calc
```

### 验证方式（删除前后都要做）
删除之前，对着上面每一个 key，在 `com/plot/plugin/road/`、`com/plot/plugin/earthwork/`、`com/plot/plugin/RoadSystemPlugin.java`、`com/plot/plugin/EarthworkPlugin.java`、`com/plot/core/command/commands/GenerateRoadCommand.java`、`com/plot/core/command/commands/EarthworkGenerateCommand.java` 这些目录/文件里搜一遍确认真的没有引用（包括作为普通字符串出现在任何地方，不只是 `PlotI18n.tr(...)` 调用里，防止有拼接构造 key 的写法）。删除之后，全项目跑一次构建，确认没有编译错误（正常不会有，因为 Java 代码里没有直接依赖 JSON key 是否存在，但求稳）。

**不要**因为清理顺手就去改这批 key 对应的编号（比如把 `plugin.road.cut_volume_result` 改名成 `plugin.road.cut_volume`）——只删旧的、不动新的，避免节外生枝。

---

## 任务 B：土方平衡插件补齐排序 + 悬浮提示

### 背景
道路插件经过多轮迭代，已经有 `RoadEdgeListHelper.SortMode`（列表排序）和 `RoadUiWidgets.renderEngineeringTooltip`（工程参数悬浮提示）这两样东西，土方插件因为迭代轮次少，这两样都还没有。这次给土方插件补齐，让两个插件的体验水准一致。

### B1. 区域列表排序

参考 `com.plot.plugin.road.RoadEdgeListHelper` 的写法（`SortMode` 枚举 + `Comparator` 映射），新建 `com.plot.plugin.earthwork.EarthworkRegionListHelper`：
```java
public enum SortMode {
    INSERTION("plugin.earthwork.sort.insertion"),
    AREA_ASC("plugin.earthwork.sort.area_asc"),
    AREA_DESC("plugin.earthwork.sort.area_desc"),
    NAME("plugin.earthwork.sort.name");
    // 构造函数存 i18nKey，参考 RoadEdgeListHelper.SortMode 的写法
}

public static List<GradingRegion> sorted(EarthworkProject project, SortMode mode);
```
排序依据：`AREA_ASC`/`AREA_DESC` 用 `region.computeArea()`，`NAME` 用 `region.getName()` 字典序，`INSERTION` 保持 `project.getRegions()`（`LinkedHashMap`）的原始遍历顺序不变。

在 `EarthworkPlugin.renderOverviewTab()` 里，列表上方加一个排序下拉框（参考 `RoadOverviewPanel`/`RoadEdgeListPanel` 里排序下拉的现成写法抄一遍），`for (GradingRegion region : project.getRegions().values())` 改成 `for (GradingRegion region : EarthworkRegionListHelper.sorted(project, currentSortMode))`。

### B2. 高级参数悬浮提示

`EarthworkPlugin` 里目前 `fillFactor`（压实系数）、`gridSize`（网格粒度）这两个滑条没有任何解释性提示，对不熟悉土方工程概念的用户不直观。参考 `RoadUiWidgets.renderEngineeringTooltip(String i18nKey)` 的写法（这个方法本身不需要复制一份到土方插件，`RoadUiWidgets` 目前是 `com.plot.plugin.road.ui` 包下的，如果要给土方插件复用，两种做法选一种：①把 `renderEngineeringTooltip` 这个通用方法挪到一个不属于任何具体插件的公共位置，比如 `com.plot.ui.component.UIUtils`，两个插件都调用它；②在土方插件里照抄一份等价的小方法。**选①**，因为这个方法本身就是纯 UI 工具、跟"道路"这个业务概念没有关系，属于典型的应该在公共层的东西，参考我们之前处理 `BuildingGeometryUtils`/`EarthworkGeometryUtils` 那次跨插件依赖问题的思路，这次直接一步到位放对位置，不要再造一次需要以后清理的债）。

新增语言文案：
```
plugin.earthwork.sort.insertion / area_asc / area_desc / name
hint.plot.earthwork.fill_factor  （解释"压实系数"：说明填方材料压实后会比松散状态收缩，系数越大表示需要挖更多土才能填够）
hint.plot.earthwork.grid_size    （解释"网格粒度"：说明这个数值只影响统计采样精度和可视化网格密度，不影响最终逐格生成范围）
```
在对应滑条后面加 `renderEngineeringTooltip("hint.plot.earthwork.fill_factor")`/`renderEngineeringTooltip("hint.plot.earthwork.grid_size")` 调用，位置和写法参照道路插件里 `RoadDefaultParamsPanel.java` 给 `max_slope`/`relaxed_slope_percent` 加提示的现成代码抄一遍。

### B3.（顺手做）方块选择器打开逻辑去重

`EarthworkPlugin.java` 里第 604 行附近有一段内联的 `client.setScreen(BlockConfigNativeScreen.forSingleSelection(...))` 调用逻辑，跟 `RoadUiWidgets.openBlockPicker` 几乎一模一样，只是各自独立实现的。既然任务 B2 已经决定把 `renderEngineeringTooltip` 挪到公共层，顺手把 `openBlockPicker` 也一起挪过去（同一个公共类里），两个插件都改成调用这个共用方法，不要各自维护一份相同逻辑。

---

## 需要新建/修改的文件清单

**新建：**
```
com/plot/plugin/earthwork/EarthworkRegionListHelper.java
```

**修改：**
```
com/plot/ui/component/UIUtils.java（或者你觉得更合适的公共 UI 工具类位置，只要不属于具体插件包）
  -- 新增 renderEngineeringTooltip、openBlockPicker 两个静态方法（从 RoadUiWidgets 挪过来）
com/plot/plugin/road/ui/RoadUiWidgets.java
  -- renderEngineeringTooltip、openBlockPicker 改成调用挪过去之后的公共方法（或者直接删除本地实现，全部改成引用公共类），道路插件其它调用这两个方法的地方**不需要改**，因为方法签名不变，只是实现挪了位置
com/plot/plugin/EarthworkPlugin.java
  -- renderOverviewTab 接入排序；fillFactor/gridSize 滑条加悬浮提示；第 604 行附近的方块选择器逻辑改成调用公共方法
src/main/resources/assets/plot/lang/en_us.json
src/main/resources/assets/plot/lang/zh_cn.json
  -- 删除任务 A 列出的 39 个 key；新增任务 B 需要的排序/提示文案
```

**不允许修改**：`RoadNetworkBuilder.java`/`RoadGraphEdits.java`/`RoadGraphQueries.java`/任何生成算法相关的文件（`RoadGenerator.java`/`RoadConstructionEvaluator.java`/`EarthworkGenerator.java`/`EarthworkBalanceUtils.java`）——这次任务完全不涉及生成逻辑。

---

## 验收标准

1. 删除 39 个 key 之后，全项目搜索确认这些字符串在 Java 代码里都不再出现（哪怕不是作为 `PlotI18n.tr` 参数），且构建正常通过。
2. `EarthworkRegionListHelper.sorted`：构造几个不同面积/名称的 `GradingRegion`，验证四种排序模式结果符合预期，`INSERTION` 模式下顺序跟 `project.getRegions().values()` 的原始遍历顺序一致。
3. 土方插件概览 Tab 实际渲染时，排序下拉框能正常切换，列表顺序跟着变化。
4. `fillFactor`/`gridSize` 滑条鼠标悬停能看到提示文字，中英文语言文件都有对应内容。
5. 道路插件原有的悬浮提示、方块选择器功能在这次重构（把实现挪到公共类）之后，行为跟改动前完全一致——运行现有的道路插件测试套件确认没有回归。
6. 土方插件的方块选择器改成调用公共方法后，功能行为不变（点击后弹出原生方块选择界面，选中后正确回填材质字段）。

---

## 实施顺序

1. 任务 A（删 key），最简单、风险最低，先做完垫底。
2. 任务 B3（把 `renderEngineeringTooltip`/`openBlockPicker` 挪到公共类），这是任务 B1/B2 的前置依赖，而且涉及改动道路插件现有代码，需要单独验证不影响道路插件原有功能。
3. 任务 B1（排序）、B2（悬浮提示），在 B3 完成、公共方法就位之后再接入土方插件。

每一步做完都跑一次现有构建和测试套件确认没有破坏道路系统或建筑插件（如果建筑插件也用到了什么共用的 UI 组件的话，顺带确认一下）。
