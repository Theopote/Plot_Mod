package com.plot.infrastructure.event.block;

import com.plot.api.geometry.Vec2d;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.Events;
import com.plot.utils.PlotI18n;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 幽灵方块管理器
 * 负责管理线转方块生成的预览方块，这些方块在画布上可见但没有落到地上
 */
public class GhostBlockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/GhostBlockManager");
    private static GhostBlockManager INSTANCE;
    
    private final EventBus eventBus;
    
    // 幽灵方块存储 - 使用线程安全的集合
    private final Map<String, GhostBlock> ghostBlocks = new ConcurrentHashMap<>();
    private final List<String> blockIds = new CopyOnWriteArrayList<>();

    // 渲染相关
    private boolean renderingEnabled = true;
    private float opacity = 0.45f; // 幽灵方块透明度
    
    /**
     * 幽灵方块数据类
     */
    public static class GhostBlock {
        private final String id;
        private final Vec2d position;
        private final double height;
        private final String blockType;
        private boolean visible;
        
        public GhostBlock(String id, Vec2d position, double height, String blockType) {
            this.id = id;
            this.position = position;
            this.height = height;
            this.blockType = blockType;
            this.visible = true;
        }
        
        // Getters
        public String getId() { return id; }
        public Vec2d getPosition() { return position; }
        public double getHeight() { return height; }
        public String getBlockType() { return blockType; }
        public boolean isVisible() { return visible; }
        
        public void setVisible(boolean visible) { this.visible = visible; }
        
        /**
         * 获取Minecraft方块实例
         */
        public Block getBlock() {
            try {
                String namespace = "minecraft";
                String path = blockType;
                
                if (blockType.contains(":")) {
                    String[] parts = blockType.split(":", 2);
                    namespace = parts[0];
                    path = parts[1];
                }
                
                Identifier blockIdentifier = Identifier.of(namespace, path);
                Block block = Registries.BLOCK.get(blockIdentifier);
                return block != Blocks.AIR ? block : Blocks.WHITE_WOOL;
            } catch (Exception e) {
                LOGGER.warn("解析幽灵方块类型失败: {}, 使用默认方块", blockType, e);
                return Blocks.WHITE_WOOL;
            }
        }
        
        @Override
        public String toString() {
            return String.format("GhostBlock[id=%s, pos=(%.2f,%.2f), height=%.2f, type=%s, visible=%b]", 
                id, position.x, position.y, height, blockType, visible);
        }
    }
    
    /**
     * 获取单例实例
     */
    public static GhostBlockManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GhostBlockManager();
        }
        return INSTANCE;
    }
    
    /**
     * 私有构造函数
     */
    private GhostBlockManager() {
        this.eventBus = EventBus.getInstance();
        LOGGER.info("幽灵方块管理器已初始化");
    }
    
    /**
     * 添加幽灵方块（使用Vec2d和高度）
     *
     * @param position  方块位置 (画布坐标)
     * @param height    方块高度 (Y坐标)
     * @param blockType 方块类型ID
     */
    public void addGhostBlock(Vec2d position, double height, String blockType) {
        String id = generateGhostBlockId();
        GhostBlock ghostBlock = new GhostBlock(id, position, height, blockType);
        
        ghostBlocks.put(id, ghostBlock);
        blockIds.add(id);
        
        LOGGER.debug("添加幽灵方块: {}", ghostBlock);
    }
    
    /**
     * 添加幽灵方块（使用BlockPos）
     *
     * @param position  方块位置 (BlockPos)
     * @param blockType 方块类型ID
     */
    public void addGhostBlock(BlockPos position, String blockType) {
        // 将BlockPos转换为Vec2d和高度
        Vec2d pos2d = new Vec2d(position.getX(), position.getZ());
        double height = position.getY();
        addGhostBlock(pos2d, height, blockType);
    }

    /**
     * 移除指定的幽灵方块
     *
     * @param id 幽灵方块ID
     */
    public void removeGhostBlock(String id) {
        GhostBlock removed = ghostBlocks.remove(id);
        if (removed != null) {
            blockIds.remove(id);
            LOGGER.debug("移除幽灵方块: {}", removed);
        }
    }
    
    /**
     * 清空所有幽灵方块
     */
    public void clearAllGhostBlocks() {
        int count = 0;
        try {
            count = ghostBlocks.size();
            if (count == 0) {
                return;
            }
            ghostBlocks.clear();
            blockIds.clear();
            LOGGER.info("已清空所有幽灵方块，共清理 {} 个", count);
        } catch (Throwable t) {
            LOGGER.error("清空幽灵方块集合时发生异常", t);
            return;
        }

        try {
            eventBus.publish(new Events.StatusMessageEvent("GhostBlockManager",
                PlotI18n.status("status.plot.ghost.cleared", count)));
        } catch (Throwable t) {
            LOGGER.error("发布幽灵方块清理状态失败", t);
        }
    }

    /**
     * 获取可见的幽灵方块列表
     * @return 可见的幽灵方块列表
     */
    public List<GhostBlock> getVisibleGhostBlocks() {
        return ghostBlocks.values().stream()
                .filter(GhostBlock::isVisible)
                .toList();
    }

    /**
     * 获取可见幽灵方块数量
     * @return 可见的幽灵方块数量
     */
    public int getVisibleGhostBlockCount() {
        return (int) ghostBlocks.values().stream()
                .filter(GhostBlock::isVisible)
                .count();
    }
    
    /**
     * 获取渲染相关设置
     */
    public boolean isRenderingEnabled() {
        return renderingEnabled;
    }
    
    public float getOpacity() {
        return opacity;
    }
    
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        LOGGER.debug("幽灵方块透明度设置: {}", this.opacity);
    }
    
    /**
     * 生成唯一的幽灵方块ID
     */
    private String generateGhostBlockId() {
        return "ghost_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString(new Random().nextInt());
    }
} 
