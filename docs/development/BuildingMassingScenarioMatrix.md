# Building Massing Scenario Matrix

> **用途**：统一 B01–B14 语义与测试入口，避免 Golden B10（厚墙）与 District B10（十栋片区）编号混淆。  
> **双层测试**：Regression（Snapshot 数量）+ Semantic Acceptance（手写正确性）。

---

## 单体 Massing（B01–B09）

| ID | 场景 | Golden Case | Regression | Semantic |
|----|------|-------------|------------|----------|
| **B01** | 小矩形 | `GoldenBuildingCaseFactory.b01` | ✅ | ✅ universal |
| **B02** | 中矩形 | b02 | ✅ | ✅ |
| **B03** | 旋转矩形 + 坡顶 | b03 | ✅ | ✅ |
| **B04** | L 形 + floor plate | b04 | ✅ | ✅ |
| **B05** | U 形 / 平顶 | b05 | ✅ | ✅ |
| **B06** | 凹多边形 + 坡顶 | b06 | ✅ | ✅ |
| **B07** | 窄走廊 inner offset 降级 | b07 | ✅ | ✅ `assertB07InnerOffsetDegradation` |
| **B08** | 手动标高 | b08 | ✅ | ✅ |
| **B09** | 多层 | b09 | ✅ | ✅ |

**入口**：`GoldenBuildingRegressionTest` + `BuildingSemanticAcceptanceTest`

---

## 构件 / 屋顶（B10–B12 Golden 命名）

| ID | 场景 | Golden Case | 说明 |
|----|------|-------------|------|
| **B10** | 厚墙 | b10ThickWall | Golden 编号；≠ District 十栋 |
| **B11** | 门窗开洞 | b11DoorsAndWindows | Golden 编号；≠ District 百栋 |
| **B12** | 坡屋顶 | b12PitchedRoof | Golden 编号；≠ District overlap |

---

## 片区 Massing（District B10–B14）

| ID | 场景 | 测试入口 | Semantic 要点 |
|----|------|----------|---------------|
| **D-B10** | 10-building district | `BuildingDistrictScenarioTest.districtTenBuildings` | 全部 generated，blocks > 0 |
| **D-B11** | 100-building district | `BuildingDistrictScenarioTest.districtHundredBuildings` | 规模 + fail-soft 0 skipped |
| **D-B12** | overlap + later-wins | `BuildingDistrictScenarioTest.districtOverlapLaterWins` | 冲突方块 + 后写覆盖 |
| **D-B13** | invalid 混合 fail-soft | `BuildingDistrictScenarioTest.districtInvalidMixedFailSoft` | 97+3 模式（10 中 3 invalid） |
| **D-B14** | 混合标高 district | `BuildingDistrictScenarioTest.districtMixedElevationSources` | 手动标高 + 默认混合，全部 generated |

**性能对照**：D-B10/D-B11 与 [BuildingBenchmarkBaseline.md](BuildingBenchmarkBaseline.md) D01/D03 同 harness。

---

## 不做

- 精确 block hash 全量保存（除 Golden Snapshot 指标）
- Pipeline / Spec 重写

---

## 相关代码

- `GoldenBuildingCaseFactory` — B01–B12 轮廓
- `BuildingDistrictScenarioTest` — D-B10–D-B14
- `DistrictMassingGenerator` — fail-soft 编排
- `DistrictBenchmarkTest` — D01–D05 性能
