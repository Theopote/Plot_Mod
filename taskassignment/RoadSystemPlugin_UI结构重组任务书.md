# Plot Mod - 道路插件任务书：UI 结构重组（统一节点/路口属性入口）

> 面向 Cursor 的开发任务书。这是一次纯粹的 UI 结构调整，**不涉及任何生成算法、数据模型改动**。目的是把现在分散在"概览"和"编辑"两个 Tab 里的节点/路口相关设置（节点标高、立体交叉、路口倒角、路口标线）统一到一个地方，让"选中一个节点该去哪里编辑它"这件事有唯一、明确的答案。

---

## 0. 现状问题（先理解为什么要改，再看怎么改）

节点/路口相关的功能，现在分散在三处调用、两个 Tab：
```
RoadOverviewPanel.renderNodeElevationEditor()   -- 节点手动标高 + 立体交叉，只在"概览"出现
RoadJunctionPanel.renderSummary()               -- 从"概览"调用
RoadJunctionPanel.renderEditor()                -- 从"编辑"调用
```
后两者调用的是同一份 `renderControls()` 逻辑（不是重复实现，这点不用改），但入口分散在两个 Tab，导致用户想编辑一个节点的完整设置（标高、立体交叉、倒角半径、标线），得在"概览"和"编辑"之间来回切换。`RoadOverviewPanel` 里还专门写了一句 `plugin.road.overview_edit_tab_hint` 提示文案，试图靠文字提醒用户"这里的东西要去另一个 Tab 找"——这次要把这个问题从结构上解决，而不是继续加提示文案。

**顺带确认一下不需要动的地方**：`RoadEditPanel` 里"多选时隐藏单条边详情"（`if (selectedCount > 1) { ...return; }`）这部分逻辑已经是对的，这次不要碰。

**这次也顺便注意到一个可以参考的现成模式**：Plot 核心的 `PropertyPanel.java` 已经有一个"画布上选中路口节点时，直接在核心属性面板里显示路口设置"的钩子（`RoadJunctionPropertyProvider`/`hasJunctionPropertyContent()`/`renderPropertySection()` 这一整套），说明"选中什么就显示什么属性"这个模式在 Plot 里已经有先例，这次插件内部的 Tab 重组要跟这个已有模式保持风格一致，不要发明一套不一样的交互逻辑。

---

## 1. Tab 结构调整

四个 Tab 的名字和大方向不变（概览/认领/编辑/生成），但内部职责重新划分：

### 1.1 "概览" Tab：只负责看和选，不放编辑控件

`RoadOverviewPanel.render()` 里删掉：
- `junctionPanel.renderSummary()` 这一行调用；
- `renderNodeElevationEditor()` 这个方法整个搬走（不是删除，是移动到 1.2 节说的统一入口里）；
- 对应的 `plugin.road.overview_edit_tab_hint` 提示文案也删掉（结构理顺之后不再需要这句"请去别处"的提示）。

"概览"保留：路网统计数字、`RoadNetworkOverviewRenderer` 平面示意图、点选交互（点击节点/边触发选中，这个选中行为本身保留，只是选中之后"去哪里看详情"这件事交给"编辑" Tab 统一处理）。

### 1.2 "编辑" Tab：统一的"选中什么就编辑什么"

`RoadEditPanel.render()` 现在的结构是"边列表 → 批量编辑 → 路口编辑器 → 单条边详情"，这次调整成更清晰的选中态分派：

```
边列表（不变）
    ↓
if 选中了一个节点（不是边）:
    渲染节点完整设置 —— 这里是新的统一入口，包含：
      · RoadOverviewPanel 原来的 renderNodeElevationEditor()（标高 + 立体交叉）
      · junctionPanel.renderEditor()（倒角半径 + 标线，也就是原来的 renderControls）
    两者顺序上先后放在同一个区域即可，不需要合并成一个方法，只要用户不用切 Tab 就能看到就行
else if 选中了多条边:
    渲染批量编辑（不变）
else if 选中了一条边:
    渲染单条边详情（不变）
else:
    显示"请选中一条边或一个节点"的提示
```

