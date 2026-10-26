# 道路逻辑删减审查（2026-10-26）

## 实施记录

同日按用户要求执行清理：删除下文六个孤立类、旧链式高程算法、无调用的局部方法和 edge-local 图表适配；移除连续坡长/缓坡三个失效设置、冗余配置路径长度及其缓存输入。

实验性起伏合并此次完整删除，包括全局及道路级开关、JSON 存储字段、UI、候选合并算法、实验专用参数和诊断字段。保留常规地形趋势平滑、坡度/水域约束、交叉点联动与方块连续性算法。旧 JSON 的退休字段由 Gson 忽略，保存时不再写出；新增配置及工程兼容测试验证其余有效数据不丢失。

删除只调用旧实现的测试；施工分类测试改用生产路径评估器，常规平滑测试使用独立测试计数工具；将负高程覆盖扩展到实际整路生成与拆段反向场景。

实施后验证：最终纵断面、编辑器、施工分类及配置兼容专项共 479 项通过，`assemble` 成功。已核对安装包中八个删除类均不存在，常规坡度受限求解器仍存在，道路类中不再包含旧开关字段。

另一次道路与配置全范围检查运行 1,657 项：发现四个快照、两个路口净空及一个未知立交道路 ID 的旧失败，以及新增兼容测试中把道路 ID 当作名称的夹具错误。夹具已补充 `setName`，并在最终专项中通过；没有修改上述旧失败的断言或快照预期。本轮未进行游戏内验证。

以下各节保留实施前的审查证据，行号为清理前位置；“建议”“候选”和“没有修改生产代码”等描述是审查时状态。

## 范围与结论

检查 `src/main/java/com/plot/plugin/road` 的 535 个 Java 文件，并追踪道路配置、生产调用、测试调用及资源入口。此次提交仅记录审查结论，没有删除生产实现或改变生成行为。

优先清理失效设置和已退出生产链路的旧实现；不要以“用户看不到内部算法”为理由移除连续性、净空、栅格覆盖与地形清理逻辑。

“无生产调用”指当前仓库的静态引用检查结果：排除注释后检查整个 `src/main/java`，并核对 `src/test`、资源注册和 API 目录。不是对仓库外调用方的兼容性保证；删除 public 符号前仍应确认没有对外承诺。不能只搜索类名就判定不可达：注释引用和只被其他死代码调用的辅助方法需要另外判断。

## 1. 优先删除无实际效果的设置

| 项目 | 证据 | 建议 |
|---|---|---|
| 连续坡长 `maxContinuousSlopeLength` | `RoadDefaultParamsPanel.java:103` 提供控件；`ProfileSolveSupport.java:12` 提供访问器，但当前生产求解器没有调用该访问器 | 删除控件和无调用的接口成员；清理对应配置访问器及指纹项 |
| 缓坡长度 `relaxedSlopeLength` | 同上，控件在 `RoadDefaultParamsPanel.java:118`；接口成员只有声明和实现 | 与连续坡长一起清理 |
| 缓坡坡度 `relaxedSlopePercent` | 同上，控件在 `RoadDefaultParamsPanel.java:130`；接口成员只有声明和实现 | 与连续坡长一起清理 |
| 配置中的路径长度 `pathLength` | `RoadSystemConfig.java:71` 保存字段；`getPathLength`、`setPathLength` 没有生产调用 | 删除冗余状态，实际长度继续来自 `RoadStationing.canonicalLength` |

前三项仍进入 `RoadSystemConfig.generationInputsFingerprint()`，控件更新还调用 `onGenerationConfigChanged()`。因此现在会改变缓存指纹、使预览失效，却不控制当前道路的纵断面。它们比单纯的死类更值得优先删除。

真正的最大坡度 `maxSlope`、地形风格 `RoadTerrainStyle` 和路径采样间距 `pathSampleDistance` 仍有实际用途，不在删除范围。当前平滑算法使用 `TerrainFollowPreset` 和 `GradeLimitedProfileSolver` 等实现；删除旧设置不等于删除平滑和坡度约束。

