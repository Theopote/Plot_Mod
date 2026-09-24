# Road Benchmark Baseline

> **用途**：记录 N01–N05 / J01–J05 的固定基线，供回归对比与优化决策。  
> **原则**：有数据再优化；无 baseline 不引入 snapshot / junction 重写。  
> **Harness**：`com.plot.plugin.road.benchmark.*`（无 Minecraft World，FlatTerrainSampler Y=64）

---

## 运行方式

| 套件 | CI 默认 | 全档位 |
|------|---------|--------|
| **N01–N03** | ✅ `RoadNetworkPerformanceBenchmarkTest` | — |
| **N04–N05** | — | `-Dplot.road.benchmark=full` 或 `PLOT_ROAD_BENCHMARK=full` |
| **J01–J05** | ✅ `JunctionRasterizationBenchmarkTest` | 同上 |

```bash
# CI 档位（N01–N03 + J01–J05）
./gradlew test --tests "com.plot.plugin.road.benchmark.*"

# 含 N04(2500 edges) / N05(5000 edges) — Linux/macOS
./gradlew test --tests "com.plot.plugin.road.benchmark.*" -Dplot.road.benchmark=full

# Windows PowerShell（Gradle 对 -D 解析不稳定时可改用环境变量）
$env:PLOT_ROAD_BENCHMARK = "full"
./gradlew test --tests "com.plot.plugin.road.benchmark.*"

# 推荐：离线跑全档位并写入 build/road-benchmark-baseline.txt（不经 JUnit，约 12 分钟）
./gradlew roadBenchmarkBaseline
```

日志关键字：`[RoadNetworkBenchmark]`、`[JunctionRasterBenchmark]`，或 Logger `Plot/RoadNetworkBenchmark`、`Plot/JunctionRasterBenchmark`。

---

## 环境记录（每次填一行）

| 字段 | 值 |
|------|-----|
| **日期** | 2026-09-24 |
| **Git** | `ced58f28` |
| **OS / JDK** | Windows 11 / JDK 22.0.2 |
| **Gradle** | 9.2.0 |
| **plot.road.benchmark** | `full`（`roadBenchmarkBaseline` 任务） |
| **备注** | ROAD-FINAL-07 首版 baseline；N04/N05 validation 含 centerline 校验 |

---

## N01–N05 路网基准

**场景**：单链路网 `RoadNetworkBenchmarkFactory.chainNetwork(edges)`，无 sidewalk/shoulder，maxSlope=12%。

| ID | Edges | createMs | snapshotMs | jsonChars | validationMs | previewMs | junctionMs | placements | memKB |
|----|------:|---------:|-----------:|----------:|-------------:|----------:|-----------:|-----------:|------:|
| **N01** | 100 | 52 | 68 | 71883 | 30 | 142 | 142 | 5005 | 79610 |
| **N02** | 500 | 51 | 120 | 358283 | 114 | 1718 | 1718 | 25005 | 146668 |
| **N03** | 1000 | 178 | 508 | 716285 | 491 | 7095 | 7095 | 50005 | 213625 |
| **N04** | 2500 | 1026 | 3323 | 1794785 | 3401 | 50761 | 50761 | 125005 | 372577 |
| **N05** | 5000 | 4871 | 15995 | 3592285 | 15473 | 283893 | 283893 | 250005 | 518584 |

**列说明**

| 列 | 含义 |
|----|------|
| createMs | 构建链式路网 |
| snapshotMs | `toJson()` + `parseSnapshot()` 往返 |
| jsonChars | 序列化 JSON 字符数 |
| validationMs | `RoadNetworkEngineeringValidator.analyze` + `blocksBuild()` |
| previewMs | 全边生成 + 路口 + `aggregateNetwork`（第二次计时） |
| junctionMs | 路口栅格化累计（含在 preview 流程内） |
| placements | `placementRecords` 数量 |
| memKB | snapshot 前后堆增量近似值（`Runtime`，仅供参考） |

**原始 log 行**（`ced58f28` / 2026-09-24）：

