package com.masterplanner.infrastructure.event.block;

import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.infrastructure.event.Events;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 方块投影事件处理器
 * 负责处理方块投影事件，在Minecraft世界中放置方块
 */
public class BlockProjectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/BlockProjectionHandler");
    private static BlockProjectionHandler INSTANCE;
    private final EventBus eventBus;

    /**
     * 获取单例实例
     */
    public static BlockProjectionHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BlockProjectionHandler();
        }
        return INSTANCE;
    }

    /**
     * 私有构造函数
     */
    private BlockProjectionHandler() {
        this.eventBus = EventBus.getInstance();
        registerEventListeners();
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        eventBus.subscribe(BlockProjectionEvent.class, this::handleBlockProjectionEvent);
    }

    /**
     * 处理方块投影事件
     */
    private void handleBlockProjectionEvent(Event event) {
        if (!(event instanceof BlockProjectionEvent blockEvent)) {
            return;
        }

        LOGGER.info("收到方块投影事件: blockId={}, x={}, y={}, z={}, rotation={}, preview={}",
                blockEvent.getBlockId(), blockEvent.getX(), blockEvent.getY(), blockEvent.getZ(),
                blockEvent.getRotation(), blockEvent.isPreview());

        // 获取Minecraft客户端实例
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            LOGGER.error("无法处理方块投影事件：Minecraft客户端、玩家或世界为null");
            return;
        }

        // 获取玩家和世界
        PlayerEntity player = client.player;
        World world = client.world;

        // 获取方块ID
        String blockId = blockEvent.getBlockId();
        if (blockId == null || blockId.isEmpty()) {
            LOGGER.warn("方块ID为空，使用默认方块(白色羊毛)");
            blockId = "minecraft:white_wool";
        }

        // 解析方块ID
        Block blockType;
        try {
            // 解析命名空间和路径
            String namespace = "minecraft";
            String path = blockId;
            
            if (blockId.contains(":")) {
                String[] parts = blockId.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            }
            
            Identifier blockIdentifier = Identifier.of(namespace, path);
            blockType = Registries.BLOCK.get(blockIdentifier);
            if (blockType == Blocks.AIR) {
                LOGGER.warn("无法找到方块: {}，使用默认方块(白色羊毛)", blockId);
                blockType = Blocks.WHITE_WOOL;
                blockId = "minecraft:white_wool";
            }
        } catch (Exception e) {
            LOGGER.error("解析方块ID失败: {}", blockId, e);
            blockType = Blocks.WHITE_WOOL;
            blockId = "minecraft:white_wool";
        }

        // 计算方块位置
        int x = (int) Math.round(blockEvent.getX());
        int y = (int) Math.round(blockEvent.getY());
        int z = (int) Math.round(blockEvent.getZ());
        
        // 如果是预览模式，使用指定的Y坐标
        if (blockEvent.isPreview()) {
            BlockPos blockPos = new BlockPos(x, y, z);
            LOGGER.info("预览方块: {} 在位置 {}", blockType.getName().getString(), blockPos);
            return;
        }
        
        // 非预览模式：垂直向下扫描找到地面
        BlockPos finalPos;
        if (blockEvent.getProjectionMode() == BlockProjectionEvent.ProjectionMode.ELEVATION) {
            int targetY = blockEvent.getElevation() != null ? blockEvent.getElevation() : y;
            finalPos = new BlockPos(x, targetY, z);
        } else {
            finalPos = findGroundPosition(world, x, y, z);
        }

        LOGGER.debug("最终方块位置: {} (原始: {}, {}, {})", finalPos, x, y, z);

        // 检查区块是否已加载
        int chunkX = finalPos.getX() >> 4;
        int chunkZ = finalPos.getZ() >> 4;
        
        // 获取玩家位置
        double playerX = player.getX();
        double playerZ = player.getZ();
        
        // 计算方块位置到玩家的直接距离（而不是区块中心距离）
        double distanceToPlayer = Math.sqrt(
            Math.pow(finalPos.getX() - playerX, 2) + 
            Math.pow(finalPos.getZ() - playerZ, 2)
        );
        
        // 如果方块距离玩家太远，发出警告并返回
        // 增加到256个方块的距离限制（16个区块）
        if (distanceToPlayer > 256) {
            LOGGER.warn("该位置距离玩家太远，无法放置方块: {} (距离: {:.1f}方块)", finalPos, distanceToPlayer);
            eventBus.publish(new Events.WarningEvent("BlockProjectionHandler", 
                String.format("目标位置 (%d, %d, %d) 距离太远，请靠近后重试 (%.1f方块)", 
                finalPos.getX(), finalPos.getY(), finalPos.getZ(), distanceToPlayer)));
            return;
        }

        // 检查区块是否已加载
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            LOGGER.warn("该位置尚未被加载，无法放置方块: {}", finalPos);
            eventBus.publish(new Events.WarningEvent("BlockProjectionHandler", 
                String.format("目标位置 (%d, %d, %d) 尚未加载，请靠近后重试", 
                finalPos.getX(), finalPos.getY(), finalPos.getZ())));
            return;
        }

        // 在创造模式下放置方块
        if (player.getAbilities().creativeMode) {
            try {
                // 在服务端执行放置方块的命令
                String command = String.format("setblock %d %d %d %s", 
                    finalPos.getX(), finalPos.getY(), finalPos.getZ(), blockId);
                LOGGER.info("执行命令: {}", command);
                Objects.requireNonNull(client.getNetworkHandler()).sendChatCommand(command);
                LOGGER.info("方块放置命令已发送");
            } catch (Exception e) {
                LOGGER.error("放置方块失败", e);
                eventBus.publish(new Events.WarningEvent("BlockProjectionHandler", 
                    String.format("在位置 (%d, %d, %d) 放置方块失败", 
                    finalPos.getX(), finalPos.getY(), finalPos.getZ())));
            }
        } else {
            LOGGER.warn("玩家不在创造模式，无法放置方块");
            eventBus.publish(new Events.WarningEvent("BlockProjectionHandler", "请切换到创造模式后再试"));
        }
    }
    
    /**
     * 垂直向下扫描找到地面位置
     * 从指定位置开始向下扫描，找到第一个非空气且可被替换的方块位置
     * 
     * @param world Minecraft世界
     * @param x X坐标
     * @param y 起始Y坐标
     * @param z Z坐标
     * @return 找到的地面位置，如果没找到则返回null
     */
    private BlockPos findGroundPosition(World world, int x, int y, int z) {
        // 从指定高度开始向下扫描
        for (int currentY = y; currentY >= world.getBottomY(); currentY--) {
            BlockPos checkPos = new BlockPos(x, currentY, z);
            
            try {
                // 获取当前位置的方块状态
                var blockState = world.getBlockState(checkPos);
                
                // 检查方块是否可以被替换
                if (!canReplaceBlock(world, checkPos, blockState)) {
                    // 找到可替换的位置，返回上方一格作为放置位置
                    BlockPos placePos = new BlockPos(x, currentY + 1, z);
                    LOGGER.debug("找到地面位置: {} (原位置: {})", placePos, checkPos);
                    return placePos;
                }
            } catch (Exception e) {
                LOGGER.warn("检查位置 {} 时发生错误: {}", checkPos, e.getMessage());
            }
        }
        
        // 如果扫描到底部都没找到合适位置，返回底部上方一格
        BlockPos bottomPos = new BlockPos(x, world.getBottomY() + 1, z);
        LOGGER.warn("扫描到底部都没找到合适位置，使用底部位置: {}", bottomPos);
        return bottomPos;
    }
    
    /**
     * 检查方块是否可以被替换
     * 
     * @param blockState 方块状态
     * @return 是否可以被替换
     */
    private boolean canReplaceBlock(World world, BlockPos pos, net.minecraft.block.BlockState blockState) {
        if (blockState == null) {
            return true;
        }
        
        // 检查是否为空气
        if (blockState.isAir()) {
            return true;
        }
        
        // 检查是否为液体
        if (!blockState.getFluidState().isEmpty()) {
            return false;
        }
        
        // 检查是否为植物（草、花等）
        if (blockState.getBlock() instanceof net.minecraft.block.PlantBlock) {
            return true; // 植物可以被替换
        }
        
        // 检查是否为雪
        if (blockState.getBlock() instanceof net.minecraft.block.SnowBlock) {
            return true; // 雪可以被替换
        }
        
        // 检查是否为树叶
        if (blockState.getBlock() instanceof net.minecraft.block.LeavesBlock) {
            return true; // 树叶可以被替换
        }
        
        // 检查是否为固体方块（使用新的API）
        return !blockState.isSolidBlock(world, pos);
    }
} 
