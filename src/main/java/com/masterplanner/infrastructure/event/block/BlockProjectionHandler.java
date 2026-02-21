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

    public static class ProjectionResult {
        private final boolean success;
        private final String message;
        private final BlockPos finalPos;
        private final String normalizedBlockId;
        private final String previousBlockId;

        public ProjectionResult(boolean success, String message, BlockPos finalPos, String normalizedBlockId, String previousBlockId) {
            this.success = success;
            this.message = message;
            this.finalPos = finalPos;
            this.normalizedBlockId = normalizedBlockId;
            this.previousBlockId = previousBlockId;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public BlockPos getFinalPos() {
            return finalPos;
        }

        public String getNormalizedBlockId() {
            return normalizedBlockId;
        }

        public String getPreviousBlockId() {
            return previousBlockId;
        }
    }

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

        ProjectionResult result = projectBlockWithResult(
                blockEvent.getBlockId(),
                blockEvent.getX(),
                blockEvent.getY(),
                blockEvent.getZ(),
                blockEvent.getProjectionMode(),
                blockEvent.getElevation(),
                blockEvent.isPreview()
        );

        if (!result.isSuccess()) {
            LOGGER.warn("方块投影失败: {}", result.getMessage());
            eventBus.publish(new Events.WarningEvent("BlockProjectionHandler", result.getMessage()));
        }
    }

    public ProjectionResult projectBlockWithResult(
            String blockId,
            double xInput,
            double yInput,
            double zInput,
            BlockProjectionEvent.ProjectionMode projectionMode,
            Integer elevation,
            boolean preview
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return new ProjectionResult(false, "Minecraft客户端未就绪", null, null, null);
        }

        PlayerEntity player = client.player;
        World world = client.world;

        String normalizedBlockId = normalizeBlockId(blockId, false);
        int x = (int) Math.round(xInput);
        int y = (int) Math.round(yInput);
        int z = (int) Math.round(zInput);

        if (preview) {
            BlockPos previewPos = new BlockPos(x, y, z);
            LOGGER.info("预览方块: {} 在位置 {}", normalizedBlockId, previewPos);
            return new ProjectionResult(true, "预览成功", previewPos, normalizedBlockId, null);
        }

        BlockProjectionEvent.ProjectionMode mode = projectionMode == null
                ? BlockProjectionEvent.ProjectionMode.GROUND
                : projectionMode;

        BlockPos finalPos;
        if (mode == BlockProjectionEvent.ProjectionMode.ELEVATION) {
            int targetY = elevation != null ? elevation : y;
            finalPos = new BlockPos(x, targetY, z);
        } else {
            finalPos = findPlacementPosition(world, x, y, z);
        }

        String validationError = validatePlacementContext(player, world, finalPos);
        if (validationError != null) {
            return new ProjectionResult(false, validationError, finalPos, normalizedBlockId, null);
        }

        String previousBlockId = getBlockIdAt(finalPos);
        boolean placed = sendSetBlockCommand(client, finalPos, normalizedBlockId);
        if (!placed) {
            return new ProjectionResult(false,
                    String.format("在位置 (%d, %d, %d) 放置方块失败", finalPos.getX(), finalPos.getY(), finalPos.getZ()),
                    finalPos,
                    normalizedBlockId,
                    previousBlockId);
        }

        return new ProjectionResult(true, "投影成功", finalPos, normalizedBlockId, previousBlockId);
    }

    public boolean setBlockAt(BlockPos pos, String blockId) {
        if (pos == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        String normalizedBlockId = normalizeBlockId(blockId, true);
        String validationError = validatePlacementContext(client.player, client.world, pos);
        if (validationError != null) {
            LOGGER.warn("setBlockAt失败: {}", validationError);
            return false;
        }

        return sendSetBlockCommand(client, pos, normalizedBlockId);
    }

    public String getBlockIdAt(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || pos == null) {
            return "minecraft:air";
        }

        try {
            Block block = client.world.getBlockState(pos).getBlock();
            Identifier id = Registries.BLOCK.getId(block);
            return id.toString();
        } catch (Exception e) {
            LOGGER.warn("读取方块ID失败: {}", pos, e);
            return "minecraft:air";
        }
    }

    private String normalizeBlockId(String blockId, boolean allowAir) {
        String candidate = (blockId == null || blockId.isEmpty()) ? "minecraft:white_wool" : blockId;
        try {
            String namespace = "minecraft";
            String path = candidate;
            if (candidate.contains(":")) {
                String[] parts = candidate.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            }

            Identifier blockIdentifier = Identifier.of(namespace, path);
            Block blockType = Registries.BLOCK.get(blockIdentifier);
            if (blockType == Blocks.AIR) {
                return allowAir ? "minecraft:air" : "minecraft:white_wool";
            }
            return blockIdentifier.toString();
        } catch (Exception e) {
            if (allowAir) {
                LOGGER.warn("解析方块ID失败: {}，恢复路径回退空气", candidate, e);
                return "minecraft:air";
            }
            LOGGER.warn("解析方块ID失败: {}，回退白色羊毛", candidate, e);
            return "minecraft:white_wool";
        }
    }

    private String validatePlacementContext(PlayerEntity player, World world, BlockPos finalPos) {
        if (player == null || world == null || finalPos == null) {
            return "Minecraft客户端未就绪";
        }

        double distanceToPlayer = Math.sqrt(
                Math.pow(finalPos.getX() - player.getX(), 2) +
                        Math.pow(finalPos.getZ() - player.getZ(), 2)
        );

        if (distanceToPlayer > 256) {
            return String.format("目标位置 (%d, %d, %d) 距离太远，请靠近后重试 (%.1f方块)",
                    finalPos.getX(), finalPos.getY(), finalPos.getZ(), distanceToPlayer);
        }

        int chunkX = finalPos.getX() >> 4;
        int chunkZ = finalPos.getZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return String.format("目标位置 (%d, %d, %d) 尚未加载，请靠近后重试",
                    finalPos.getX(), finalPos.getY(), finalPos.getZ());
        }

        if (!player.getAbilities().creativeMode) {
            return "请切换到创造模式后再试";
        }

        return null;
    }

    private boolean sendSetBlockCommand(MinecraftClient client, BlockPos pos, String blockId) {
        try {
            String command = String.format("setblock %d %d %d %s", pos.getX(), pos.getY(), pos.getZ(), blockId);
            LOGGER.info("执行命令: {}", command);
            Objects.requireNonNull(client.getNetworkHandler()).sendChatCommand(command);
            return true;
        } catch (Exception e) {
            LOGGER.error("发送setblock命令失败", e);
            return false;
        }
    }

    /**
     * 按规则寻找投影放置位置：
     * - 水面：放在流体方块上方（表面）
     * - 植物/装饰/积雪层：直接替换当前位置
     * - 雪块：放在雪块上方
     * - 其他实心方块：放在方块上方
     */
    private BlockPos findPlacementPosition(World world, int x, int y, int z) {
        for (int currentY = y; currentY >= world.getBottomY(); currentY--) {
            BlockPos checkPos = new BlockPos(x, currentY, z);

            try {
                var blockState = world.getBlockState(checkPos);
                if (blockState == null || blockState.isAir()) {
                    continue;
                }

                if (!blockState.getFluidState().isEmpty()) {
                    return checkPos.up();
                }

                if (shouldReplaceAtSamePosition(world, checkPos, blockState)) {
                    return checkPos;
                }

                if (blockState.isSolidBlock(world, checkPos)) {
                    return checkPos.up();
                }
            } catch (Exception e) {
                LOGGER.warn("检查位置 {} 时发生错误: {}", checkPos, e.getMessage());
            }
        }

        BlockPos bottomPos = new BlockPos(x, world.getBottomY() + 1, z);
        LOGGER.warn("未找到合适放置点，使用底部位置: {}", bottomPos);
        return bottomPos;
    }

    private boolean shouldReplaceAtSamePosition(World world, BlockPos pos, net.minecraft.block.BlockState blockState) {
        if (blockState == null) {
            return false;
        }

        // 积雪层：忽略并直接替换。
        if (blockState.getBlock() == Blocks.SNOW) {
            return true;
        }

        // 雪块：不替换，视作地面。
        if (blockState.getBlock() == Blocks.SNOW_BLOCK) {
            return false;
        }

        // 植物：直接替换。
        if (blockState.getBlock() instanceof net.minecraft.block.PlantBlock) {
            return true;
        }

        // 其他装饰：非实心则直接替换。
        try {
            return !blockState.isSolidBlock(world, pos);
        } catch (Exception e) {
            return false;
        }
    }
} 
