package com.masterplanner.infrastructure.event.block;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.Events;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/GhostBlockManager");
    private static GhostBlockManager INSTANCE;
    
    private final EventBus eventBus;
    
    // 幽灵方块存储 - 使用线程安全的集合
    private final Map<String, GhostBlock> ghostBlocks = new ConcurrentHashMap<>();
    private final List<String> blockIds = new CopyOnWriteArrayList<>();
    
    // 渲染相关
    private boolean renderingEnabled = true;
    private float opacity = 0.6f; // 幽灵方块透明度
    
    /**
     * 幽灵方块数据类
     */
    public static class GhostBlock {
        private final String id;
        private final Vec2d position;
        private final double height;
        private final String blockType;
        private final long createdTime;
        private boolean visible;
        
        public GhostBlock(String id, Vec2d position, double height, String blockType) {
            this.id = id;
            this.position = position;
            this.height = height;
            this.blockType = blockType;
            this.createdTime = System.currentTimeMillis();
            this.visible = true;
        }
        
        // Getters
        public String getId() { return id; }
        public Vec2d getPosition() { return position; }
        public double getHeight() { return height; }
        public String getBlockType() { return blockType; }
        public long getCreatedTime() { return createdTime; }
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
     * @param position 方块位置 (画布坐标)
     * @param height 方块高度 (Y坐标)
     * @param blockType 方块类型ID
     * @return 生成的幽灵方块ID
     */
    public String addGhostBlock(Vec2d position, double height, String blockType) {
        String id = generateGhostBlockId();
        GhostBlock ghostBlock = new GhostBlock(id, position, height, blockType);
        
        ghostBlocks.put(id, ghostBlock);
        blockIds.add(id);
        
        LOGGER.debug("添加幽灵方块: {}", ghostBlock);
        
        // 发布幽灵方块添加事件
        eventBus.publish(new Events.WarningEvent("GhostBlockManager", 
            String.format("已添加幽灵方块: %s 在位置 (%.2f, %.2f, %.2f)", 
            blockType, position.x, height, position.y)));
        
        return id;
    }
    
    /**
     * 添加幽灵方块（使用BlockPos）
     * @param position 方块位置 (BlockPos)
     * @param blockType 方块类型ID
     * @return 生成的幽灵方块ID
     */
    public String addGhostBlock(BlockPos position, String blockType) {
        // 将BlockPos转换为Vec2d和高度
        Vec2d pos2d = new Vec2d(position.getX(), position.getZ());
        double height = position.getY();
        return addGhostBlock(pos2d, height, blockType);
    }
    
    /**
     * 批量添加幽灵方块
     * @param positions 方块位置列表
     * @param height 统一高度
     * @param blockType 方块类型
     * @return 添加的幽灵方块数量
     */
    public int addGhostBlocks(List<Vec2d> positions, double height, String blockType) {
        int count = 0;
        for (Vec2d position : positions) {
            addGhostBlock(position, height, blockType);
            count++;
        }
        
        LOGGER.info("批量添加了 {} 个幽灵方块", count);
        return count;
    }
    
    /**
     * 移除指定的幽灵方块
     * @param id 幽灵方块ID
     * @return 是否成功移除
     */
    public boolean removeGhostBlock(String id) {
        GhostBlock removed = ghostBlocks.remove(id);
        if (removed != null) {
            blockIds.remove(id);
            LOGGER.debug("移除幽灵方块: {}", removed);
            return true;
        }
        return false;
    }
    
    /**
     * 清空所有幽灵方块
     */
    public void clearAllGhostBlocks() {
        int count = ghostBlocks.size();
        ghostBlocks.clear();
        blockIds.clear();
        
        LOGGER.info("已清空所有幽灵方块，共清理 {} 个", count);
        
        // 发布清理事件
        eventBus.publish(new Events.WarningEvent("GhostBlockManager", 
            String.format("已清理 %d 个幽灵方块", count)));
    }
    
    /**
     * 获取所有幽灵方块
     * @return 幽灵方块映射的副本
     */
    public Map<String, GhostBlock> getAllGhostBlocks() {
        return new HashMap<>(ghostBlocks);
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
     * 获取幽灵方块数量
     * @return 幽灵方块总数
     */
    public int getGhostBlockCount() {
        return ghostBlocks.size();
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
     * 设置所有幽灵方块的可见性
     * @param visible 是否可见
     */
    public void setAllGhostBlocksVisible(boolean visible) {
        ghostBlocks.values().forEach(block -> block.setVisible(visible));
        LOGGER.info("设置所有幽灵方块可见性: {}", visible);
    }
    
    /**
     * 投影所有幽灵方块到minecraft世界
     * 将幽灵方块转换为真实方块并清理幽灵方块
     * @return 投影的方块数量
     */
    public int projectAllGhostBlocks() {
        List<GhostBlock> visibleBlocks = getVisibleGhostBlocks();
        int projectedCount = 0;
        
        for (GhostBlock ghostBlock : visibleBlocks) {
            // 发布方块投影事件，设置为非预览模式（真实投影）
            eventBus.publish(new BlockProjectionEvent(
                ghostBlock.getBlockType(),
                ghostBlock.getPosition().x,
                ghostBlock.getHeight(),
                ghostBlock.getPosition().y,
                0.0f,
                false  // 非预览模式，实际投影
            ));
            projectedCount++;
        }
        
        // 投影完成后清理幽灵方块
        clearAllGhostBlocks();
        
        LOGGER.info("已投影 {} 个幽灵方块到minecraft世界", projectedCount);
        
        // 发布投影完成事件
        eventBus.publish(new Events.WarningEvent("GhostBlockManager", 
            String.format("已成功投影 %d 个方块到minecraft世界", projectedCount)));
        
        return projectedCount;
    }
    
    /**
     * 获取渲染相关设置
     */
    public boolean isRenderingEnabled() {
        return renderingEnabled;
    }
    
    public void setRenderingEnabled(boolean enabled) {
        this.renderingEnabled = enabled;
        LOGGER.debug("幽灵方块渲染设置: {}", enabled);
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
    
    /**
     * 获取幽灵方块统计信息
     * @return 统计信息字符串
     */
    public String getStatistics() {
        int total = getGhostBlockCount();
        int visible = getVisibleGhostBlockCount();
        return String.format("幽灵方块统计: 总数=%d, 可见=%d, 渲染=%s", 
            total, visible, renderingEnabled ? "开启" : "关闭");
    }
    
    /**
     * 检查指定位置是否有幽灵方块
     * @param position 位置
     * @param tolerance 容差范围
     * @return 是否存在幽灵方块
     */
    public boolean hasGhostBlockAt(Vec2d position, double tolerance) {
        return ghostBlocks.values().stream()
                .anyMatch(block -> {
                    double distance = Math.sqrt(
                        Math.pow(block.getPosition().x - position.x, 2) +
                        Math.pow(block.getPosition().y - position.y, 2)
                    );
                    return distance <= tolerance;
                });
    }
    
    /**
     * 获取指定位置附近的幽灵方块
     * @param position 位置
     * @param radius 搜索半径
     * @return 附近的幽灵方块列表
     */
    public List<GhostBlock> getGhostBlocksNear(Vec2d position, double radius) {
        return ghostBlocks.values().stream()
                .filter(block -> {
                    double distance = Math.sqrt(
                        Math.pow(block.getPosition().x - position.x, 2) +
                        Math.pow(block.getPosition().y - position.y, 2)
                    );
                    return distance <= radius;
                })
                .toList();
    }
} 