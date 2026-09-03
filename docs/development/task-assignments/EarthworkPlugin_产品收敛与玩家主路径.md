# EarthworkPlugin：按新定位收敛（任务书）

> 产品准绳：[Earthwork_产品定位.md](../Earthwork_产品定位.md)  
> ADR：[0009](../../decisions/0009-earthwork-minecraft-tool-not-civil-cad.md)  
> 本任务书**禁止**把冻结清单里的专业模型做成主流程。

---

## 目标

把插件从「专业土方系统」收敛为「框选 → 推荐高度 → 预览挖填 → 微调 → 施工」，并保证数字在含边坡的最终地形上正确。

---

## 不做

- 材料兼容矩阵、土质分类、成本、跨场地调运 UI
- Newton / 连续优化 / VerticalAdjustmentPolicy 全家桶
- 基坑工作面、底板厚度、施工控制标高文案
- 继续新增领域对象（新的 Zone 类型、新的 Balance 层）

---

## P0（必须先做）

1. **平衡在最终边坡之后**  
   对每个候选整数 Y：完整 `buildTerrain(y)`（含边缘处理）→ 再 `calculate`。  
   `score = abs(cut - fill)`（Minecraft Mode 下 cut/fill 按 1:1）。  
   选最小 score。测试：放坡会改变方量时，UI 不得在放坡前数字上显示 Balanced。

2. **锁定高度**  
   建筑平台、道路走廊默认 `autoAdjustElevation = false`。求解不得改这些 Zone 的目标 Y。

3. **Preview / Build / Report 同源**  
   同一 `DesignTerrain` + 同一 volume 结果驱动虚影、落地和面板数字。禁止三套各算一遍。

## P1

- Quick Mode 面板：区域、自动平衡/指定高度、边缘三选一、预览/施工
- 目标高度 slider + 实时 CUT/FILL
- 一键推荐（平衡高度）；主 UI 用 blocks，不用工程字段名
- 默认 conversion 100% / 100%

## P2（可另开任务）

剖面、热力图、标高—方量曲线、Before/After、推荐原因、几何量 vs 改方块、工作量条。  
同时给出「挖填平衡」和「最少施工」两个推荐，不要合成一个分数。

## P3 / P4

P3：Pad / Pit / 挡墙 / 道路衔接的 Minecraft 文案与默认值。  
P4：Learn Mode 包装现有 MaterialConversion 与 Allocation，不加深模型。

---

## 验收对照

玩家框一块地后，应能在不打开任何系数面板的情况下：看到推荐 Y、挖/填/差值、红蓝预览，改 slider 后数字与预览一起变，施工结果与预览一致。
