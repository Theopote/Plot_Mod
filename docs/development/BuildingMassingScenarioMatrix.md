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

---

## 真实生成链（包 A：D-B15–D-B22）

| ID | 场景 | 测试入口 | Semantic 要点 |
|----|------|----------|---------------|
| **D-B15** | L 形/凹形坡顶 + 窄走廊降级 | `BuildingRealisticGenerationChainTest.dB15_...` | L→HIP、凹→GABLE 保留；窄走廊 inner offset + roof downgrade |
| **D-B16** | 椭圆/圆离散 + 多层 | `BuildingRealisticGenerationChainTest.dB16_...` | sampled site 下 ellipse(5F)/circle(4F) 全生成 |
| **D-B17** | 重叠 footprint + sampled 地形 | `BuildingRealisticGenerationChainTest.dB17_...` | later-wins + overlap 检测 |
| **D-B18** | 小 footprint 厚墙 inner offset | `BuildingRealisticGenerationChainTest.dB18_...` | fail-soft 降级，仍有墙体量 |
| **D-B19** | **100 栋混合片区 gap 报告** | `MixedDistrictRegressionGateTest.ciGateMixedDistrict100_D_B19`（**CI 门禁**）；场景复测 `BuildingRealisticGenerationChainTest.dB19_...` | 97 generated / 3 invalid；按 `RealisticFootprintKind` 分桶 |
| **D-B20** | 坡顶 eligibility 与几何一致 | `BuildingRealisticGenerationChainTest.dB20_...` | `RoofGenerationStage.resolveRoofType` 对齐 `isSlopedRoofEligible` |
| **D-B21** | site analysis skip 条件 | `BuildingRealisticGenerationChainTest.dB21_...` | failed bundle + 无 manual/pad → must skip |
| **D-B22** | 陡坡采样预警 | `BuildingRealisticGenerationChainTest.dB22_...` | 列采样高差 ≥ `SEVERE_STEEP_THRESHOLD` |

**Fixtures**：`RealisticDistrictFixtures.mixedDistrict100()` — 55 矩形 + 8 L + 6 凹 + 8 椭圆 + 4 圆 + 4 窄走廊 + 4 厚墙小面 + 4 重叠 + 3 invalid + 4 复杂坡顶。

**Gap 报告**：`RealisticDistrictGapReport.analyze()` — CI 日志输出 skip / roof downgrade / inner offset / overlap 分桶。

**性能对照**：D-B10/D-B11 与 [BuildingBenchmarkBaseline.md](BuildingBenchmarkBaseline.md) D01/D03 同 harness。

---

## Footprint Repair（包 B）

认领与片区生成前统一走 `BuildingFootprintRepair` → `BuildingFootprintValidator`：

| 自动修复 | 仍硬拒绝 |
|----------|----------|
| 连续重复点、首尾闭合重复 | 自交 |
| 共线冗余点 | 零面积 / 退化 |
| 极短边（&lt; 1e-3 画布单位） | 顶点不足 |
| 绕序统一为 CCW | 非有限坐标（修复后仍失败） |

**测试**：`BuildingFootprintRepairTest`、`BuildingFootprintValidatorTest`

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