```text
[RoadNetworkBenchmark] N01 roads=1 edges=100 nodes=101 createMs=52 snapshotMs=68 jsonChars=71883 validationMs=30 previewMs=142 genMs=142 junctionMs=142 placements=5005 memKB=79610
[RoadNetworkBenchmark] N02 roads=1 edges=500 nodes=501 createMs=51 snapshotMs=120 jsonChars=358283 validationMs=114 previewMs=1718 genMs=1718 junctionMs=1718 placements=25005 memKB=146668
[RoadNetworkBenchmark] N03 roads=1 edges=1000 nodes=1001 createMs=178 snapshotMs=508 jsonChars=716285 validationMs=491 previewMs=7095 genMs=7095 junctionMs=7095 placements=50005 memKB=213625
[RoadNetworkBenchmark] N04 roads=1 edges=2500 nodes=2501 createMs=1026 snapshotMs=3323 jsonChars=1794785 validationMs=3401 previewMs=50761 genMs=50761 junctionMs=50761 placements=125005 memKB=372577
[RoadNetworkBenchmark] N05 roads=1 edges=5000 nodes=5001 createMs=4871 snapshotMs=15995 jsonChars=3592285 validationMs=15473 previewMs=283893 genMs=283893 junctionMs=283893 placements=250005 memKB=518584
```

> **注意**：链式单 Road 场景下 preview 流程会触发 `Intersection splitting aborted: edge count … exceeds limit 2000`（无实际路口，可忽略）。`junctionMs` 在此 harness 中与 `previewMs` 相同（整段生成计时，非单独路口累计）。

---

## J01–J05 路口栅格化基准

| ID | 拓扑 | minDegree | junctions | vertices | bboxArea | rasterMs | cells |
|----|------|----------:|----------:|---------:|---------:|---------:|------:|
| **J01** | T 字 | 3 | 1 | 6 | 56 | 23 | 165 |
| **J02** | 十字 | 4 | 1 | 8 | 64 | 2 | 199 |
| **J03** | 五岔 | 5 | 1 | 10 | 80 | 2 | 253 |
| **J04** | 八岔 | 8 | 1 | 16 | 98 | 2 | 348 |
| **J05** | 宽路 T | 3 | 1 | 6 | 56 | 1 | 165 |

**列说明**

| 列 | 含义 |
|----|------|
| junctions | 满足 minDegree 的节点数 |
| vertices | `buildJunctionFillPolygon` 顶点合计 |
| bboxArea | 各路口 polygon 包围盒面积之和（plan 单位²） |
| rasterMs | polygon 构建 + `generateJunction` 总耗时 |
| cells | 路口 solid primitives 数量 |

**原始 log 行**（`ced58f28` / 2026-09-24）：

```text
[JunctionRasterBenchmark] J01 junctions=1 vertices=6 bboxArea=56 rasterMs=23 cells=165
[JunctionRasterBenchmark] J02 junctions=1 vertices=8 bboxArea=64 rasterMs=2 cells=199
[JunctionRasterBenchmark] J03 junctions=1 vertices=10 bboxArea=80 rasterMs=2 cells=253
[JunctionRasterBenchmark] J04 junctions=1 vertices=16 bboxArea=98 rasterMs=2 cells=348
[JunctionRasterBenchmark] J05 junctions=1 vertices=6 bboxArea=56 rasterMs=1 cells=165
```

---

## 回归判定（建议）

在未改 harness 场景的前提下：

| 变化 | 建议 |
|------|------|
| snapshotMs / jsonChars | > **+20%** → 调查序列化或模型膨胀 |
| previewMs / junctionMs | > **+25%** → 调查生成或栅格化热点 |
| placements / cells | 显著偏离（非预期） → 行为回归，非纯性能 |
| memKB | 仅作趋势参考，不做 hard gate |

---

## 历史记录

| 日期 | Git | 记录人 | 说明 |
|------|-----|--------|------|
| 2026-09-24 | `ced58f28` | ROAD-FINAL-07 | 首版 baseline（N01–N05 + J01–J05）；`./gradlew roadBenchmarkBaseline` |

---

## 相关代码

- `RoadBenchmarkHarness` — N 套件执行
- `JunctionBenchmarkHarness` — J 套件执行
- `RoadBenchmarkBaselineRunner` — 离线全档位采集（`roadBenchmarkBaseline` Gradle 任务）
- `RoadNetworkPerformanceBenchmarkTest` — N01–N05（CI gate）
- `JunctionRasterizationBenchmarkTest` — J01–J05（CI gate）
- `RoadBenchmarkResult` / `JunctionBenchmarkHarness.JunctionMetrics` — 结果字段定义
