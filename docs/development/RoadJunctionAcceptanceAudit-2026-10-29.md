# Road Junction Acceptance Audit — 2026-10-29

只读深查 `CrossingMaterializer → ElevationResolver → JunctionGenerator → Assembler/flush → Clearance` 后的缺测与回归落地说明。

## 链路结论

| 环节 | 状态 | 备注 |
|------|------|------|
| Materialize sharedElevation | 有策略 | 仅双方 EDITED 或 USER 锁匹配时权威 |
| NetworkNodeElevationResolver | 有测试 | 共识 + feasible refine |
| JunctionGenerator degree≤2 | **已补回归** | 跳过广场；自环需 `breakJunctionSelfLoops` |
| Plaza 平面 Y | 已有 + **已补 T/十字** | 无 per-cell 混合 |
| flushJunctionSolids 保护上层 | 已有 + **已补 merge 回归** | plaza override + expectedProtected |
| SlopeEnvelopeClearancePass | 已有 + **已补挖填过渡** | 实机 A03 仍需目视 |

## 本轮新增自动化

- `RoadJunctionAcceptanceRegressionTest` — T 字平面、degree≤2 跳过、立交 merge 护甲板、十字预览
- `ProfessionalToolDraftRegressionTest.singleFieldEditPreserves…` — 单字段编辑保留未暴露材质/null 继承
- `RoadProfileGenerationParityTest.confirmedEditedDesignOverlayMatches…` — 确认后设计线↔建造
- `SlopeEnvelopeClearancePassTest.cutToFillTransition…` — 挖填半段均有边坡列
- `RoadVoxelProfileCompileJob.phaseTiming()` + 测试 — 三阶段耗时统计（先度量再拆分）

## Golden 重跑（本轮独立）

`./gradlew roadGoldenAcceptance`：54 tests，**R01–R06 / R10 / R12 与语义/工作流通过**；路口相关 **R03–R05 通过**。

既有 Snapshot 漂移（与本轮编辑器/分帧改动无关，未刷新 baseline）：

| ID | expected surfaceBlocks | actual |
|----|------------------------|--------|
| R07 | 197 | 199 |
| R08 | 371 | 361 |
| R09 | 349 | 207 |
| R11 | 549 | 547 |

立交净空/merge 保护由 `RoadJunctionAcceptanceRegressionTest` + stack generation 测试覆盖；R11 指标漂移需单独 baseline 决策，不在本轮盲刷。

## 仍属实机缺口

- A03 山体+悬崖混合观感
- A05 复杂立交实景
- A06 桥隧洞口/桥台衔接

映射表见 `RoadGoldenAcceptance.md` A01–A08 节。