**需要注意**：现在"节点"和"边"的选中状态好像是分开管理的（`getSelectedNodeId()`/`getSelectedEdgeIds()`），点了节点之后边的选中状态是不是要自动清空（反之亦然），需要确认现有的 `handleNodeSelect`/`handleEdgeSelect` 是怎么处理互斥关系的，如果现在两者可以同时非空（比如用户先选了边，又点了个节点，边的选中没有被清掉），这次要在"编辑" Tab 的分派逻辑里明确一个优先级（建议：节点选中优先于边选中，因为点节点通常是更明确的意图），并且在用户做出新选择时把另一种选中状态清空，避免出现"选了节点又选了边，界面不知道该显示哪个"的歧义状态。

### 1.3 "认领"、"生成" Tab：不变

这两个 Tab 这次不涉及。

---

## 2. 需要修改的文件清单

```
com/plot/plugin/road/ui/RoadOverviewPanel.java  -- 删除 renderNodeElevationEditor 调用（方法本身挪到 RoadEditPanel 或者新建一个小的辅助类共享）、删除 junctionPanel.renderSummary() 调用、删除 overview_edit_tab_hint 相关文案调用
com/plot/plugin/road/ui/RoadEditPanel.java      -- 新增"选中节点时显示什么"的分支，整合节点标高/立体交叉/路口编辑器
com/plot/plugin/road/manager/RoadNetworkManager.java（或者选中状态实际管理的那个类）-- 确认/补充节点选中与边选中的互斥处理
src/main/resources/assets/plot/lang/en_us.json -- 删除不再使用的 overview_edit_tab_hint，新增"请选中一条边或一个节点"提示文案（如果原来没有的话）
src/main/resources/assets/plot/lang/zh_cn.json -- 同上
```

**`renderNodeElevationEditor()` 这个方法搬到哪个文件**，你自己判断——可以直接挪进 `RoadEditPanel.java` 作为私有方法，也可以新建一个 `RoadNodePropertyPanel.java`（类似 `RoadJunctionPanel` 的角色，专门管"节点级别的属性"，这样以后节点相关的设置再增加，就有一个明确该往哪加的地方，不用再散落）。**我倾向于新建一个 `RoadNodePropertyPanel.java`**，把"节点标高/立体交叉"和"路口倒角/标线"这两块目前分属两个类的东西，未来有可能进一步合并成一个真正统一的"节点属性"类，这次先不强求合并，但至少物理上放在一起、由同一个上层调用点驱动，是为将来合并铺路。

**不允许修改**：`RoadJunctionPanel.renderControls()` 内部逻辑（照搬过来用，不要重写）、`RoadOverviewPanel.renderNodeElevationEditor()` 内部逻辑（原样搬过去，不要重写）、`RoadNetworkOverviewRenderer.java`（平面示意图渲染逻辑不涉及）、任何生成/算法相关文件。

---

## 3. 验收标准

1. "概览" Tab 渲染时，不应该再出现任何可编辑的控件（滑条、下拉框、复选框），只有文字统计、示意图、点选交互。
2. 在"概览"或者画布上点选一个路口节点后，切到"编辑" Tab，应该能在一个位置看到该节点的标高设置、立体交叉设置、倒角半径、标线设置，不需要再去别的地方找。
3. 选中节点后再选中一条边，节点的选中状态应该被正确清除（编辑 Tab 应该切换成显示边的详情，不应该同时显示节点和边两种内容，也不应该都不显示）。
4. 原有的批量编辑（多选边）、单条边详情、坡度覆盖编辑功能行为不变，这次改动不应该引入任何回归——运行现有的道路插件测试套件确认。
5. `plugin.road.overview_edit_tab_hint` 这个 key 从语言文件里删除后，确认没有代码还在引用它（参考之前清理废弃 i18n key 那次任务的验证方式）。

---

## 4. 实施顺序

1. 先确认节点选中和边选中的互斥关系现状（第 1.2 节提到的那个问题），需要补的话先补上，这是后面分派逻辑能正确工作的前提。
2. 把 `renderNodeElevationEditor()` 搬到新位置，`RoadEditPanel` 接入"选中节点时显示节点属性"的分支，`RoadOverviewPanel` 删除对应调用。
3. 实机测试一遍：点选边、点选节点、多选边，确认"编辑" Tab 显示的内容始终和当前选中状态对应，不会出现内容对不上或者同时显示多种内容的情况。

这次改动风险较低（不涉及生成逻辑，主要是界面代码的搬移和分派条件调整），但因为改的是用户每次操作都会用到的核心交互路径，建议做完之后完整走一遍"认领 → 选中节点调整设置 → 选中边调整参数 → 生成"这条流程，确认整体操作手感是顺的，没有哪个环节会让人愣一下"这个东西现在该去哪找"。
