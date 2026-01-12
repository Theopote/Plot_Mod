package com.masterplanner.ui.component;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强的方块图标渲染器
 * 
 * 提供稳定的方块图标渲染功能，支持缓存和错误恢复
 * 使用ImGui的纹理绑定API确保正确显示
 * 
 * 线程安全修复：
 * - 所有OpenGL操作都在synchronized方法中执行，确保在主渲染线程上运行
 * - 修复了初始化逻辑的竞态条件
 * - 完善了OpenGL状态恢复机制
 */
public final class BlockIconRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/BlockIconRenderer");
    // 延迟获取客户端实例，避免在类加载时获取null
    private static MinecraftClient getClient() {
        return MinecraftClient.getInstance();
    }

    // === 常量 ===
    private static final int ICON_RESOLUTION = 32; // 纹理分辨率
    private static final int ICON_PADDING = 4; // 图标内边距

    // === 缓存 ===
    private static final Map<Block, ItemStack> itemStackCache = new ConcurrentHashMap<>();
    private static final Map<Block, String> blockNameCache = new ConcurrentHashMap<>();
    private static final Map<Block, Integer> textureIdCache = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    
    // === 线程安全锁（已移除未使用字段以消除警告） ===

    private BlockIconRenderer() {}

    /**
     * 从 RenderSystem#setProjectionMatrix 的第二个枚举参数类型中，挑选一个适合正交投影的枚举常量。
     * 尝试名称包含 ORTHO/ORTHOGRAPHIC/BY_Z，其次回退到第一个常量。
     */
    private static Object pickProjectionSecondParam(Class<?> enumType) {
        if (enumType == null || !enumType.isEnum()) return null;
        Object[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) return null;
        Object fallback = constants[0];
        Object byZCandidate = null;
        for (Object c : constants) {
            String name = (c instanceof Enum<?> e) ? e.name() : String.valueOf(c);
            if (name != null) {
                String upper = name.toUpperCase();
                if (upper.contains("ORTHO") || upper.contains("ORTHOGRAPHIC")) {
                    return c;
                }
                if (upper.contains("BY_Z")) {
                    byZCandidate = c;
                }
            }
        }
        return byZCandidate != null ? byZCandidate : fallback;
    }

    /**
     * 初始化渲染器 - 线程安全版本
     */
    public static synchronized void initialize() {
        if (initialized) {
            LOGGER.debug("BlockIconRenderer 已经初始化");
            return;
        }
        
        try {
            // 确保Minecraft客户端已初始化
            MinecraftClient client = getClient();
            if (client == null) {
                LOGGER.error("Minecraft客户端未初始化，无法创建BlockIconRenderer");
                return;
            }
            
            // 检查是否在主渲染线程
            if (!client.isOnThread()) {
                throw new IllegalStateException("BlockIconRenderer.initialize() 必须在主渲染线程上执行");
            }

            // Minecraft 1.21.11 渲染管线变更：Framebuffer / RenderSystem / DrawContext 构造与状态API发生大幅调整。
            // 当前版本先降级为“无离屏纹理渲染”，改由 GuiOverlayRenderer 在 ImGui 后覆盖绘制物品图标。
            // 这样能保证编译通过并保留核心 UI 功能（方块图标仍可见）。
            initialized = true;
            LOGGER.info("BlockIconRenderer 初始化成功");
            
            // 预加载一些常用方块
            preloadCommonBlocks();
            
        } catch (Exception e) {
            LOGGER.error("BlockIconRenderer 初始化失败", e);
            initialized = false;
        }
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取方块的纹理ID（用于ImGui）- 线程安全版本
     * 
     * @param block 方块
     * @return 纹理ID，如果失败则返回-1
     */
    public static int getBlockTextureId(Block block) {
        if (block == null) {
            LOGGER.debug("方块为null，返回-1");
            return -1;
        }
        
        // 检查是否在主渲染线程
        MinecraftClient client = getClient();
        if (client != null && !client.isOnThread()) {
            throw new IllegalStateException("getBlockTextureId 必须在主渲染线程上调用, 当前线程: " + Thread.currentThread().getName());
        }
        
        if (!isInitialized()) {
            LOGGER.debug("BlockIconRenderer未初始化，尝试初始化");
            initialize();
            if (!isInitialized()) {
                LOGGER.error("BlockIconRenderer初始化失败，无法创建纹理");
                return -1;
            }
        }
        
        return textureIdCache.computeIfAbsent(block, b -> {
            try {
                return createBlockTexture(b);
            } catch (Exception e) {
                LOGGER.error("为方块 {} 创建纹理失败", Registries.BLOCK.getId(b), e);
                return -1;
            }
        });
    }

    /**
     * 创建方块纹理 - 线程安全版本，使用ImGui纹理绑定
     * 
     * 注意：此方法必须在主渲染线程上调用，且已通过synchronized保证线程安全
     */
    private static synchronized int createBlockTexture(Block block) {
        // 1.21.11：离屏渲染路径暂时禁用（相关 API 已变化且会导致编译失败）
        // 目前方块图标在 UI 中通过 GuiOverlayRenderer（DrawContext.drawItem）覆盖绘制。
        return -1;
    }

    /**
     * 获取方块的物品堆栈
     * 
     * @param block 方块
     * @return 对应的物品堆栈
     */
    public static ItemStack getItemStackForBlock(Block block) {
        if (block == null) {
            LOGGER.debug("方块为null，返回空物品堆栈");
            return ItemStack.EMPTY;
        }
        
        return itemStackCache.computeIfAbsent(block, b -> {
            try {
                // 特殊处理流体方块
                if (b == Blocks.WATER) {
                    return new ItemStack(Items.WATER_BUCKET);
                } else if (b == Blocks.LAVA) {
                    return new ItemStack(Items.LAVA_BUCKET);
                } else if (b == Blocks.POWDER_SNOW) {
                    return new ItemStack(Items.POWDER_SNOW_BUCKET);
                } else if (b == Blocks.AIR) {
                    // 空气方块没有物品形式
                    return ItemStack.EMPTY;
                } else {
                    // 尝试获取方块的物品形式
                    try {
                        ItemStack stack = new ItemStack(b.asItem());
                        if (stack.isEmpty()) {
                            LOGGER.debug("方块 {} 的物品形式为空", Registries.BLOCK.getId(b));
                            return ItemStack.EMPTY;
                        }
                        return stack;
                    } catch (Exception e) {
                        LOGGER.debug("无法为方块 {} 创建物品堆栈: {}", Registries.BLOCK.getId(b), e.getMessage());
                        return ItemStack.EMPTY;
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("处理方块 {} 时发生异常: {}", Registries.BLOCK.getId(b), e.getMessage());
                return ItemStack.EMPTY;
            }
        });
    }

    /**
     * 获取方块的显示名称
     * 
     * @param block 方块
     * @return 本地化的方块名称
     */
    public static String getBlockDisplayName(Block block) {
        if (block == null) return "未知方块";
        
        return blockNameCache.computeIfAbsent(block, b -> {
            try {
                String name = b.getName().getString();
                if (name == null || name.isEmpty()) {
                    return Registries.BLOCK.getId(b).toString();
                }
                return name;
            } catch (Exception e) {
                LOGGER.debug("无法获取方块 {} 的名称: {}", Registries.BLOCK.getId(b), e.getMessage());
                return Registries.BLOCK.getId(b).toString();
            }
        });
    }

    /**
     * 检查方块是否有有效的图标
     * 
     * @param block 方块
     * @return 是否有有效图标
     */
    public static boolean hasValidIcon(Block block) {
        if (block == null) return false;
        
        ItemStack itemStack = getItemStackForBlock(block);
        boolean hasValidIcon = !itemStack.isEmpty();
        
        if (!hasValidIcon) {
            LOGGER.debug("方块 {} 没有有效图标", Registries.BLOCK.getId(block));
        }
        
        return hasValidIcon;
    }

    /**
     * 清理缓存 - 线程安全版本
     */
    public static synchronized void clearCache() {
        LOGGER.info("开始清理BlockIconRenderer缓存...");
        
        itemStackCache.clear();
        blockNameCache.clear();
        
        // 清理纹理
        int deletedCount = 0;
        for (Integer textureId : textureIdCache.values()) {
            if (textureId != null && textureId > 0) {
                try {
                    GL11.glDeleteTextures(textureId);
                    deletedCount++;
                } catch (Exception e) {
                    LOGGER.warn("删除纹理时出错: {}", e.getMessage());
                }
            }
        }
        textureIdCache.clear();
        
        LOGGER.info("BlockIconRenderer 缓存已清理，删除了 {} 个纹理", deletedCount);
    }

    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("物品堆栈缓存: %d, 名称缓存: %d, 纹理缓存: %d", 
            itemStackCache.size(), blockNameCache.size(), textureIdCache.size());
    }

    /**
     * 预加载常用方块 - 线程安全版本
     */
    public static synchronized void preloadCommonBlocks() {
        LOGGER.info("开始预加载常用方块图标...");
        
        Block[] commonBlocks = {
            Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COBBLESTONE,
            Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
            Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY,
            Blocks.WATER, Blocks.LAVA, Blocks.OBSIDIAN,
            Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.GOLD_ORE,
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.BRICKS, Blocks.GLASS, Blocks.WHITE_WOOL,
            Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK
        };
        
        int successCount = 0;
        for (Block block : commonBlocks) {
            try {
                ItemStack stack = getItemStackForBlock(block);
                if (!stack.isEmpty()) {
                    getBlockDisplayName(block);
                    int textureId = getBlockTextureId(block);
                    if (textureId != -1) {
                        successCount++;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("预加载方块 {} 时出错: {}", Registries.BLOCK.getId(block), e.getMessage());
            }
        }
        
        LOGGER.info("常用方块图标预加载完成，成功加载 {}/{} 个方块，缓存统计: {}", 
                   successCount, commonBlocks.length, getCacheStats());
    }

    /**
     * 清理资源 - 线程安全版本
     * 
     * 重要：必须在Mod关闭时调用此方法，否则会造成VRAM泄漏
     */
    public static synchronized void cleanup() {
        LOGGER.info("开始清理BlockIconRenderer资源...");
        clearCache();
        initialized = false;
        LOGGER.info("BlockIconRenderer 资源清理完成");
    }

    /**
     * 测试方块图标渲染功能 - 线程安全版本
     * 用于验证渲染器是否正常工作
     */
    public static synchronized void testBlockIconRendering() {
        LOGGER.info("开始测试方块图标渲染功能...");
        
        if (!isInitialized()) {
            LOGGER.warn("BlockIconRenderer未初始化，无法进行测试");
            return;
        }
        
        Block[] testBlocks = {
            Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COBBLESTONE,
            Blocks.OAK_PLANKS, Blocks.BRICKS, Blocks.GLASS, Blocks.WHITE_WOOL
        };
        
        int successCount = 0;
        int totalCount = testBlocks.length;
        
        for (Block block : testBlocks) {
            try {
                // 测试物品堆栈获取
                ItemStack stack = getItemStackForBlock(block);
                if (stack.isEmpty()) {
                    LOGGER.warn("测试方块 {} 无法获取物品堆栈", Registries.BLOCK.getId(block));
                    continue;
                }
                
                // 测试纹理创建
                int textureId = getBlockTextureId(block);
                if (textureId == -1) {
                    LOGGER.warn("测试方块 {} 无法创建纹理", Registries.BLOCK.getId(block));
                    continue;
                }
                
                // 验证纹理是否有效 - 使用正确的OpenGL API
                if (!isTextureValid(textureId)) {
                    LOGGER.warn("测试方块 {} 纹理无效", Registries.BLOCK.getId(block));
                    continue;
                }
                
                // 测试名称获取
                String name = getBlockDisplayName(block);
                if (name == null || name.isEmpty()) {
                    LOGGER.warn("测试方块 {} 无法获取名称", Registries.BLOCK.getId(block));
                    continue;
                }
                
                successCount++;
                LOGGER.debug("测试方块 {} 通过所有测试 (textureId: {})", Registries.BLOCK.getId(block), textureId);
                
            } catch (Exception e) {
                LOGGER.error("测试方块 {} 时发生异常: {}", Registries.BLOCK.getId(block), e.getMessage());
            }
        }
        
        LOGGER.info("方块图标渲染测试完成: {}/{} 个方块测试通过", successCount, totalCount);
        
        if (successCount == 0) {
            LOGGER.error("所有测试方块都失败，可能存在严重问题");
        } else if (successCount < totalCount) {
            LOGGER.warn("部分测试方块失败，可能存在兼容性问题");
        } else {
            LOGGER.info("所有测试方块都通过，渲染器工作正常");
        }
    }

    /**
     * 验证纹理是否有效 - 修复版本
     * 
     * 使用正确的OpenGL API来验证纹理是否有效
     */
    public static boolean isTextureValid(int textureId) {
        if (textureId <= 0) return false;
        
        try {
            // 绑定纹理
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            
            // 检查纹理是否有效 - 使用glGetTexLevelParameteriv获取纹理信息
            int[] width = new int[1];
            int[] height = new int[1];
            
            // 获取纹理的宽度和高度
            GL11.glGetTexLevelParameteriv(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH, width);
            GL11.glGetTexLevelParameteriv(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT, height);
            
            // 解绑纹理
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            
            // 检查纹理尺寸是否有效
            boolean isValid = width[0] > 0 && height[0] > 0;
            
            if (!isValid) {
                LOGGER.debug("纹理验证失败: textureId={}, width={}, height={}", textureId, width[0], height[0]);
            }
            
            return isValid;
            
        } catch (Exception e) {
            LOGGER.debug("验证纹理时出错: textureId={}, error={}", textureId, e.getMessage());
            return false;
        }
    }

    // === 兼容性方法 ===
    
    /**
     * @deprecated 使用 getBlockTextureId(Block) 替代
     */
    @Deprecated
    public static int getBlockTextureId(Block block, boolean highPriority) {
        return getBlockTextureId(block);
    }

    /**
     * @deprecated 不再需要渲染tick
     */
    @Deprecated
    public static void renderTick() {
        // 空实现，不再需要
    }

    /**
     * @deprecated 使用 initialize() 替代
     */
    @Deprecated
    public static boolean ensureInitialized() {
        if (!initialized) {
            initialize();
        }
        return initialized;
    }
}