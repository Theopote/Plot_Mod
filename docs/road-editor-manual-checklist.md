# Road Editor Manual Checklist

Use this list when validating floating editors and profile authority after code changes.

## Resolutions and DPI

- [ ] 1280×720: all six floating windows open without clipping footer buttons
- [ ] 1920×1080: centerline segment list scrolls independently; footer remains visible
- [ ] 150% display scaling: conflict banner and reload confirm text wrap cleanly (EN + ZH)

## Floating editor lifecycle

- [ ] Cross section / variable XS / centerline / station facilities / batch / diagnostics open on selected road
- [ ] Dirty window: scope change shows close confirm; choosing **Continue editing** keeps selection on bound road
- [ ] Version conflict on Apply: banner appears; **Reload from live network** shows second confirm about discarding edits
- [ ] After reload: working copy matches live; new edits can be applied without overwriting unrelated roads

## Profile and build consistency (smoke)

- [ ] MANUAL_PROFILE road: green design line, gray block line, and preview build align within one block
- [ ] AUTO road with dormant stored VA: generation preview does not follow old curve until EDITED is re-confirmed
- [ ] Stale gray block line is dimmed / labeled as last preview while recomputing (A07)

## In-game acceptance mapping (A01–A08)

See `docs/development/RoadGoldenAcceptance.md` for the full R/W/J mapping. Quick smoke order:

- [ ] **A01** flat straight (R01)
- [ ] **A04** T or cross junction plane (R03/R04)
- [ ] **A05** grade-separated or figure-eight stack (R11)
- [ ] **A07** edit profile → rebuild matches design line
- [ ] **A08** floating editor cancel / restore / conflict reload

Automation gaps that still need eyes-on: **A03** cliff+cut/fill, **A06** bridge/tunnel junctions.

## Locale

- [ ] English: `reload_from_live_confirm` reads clearly next to reload button
- [ ] Chinese: same flow; no truncated warning on narrow panels

## 路灯设计与道路布置

操作入口为 **道路 → 横断面 → 专业工具 → 路灯设计**。选中一条道路后，可从八个内置预设开始，调整杆高、材料、基座和最多三层灯头。在设计器中用旋转和缩放检查单体方块，或切换到横断面情境查看灯柱与边带的位置。道路生成预览显示工程坐标中的实际布置。

`./gradlew runGameTest` 在隔离的 Minecraft 测试世界中逐格检查预设的方块 ID、显式状态、邻居更新后的支撑，以及吊灯布置记录的写入和回退。以下项目补充客户端交互、视觉外形和道路施工操作验收。

- [ ] 依次打开八个预设，检查倒 L、十字、双吊灯和檐式造型能区分。旋转后检查链条与灯笼的方向。
- [ ] 编辑双层或三层灯并另存为自定义预设。关闭并重开窗口后选用该预设，再重新加载道路项目检查已应用样式。改名或删除预设后，检查已应用道路仍保有各自的样式。
- [ ] 先改间距，再调出无效的层高组合，检查确认和另存被拦截。调高灯杆或缩短链条后，原编辑应仍在草稿中。取消与恢复草稿，检查道路保持原方案。确认后检查撤销、重做，以及生成预览是否刷新。
- [ ] 在平直道路切换左侧、右侧、双侧、交错和中央分隔带，检查间距、方向及内移量。中央分隔带需要先在横断面中启用。
- [ ] 在变截面设施页核对整路灯光的继承提示。在弯道、纵坡及变宽段检查挑臂朝向、灯柱竖直、灯脚与铺装贴合。在没有可用铺装边带、仅有边坡或内移越过有效边带的位置，检查相应灯位被跳过。
- [ ] 在 T 字和十字路口检查圆角及端点退让。放大挑臂后再次检查整盏灯避开路口。
- [ ] 检查隧道洞身、洞口和立交保护区内的净空、衬砌及上层桥面。切换桥面选项后，检查灯仅出现在启用且拥有安全铺装支撑的边带。
- [ ] 在灯头或灯臂预定位置放置一个与道路清障无关的现有方块。重新预览，检查整盏灯被跳过且原有方块保留。
- [ ] 生成道路预览并实际建造，逐格比较灯柱、梁、链条和吊灯，检查方块状态。撤销后检查原来的方块状态得到恢复。

道路方案随项目保存。自定义预设保存在客户端配置目录的 `plot-road-lamps.json`。恢复原路灯设置会让该道路重新使用旧版横断面设置。改变样式后，已建成的灯仍由原施工记录的撤销处理。
