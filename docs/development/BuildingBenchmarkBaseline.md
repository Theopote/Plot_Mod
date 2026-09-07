# Building Benchmark Baseline

> **用途**：记录 D01–D05 片区 Massing 基准，供回归对比与优化决策。  
> **原则**：有数据再优化；无 baseline 不重构 Pipeline / Spec。  
> **Harness**：`com.plot.plugin.building.benchmark.DistrictBenchmarkTest`（无 Minecraft World，`BuildingGenerationContext.forTesting`）

---

## 运行方式

| 套件 | CI 默认 | 全档位 |
|------|---------|--------|
| **D01–D02** | ✅ `DistrictBenchmarkTest.ciGateDistrictScales` | — |
| **D03–D05** | — | `-Dplot.district.benchmark=full` |

```bash
# CI 档位（D01–D02）
./gradlew test --tests "com.plot.plugin.building.benchmark.DistrictBenchmarkTest"

# 含 D03(100) / D04(250) / D05(500)
./gradlew test --tests "com.plot.plugin.building.benchmark.DistrictBenchmarkTest.fullSuiteWhenPropertyEnabled" -Dplot.district.benchmark=full
```

日志关键字：`[DistrictBenchmark]`，或 Logger `Plot/DistrictBenchmark`。

---

## 环境记录（每次填一行）

| 字段 | 值 |
|------|-----|
| **日期** | 2026-09-07 |
| **Git** | `7286747b` |
| **OS / JDK** | Windows 11 / JDK 21 |
| **Gradle** | 9.2 |
| **plot.district.benchmark** | `""` / `full` |
| **备注** | 首版 baseline |

---

## D01–D05 片区 Massing 基准

**场景**：网格排列矩形 footprint（8×6 m），`DistrictMassingGenerator` fail-soft 路径，默认 Pipeline，`windowSpacing=0`，平顶。

| ID | Buildings | Floors | generated | skipped | blocks | genMs | blocksPerSec | memMB |
|----|----------:|-------:|----------:|--------:|-------:|------:|-------------:|------:|
| **D01** | 10 | 4 | 10 | 0 | 4080 | 29.7 | 137330 | 11.0 |
| **D02** | 50 | 4 | 50 | 0 | 20400 | 62.7 | 325388 | 26.0 |
| **D03** | 100 | 4 | 100 | 0 | 40800 | 124.4 | 327890 | 43.3 |
| **D04** | 250 | 3 | 250 | 0 | 78000 | 224.0 | 348238 | 86.2 |
| **D05** | 500 | 3 | 500 | 0 | 156000 | 540.9 | 288390 | 43.3 |

**列说明**

| 列 | 含义 |
|----|------|
| generated / skipped | fail-soft 汇总（baseline 场景 skipped=0） |
| blocks | 合并后 `mergedPlacementRecords` 数量 |
| genMs | 整片 `DistrictMassingGenerator.generate` 耗时 |
| blocksPerSec | blocks / genMs |
| memMB | 运行前后堆增量近似值（`Runtime`，仅供参考） |

**示例 log 行**（填入上表）：

```text
[DistrictBenchmark] D02 buildings=50 generated=50 skipped=0 blocks=20400 genMs=62.7 blocksPerSec=325388 memMB=26.0
```

---

## CI 软上限（`ciGateDistrictScales`）

| ID | maxMillis |
|----|----------:|
| D01 | 15000 |
| D02 | 60000 |

超过上限测试失败；**不代表**与 baseline 偏差，仅防灾难性回归。

---

## 回归判定（建议）

在未改 harness 场景的前提下：

| 变化 | 建议 |
|------|------|
| genMs | > **+25%** → 调查 Pipeline 或几何热点 |
| blocks | 显著偏离（非预期） → 行为回归，非纯性能 |
| skipped | baseline 场景应为 0；非 0 → fail-soft 或校验回归 |
| memMB | 仅作趋势参考，不做 hard gate |

---

## 历史记录

| 日期 | Git | 记录人 | 说明 |
|------|-----|--------|------|
| 2026-09-07 | 7286747b | — | 首版 baseline（D01–D05） |

---

## 相关代码

- `DistrictBenchmarkTest` — D01–D05 执行与 CI gate
- `DistrictMassingGenerator` — 片区 fail-soft 编排
- [BuildingPlugin_产品定位.md](BuildingPlugin_产品定位.md) — Massing 产品准绳
- [BuildingMassingScenarioMatrix.md](BuildingMassingScenarioMatrix.md) — B01–B14 语义/场景矩阵