配置字段是 JSON 数据契约：移除旧键后应验证旧配置仍能载入，且其余字段、预设和材质保持正确；不要把仍在工作的旧数据迁移一起删除。

## 2. 可整类清理的六个候选

| 文件（相对于 `src/main/java/com/plot/plugin/road/`） | 行数 | 当前状态 | 处理建议 |
|---|---:|---|---|
| `RoadLongitudinalProfileRenderer.java` | 777 | 无实际生产调用；另一个文件的两处注释仍提到它；`src/test` 也没有直接引用 | 删除旧 edge-local 图表实现；当前编辑器使用 `RoadProfileChartRenderer` |
| `ui/RoadDirectionIndicator.java` | 126 | 无生产或测试调用 | 删除未接入的 UI 实现；不是删除道路方向、里程或反向操作本身 |
| `RoadNodeListHelper.java` | 132 | 只有 `RoadNodeListHelperTest` 引用 | 删除旧全节点列表辅助实现及专测它的旧测试 |
| `ui/VerticalAlignmentPviDrafts.java` | 114 | 类注释明确说明仅为旧测试保留；只有 `VerticalAlignmentEditorTest` 引用 | 删除旧草稿实现；需要的编辑行为用现行 `ProfileEditSession` 测试保障 |
| `spatial/RoadEdgeSpatialIndex.java` | 167 | 只有 `RoadEdgeSpatialIndexTest` 引用，未接入生产交点检测 | 可删除孤立实现；不能宣称删除会加速，因为运行时本来没用它。若以后需要空间索引，应以真实调用和基准为前提接入 |
| `pipeline/corridor/JunctionCellElevationResolver.java` | 110 | 只有两份测试引用；`resolve` 忽略几何和剖面输入，只返回 `junctionY`；其余采样辅助方法也只服务测试 | 删除过期路口高程解析实现和孤立测试，保留实际生成链的路口平面及衔接验证 |

合计 **1,426 行生产源码**，未计关联测试、旧适配方法及未使用的翻译键。此数字是候选规模，不是已经删除的规模。

`JunctionNegativeElevationTest` 中一部分断言仍期待旧的按单元剖面混合结果，而对应方法已经恒定返回路口平面；这不能证明当前实际路口生成存在相同问题。负高程等有价值场景应转移至真正工作的生成链路测试，而非为了通过旧测试恢复旧实现。

## 3. 局部死代码与兼容适配

| 位置 | 证据与建议 |
|---|---|
| `RoadSlopeUtils.computeChainedTargetHeights` 三个重载及其私有实现 | 当前只有自身重载和测试调用。可连同 `computeChainedTargetHeightsForward`、`applyManualEndHeightCorrection` 删除；这一旧算法还承载已失效的坡长/缓坡参数。保留仍被实际走廊生成调用的 `computeSlopeProfile` 和路口共识高度工具，不能整类删掉 |
| `RoadSlopeUtils.averageGroundHeight` | 无生产调用，只是转发到仍在使用的 `averageJunctionHeight`；可删除前者 |
| `RoadConstructionEvaluator.evaluateSegment` | 只有测试调用；生产分类和水平标高优化使用 `evaluatePath`。可以删单段旧入口，并把有效场景迁到路径评估测试；不能删除整个评估器 |
| `RoadNetworkProfileSourceMigration.migrateProfileSources` | private 方法无引用，重复了实际在用的 `normalizeLoadedProfileSources` 遍历；可删这一个方法。其余加载迁移必须保留 |
| `RoadVerticalJunctionService.pviIndexNearStation`、`junctionPviIndex` | 两个 private 方法无引用；可删。类中实际使用的交叉点编辑与同步服务必须保留 |
| `VerticalProfileGenerationPolicy.applyTerrainFirstUnlessActive` 两个重载 | 无调用的 deprecated 委托，现行入口为 `applyGenerationProfileAuthority`；可清理 |
| `ProfileChartIntersectionSupport` 的 edge-local 适配 | `hitIntersectionEdgeLocal`、`drawIntersectionMarkersEdgeLocal` 由旧渲染器引用；旧渲染器删除后继续清理这一支，保留现行道路级里程绘制与命中测试 |

