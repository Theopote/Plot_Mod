# ADR 0009: 土方插件是 Minecraft 整形工具，不是 Civil CAD

## Status

Accepted — 2026-09-03

## Context

Earthwork 已从「整平区域」演进为 Site / Zone / Pad / Pit / Corridor、多层平衡、材料换算、跨场地调配矩阵。从专业土方软件看，这套架构仍只是开始；从 Minecraft Mod 看，已经接近玩家可理解的复杂度上限。

上一轮工程向建议（VerticalAdjustmentPolicy、材料兼容矩阵、项目级约束优化、基坑工作面等）在 Civil 3D 语境下合理，但会把主流程变成玩家无法操作的专业系统。

需要一份明确决策，避免后续任务书继续按「在 Minecraft 里实现专业土方工程系统」加功能。

## Decision

1. **产品定义**以 [Earthwork_产品定位.md](../development/Earthwork_产品定位.md) 为准。愿景是：几十秒内把地形变成可建造场地，并用可视化讲清挖填、平衡标高、边坡。

2. **底层领域模型可以保留**（`EarthworkSite`、材料模型、allocation 等），**玩家 UI 默认只暴露约 20%**。Quick / Builder / Learn 三档；Quick 为默认。

3. **默认 Minecraft Mode**：挖 1 ≈ 填 1。换算系数、跨场地矩阵、项目级平衡不进入主路径；可在 Learn / Advanced / 实验中包装。

4. **标高求解用离散整数扫描**，每次候选 Y 必须包含最终边坡后再算方量。禁止把连续约束优化器、Newton、复杂迭代收敛作为近期方案。

5. **区域高度约束简化为** `autoAdjustElevation` + `maxAutoAdjustment`（blocks）。不做 LOCKED / DERIVED / BOUNDED 策略网。

6. **冻结**专业材料分类、跨场地物流、成本、复杂基坑模型、以报表/调运为主流程的功能。解冻必须先改本 ADR 与产品定位文档。

7. **近期优先级**固定为：P0 正确性（边坡后方量、锁定标高、Preview=Build=Report）→ P1 Quick 交互 → P2 可视化学习 → P3 建造联动 → P4 把已有复杂模型做成教学内容。

## Consequences

- 新代码、任务书、UI 文案按「建造玩家 + 学习可视化」写，不按工程造价软件写。
- [EarthworkSite_领域设计.md](../development/EarthworkSite_领域设计.md) 与 [Earthwork_2.0_架构.md](../development/Earthwork_2.0_架构.md) 仍描述内部结构，但**不得单独作为加功能的授权**。
- 已实现的复杂模块优先隐藏或降级，而不是删除；删除需另开任务并保证迁移。
- 道路 / 建筑插件联动仍是目标特色，排在 P3，不因此引入施工组织设计。
