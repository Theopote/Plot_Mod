package com.masterplanner.ui.dialog.BlockConfigDialog;

import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.core.state.AppState;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 方块分类管理器 - 简化版
 * <p>
 * 负责方块分类的规则加载、管理和应用。
 * <p>
 * V3.0 重大更新：
 * - 科学划分为10个精确分类，确保分类清晰且全面覆盖
 * - 使用基于标签的分类系统，比关键词匹配更准确和高效
 * - 更好的Mod兼容性和扩展性
 * - 新增光源方块和液体环境分类，解决原分类遗漏问题
 * <p>
 * 新的10分类体系：
 * 1. 建筑方块 - 基础结构方块，无特殊功能，强调结构性和通用性
 * 2. 染色方块 - 具有16种颜色变体的方块，主要用于装饰和调色盘设计
 * 3. 自然地形 - 在世界生成时自然出现的方块，通常未经加工
 * 4. 植物与树叶 - 与植物生长、农业或自然环境相关的方块
 * 5. 红石与机械 - 用于红石电路、机械装置或自动化系统的方块
 * 6. 功能与设施 - 具有GUI或可通过玩家交互触发特定功能的方块
 * 7. 装饰方块 - 主要用于美观，无结构或功能性用途的方块
 * 8. 光源方块 - 发出光照，用于照明或装饰，防止怪物生成
 * 9. 液体与环境 - 与液体或环境交互相关的方块，影响地形或机制
 * 10. 杂项 - 不适合以上类别的特殊、稀有或混合用途方块
 */
