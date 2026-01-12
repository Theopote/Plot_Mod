package com.masterplanner.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 区块更新器
 * 用于强制更新正交相机视图范围内的区块
 */
public class ChunkUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ChunkUpdater");
    
    /**
     * 强制更新正交相机视图范围内的区块
     * 
     * @param camera 正交相机
     * @return 更新的区块数量
     */
    public static int forceUpdateChunksInView(OrthographicCamera camera) {
        if (camera == null || !camera.isEnabled()) {
            LOGGER.warn("相机未启用，无法更新区块");
            return 0;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            LOGGER.warn("客户端、世界或玩家为空，无法更新区块");
            return 0;
        }
        
        World world = client.world;
        
        // 获取相机视图范围
        float left = -camera.getViewDistance() * camera.getScale() / 2 + camera.getPosition().x;
        float right = camera.getViewDistance() * camera.getScale() / 2 + camera.getPosition().x;
        float bottom = -camera.getViewDistance() * camera.getScale() / 2 + camera.getPosition().y;
        float top = camera.getViewDistance() * camera.getScale() / 2 + camera.getPosition().y;
        
        // 计算区块范围
        int minChunkX = (int) Math.floor(left) >> 4;
        int maxChunkX = (int) Math.ceil(right) >> 4;
        int minChunkZ = (int) Math.floor(bottom) >> 4;
        int maxChunkZ = (int) Math.ceil(top) >> 4;
        
        LOGGER.info("正在更新区块范围: X[{} to {}], Z[{} to {}]", minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        
        // 收集需要更新的区块
        Set<ChunkPos> chunksToUpdate = new HashSet<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                chunksToUpdate.add(new ChunkPos(x, z));
            }
        }
        
        // 强制更新区块
        int updatedCount = 0;
        for (ChunkPos chunkPos : chunksToUpdate) {
            try {
                // 检查区块是否已加载
                if (!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    LOGGER.debug("区块 [{}, {}] 未加载，跳过", chunkPos.x, chunkPos.z);
                    continue;
                }
                
                // 强制更新区块
                forceUpdateChunk(world, chunkPos);
                updatedCount++;
                
                // 每更新10个区块记录一次日志
                if (updatedCount % 10 == 0) {
                    LOGGER.debug("已更新 {} 个区块", updatedCount);
                }
            } catch (Exception e) {
                LOGGER.error("更新区块 [{}, {}] 时出错: {}", chunkPos.x, chunkPos.z, e.getMessage());
            }
        }
        
        LOGGER.info("区块更新完成，共更新 {} 个区块", updatedCount);
        return updatedCount;
    }
    
    /**
     * 强制更新单个区块
     * 
     * @param world 世界
     * @param chunkPos 区块位置
     */
    private static void forceUpdateChunk(World world, ChunkPos chunkPos) {
        try {
            // 获取区块
            var chunk = world.getChunk(chunkPos.x, chunkPos.z);
            if (chunk == null || chunk.isEmpty()) {
                LOGGER.debug("区块 [{}, {}] 为空，无法更新", chunkPos.x, chunkPos.z);
                return;
            }
            
            // 获取区块中的一个方块位置，用于触发更新
            BlockPos blockPos = new BlockPos(chunkPos.getStartX(), 0, chunkPos.getStartZ());
            
            // 向上查找第一个非空气方块
            BlockPos topPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, blockPos);
            
            // 如果找到了非空气方块，使用它的位置
            if (topPos != null) {
                blockPos = topPos;
            }
            
            // 强制更新区块 - 通过方块更新触发区块更新
            // 标志3表示：1(更新方块) + 2(发送到客户端)
            world.updateListeners(blockPos, world.getBlockState(blockPos), world.getBlockState(blockPos), 3);
            
            LOGGER.debug("已更新区块 [{}, {}]", chunkPos.x, chunkPos.z);
        } catch (Exception e) {
            LOGGER.error("强制更新区块 [{}, {}] 时出错: {}", chunkPos.x, chunkPos.z, e.getMessage());
        }
    }
} 