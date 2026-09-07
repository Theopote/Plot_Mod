# Building Benchmark Baseline

> **用途**：记录 D01–D05 片区 Massing 基准，供回归对比与优化决策。  
> **原则**：有数据再优化；无 baseline 不重构 Pipeline / Spec。  
> **Harness**：`com.plot.plugin.building.benchmark.DistrictBenchmarkTest`（无 Minecraft World；D01–D05/D03 flat site，D06/D07 sampled-site）

---

## 运行方式

| 套件 | CI 默认 | 全档位 |
|------|---------|--------|
| **D01–D02** | ✅ `ciGateDistrictScales` | — |
| **D06–D07**（sampled-site） | ✅ `ciGateSampledSiteDistrictScales` | D06-full / D07-full @ 500 |
| **Overlap500** | ✅ `overlapFootprintPairBenchmarkAt500` | — |
| **D03–D05** | — | `-Dplot.district.benchmark=full` |

```bash
# CI 档位（D01–D02 + D06–D07 + Overlap500）
./gradlew test --tests "com.plot.plugin.building.benchmark.DistrictBenchmarkTest"

# 含 D03(100) / D04(250) / D05(500) / D06-full(100) / D07-full(500)
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

| ID | Buildings | Floors | Path | generated | skipped | blocks | genMs | blocksPerSec | memMB |
|----|----------:|-------:|------|----------:|--------:|-------:|------:|-------------:|------:|
| **D01** | 10 | 4 | flat site | 10 | 0 | 4080 | 29.7 | 137330 | 11.0 |
| **D02** | 50 | 4 | flat site | 50 | 0 | 20400 | 62.7 | 325388 | 26.0 |
| **D03** | 100 | 4 | flat site | 100 | 0 | 40800 | 124.4 | 327890 | 43.3 |
| **D04** | 250 | 3 | flat site | 250 | 0 | 78000 | 224.0 | 348238 | 86.2 |
| **D05** | 500 | 3 | flat site | 500 | 0 | 156000 | 540.9 | 288390 | 43.3 |
| **D06** | 10 | 4 | sampled-site | 10 | 0 | 4320 | 16.9 | 255954 | 10.5 |
| **D07** | 50 | 4 | sampled-site | 50 | 0 | 21600 | 40.7 | 530438 | 13.8 |

**D06/D07**：`BuildingGenerationContext.forTestingWithSampledSite` + `SampledTerrainFixtures` 合成列采样，走 terrain elevation 决策（无 Minecraft World）。

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

## CI 软上限（`ciGateDistrictScales` / `ciGateSampledSiteDistrictScales`）

| ID | maxMillis |
|----|----------:|
| D01 | 15000 |
| D02 | 60000 |
| D06 | 20000 |
| D07 | 80000 |

超过上限测试失败；**不代表**与 baseline 偏差，仅防灾难性回归。

---

## Overlap footprint 检测（O(n²) 决策）

| 场景 | 结果 | 决策 |
|------|------|------|
| **Overlap500** — 500 栋网格、124 750 对 polygon 检测 | pairs=0，**overlapMs≈554**（2026-09-07） | **保留 O(n²)**；阈值 5000ms CI gate |

R-tree / spatial index **暂不引入**；当 Overlap500 稳定超过 **500ms** 或产品需要密集重叠片区时再评估。

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
| 2026-09-07 | — | — | D06/D07 sampled-site + Overlap500 决策 + schemaVersion v1 |

---

## 持久化 schema

| 版本 | 说明 |
|------|------|
| v0 | 历史侧车 JSON，无 `schemaVersion` |
| v1 | 当前；加载经 `BuildingProjectMigrator` 自动补齐 `schemaVersion: 1` |

---

## 相关代码

- `DistrictBenchmarkTest` — D01–D07 执行与 CI gate
- `BuildingGenerationContextFactory.forTestingWithSampledSite` — 合成 terrain 路径
- `BuildingProjectMigrator` / `BuildingProjectSchema` — JSON 迁移
- `DistrictMassingGenerator` — 片区 fail-soft 编排
- [BuildingPlugin_产品定位.md](BuildingPlugin_产品定位.md) — Massing 产品准绳
- [BuildingMassingScenarioMatrix.md](BuildingMassingScenarioMatrix.md) — B01–B14 语义/场景矩阵
