# Road Benchmark Baseline

> **用途**：记录 N01–N05 / J01–J05 的固定基线，供回归对比与优化决策。  
> **原则**：有数据再优化；无 baseline 不引入 snapshot / junction 重写。  
> **Harness**：`com.plot.plugin.road.benchmark.*`（无 Minecraft World，FlatTerrainSampler Y=64）

---

## 运行方式

| 套件 | CI 默认 | 全档位 |
|------|---------|--------|
| **N01–N03** | ✅ `RoadNetworkPerformanceBenchmarkTest` | — |
| **N04–N05** | — | `-Dplot.road.benchmark=full` |
| **J01–J05** | ✅ `JunctionRasterizationBenchmarkTest` | 同上（`fullJunctionBenchmarkWhenPropertyEnabled` 汇总） |

```bash
# CI 档位（N01–N03 + J01–J05）
./gradlew test --tests "com.plot.plugin.road.benchmark.*"

# 含 N04(2500 edges) / N05(5000 edges)
./gradlew test --tests "com.plot.plugin.road.benchmark.*" -Dplot.road.benchmark=full
```

日志关键字：`[RoadNetworkBenchmark]`、`[JunctionRasterBenchmark]`，或 Logger `Plot/RoadNetworkBenchmark`、`Plot/JunctionRasterBenchmark`。

---

## 环境记录（每次填一行）

| 字段 | 值 |
|------|-----|
| **日期** | YYYY-MM-DD |
| **Git** | `<commit>` |
| **OS / JDK** | e.g. Windows 11 / JDK 21 |
| **Gradle** | e.g. 9.2 |
| **plot.road.benchmark** | `""` / `full` |
| **备注** | 首次 baseline / 回归对比 / PR #… |

---

## N01–N05 路网基准

**场景**：单链路网 `RoadNetworkBenchmarkFactory.chainNetwork(edges)`，无 sidewalk/shoulder，maxSlope=12%。

| ID | Edges | createMs | snapshotMs | jsonChars | validationMs | previewMs | junctionMs | placements | memKB |
|----|------:|---------:|-----------:|----------:|-------------:|----------:|-----------:|-----------:|------:|
| **N01** | 100 | — | — | — | — | — | — | — | — |
| **N02** | 500 | — | — | — | — | — | — | — | — |
| **N03** | 1000 | — | — | — | — | — | — | — | — |
| **N04** | 2500 | — | — | — | — | — | — | — | — |
| **N05** | 5000 | — | — | — | — | — | — | — | — |

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

**示例 log 行**（填入上表）：

```text
N03 roads=1 edges=1000 nodes=1001 createMs=12 snapshotMs=45 jsonChars=842000 validationMs=8 previewMs=120 genMs=120 junctionMs=15 placements=42000 memKB=8192
```

---

## J01–J05 路口栅格化基准

| ID | 拓扑 | minDegree | junctions | vertices | bboxArea | rasterMs | cells |
|----|------|----------:|----------:|---------:|---------:|---------:|------:|
| **J01** | T 字 | 3 | — | — | — | — | — |
| **J02** | 十字 | 4 | — | — | — | — | — |
| **J03** | 五岔 | 5 | — | — | — | — | — |
| **J04** | 八岔 | 8 | — | — | — | — | — |
| **J05** | 宽路 T | 3 | — | — | — | — | — |

**列说明**

| 列 | 含义 |
|----|------|
| junctions | 满足 minDegree 的节点数 |
| vertices | `buildJunctionFillPolygon` 顶点合计 |
| bboxArea | 各路口 polygon 包围盒面积之和（plan 单位²） |
| rasterMs | polygon 构建 + `generateJunction` 总耗时 |
| cells | 路口 solid primitives 数量 |

**示例 log 行**：

```text
J02 junctions=1 vertices=24 bboxArea=1600 rasterMs=18 cells=842
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
| — | — | — | 首版 baseline（待填） |

---

## 相关代码

- `RoadBenchmarkHarness` — N 套件执行
- `RoadNetworkPerformanceBenchmarkTest` — N01–N05
- `JunctionRasterizationBenchmarkTest` — J01–J05
- `RoadBenchmarkResult` / `JunctionMetrics` — 结果字段定义
