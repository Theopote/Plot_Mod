package com.plot.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Vector3f;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.view.CameraSettingsEvent;

public class CameraManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/CameraManager");

    private boolean isOrthographic = false;
    private float viewDistance = 50.0f; // 默认视图范围
    private CameraState savedState;

    private final OrthographicCamera orthographicCamera = new OrthographicCamera();
    private final EventBus eventBus = EventBus.getInstance();

    private float rotationAngle = 0.0f; // 添加旋转角度属性，默认为0度

    private float panX = 0.0f;
    private float panY = 0.0f;

    private final MinecraftClient client = MinecraftClient.getInstance();

    // 添加区块更新时间戳，用于限制更新频率
    private long lastChunkUpdateTime = 0;
    
    // 标志：是否正在拖动平移（用于跳过频繁的区块更新）
    private boolean isPanning = false;
    
    // 平移的基准位置（拖动开始时的玩家位置，用于计算平移偏移）
    private Vec3d panBasePlayerPos = null;

    // 相机状态
    private static class CameraState {
        float pitch;
        float yaw;
        Vec3d position;
        boolean hudHidden;

        CameraState(float pitch, float yaw, Vec3d position, boolean hudHidden) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.position = position;
            this.hudHidden = hudHidden;
        }
    }

    // 使用静态内部类实现线程安全的单例模式
    private static class CameraManagerHolder {
        private static final CameraManager INSTANCE = new CameraManager();
    }

    private CameraManager() {
        // 在构造函数中初始化默认状态
        PlayerEntity player = client.player;
        if (player != null) {
            savedState = new CameraState(
                    player.getPitch(),
                    player.getYaw(),
                    new Vec3d(player.getX(), player.getY(), player.getZ()),
                    client.options.hudHidden
            );
        }
    }

    public static CameraManager getInstance() {
        return CameraManagerHolder.INSTANCE;
    }

    public void saveCurrentState() {
        PlayerEntity player = client.player;
        if (player != null) {
            savedState = new CameraState(
                    player.getPitch(),
                    player.getYaw(),
                    new Vec3d(player.getX(), player.getY(), player.getZ()),
                    client.options.hudHidden
            );
            LOGGER.info("Saved camera state: pitch={}, yaw={}, pos={}",
                    savedState.pitch, savedState.yaw, savedState.position);
        } else {
            savedState = null;  // 确保没有玩家时 savedState 为 null
            LOGGER.warn("No player found, cannot save camera state.");
        }
    }

    public void restoreState() {
        PlayerEntity player = client.player;
        if (player == null) {
            LOGGER.warn("No player found, cannot restore camera state.");
            return;
        }
        
        // 如果当前是正交模式，先切换回透视模式
        if (isOrthographic) {
            isOrthographic = false;
            orthographicCamera.setEnabled(false);
        }

        // 关键修改：使用当前玩家位置（因为拖动时已经更新了玩家位置）
        // 而不是恢复保存的位置，这样玩家关闭窗口后会保持在拖动后的位置
        Vec3d currentPosition = new Vec3d(player.getX(), player.getY(), player.getZ());
        
        // 恢复角度和 HUD 状态（如果保存了状态）
        if (savedState != null) {
            player.setPitch(savedState.pitch);
            player.setYaw(savedState.yaw);
            client.options.hudHidden = savedState.hudHidden;
        } else {
            // 如果没有保存的状态，使用默认值
            player.setPitch(0.0f);
            player.setYaw(0.0f);
            client.options.hudHidden = false;
        }
        
        // 确保玩家位置正确（使用当前位置，但可能需要调整Y坐标到地面）
        // 注意：X和Z坐标保持拖动后的值，Y坐标可能需要调整到合理的高度
        World world = player.getEntityWorld();
        if (world != null) {
            BlockPos pos = new BlockPos((int)currentPosition.x, (int)currentPosition.y, (int)currentPosition.z);
            int terrainHeight = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
            double safeY = Math.max(terrainHeight + 1.0, currentPosition.y);
            player.setPos(currentPosition.x, safeY, currentPosition.z);
        } else {
            player.setPos(currentPosition.x, currentPosition.y, currentPosition.z);
        }

        // 重置旋转角度和平移值（因为已经反映在玩家位置上了）
        rotationAngle = 0.0f;
        panX = 0.0f;
        panY = 0.0f;
        panBasePlayerPos = null;

        LOGGER.info("Restored camera state: pitch={}, yaw={}, pos={} (使用拖动后的位置)",
                player.getPitch(), player.getYaw(), currentPosition);
    }

    public void toggleCamera() {
        try {
            // 如果要切换到正交相机，先确保视图范围在安全范围内
            if (!isOrthographic) {
                // 设置一个安全的最小视图范围
                float safeViewDistance = Math.max(50.0f, viewDistance);
                if (viewDistance != safeViewDistance) {
                    viewDistance = safeViewDistance;
                    orthographicCamera.setViewDistance(safeViewDistance);
                }
            }

            isOrthographic = !isOrthographic;
            orthographicCamera.setEnabled(isOrthographic);

            PlayerEntity player = client.player;
            if (player != null) {
                // 无论是正交还是透视模式，都设置为俯视图
                player.setPitch(90.0f);
                player.setYaw(180.0f + rotationAngle);
                
                // 更新相机位置和其他参数
                updateCamera();
            }

            LOGGER.info("Camera mode switched to: {}, viewDistance: {}", 
                isOrthographic ? "Orthographic" : "Perspective", viewDistance);
        } catch (Exception e) {
            LOGGER.error("切换相机模式时发生错误", e);
            // 发生错误时恢复到透视模式
            isOrthographic = false;
            orthographicCamera.setEnabled(false);
            updateCamera();
        }
    }

    public void setViewDistance(float distance) {
        try {
            // 根据相机模式设置不同的最小值
            float minDistance = isOrthographic ? 50.0f : 20.0f;
            float oldDistance = this.viewDistance;
            this.viewDistance = Math.max(minDistance, Math.min(1000.0f, distance));  // 扩大最大视图距离到1000
            
            // 如果视图范围变小，需要相应调整平移值以避免视觉跳跃
            if (this.viewDistance < oldDistance) {
                float ratio = this.viewDistance / oldDistance;
                panX *= ratio;
                panY *= ratio;
            }
            
            // 同时更新正交相机的视图距离
            orthographicCamera.setViewDistance(this.viewDistance);

            updateCamera();
            LOGGER.debug("视图范围已更新: {}, panX: {}, panY: {}", this.viewDistance, panX, panY);
        } catch (Exception e) {
            LOGGER.error("设置视图范围时发生错误", e);
        }
    }

    public float getViewDistance() {
        return viewDistance;
    }

    public boolean isOrthographic() {
        return isOrthographic;
    }

    public OrthographicCamera getOrthographicCamera() {
        return orthographicCamera;
    }


    
    /**
     * 设置平移值（用于拖动过程中，跳过区块更新以提高性能）
     * @param x X轴平移值
     * @param y Y轴平移值
     * @param skipChunkUpdate 是否跳过区块更新
     */
    public void setPanXY(float x, float y, boolean skipChunkUpdate) {
        // 根据视图范围调整平移值的限制
        float safeViewDistance = Math.max(isOrthographic ? 50.0f : 20.0f, viewDistance);
        float maxPan = safeViewDistance * 0.5f;
        
        this.panX = Math.max(-maxPan, Math.min(maxPan, x));
        this.panY = Math.max(-maxPan, Math.min(maxPan, y));
        
        if (skipChunkUpdate) {
            // 拖动过程中，更新相机位置和玩家位置，但不触发耗时操作
            PlayerEntity player = client.player;
            if (player != null && panBasePlayerPos != null) {
                // 将平移值转换为玩家位置的偏移
                double radians = Math.toRadians(rotationAngle);
                double cosAngle = Math.cos(radians);
                double sinAngle = Math.sin(radians);
                
                // 将平移值（相机坐标系）转换为世界坐标偏移
                double worldOffsetX = panX * cosAngle - panY * sinAngle;
                double worldOffsetZ = panX * sinAngle + panY * cosAngle;
                
                // 基于基准位置计算新位置
                double newX = panBasePlayerPos.x + worldOffsetX;
                double newZ = panBasePlayerPos.z + worldOffsetZ;
                
                if (isOrthographic) {
                    // 正交相机：更新相机位置（拖动时跳过投影矩阵更新）
                    orthographicCamera.setPosition(new Vector3f(panX, panY, safeViewDistance), true);
                    
                    // 保持高度不变（或根据地形调整，但拖动时跳过耗时操作）
                    double newY = panBasePlayerPos.y;
                    player.setPos(newX, newY, newZ);
                } else {
                    // 透视相机：直接移动玩家位置
                    // 保持高度不变
                    double newY = panBasePlayerPos.y;
                    player.setPos(newX, newY, newZ);
                }
                
                // 锁定相机角度
                player.setPitch(90.0f);
                player.setYaw(180.0f + rotationAngle);
            }
            // 不发布事件，不触发区块更新
        } else {
            // 拖动结束时，执行完整的更新
            updateCamera();
        }
    }
    
    /**
     * 设置是否正在拖动平移
     * @param panning 是否正在拖动
     */
    public void setPanning(boolean panning) {
        boolean oldPanning = this.isPanning;
        this.isPanning = panning;
        LOGGER.debug("设置拖动状态: {} -> {}", oldPanning, panning);
        
        PlayerEntity player = client.player;
        if (player != null) {
            if (panning && !oldPanning) {
                // 拖动开始时，更新基准位置为当前玩家位置
                // 这样每次拖动都基于最新的位置，避免位置跳跃
                panBasePlayerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
                // 通知正交相机开始拖动
                if (isOrthographic) {
                    orthographicCamera.setPanning(true);
                }
                LOGGER.debug("拖动开始，更新基准位置: {}", panBasePlayerPos);
            } else if (!panning && oldPanning) {
                // 通知正交相机拖动结束
                if (isOrthographic) {
                    orthographicCamera.setPanning(false);
                }
                // 拖动结束时，执行一次完整的更新（包括区块更新）
                LOGGER.debug("拖动结束，执行完整更新");
                
                // 更新基准位置为拖动结束时的玩家位置（考虑平移值）
                // 这样后续的视图范围调整会基于新的基准位置，而不是重置
                if (panBasePlayerPos != null && (panX != 0.0f || panY != 0.0f)) {
                    // 获取当前玩家位置（已经根据平移值更新过了）
                    // 将基准位置更新为当前玩家位置，重置平移值为0
                    panBasePlayerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
                    panX = 0.0f;
                    panY = 0.0f;
                    LOGGER.debug("拖动结束，更新基准位置为: {}, 重置平移值", panBasePlayerPos);
                }
                
                updateCamera();
                // 拖动结束后，强制更新区块（不受时间限制）
                forceUpdateChunksInViewImmediate();
            }
        }
    }

    /**
     * 获取X轴平移值
     */
    public float getPanX() {
        return panX;
    }

    /**
     * 获取Y轴平移值
     */
    public float getPanY() {
        return panY;
    }

    private int getTerrainHeight(BlockPos pos, World world) {
        return world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
    }

    private void updateCamera() {
        // 拖动过程中完全跳过更新
        if (isPanning) {
            LOGGER.debug("拖动过程中跳过 updateCamera()");
            return;
        }
        
        PlayerEntity player = client.player;
        if (player == null) return;

        try {
            // 获取当前位置的地形高度
            World world = player.getEntityWorld();
            BlockPos currentPos = player.getBlockPos();
            int terrainHeight = getTerrainHeight(currentPos, world);

            // 确保视图范围不会太小，防止相机高度计算出现问题
            float safeViewDistance = Math.max(isOrthographic ? 50.0f : 20.0f, viewDistance);

            // 计算相机高度
            // 正交相机：使用固定的合理高度（不随viewDistance线性增长）
            // 透视相机：基于viewDistance计算高度
            float cameraHeight;
            if (isOrthographic) {
                // 正交相机保持在地形上方150格的固定高度，确保在渲染距离内
                cameraHeight = terrainHeight + 150.0f;
            } else {
                // 透视相机使用原来的逻辑
                cameraHeight = Math.max(terrainHeight + 20.0f, safeViewDistance);
            }

            // 获取玩家基础位置（用于计算基准位置）
            Vec3d basePos = new Vec3d(player.getX(), player.getY(), player.getZ());
            
            // 确定基准位置和计算新位置
            double newX, newZ;
            if (panBasePlayerPos != null && (panX != 0.0f || panY != 0.0f)) {
                // 如果有基准位置且平移值不为0，基于基准位置计算新位置
                Vec3d basePosForPan = panBasePlayerPos;
                
                // 将平移值转换为玩家位置的偏移
                double radians = Math.toRadians(rotationAngle);
                double cosAngle = Math.cos(radians);
                double sinAngle = Math.sin(radians);
                
                // 将平移值（相机坐标系）转换为世界坐标偏移
                double worldOffsetX = panX * cosAngle - panY * sinAngle;
                double worldOffsetZ = panX * sinAngle + panY * cosAngle;
                
                // 计算新的玩家位置（基于基准位置 + 平移偏移）
                newX = basePosForPan.x + worldOffsetX;
                newZ = basePosForPan.z + worldOffsetZ;
            } else {
                // 如果没有基准位置或平移值为0，使用当前位置
                newX = basePos.x;
                newZ = basePos.z;
                
                // 如果没有基准位置，将当前位置设置为基准位置
                if (panBasePlayerPos == null) {
                    panBasePlayerPos = new Vec3d(basePos.x, basePos.y, basePos.z);
                    LOGGER.debug("首次使用平移，设置基准位置: {}", panBasePlayerPos);
                }
            }
            
            // 设置相机角度和位置
            player.setPitch(90.0f);
            player.setYaw(180.0f + rotationAngle);
            player.setPos(newX, cameraHeight, newZ);
            
            // 设置HUD显示状态
            // 如果 Plot 屏幕打开，始终隐藏 HUD（无论相机模式）
            boolean isPlotOpen = com.plot.ui.screen.PlotScreenState.isPlotScreenOpen();
            if (isPlotOpen) {
                // Plot 打开时，始终隐藏 HUD
                client.options.hudHidden = true;
            } else {
                // Plot 关闭时，根据相机模式决定
                client.options.hudHidden = isOrthographic;
            }
            
            if (isOrthographic) {
                // 根据视图范围调整平移值的限制
                float maxPan = safeViewDistance * 0.5f;
                float adjustedPanX = Math.max(-maxPan, Math.min(maxPan, panX));
                float adjustedPanY = Math.max(-maxPan, Math.min(maxPan, panY));
                
                // 如果平移值被调整，更新存储的值
                if (adjustedPanX != panX || adjustedPanY != panY) {
                    panX = adjustedPanX;
                    panY = adjustedPanY;
                    LOGGER.debug("平移值已调整: panX={}, panY={}", panX, panY);
                }
                
                orthographicCamera.setPosition(new Vector3f(adjustedPanX, adjustedPanY, safeViewDistance));
                orthographicCamera.setViewDistance(safeViewDistance);
                
                // 发布相机设置变更事件，通知其他组件更新视图（拖动过程中跳过）
                if (!isPanning) {
                    eventBus.publish(new CameraSettingsEvent(orthographicCamera));
                    // 在相机设置更改后强制更新区块
                    forceUpdateChunksInView();
                }
            }
            // 透视相机：玩家位置已经更新，不需要额外处理
        } catch (Exception e) {
            LOGGER.error("更新相机时发生错误: {}", e.getMessage(), e);
            // 发生错误时重置平移值
            panX = 0.0f;
            panY = 0.0f;
        }
    }

    public void onFrame() {
        PlayerEntity player = client.player;
        if (player == null) return;
        
        // 如果 Plot 屏幕打开，确保 HUD 始终隐藏
        boolean isPlotOpen = com.plot.ui.screen.PlotScreenState.isPlotScreenOpen();
        if (isPlotOpen) {
            client.options.hudHidden = true;
        }
        
        // 拖动过程中跳过耗时操作
        if (isPanning) {
            // 只锁定相机角度，不执行其他耗时操作
            player.setPitch(90.0f);
            player.setYaw(180.0f + rotationAngle);
            return;
        }

        try {
            // 锁定相机角度
            player.setPitch(90.0f);
            player.setYaw(180.0f + rotationAngle);

            // 获取当前位置
            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            World world = player.getEntityWorld();
            
            // 获取当前位置的地形高度
            BlockPos pos = new BlockPos((int)currentPos.x, (int)currentPos.y, (int)currentPos.z);
            int terrainHeight = getTerrainHeight(pos, world);
            
            // 使用安全的视图范围值
            float safeViewDistance = Math.max(isOrthographic ? 50.0f : 20.0f, viewDistance);

            // 计算目标高度
            float targetHeight;
            if (isOrthographic) {
                // 正交相机保持固定高度
                targetHeight = Math.max(terrainHeight + 20.0f, terrainHeight + 150.0f);
            } else {
                // 透视相机使用原来的逻辑
                targetHeight = Math.max(terrainHeight + 20.0f, safeViewDistance);
            }

            // 如果高度差异显著，平滑调整高度
            if (Math.abs(currentPos.y - targetHeight) > 0.1f) {
                // 使用插值来平滑过渡
                float newHeight = (float) (currentPos.y + (targetHeight - currentPos.y) * 0.1);
                player.setPos(currentPos.x, newHeight, currentPos.z);
            }
        } catch (Exception e) {
            LOGGER.error("更新相机帧时发生错误", e);
        }
    }
    public void setToPerspective() {
        isOrthographic = false;
        updateCamera();
    }
    
    /**
     * 强制更新正交相机视图范围内的区块（受时间限制）
     */
    public void forceUpdateChunksInView() {
        // 拖动过程中完全跳过区块更新
        if (isPanning) {
            LOGGER.debug("拖动过程中跳过 forceUpdateChunksInView()");
            return;
        }
        
        if (!isOrthographic) {
            LOGGER.warn("当前不是正交模式，无法更新区块");
            return;
        }
        
        // 添加时间限制，防止频繁更新造成性能问题
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChunkUpdateTime < 500) { // 限制为至少间隔500毫秒
            LOGGER.debug("区块更新过于频繁，跳过本次更新");
            return;
        }
        
        // 更新时间戳
        lastChunkUpdateTime = currentTime;

        ChunkUpdater.forceUpdateChunksInView(orthographicCamera);
    }
    
    /**
     * 立即强制更新正交相机视图范围内的区块（不受时间限制）
     * 用于拖动结束后立即更新新区域的区块
     */
    private void forceUpdateChunksInViewImmediate() {
        if (!isOrthographic) {
            LOGGER.warn("当前不是正交模式，无法更新区块");
            return;
        }
        
        // 重置时间戳，确保下次调用 forceUpdateChunksInView() 也能执行
        lastChunkUpdateTime = System.currentTimeMillis();
        
        // 立即更新区块，不受时间限制
        int updatedCount = ChunkUpdater.forceUpdateChunksInView(orthographicCamera);
        LOGGER.debug("拖动结束后立即更新区块，共更新 {} 个区块", updatedCount);
    }
}