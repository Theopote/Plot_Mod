# 电力线路 — PL-BLOCKSTATE-S1 收尾说明

> **状态**：✅ 已关闭（代码 + 自动化回归）  
> **前置**：PL-TOWER-S1（25 预设可实现性）  
> **目标**：从裸 `blockId` 升级到 `BlockSpec { id, properties }`，使 `/setblock` 输出带正确 BlockState。

---

## 架构

```
Preset / PoleDesign / TowerStructure
  → EffectivePoleDesignResolver（杆材 Quick Tune）
  → 放置器（见下表）
  → VoxelSink.put(BlockSpec)
  → BlockRecord.newBlockId（完整 setblock 参数字符串）
  → BlockProjectionHandler.normalizeBlockId（校验 base id，保留 BlockState）
  → /setblock
```

核心类型：

| 类型 | 路径 | 职责 |
|------|------|------|
| `BlockSpec` | `com.plot.core.block` | 解析/序列化 `minecraft:block[prop=val]` |
| `DirectionalBlockSpecs` | `powerline.placement` | plan/成员方向 → BlockState 映射 |
| `BlockRecord(BlockSpec)` | `com.plot.core.command` | 存储完整 setblock 参数 |

---

## BlockState 覆盖（S1 范围）

| 方块 | BlockState | 放置路径 |
|------|------------|----------|
| `lightning_rod` | `facing` | 横担、身份十字、塔体成员、天线、绝缘子、导线（材质为避雷针时） |
| `chain` | `axis` | 郊区路灯下垂、绝缘子串、导线（材质为锁链时） |
| `soul_lantern` | `hanging=true` | 郊区路灯灯头 |
| `lantern` | `hanging=false` | 日式街区 / 格构 catalog 柱顶 |
| `vine` | 四向 `true`, `up=false` | 乡村木杆柱顶 |
| `iron_trapdoor` | `facing,half=bottom,open=false` | 废土风电轮毂 |
| `*_slab` | `type=bottom` | catalog 横担层 |

**保持裸 id（无状态需求）**：`iron_bars`、`iron_block`、围栏/原木柱身等。

**参数化 preset 与 catalog 分叉**：如 `compactLattice` 走参数化编译，不含 catalog 的 `lantern` CAP；灯笼 BlockState 由 catalog 设计路径（如 `latticeSteelTower`）覆盖。

---

## 自动化回归

```bash
# BlockSpec 与方向映射单元测试
./gradlew test --tests "com.plot.core.block.BlockSpecTest"
./gradlew test --tests "com.plot.plugin.powerline.placement.DirectionalBlockSpecsTest"
./gradlew test --tests "com.plot.plugin.powerline.placement.PoleLayerVoxelPlacerTest"
./gradlew test --tests "com.plot.plugin.powerline.placement.GenerationVoxelSinkTest"
./gradlew test --tests "com.plot.plugin.powerline.TowerDecorationGeneratorTest"

# 身份特征 + 端到端 BlockState
./gradlew test --tests "com.plot.plugin.powerline.PresetIdentityBlockStateTest"

# 全 preset 可实现性 + Quick Tune 杆材 Generator 矩阵（含本项）
./gradlew test --tests "com.plot.plugin.powerline.PresetMinecraftRealizabilityTest"

# 电力线路全包（推荐合并前跑一遍）
./gradlew test --tests "com.plot.plugin.powerline.*"
```

### 关键测试类

| 测试 | 验证 |
|------|------|
| `PresetIdentityBlockStateTest` | 灯笼/藤蔓/陷阱门/避雷针/链；`assertNoBareDirectionalBlocks`；steampunk/compactLattice 参数化 |
| `PresetMinecraftRealizabilityTest` | 25 preset × Quick Tune 杆材在 Generator 输出中出现 |
| `DirectionalBlockSpecsTest` | 方向映射纯函数 |
| `TowerDecorationGeneratorTest` | 塔顶天线 `facing=up`、横臂避雷针 |

---

## In-Game 抽检（可选，约 5 分钟）

1. **日式街区** — 柱顶灯笼坐放（非悬挂），横担为下半砖 slab。
2. **Fantasy Copper** — 十字避雷针朝向正确（非随机默认朝向）。
3. **Suburban Lamp** — 横担末端 `chain[axis=y]` + `soul_lantern[hanging=true]`。
4. **Wasteland Wind** — 柱顶水平铁活板门轮毂。
5. **Classic Wood + Quick Tune 杆材** — 改杆材后生成柱身方块 ID 随之变化。

---

## 已知非阻塞后续（不在 S1）

- 楼梯 / 墙类方块的 `facing`（当前 catalog 未使用）
- 预览色卡按 BlockState 细分（当前按 base id）
- 严格 preset 注册表：内置方块禁止 silent air fallback

---

## 相关文档

- [电力线路_风格一致性验收.md](电力线路_风格一致性验收.md) — 风格/几何/材质验收
- [电力线路_代码审查.md](电力线路_代码审查.md) — 模块边界
