# 电力线路 — PL-BLOCKSTATE-S1 收尾说明

> **状态**：✅ S1 关闭（S1.1 P1 已修） · P2 `BlockSpecValidator` 待办  
> **前置**：PL-TOWER-S1（25 预设可实现性）  
> **目标**：从裸 `blockId` 升级到 `BlockSpec { id, properties }`，使 `/setblock` 输出带正确 BlockState。  
> **S1.1**：Directional Material Consistency Fix — 方向 segment 级、材质 BlockPos 级（见下文）。

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
./gradlew test --tests "com.plot.plugin.powerline.ConductorSpanMaterialMixBlockStateTest"

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

完整 **25 preset** 清单见：[电力线路_25预设游戏内抽检.md](电力线路_25预设游戏内抽检.md)（含 BlockState F3 速查、⭐ 必检 8 项、Quick Tune 矩阵）。

**BlockState 优先 5 项（速检）：**

1. **日式街区** — 柱顶灯笼坐放（非悬挂），横担为下半砖 slab。  
2. **Fantasy Copper** — 十字避雷针朝向正确（非随机默认朝向）。  
3. **Suburban Lamp** — 横担末端 `chain[axis=y]` + `soul_lantern[hanging=true]`。  
4. **Wasteland Wind** — 柱顶水平铁活板门轮毂 + 四向桨叶。  
5. **Classic Wood + Quick Tune 杆材** — 改杆材后柱身方块 ID 随之变化。

---

## 设计原则（P1 修复）

**方向（BlockState）按 segment / member 计算；材质（MaterialMix）按每个 `BlockPos` 独立 resolve。**

二者不可为省事合并为「取 segment 首格材质再整段复用」。导线、跳线、绝缘子 V 型腿、塔体成员等路径均须：

```java
for (BlockPos pos : blocks) {
    String blockId = MaterialMixResolver.resolve(material, pos, seed);
    String placementId = DirectionalBlockSpecs.resolveMemberPlacement(
        blockId, deltaX, deltaY, deltaZ).toSetBlockArgument();
    recordBlock(result, pos, placementId, ...);
}
```

回归：`ConductorSpanMaterialMixBlockStateTest`（混合 chain/iron_bars 与 lightning_rod/iron_bars，同时断言 BlockState）。

---

## 验收清单（2026-09）

| 项目 | 状态 |
|------|------|
| BlockSpec / 序列化 / BlockRecord 完整 state | ✅ |
| `/setblock` 携带 state | ✅ |
| lightning_rod / chain / lantern / slab / vine / trapdoor | ✅ |
| Identity Feature / 导线 / Jumper / 绝缘子 / 塔体成员 | ✅ |
| 25 preset Quick Tune Generator 矩阵 | ✅ |
| MaterialMix 每 BlockPos 解析（S1.1） | ✅ |
| BlockState property 合法性验证 | ⚠️ P2 `BlockSpecValidator` |
| `BlockRecord.newBlockId` 字段命名 | 📝 长期改为 `newBlockArgument` 或 `BlockSpec` |

**下一步（产品）**：25 preset 游戏内视觉巡检 → [电力线路_25预设游戏内抽检.md](电力线路_25预设游戏内抽检.md)，不再扩 BlockState 类型。

---

## P2  backlog：`BlockSpecValidator`

`BlockProjectionHandler.normalizeBlockId()` 仅校验 base id 是否注册，不校验 `axis`/`facing`/`hanging` 等 property 是否合法。内置 `DirectionalBlockSpecs` 为固定值，短期风险低。

建议后续在 core 增加 `BlockSpecValidator`（registry + default state）：

```
BlockSpec → validate → setblock
```

而非到服务端命令解析阶段才失败。

---

## 已知非阻塞后续

- 楼梯 / 墙类方块的 `facing`（当前 catalog 未使用）
- 预览色卡按 BlockState 细分（当前按 base id）
- 严格 preset 注册表：内置方块禁止 silent air fallback

---

## 相关文档

- [电力线路_风格一致性验收.md](电力线路_风格一致性验收.md) — 风格/几何/材质验收
- [电力线路_代码审查.md](电力线路_代码审查.md) — 模块边界