删除专供死实现使用的测试，不是用删除测试掩盖生成错误：先确认没有生产调用，再将仍有价值的用户场景转移到现行入口。仍在工作的负高程、路口连接和纵断面连续性回归不能删。

## 4. 有真实作用，但值得简化的内容

### 实验性起伏合并

`RoadGenerationSettingsPanel` 和 `RoadStyleProductControls.renderExperimentalProfileSettings` 将实验算法开关展示给用户；`ProfileOscillationReducer` 确实被当前求解器调用，因此不是死代码。

建议普通建造界面只保留稳定的地形风格与最大坡度；实验算法开关移到明确的开发入口，或在完成效果验收后统一为内部策略。不要直接强制开启或删除它来“简化”，因为这会改变道路结果，且近期用户明确要求处理短距离起伏。算法诊断也有生产消费者 `RoadProfileSolver.recordDiagnostics`，不是只有测试读取。

### 桥梁/隧道施工复杂度评分

`RoadConstructionEvaluator.evaluatePath` 被 `RoadConstructionClassifier` 和 `FlatElevationOptimizer` 使用，会影响填挖、桥梁、隧道选择。对 Minecraft 有可见价值：避免高填方、巨量开挖和零碎桥隧。现状已经使用内部启发式常量，不是必须让玩家配置的真实工程造价模型。

可以继续减少内部重复入口、统一阈值来源；不建议直接删掉评分层或改成单一高度阈值，除非用悬崖、山谷和水域场景证明结果更好。

### 过多微观平滑参数

`TerrainFollowPreset` 包含滤波窗口、迭代次数、坡度变化、起伏跨度等内部参数。它们不是因“内部”就无效，但应保持为少量预设，不继续向玩家展开成更多滑条。合并算法前要比较方块路面的连续性、起伏次数、开挖量和运行耗时，不能只比较数学曲线得分。

## 5. 建议保留的底层能力

- 道路统一里程和方向：拆段、反向、自交后仍需正确定位高程，不能用边数组顺序替代。
- 已确认编辑曲线的权威与几何失效判断：防止生成阶段误用自动设计覆盖用户曲线。
- 平交锁定、立交净空与跨道路联动：直接决定是否连通、是否穿模。
- 连续高程求解及方块高程离散：直接防止断崖和重复上下跳格。
- 路面覆盖、隧道衬砌、开口净空及地形清理：直接决定有没有洞、有没有残留挡路方块。
- 悬崖侧半隧道/挂壁处理：避免把高崖全部当作边坡挖掉。
- 快照、撤销、缓存和分帧预算：虽不是道路外观功能，但影响编辑可靠性和卡顿。
- 仍有效的旧工程/配置迁移：防止用户已有道路和材质丢失。

## 6. 实施顺序与验收

1. 删除失效的三个坡长/缓坡 UI 控件与相关缓存输入，清理无人读取的 `ProfileSolveSupport` 成员；确认真实最大坡度与地形风格仍有效。
2. 清理六个孤立类及只服务它们的适配、测试和翻译键；不要删除共享的现行代码。
3. 清理旧链式高程求解和 private 死方法；保留真实生产入口的行为回归。
4. 单独处理实验选项的 UI 收敛，避免与基础死代码删除混在一起改变生成结果。

验证应覆盖旧 JSON 加载、道路相关编译与测试、实际生成结果，以及长路/多路口的耗时。纯死代码删除通常主要减少维护成本、误用机会与无效测试；不应承诺明显 FPS 提升。失效设置减少无谓的预览失效可能有实际收益，但收益大小需要计时确认。

本次为静态调用与实现审查，没有运行游戏、没有做新性能基准，也没有修改生产代码或重建安装包。
