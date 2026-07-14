# Plot Mod - 建筑插件任务书：列表排序 + 工程参数悬浮提示

> 面向 Cursor 的开发任务书。目的是把建筑插件的 UI 打磨程度补齐到跟道路插件、土方插件一致的水准（这两个插件已经有列表排序和悬浮提示，建筑插件还没有）。做法直接照抄土方插件那次的实现方式，不需要重新设计。

---

## 任务 A：建筑列表排序

### 现状
`BuildingPlugin.renderOverviewTab()` 里 `for (BuildingFootprint building : project.getBuildings().values())` 是直接按 `LinkedHashMap` 插入顺序遍历的，没有排序选项。

### 要做的事
新建 `com.plot.plugin.building.BuildingListHelper`（照抄 `com.plot.plugin.earthwork.EarthworkRegionListHelper` 的结构，不要另起一套写法）：
```java
public enum SortMode {
    INSERTION("plugin.building.sort.insertion"),
    AREA_ASC("plugin.building.sort.area_asc"),
    AREA_DESC("plugin.building.sort.area_desc"),
    NAME("plugin.building.sort.name");
    // 构造函数存 i18nKey + label() 方法，参考 EarthworkRegionListHelper.SortMode 的写法
}

public static List<BuildingFootprint> sorted(BuildingProject project, SortMode mode);
```
排序依据：`AREA_ASC`/`AREA_DESC` 用 `building.computeArea()`，`NAME` 用 `building.getName()` 字典序，`INSERTION` 保持原始遍历顺序不变。

`BuildingPlugin` 新增一个字段 `private BuildingListHelper.SortMode buildingSortMode = BuildingListHelper.SortMode.INSERTION;`，在 `renderOverviewTab()` 列表上方加排序下拉框（照抄 `EarthworkPlugin.renderOverviewTab()` 里 `##earthwork_region_sort` 那个下拉框的写法），`for` 循环的遍历对象从 `project.getBuildings().values()` 改成 `BuildingListHelper.sorted(project, buildingSortMode)`。

---

## 任务 B：工程参数悬浮提示

### 现状
`renderEditTab()` 里这几个参数目前没有任何解释性提示：`roofPitchRatio`（坡度比数值含义不直观）、`manualBaseElevation`（手动标高，跟道路/土方插件是同一个概念但目前没接同一套提示）、`windowSpacing`（0 表示不开窗这一点不明显）、`foundationFillMaterial`（这个材质只在填方地基时才会用到，容易让人不理解"这个材质到底用在哪里"）。

### 要做的事

在这四个参数控件后面各加一次 `UIUtils.renderEngineeringTooltip(...)` 调用（这个方法已经是公共方法，`BuildingPlugin` 直接 import 调用即可，不需要新增方法），新增语言文案：
```
hint.plot.building.roof_pitch          （解释"坡度比"：数值越大坡度越缓，比如填 4 表示水平前进 4 格、垂直升高 1 格；仅对双坡/四坡屋顶生效）
hint.plot.building.manual_elevation    （解释手动标高：指定后建筑地基会以此标高为准整平，不再根据周围地形自动计算）
hint.plot.building.window_spacing      （解释开窗间距：数值为 0 时表示这面墙不开窗）
hint.plot.building.foundation_material （解释地基材质：仅在地基低于原地形、需要填方时用到，纯挖方场景下不会用到这个材质）
```

调用位置分别紧跟在对应滑条/复选框之后，参考 `EarthworkPlugin.java` 里 `UIUtils.renderEngineeringTooltip("hint.plot.earthwork.fill_factor")` 那两处的写法照抄。

---

## 需要新建/修改的文件清单

**新建：**
```
com/plot/plugin/building/BuildingListHelper.java
```

**修改：**
```
com/plot/plugin/BuildingPlugin.java             -- 接入排序下拉框；四处参数加悬浮提示
src/main/resources/assets/plot/lang/en_us.json  -- 新增排序文案（4个）+ 提示文案（4个）
src/main/resources/assets/plot/lang/zh_cn.json  -- 同上
```

**不允许修改**：`BuildingGenerator.java`/`BuildingFoundationUtils.java`/`BuildingRoofGenerator.java`（生成算法不涉及）、`EarthworkRegionListHelper.java`（只参考写法，不要改动这个文件本身）、`UIUtils.java`（`renderEngineeringTooltip` 已经是现成的公共方法，直接调用即可，不需要改它）。

---

## 验收标准

1. `BuildingListHelper.sorted`：构造几个不同面积/名称的 `BuildingFootprint`，验证四种排序模式结果符合预期，`INSERTION` 模式下顺序跟 `project.getBuildings().values()` 的原始遍历顺序一致（参考 `EarthworkRegionListHelperTest` 的测试结构照抄一份）。
2. 建筑插件概览 Tab 实际渲染时，排序下拉框能正常切换，列表顺序跟着变化。
3. 四个参数的悬浮提示鼠标悬停能看到提示文字，中英文语言文件都有对应内容。
4. 现有的建筑插件测试套件（`BuildingGeneratorSmokeTest`/`BuildingGeometryUtilsTest` 等）全部保持通过，这次改动不涉及生成逻辑，不应该有任何回归。

---

## 实施顺序

任务 A、B 相互独立，顺序不重要，可以一起做或者分开做都可以。做完之后跑一次现有构建和测试，确认没有影响建筑插件的生成功能。
