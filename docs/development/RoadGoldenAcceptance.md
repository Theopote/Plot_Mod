# Road Golden Acceptance

> **用途**：ROAD-FINAL-08 封版验收清单 — 自动化 Golden/Acceptance 测试 + 可选实机抽检。  
> **Harness**：`com.plot.plugin.road.golden.*`（无 Minecraft World，FlatTerrainSampler / 合成地形）

---

## 运行方式

```bash
# 全套 Golden（回归 + 语义 + 工作流）
./gradlew roadGoldenAcceptance

# 或分项
./gradlew test --tests "com.plot.plugin.road.golden.*"
./gradlew test --tests "com.plot.plugin.road.RoadWorkflowTest"
./gradlew test --tests "com.plot.plugin.road.model.RoadPhase2WorkflowTest"
```

刷新 Regression Snapshot（R01–R12 metrics）：

```bash
./gradlew test --tests "com.plot.plugin.road.golden.RoadGoldenSnapshotGeneratorTest" --console=plain
# 将 stdout 复制到 RoadGoldenExpectations.java
```

---

## 测试分层

| 层 | 类 | 职责 |
|----|-----|------|
| **A. Regression Golden** | `RoadGoldenRegressionTest` | R01–R12 生成指标 Snapshot 锁定 |
| **B. Semantic Acceptance** | `RoadSemanticAcceptanceTest` | 单场景正确性（桥/隧/路口/环/边坡等） |
| **C. Workflow Acceptance** | `RoadGoldenWorkflowAcceptanceTest` | Undo/Redo、JSON 持久化、可变横断面 |
| **扩展工作流** | `RoadWorkflowTest` | T 字认领、立交、多段 batch edit |
| **Phase 2 全链** | `RoadPhase2WorkflowTest` | HA/VA/VCS/设施 → 图编辑 → 反向 → 再生成 |

详见 `RoadGoldenTestKinds.java`。

---

## 场景矩阵（R01–R12）

| ID | 场景 | 自动化 | 实机抽检要点 |
|----|------|--------|--------------|
| **R01** | 直路 / 平地 | ✅ universal + straight | 路面连续、标高 64 |
| **R02** | 弯路 | ✅ universal | 弯道无断线 |
| **R03** | T 字路口 | ✅ junction ≥3 | 路口填充、标线 |
| **R04** | 十字路口 | ✅ junction ≥4 | 四向汇合 |
| **R05** | 五岔 | ✅ junction ≥5 | 复杂路口 polygon |
| **R06** | 纵坡 | ✅ embankment | 填挖方、纵坡限制 |
| **R07** | 贴地 | ✅ embankment | 起伏地形贴合 |
| **R08** | 桥梁 | ✅ bridge | 河谷架桥、无填谷 |
| **R09** | 隧道 | ✅ tunnel | 山体贯通 |
| **R10** | 涉水 | ✅ bridge | 水面架桥 |
| **R11** | 立体交叉 | ✅ grade-separated | 上跨/下穿 clearance |
| **R12** | 闭合环 | ✅ LOOP topology | 闭环 LOOP 模式 |

---

## 工作流验收（W01–W02 + 扩展）

| ID | 场景 | 自动化 | 实机抽检要点 |
|----|------|--------|--------------|
| **W01** | 认领 → 编辑 → Undo/Redo → 保存 | ✅ `RoadGoldenWorkflowAcceptanceTest.w01` | 编辑 Tab 撤销/重做 |
| **W02** | 可变横断面 + 重载 | ✅ `RoadGoldenWorkflowAcceptanceTest.w02` | VCS 桩号断面切换 |
| **W03** | T 字 + 批量横断面 | ✅ `RoadWorkflowTest.scenario1` | 多选 batch edit |
| **W04** | 立交生成 | ✅ `RoadWorkflowTest.scenario2` | 节点立交 clearance |
| **W05** | Phase 2 全设计链 | ✅ `RoadPhase2WorkflowTest` | HA/VA/VCS/设施/反向 |

---

## 实机抽检清单（可选，封版前 1 轮）

在 dev client 中逐项目视确认（自动化已覆盖的可抽检 2–3 项即可）：

- [ ] 认领直路 + T 字 → 计算预览 → 落地
- [ ] 编辑 Tab：修改横断面 → Undo → Redo
- [ ] 纵坡/贴地场景：预览纵断面合理
- [ ] 桥/隧场景：结构分类正确
- [ ] 保存工程 → 重载 → 预览结果一致
- [ ] 部分生成失败时「建造」被阻止（ROAD-FINAL-02）

---

## 环境记录

| 字段 | 值 |
|------|-----|
| **日期** | 2026-09-24 |
| **Git** | `ced58f28`（baseline 同 commit 可复现） |
| **JDK** | 21+ |
| **自动化结果** | `./gradlew roadGoldenAcceptance` BUILD SUCCESSFUL（54 tests） |

---

## 封版收敛（ROAD-FINAL-01–09）

| ID | 状态 | 摘要 |
|----|------|------|
| FINAL-01 | ✅ | Auto Repair 诊断缓存 |
| FINAL-02 | ✅ | 部分生成失败阻止建造 |
| FINAL-03 | ✅ | Network mutation 事务协议 |
| FINAL-04 | ✅ | 预览分帧 Job |
| FINAL-05 | ✅ | RoadEditPanel 机械拆分 |
| FINAL-06 | ✅ | 中心线自交 Validator |
| FINAL-07 | ✅ | Benchmark baseline |
| FINAL-08 | ✅ | Golden 验收矩阵 + W01/W02 |
| FINAL-09 | ✅ | 生命周期：`onDeactivate` 不保存，仅清理瞬时 UI |

---

## 历史记录

| 日期 | Git | 说明 |
|------|-----|------|
| 2026-09-24 | `ced58f28` | ROAD-FINAL-08 首版验收矩阵 + W01/W02 工作流测试 |
| 2026-09-24 | — | ROAD-FINAL-09 生命周期清理；W01 stale reference 修复 |

---

## 相关代码

- `RoadGoldenScenarioFactory` — R01–R12 场景
- `RoadGoldenExpectations` — Regression expected
- `RoadSemanticAcceptanceAssertions` — 手写语义规则
- `RoadGoldenWorkflowAcceptanceTest` — W01/W02
- `RoadBenchmarkBaseline.md` — 性能 baseline（ROAD-FINAL-07）