public class BlockCategoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/BlockCategoryManager");
    
    // 配置文件路径
    private static final String CONFIG_DIR = "config/masterplanner";
    private static final String CONFIG_FILE = "block_categories.json";
    
    // 分类规则列表和默认规则
    private final List<CategoryRule> categoryRules = new ArrayList<>();
    private CategoryRule defaultRule;
    
    // 分类后的方块
    private final Map<BlockCategory, List<Block>> categorizedBlocks = new EnumMap<>(BlockCategory.class);

    /**
     * 方块分类枚举 - 10分类精确体系
     * 科学划分Minecraft中的所有方块，确保分类清晰且全面覆盖
     */
    public enum BlockCategory {
        BUILDING_BLOCKS("建筑方块"),        // 基础结构方块，无特殊功能，强调结构性和通用性
        COLORED_BLOCKS("染色方块"),         // 具有16种颜色变体的方块，主要用于装饰和调色盘设计
        NATURAL_TERRAIN("自然地形"),       // 在世界生成时自然出现的方块，通常未经加工
        PLANTS_FOLIAGE("植物与树叶"),      // 与植物生长、农业或自然环境相关的方块
        REDSTONE_MECHANISMS("红石与机械"),  // 用于红石电路、机械装置或自动化系统的方块
        FUNCTIONAL_UTILITY("功能与设施"),   // 具有GUI或可通过玩家交互触发特定功能的方块
        DECORATIVE_BLOCKS("装饰方块"),     // 主要用于美观，无结构或功能性用途的方块
        LIGHT_SOURCES("光源方块"),         // 发出光照，用于照明或装饰，防止怪物生成
        LIQUIDS_ENVIRONMENT("液体与环境"),  // 与液体或环境交互相关的方块，影响地形或机制
        MISCELLANEOUS("杂项");            // 不适合以上类别的特殊、稀有或混合用途方块
        
        private final String displayName;
        
        BlockCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 分类规则类
     * 定义方块分类的匹配规则
     */
    private static class CategoryRule {
        private final BlockCategory category;
        private final List<TagKey<Block>> tags;
        private final List<Pattern> namePatterns;
        private final List<Pattern> idPatterns;
        private final boolean isDefault;
        
        public CategoryRule(BlockCategory category, List<TagKey<Block>> tags, 
                       List<String> nameKeywords, List<String> idKeywords, boolean isDefault) {
            this.category = category;
            this.tags = tags;
            this.namePatterns = compilePatterns(nameKeywords);
            this.idPatterns = compilePatterns(idKeywords);
            this.isDefault = isDefault;
        }
        
        private List<Pattern> compilePatterns(List<String> keywords) {
            return keywords.stream()
                .map(keyword -> Pattern.compile(keyword, Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
        }
        
        public BlockCategory getCategory() {
            return category;
        }
        
        public boolean matches(Block block, Identifier blockId) {
            // 如果是默认规则，直接返回false，让其他规则先匹配
            if (isDefault) {
                return false;
            }
            
            // 先检查ID关键词，这通常更可靠
            String id = blockId.toString().toLowerCase();
            for (Pattern pattern : idPatterns) {
                if (pattern.matcher(id).find()) {
                    LOGGER.debug("方块 {} 匹配ID关键词 {} -> 分类: {}", blockId, pattern.pattern(), category.getDisplayName());
                        return true;
                }
            }
            
            // 检查名称关键词
            try {
            String blockName = block.getName().getString().toLowerCase();
            for (Pattern pattern : namePatterns) {
                if (pattern.matcher(blockName).find()) {
                    LOGGER.debug("方块 {} 匹配名称关键词 {} -> 分类: {}", blockId, pattern.pattern(), category.getDisplayName());
                    return true;
                }
            }
            } catch (Exception e) {
                LOGGER.debug("获取方块 {} 名称失败: {}", blockId, e.getMessage());
            }
            
            // 最后检查标签（可能失败，所以放在最后）
            for (TagKey<Block> tag : tags) {
                try {
                    if (BlockCategoryManager.isInTag(block, tag)) {
                        LOGGER.debug("方块 {} 匹配标签 {} -> 分类: {}", blockId, tag.id(), category.getDisplayName());
                    return true;
                    }
                } catch (Exception e) {
                    LOGGER.debug("检查方块 {} 标签 {} 失败: {}", blockId, tag.id(), e.getMessage());
                }
            }
            
            return false;
        }
    }
    
    /**
     * 构造函数
     * @param appState 应用状态
     * @param eventBus 事件总线
     * @param showWarningDialog 显示警告对话框的回调
     */
    public BlockCategoryManager(AppState appState, EventBus eventBus, Consumer<String> showWarningDialog) {
        // 应用状态和事件总线

        // 初始化分类映射
        for (BlockCategory category : BlockCategory.values()) {
            categorizedBlocks.put(category, new ArrayList<>());
        }
        
        // 初始化分类规则
        initCategoryRules();
        
        // 初始化方块分类
        initBlockCategories();
    }
    
    /**
     * 初始化分类规则
     * 尝试从配置文件加载，如果失败则使用默认规则
     */
    private void initCategoryRules() {
        LOGGER.info("开始初始化方块分类规则...");
        
        // 暂时跳过配置文件加载，强制使用默认规则
        // 这是为了解决分类失效问题的临时方案
        boolean loadedFromConfig = false;  // 强制为false
        LOGGER.info("强制使用默认分类规则以解决分类问题");
        
        // 如果加载失败，使用默认规则
        if (!loadedFromConfig) {
            LOGGER.info("使用默认分类规则");
            initDefaultCategoryRules();
            
            // 验证规则是否正确加载
            LOGGER.info("验证分类规则加载结果：");
            LOGGER.info("  - 总规则数: {}", categoryRules.size());
            LOGGER.info("  - 默认规则: {}", defaultRule != null ? defaultRule.getCategory().getDisplayName() : "null");
            
            // 显示前几个规则的信息
            for (int i = 0; i < Math.min(3, categoryRules.size()); i++) {
                CategoryRule rule = categoryRules.get(i);
                LOGGER.info("  - 规则{}: {} (标签: {}, ID关键词: {}, 名称关键词: {})", 
                    i + 1, 
                    rule.getCategory().getDisplayName(),
                    rule.tags.size(),
                    rule.idPatterns.size(),
                    rule.namePatterns.size());
            }
            
            // 保存默认配置文件（可选）
            try {
                saveDefaultConfigFile();
                LOGGER.info("已保存默认分类规则到配置文件");
            } catch (IOException e) {
                LOGGER.error("保存默认分类规则失败: {}", e.getMessage());
            }
        }
        
        LOGGER.info("分类规则初始化完成，共加载 {} 条规则", categoryRules.size());
    }

    /**
     * 保存默认配置文件 - 使用新的10分类精确体系
     * @throws IOException 如果保存失败
     */
    private void saveDefaultConfigFile() throws IOException {
        // 创建配置目录
        Path configDir = Paths.get(CONFIG_DIR);
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }
        
        // 创建配置文件
        Path configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        File configFile = configPath.toFile();
        
        // 创建JSON对象
        JsonObject json = new JsonObject();
        json.addProperty("version", "3.0");
        
        // 创建规则数组
        JsonArray rulesArray = new JsonArray();
        
        // 1. 建筑方块规则
        JsonObject buildingRule = getObject();

        rulesArray.add(buildingRule);
        
        // 2. 染色方块规则
        JsonObject coloredRule = getColoredRule();

        rulesArray.add(coloredRule);
        
        // 3. 自然地形规则
        JsonObject naturalRule = getNaturalRule();

        rulesArray.add(naturalRule);
        
        // 4. 植物与树叶规则
        JsonObject plantsRule = getPlantsRule();

        rulesArray.add(plantsRule);
        
        // 5. 红石与机械规则
        JsonObject redstoneRule = getRedstoneRule();

        rulesArray.add(redstoneRule);
        
        // 6. 功能与设施规则
        JsonObject functionalRule = getJsonObject();

        rulesArray.add(functionalRule);
        
        // 7. 装饰方块规则
        JsonObject decorativeRule = getDecorativeRule();

        rulesArray.add(decorativeRule);
        
        // 8. 光源方块规则
        JsonObject lightSourceRule = getLightSourceRule();

        rulesArray.add(lightSourceRule);
        
        // 9. 液体与环境规则
        JsonObject liquidsEnvironmentRule = getLiquidsEnvironmentRule();

        rulesArray.add(liquidsEnvironmentRule);
        
        // 10. 杂项方块规则（默认规则）
        JsonObject miscRule = new JsonObject();
        miscRule.addProperty("category", "MISCELLANEOUS");
        miscRule.addProperty("default", true);
        
        JsonArray miscTags = new JsonArray();
        miscRule.add("tags", miscTags);
        
        JsonArray miscIdKeywords = new JsonArray();
        miscRule.add("idKeywords", miscIdKeywords);
        
        rulesArray.add(miscRule);
        
        // 将规则数组添加到JSON对象
        json.add("rules", rulesArray);
        
        // 写入文件
        try (FileWriter writer = new FileWriter(configFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(json, writer);
        }
        
        LOGGER.info("已保存10分类精确体系规则到配置文件: {}", configPath);
    }

    private static @NotNull JsonObject getColoredRule() {
        JsonObject coloredRule = new JsonObject();
        coloredRule.addProperty("category", "COLORED_BLOCKS");

        JsonArray coloredTags = new JsonArray();
        coloredTags.add("minecraft:wool");
        coloredTags.add("minecraft:terracotta");
        coloredTags.add("minecraft:concrete");
        coloredTags.add("minecraft:concrete_powder");
        coloredTags.add("minecraft:stained_glass");
        coloredTags.add("minecraft:stained_glass_panes");
        coloredTags.add("minecraft:shulker_boxes");
        coloredRule.add("tags", coloredTags);
        return coloredRule;
    }

    private static @NotNull JsonObject getLiquidsEnvironmentRule() {
        JsonObject liquidsEnvironmentRule = new JsonObject();
        liquidsEnvironmentRule.addProperty("category", "LIQUIDS_ENVIRONMENT");

        JsonArray liquidsEnvironmentIdKeywords = new JsonArray();
        liquidsEnvironmentIdKeywords.add("water");
        liquidsEnvironmentIdKeywords.add("lava");
        liquidsEnvironmentIdKeywords.add("ice");
        liquidsEnvironmentIdKeywords.add("packed_ice");
        liquidsEnvironmentIdKeywords.add("blue_ice");
        liquidsEnvironmentIdKeywords.add("frosted_ice");
        liquidsEnvironmentIdKeywords.add("snow");
        liquidsEnvironmentIdKeywords.add("snow_block");
        liquidsEnvironmentIdKeywords.add("sponge");
        liquidsEnvironmentIdKeywords.add("wet_sponge");
        liquidsEnvironmentIdKeywords.add("coral_block");
        liquidsEnvironmentIdKeywords.add("dead_coral_block");
        liquidsEnvironmentRule.add("idKeywords", liquidsEnvironmentIdKeywords);
        return liquidsEnvironmentRule;
    }

    private static @NotNull JsonObject getLightSourceRule() {
        JsonObject lightSourceRule = new JsonObject();
        lightSourceRule.addProperty("category", "LIGHT_SOURCES");

        JsonArray lightSourceIdKeywords = new JsonArray();
        lightSourceIdKeywords.add("torch");
        lightSourceIdKeywords.add("lantern");
        lightSourceIdKeywords.add("glowstone");
        lightSourceIdKeywords.add("sea_lantern");
        lightSourceIdKeywords.add("redstone_lamp");
        lightSourceIdKeywords.add("end_rod");
        lightSourceIdKeywords.add("shroomlight");
        lightSourceIdKeywords.add("jack_o_lantern");
        lightSourceIdKeywords.add("campfire");
        lightSourceIdKeywords.add("soul_campfire");
        lightSourceIdKeywords.add("candle");
        lightSourceIdKeywords.add("beacon");
        lightSourceRule.add("idKeywords", lightSourceIdKeywords);
        return lightSourceRule;
    }

    private static @NotNull JsonObject getDecorativeRule() {
        JsonObject decorativeRule = new JsonObject();
        decorativeRule.addProperty("category", "DECORATIVE_BLOCKS");

        JsonArray decorativeTags = new JsonArray();
        decorativeTags.add("minecraft:banners");
        decorativeTags.add("minecraft:carpets");
        decorativeTags.add("minecraft:candles");
        decorativeRule.add("tags", decorativeTags);

        JsonArray decorativeIdKeywords = new JsonArray();
        decorativeIdKeywords.add("chain");
        decorativeIdKeywords.add("flower_pot");
        decorativeIdKeywords.add("item_frame");
        decorativeIdKeywords.add("glow_item_frame");
        decorativeIdKeywords.add("armor_stand");
        decorativeIdKeywords.add("painting");
        decorativeIdKeywords.add("skull");
        decorativeIdKeywords.add("head");
        decorativeIdKeywords.add("scaffolding");
        decorativeIdKeywords.add("bookshelf");
        decorativeIdKeywords.add("chiseled_bookshelf");
        decorativeIdKeywords.add("decorated_pot");
        decorativeIdKeywords.add("pottery_sherd");
        decorativeIdKeywords.add("music_disc");
        decorativeIdKeywords.add("dragon_head");
        decorativeIdKeywords.add("wither_skeleton_skull");
        decorativeRule.add("idKeywords", decorativeIdKeywords);
        return decorativeRule;
    }

    private static @NotNull JsonObject getRedstoneRule() {
        JsonObject redstoneRule = new JsonObject();
        redstoneRule.addProperty("category", "REDSTONE_MECHANISMS");

        JsonArray redstoneTags = new JsonArray();
        redstoneTags.add("minecraft:rails");
        redstoneTags.add("minecraft:buttons");
        redstoneTags.add("minecraft:pressure_plates");
        redstoneRule.add("tags", redstoneTags);

        JsonArray redstoneIdKeywords = new JsonArray();
        redstoneIdKeywords.add("redstone");
        redstoneIdKeywords.add("piston");
        redstoneIdKeywords.add("observer");
        redstoneIdKeywords.add("repeater");
        redstoneIdKeywords.add("comparator");
        redstoneIdKeywords.add("lever");
        redstoneIdKeywords.add("hopper");
        redstoneIdKeywords.add("dispenser");
        redstoneIdKeywords.add("dropper");
        redstoneIdKeywords.add("target");
        redstoneIdKeywords.add("crafter");
        redstoneIdKeywords.add("tripwire_hook");
        redstoneIdKeywords.add("daylight_detector");
        redstoneIdKeywords.add("sticky_piston");
        redstoneIdKeywords.add("slime_block");
        redstoneIdKeywords.add("honey_block");
        redstoneIdKeywords.add("sculk_sensor");
        redstoneIdKeywords.add("calibrated_sculk_sensor");
        redstoneRule.add("idKeywords", redstoneIdKeywords);
        return redstoneRule;
    }

    private static @NotNull JsonObject getNaturalRule() {
        JsonObject naturalRule = new JsonObject();
        naturalRule.addProperty("category", "NATURAL_TERRAIN");

        JsonArray naturalTags = new JsonArray();
        naturalTags.add("minecraft:dirt");
        naturalTags.add("minecraft:sand");
        naturalTags.add("minecraft:base_stone_overworld");
        naturalTags.add("minecraft:base_stone_nether");
        naturalTags.add("minecraft:ores");
        naturalTags.add("minecraft:ice");
        naturalRule.add("tags", naturalTags);

        JsonArray naturalIdKeywords = getElements();
        naturalRule.add("idKeywords", naturalIdKeywords);
        return naturalRule;
    }

    private static @NotNull JsonArray getElements() {
        JsonArray naturalIdKeywords = new JsonArray();
        naturalIdKeywords.add("gravel");
        naturalIdKeywords.add("clay");
        naturalIdKeywords.add("snow");
        naturalIdKeywords.add("soul_sand");
        naturalIdKeywords.add("soul_soil");
        naturalIdKeywords.add("basalt");
        naturalIdKeywords.add("blackstone");
        naturalIdKeywords.add("magma_block");
        naturalIdKeywords.add("obsidian");
        naturalIdKeywords.add("amethyst");
        naturalIdKeywords.add("netherrack");
        naturalIdKeywords.add("end_stone_ore");
        naturalIdKeywords.add("ancient_debris");
        naturalIdKeywords.add("gilded_blackstone");
        naturalIdKeywords.add("crying_obsidian");
        return naturalIdKeywords;
    }

    private static @NotNull JsonObject getPlantsRule() {
        JsonObject plantsRule = new JsonObject();
        plantsRule.addProperty("category", "PLANTS_FOLIAGE");

        JsonArray plantsTags = new JsonArray();
        plantsTags.add("minecraft:leaves");
        plantsTags.add("minecraft:flowers");
        plantsTags.add("minecraft:saplings");
        plantsTags.add("minecraft:crops");
        plantsTags.add("minecraft:corals");
        plantsTags.add("minecraft:wart_blocks");
        plantsRule.add("tags", plantsTags);

        JsonArray plantsIdKeywords = new JsonArray();
        plantsIdKeywords.add("mushroom");
        plantsIdKeywords.add("vine");
        plantsIdKeywords.add("fern");
        plantsIdKeywords.add("grass");
        plantsIdKeywords.add("lily_pad");
        plantsIdKeywords.add("moss");
        plantsIdKeywords.add("mangrove_roots");
        plantsIdKeywords.add("azalea");
        plantsIdKeywords.add("bamboo");
        plantsIdKeywords.add("sugar_cane");
        plantsIdKeywords.add("kelp");
        plantsIdKeywords.add("seagrass");
        plantsIdKeywords.add("sea_pickle");
        plantsIdKeywords.add("cactus");
        plantsIdKeywords.add("chorus");
        plantsIdKeywords.add("sweet_berry");
        plantsIdKeywords.add("glow_berries");
        plantsIdKeywords.add("cave_vines");
        plantsIdKeywords.add("hanging_roots");
        plantsIdKeywords.add("spore_blossom");
        plantsRule.add("idKeywords", plantsIdKeywords);
        return plantsRule;
    }

    private static @NotNull JsonObject getObject() {
        JsonObject buildingRule = new JsonObject();
        buildingRule.addProperty("category", "BUILDING_BLOCKS");

        JsonArray buildingTags = new JsonArray();
        buildingTags.add("minecraft:planks");
        buildingTags.add("minecraft:stone_bricks");
        buildingTags.add("minecraft:stairs");
        buildingTags.add("minecraft:slabs");
        buildingTags.add("minecraft:walls");
        buildingTags.add("minecraft:fences");
        buildingTags.add("minecraft:fence_gates");
        buildingTags.add("minecraft:doors");
        buildingTags.add("minecraft:trapdoors");
        buildingTags.add("minecraft:logs");
        buildingRule.add("tags", buildingTags);

        JsonArray buildingIdKeywords = getJsonElements();
        buildingRule.add("idKeywords", buildingIdKeywords);
        return buildingRule;
    }

    private static @NotNull JsonArray getJsonElements() {
        JsonArray buildingIdKeywords = new JsonArray();
        buildingIdKeywords.add("brick");
        buildingIdKeywords.add("purpur");
        buildingIdKeywords.add("prismarine");
        buildingIdKeywords.add("quartz");
        buildingIdKeywords.add("end_stone");
        buildingIdKeywords.add("nether_brick");
        buildingIdKeywords.add("sandstone");
        buildingIdKeywords.add("red_sandstone");
        buildingIdKeywords.add("deepslate");
        buildingIdKeywords.add("calcite");
        buildingIdKeywords.add("polished");
        buildingIdKeywords.add("chiseled");
        buildingIdKeywords.add("copper");
        buildingIdKeywords.add("tuff");
        buildingIdKeywords.add("dripstone");
        buildingIdKeywords.add("smooth");
        buildingIdKeywords.add("cut");
        buildingIdKeywords.add("cobbled");
        return buildingIdKeywords;
    }

    private static @NotNull JsonObject getJsonObject() {
        JsonObject functionalRule = new JsonObject();
        functionalRule.addProperty("category", "FUNCTIONAL_UTILITY");

        JsonArray functionalTags = new JsonArray();
        functionalTags.add("minecraft:beds");
        functionalRule.add("tags", functionalTags);

        JsonArray functionalIdKeywords = new JsonArray();
        functionalIdKeywords.add("crafting_table");
        functionalIdKeywords.add("furnace");
        functionalIdKeywords.add("chest");
        functionalIdKeywords.add("ender_chest");
        functionalIdKeywords.add("barrel");
        functionalIdKeywords.add("anvil");
        functionalIdKeywords.add("beacon");
        functionalIdKeywords.add("enchanting_table");
        functionalIdKeywords.add("brewing_stand");
        functionalIdKeywords.add("cauldron");
        functionalIdKeywords.add("composter");
        functionalIdKeywords.add("loom");
        functionalIdKeywords.add("grindstone");
        functionalIdKeywords.add("cartography_table");
        functionalIdKeywords.add("fletching_table");
        functionalIdKeywords.add("smithing_table");
        functionalIdKeywords.add("stonecutter");
        functionalIdKeywords.add("smoker");
        functionalIdKeywords.add("blast_furnace");
        functionalIdKeywords.add("jukebox");
        functionalIdKeywords.add("note_block");
        functionalIdKeywords.add("lectern");
        functionalIdKeywords.add("lodestone");
        functionalIdKeywords.add("respawn_anchor");
        functionalIdKeywords.add("hopper");
        functionalIdKeywords.add("shulker_box");
        functionalIdKeywords.add("trapped_chest");
        functionalIdKeywords.add("ender_eye");
        functionalIdKeywords.add("conduit");
        functionalIdKeywords.add("bell");
        functionalRule.add("idKeywords", functionalIdKeywords);
        return functionalRule;
    }

    /**
     * 初始化默认分类规则 - 使用新的10分类精确体系
     */
    private void initDefaultCategoryRules() {
        // 清除现有规则
        categoryRules.clear();
        
        LOGGER.info("开始初始化默认分类规则...");
        
        // 1. 建筑方块规则 - 增强版
        List<TagKey<Block>> buildingTags = Stream.of(
            createTagKey("minecraft:planks"),
            createTagKey("minecraft:stone_bricks"),
            createTagKey("minecraft:stairs"),
            createTagKey("minecraft:slabs"),
            createTagKey("minecraft:walls"),
            createTagKey("minecraft:fences"),
            createTagKey("minecraft:fence_gates"),
            createTagKey("minecraft:doors"),
            createTagKey("minecraft:trapdoors"),
            createTagKey("minecraft:logs")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        // 增强建筑方块的ID关键词，包含常见方块
        List<String> buildingIdKeywords = Arrays.asList(
            "brick", "purpur", "prismarine", "quartz", "end_stone", "nether_brick",
            "sandstone", "red_sandstone", "deepslate", "calcite", "polished", "chiseled",
            "copper", "tuff", "dripstone", "smooth", "cut", "cobbled",
            // 新增常见建筑方块关键词
            "cobblestone", "stone", "granite", "diorite", "andesite", "mossy",
            "cracked", "infested", "reinforced", "waxed", "exposed", "weathered", "oxidized"
        );
        categoryRules.add(new CategoryRule(BlockCategory.BUILDING_BLOCKS, buildingTags, 
            Collections.emptyList(), buildingIdKeywords, false));
        LOGGER.info("建筑方块规则：{} 个标签，{} 个ID关键词", buildingTags.size(), buildingIdKeywords.size());
        
        // 2. 染色方块规则 - 增强版
        List<TagKey<Block>> coloredTags = Stream.of(
            createTagKey("minecraft:wool"),
            createTagKey("minecraft:terracotta"),
            createTagKey("minecraft:concrete"),
            createTagKey("minecraft:concrete_powder"),
            createTagKey("minecraft:stained_glass"),
            createTagKey("minecraft:stained_glass_panes"),
            createTagKey("minecraft:shulker_boxes")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        // 增强染色方块的ID关键词
        List<String> coloredIdKeywords = Arrays.asList(
            "concrete", "wool", "terracotta", "stained_glass", "shulker_box",
            // 颜色前缀
            "white_", "orange_", "magenta_", "light_blue_", "yellow_", "lime_", "pink_",
            "gray_", "light_gray_", "cyan_", "purple_", "blue_", "brown_", "green_", "red_", "black_"
        );
        
        categoryRules.add(new CategoryRule(BlockCategory.COLORED_BLOCKS, coloredTags, 
            Collections.emptyList(), coloredIdKeywords, false));
        LOGGER.info("染色方块规则：{} 个标签，{} 个ID关键词", coloredTags.size(), coloredIdKeywords.size());
        
        // 3. 自然地形规则 - 增强版
        List<TagKey<Block>> naturalTags = Stream.of(
            createTagKey("minecraft:dirt"),
            createTagKey("minecraft:sand"),
            createTagKey("minecraft:base_stone_overworld"),
            createTagKey("minecraft:base_stone_nether"),
            createTagKey("minecraft:ores"),
            createTagKey("minecraft:ice")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        List<String> naturalIdKeywords = Arrays.asList(
            "gravel", "clay", "soul_sand", "soul_soil", "basalt", "blackstone",
            "magma_block", "obsidian", "amethyst", "netherrack", "end_stone_ore",
            "ancient_debris", "gilded_blackstone", "crying_obsidian",
            // 新增自然地形关键词
            "dirt", "grass_block", "podzol", "mycelium", "coarse_dirt", "rooted_dirt",
            "sand", "red_sand", "suspicious_sand", "gravel", "suspicious_gravel"
        );
        categoryRules.add(new CategoryRule(BlockCategory.NATURAL_TERRAIN, naturalTags, 
            Collections.emptyList(), naturalIdKeywords, false));
        LOGGER.info("自然地形规则：{} 个标签，{} 个ID关键词", naturalTags.size(), naturalIdKeywords.size());
        
        // 4. 植物与树叶规则
        List<TagKey<Block>> plantsTags = Stream.of(
            createTagKey("minecraft:leaves"),
            createTagKey("minecraft:flowers"),
            createTagKey("minecraft:saplings"),
            createTagKey("minecraft:crops"),
            createTagKey("minecraft:corals"),
            createTagKey("minecraft:wart_blocks")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        List<String> plantsIdKeywords = Arrays.asList(
            "mushroom", "vine", "fern", "grass", "lily_pad", "moss", "mangrove_roots", "azalea",
            "bamboo", "sugar_cane", "kelp", "seagrass", "sea_pickle", "cactus", "chorus",
            "sweet_berry", "glow_berries", "cave_vines", "hanging_roots", "spore_blossom"
        );
        categoryRules.add(new CategoryRule(BlockCategory.PLANTS_FOLIAGE, plantsTags, 
            Collections.emptyList(), plantsIdKeywords, false));
        LOGGER.info("植物与树叶规则：{} 个标签，{} 个ID关键词", plantsTags.size(), plantsIdKeywords.size());
        
        // 5. 红石与机械规则
        List<TagKey<Block>> redstoneTags = Stream.of(
            createTagKey("minecraft:rails"),
            createTagKey("minecraft:buttons"),
            createTagKey("minecraft:pressure_plates")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        List<String> redstoneIdKeywords = Arrays.asList(
            "redstone", "piston", "observer", "repeater", "comparator", "lever",
            "dispenser", "dropper", "target", "crafter", "tripwire_hook", "daylight_detector",
            "sticky_piston", "slime_block", "honey_block", "sculk_sensor", "calibrated_sculk_sensor"
        );
        categoryRules.add(new CategoryRule(BlockCategory.REDSTONE_MECHANISMS, redstoneTags, 
            Collections.emptyList(), redstoneIdKeywords, false));
        LOGGER.info("红石与机械规则：{} 个标签，{} 个ID关键词", redstoneTags.size(), redstoneIdKeywords.size());
        
        // 6. 功能与设施规则
        List<TagKey<Block>> functionalTags = Stream.of(
            createTagKey("minecraft:beds")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        List<String> functionalIdKeywords = Arrays.asList(
            "crafting_table", "furnace", "chest", "ender_chest", "barrel", "anvil", "beacon",
            "enchanting_table", "brewing_stand", "cauldron", "composter", "loom", "grindstone",
            "cartography_table", "fletching_table", "smithing_table", "stonecutter", "smoker", 
            "blast_furnace", "jukebox", "note_block", "lectern", "lodestone", "respawn_anchor",
            "hopper", "shulker_box", "trapped_chest", "ender_eye", "conduit", "bell"
        );
        categoryRules.add(new CategoryRule(BlockCategory.FUNCTIONAL_UTILITY, functionalTags, 
            Collections.emptyList(), functionalIdKeywords, false));
        LOGGER.info("功能与设施规则：{} 个标签，{} 个ID关键词", functionalTags.size(), functionalIdKeywords.size());
        
        // 7. 装饰方块规则
        List<TagKey<Block>> decorativeTags = Stream.of(
            createTagKey("minecraft:banners"),
            createTagKey("minecraft:carpets"),
            createTagKey("minecraft:candles")
        ).filter(Objects::nonNull).collect(Collectors.toList());
        
        List<String> decorativeIdKeywords = Arrays.asList(
            "chain", "flower_pot", "item_frame", "glow_item_frame",
            "armor_stand", "painting", "skull", "head", "scaffolding", "bookshelf", "chiseled_bookshelf",
            "decorated_pot", "pottery_sherd", "music_disc", "dragon_head", "wither_skeleton_skull"
        );
        categoryRules.add(new CategoryRule(BlockCategory.DECORATIVE_BLOCKS, decorativeTags, 
            Collections.emptyList(), decorativeIdKeywords, false));
        LOGGER.info("装饰方块规则：{} 个标签，{} 个ID关键词", decorativeTags.size(), decorativeIdKeywords.size());
        
        // 8. 光源方块规则
        List<String> lightSourceIdKeywords = Arrays.asList(
            "torch", "lantern", "glowstone", "sea_lantern", "redstone_lamp", "end_rod",
            "shroomlight", "jack_o_lantern", "campfire", "soul_campfire", "candle", "beacon"
        );
        categoryRules.add(new CategoryRule(BlockCategory.LIGHT_SOURCES, Collections.emptyList(), 
            Collections.emptyList(), lightSourceIdKeywords, false));
        LOGGER.info("光源方块规则：{} 个ID关键词", lightSourceIdKeywords.size());
        
        // 9. 液体与环境规则
        List<String> liquidsEnvironmentIdKeywords = Arrays.asList(
            "water", "lava", "ice", "packed_ice", "blue_ice", "frosted_ice", "snow", "snow_block",
            "sponge", "wet_sponge", "coral_block", "dead_coral_block"
        );
        categoryRules.add(new CategoryRule(BlockCategory.LIQUIDS_ENVIRONMENT, Collections.emptyList(), 
            Collections.emptyList(), liquidsEnvironmentIdKeywords, false));
        LOGGER.info("液体与环境规则：{} 个ID关键词", liquidsEnvironmentIdKeywords.size());
        
        // 10. 杂项方块规则（默认规则）
        defaultRule = new CategoryRule(BlockCategory.MISCELLANEOUS, 
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), true);
        categoryRules.add(defaultRule);
        LOGGER.info("杂项规则：默认分类");
        
        LOGGER.info("默认分类规则初始化完成，共 {} 条规则", categoryRules.size());
    }
    
    /**
     * 安全创建TagKey，处理可能的null值
     * @param tagId 标签ID字符串
     * @return TagKey或null
     */
    private static TagKey<Block> createTagKey(String tagId) {
        try {
            Identifier identifier = Identifier.tryParse(tagId);
            if (identifier == null) {
                LOGGER.warn("无效的标签ID: {}", tagId);
                return null;
            }
            return TagKey.of(RegistryKeys.BLOCK, identifier);
        } catch (Exception e) {
            LOGGER.error("创建标签失败: {}", tagId, e);
            return null;
        }
    }
    
    /**
     * 初始化方块分类
     * 将所有方块按规则分类到不同类别
     */
    public void initBlockCategories() {
        LOGGER.info("开始初始化方块分类...");
        
        // 清空现有分类
        for (List<Block> blocks : categorizedBlocks.values()) {
            blocks.clear();
        }
        
        // 测试几个常见方块的分类
        testBlockCategorization();
        
        int totalBlocks = 0;
        int categorizedBlocks = 0;
        int airLikeBlocks = 0;  // 🔥 统计类似AIR的块
        
        // 遍历所有方块
        for (Block block : Registries.BLOCK) {
            // 获取方块ID
            Identifier blockId = Registries.BLOCK.getId(block);
            
            // 跳过minecraft:air方块
            if (blockId.toString().equals("minecraft:air")) {
                LOGGER.debug("🔥 跳过AIR块");
                airLikeBlocks++;
                continue;
            }
            
            // 🔥 关键检查：block是否有有效的Item形式
            // 这是方块图标不显示的常见原因
            try {
                net.minecraft.item.Item item = block.asItem();
                if (item == net.minecraft.item.Items.AIR) {
                    LOGGER.warn("⚠️  方块 {} 没有有效的Item形式（asItem返回Items.AIR），不应该添加到分类中", blockId);
                    airLikeBlocks++;
                    continue;
                }
            } catch (Exception e) {
                LOGGER.debug("检查方块 {} 的Item形式时失败: {}", blockId, e.getMessage());
            }
            
            totalBlocks++;
            
            // 应用分类规则
            boolean categorized = false;
            for (CategoryRule rule : categoryRules) {
                if (rule.matches(block, blockId)) {
                    this.categorizedBlocks.get(rule.getCategory()).add(block);
                    categorized = true;
                    categorizedBlocks++;
                    
                    // 详细日志记录前几个方块的分类结果
                    if (categorizedBlocks <= 20) {
                        LOGGER.info("✓ 方块 {} 分类到: {}", blockId, rule.getCategory().getDisplayName());
                    }
                    break;
                }
            }
            
            // 如果没有匹配到任何规则，使用默认分类
            if (!categorized && defaultRule != null) {
                this.categorizedBlocks.get(defaultRule.getCategory()).add(block);
                categorizedBlocks++;
                
                // 记录前几个未分类的方块
                if (this.categorizedBlocks.get(defaultRule.getCategory()).size() <= 10) {
                    LOGGER.info("→ 方块 {} 使用默认分类: {}", blockId, defaultRule.getCategory().getDisplayName());
                }
            }
        }
        
        // 对每个分类中的方块按名称排序
        for (List<Block> blocks : this.categorizedBlocks.values()) {
            blocks.sort(Comparator.comparing(block -> block.getName().getString()));
        }
        
        // 记录每个分类的方块数量
        LOGGER.info("🔍 方块分类统计（总计 {} 个方块，已分类 {} 个，AIR/无Item {} 个）：", 
                   totalBlocks, categorizedBlocks, airLikeBlocks);
        for (BlockCategory category : BlockCategory.values()) {
            int count = this.categorizedBlocks.get(category).size();
            LOGGER.info("  {} : {} 个方块", category.getDisplayName(), count);
            
            // 显示每个分类的前几个方块作为示例
            if (count > 0 && count < 1000) {  // 只为非杂项分类显示示例
                List<Block> categoryBlocks = this.categorizedBlocks.get(category);
                StringBuilder examples = new StringBuilder("    示例: ");
                for (int i = 0; i < Math.min(5, categoryBlocks.size()); i++) {
                    if (i > 0) examples.append(", ");
                    examples.append(Registries.BLOCK.getId(categoryBlocks.get(i)).getPath());
                }
                LOGGER.info(examples.toString());
            }
        }
        
        LOGGER.info("✓ 方块分类初始化完成");
    }
    
    /**
     * 测试特定方块的分类
     */
    private void testBlockCategorization() {
        LOGGER.info("=== 开始测试方块分类 ===");
        
        // 测试常见的建筑方块
        String[] testBlockIds = {
            "minecraft:stone", "minecraft:cobblestone", "minecraft:stone_bricks", 
            "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:bricks",
            "minecraft:quartz_block", "minecraft:sandstone", "minecraft:oak_stairs",
            "minecraft:stone_slab", "minecraft:oak_fence", "minecraft:oak_door",
            "minecraft:red_wool", "minecraft:blue_wool", "minecraft:white_concrete",
            "minecraft:dirt", "minecraft:grass_block", "minecraft:sand",
            "minecraft:redstone", "minecraft:piston", "minecraft:torch"
        };
        
        for (String blockIdStr : testBlockIds) {
            Identifier blockId = Identifier.tryParse(blockIdStr);
            if (blockId != null) {
                Block block = Registries.BLOCK.get(blockId);
                if (block != net.minecraft.block.Blocks.AIR) {
                    LOGGER.info("测试方块: {} ({})", blockIdStr, block.getName().getString());
                    
                    // 测试每个规则
                    boolean matched = false;
                    for (int i = 0; i < categoryRules.size(); i++) {
                        CategoryRule rule = categoryRules.get(i);
                        LOGGER.debug("  - 测试规则{}: {}", i + 1, rule.getCategory().getDisplayName());
                        
                        if (rule.matches(block, blockId)) {
                            LOGGER.info("  -> ✅ 匹配规则{}: {}", i + 1, rule.getCategory().getDisplayName());
                            matched = true;
                            break;
                        } else {
                            LOGGER.debug("  -> ❌ 不匹配规则{}: {}", i + 1, rule.getCategory().getDisplayName());
                        }
                    }
                    
                    if (!matched) {
                        LOGGER.info("  -> ⚠️ 未匹配任何规则，将使用默认分类: {}", 
                            defaultRule != null ? defaultRule.getCategory().getDisplayName() : "无默认规则");
                    }
                } else {
                    LOGGER.warn("无法找到方块: {}", blockIdStr);
                }
            }
        }
        
        LOGGER.info("=== 方块分类测试完成 ===");
    }
    
    /**
     * 检查方块是否在指定标签中
     * @param block 方块
     * @param tag 标签
     * @return 是否在标签中
     */
    private static boolean isInTag(Block block, TagKey<Block> tag) {
        try {
            // 使用RegistryEntry直接检查，这是推荐的现代方法
            return Registries.BLOCK.getEntry(block).isIn(tag);
        } catch (Exception e) {
            // 如果失败，使用BlockState的方法作为fallback
            try {
                return block.getDefaultState().isIn(tag);
            } catch (Exception ex) {
                LOGGER.debug("无法检查方块 {} 是否在标签 {} 中: {}", 
                    Registries.BLOCK.getId(block), tag.id(), ex.getMessage());
                return false;
            }
        }
    }
    
    /**
     * 获取指定分类的方块列表
     * @param category 分类
     * @return 方块列表
     */
    public List<Block> getBlocksInCategory(BlockCategory category) {
        return categorizedBlocks.getOrDefault(category, Collections.emptyList());
    }
    
    /**
     * 获取所有分类的方块映射
     * @return 分类到方块列表的映射
     */
    public Map<BlockCategory, List<Block>> getCategorizedBlocks() {
        return Collections.unmodifiableMap(categorizedBlocks);
    }
} 