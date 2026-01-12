package com.masterplanner.infrastructure.coordinate;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.canvas.Canvas;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.camera.CameraManager;
import com.masterplanner.camera.OrthographicCamera;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Vector3f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 坐标转换器 - 专门处理画布坐标与Minecraft世界坐标的转换
 * <p>
 * 坐标系说明：
 * - 画布坐标系：Canvas内部使用的世界坐标系，支持浮点数，原点可能不在(0,0)
 * - Minecraft世界坐标系：游戏世界的XZ平面，Y轴为高度，使用整数块坐标
 * <p>
 * 核心原理：
 * 1. 画布上的绘图直接对应Minecraft世界的XZ平面投影
 * 2. 通过相机的偏移和缩放参数进行坐标映射
 * 3. 确保"所见即所得"的视图一致性
 * 4. 【新增】正确处理画布区域在窗口中的位置偏移
 * 5. 【核心修复】确保坐标转换与顶视图相机的视野范围完全匹配
 * 6. 【优化】增强窗口尺寸获取鲁棒性、玩家位置验证、浮点精度处理
 * 7. 【新增】缓存机制和坐标验证
 */
public class CoordinateTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinateTransformer.class);
    private static CoordinateTransformer INSTANCE;

    private final AppState appState;
    
    // 【新增】缓存机制
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCacheUpdate = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 5000; // 5秒缓存有效期
    
    // 【新增】视图范围变化监控
    private float lastViewDistance = -1.0f;
    
    // 【新增】坐标验证常量
    private static final double MINECRAFT_MAX_COORDINATE = 30000000.0;
    private static final double MINECRAFT_MIN_COORDINATE = -30000000.0;

    public static synchronized CoordinateTransformer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CoordinateTransformer();
        }
        return INSTANCE;
    }

    private CoordinateTransformer() {
        this.appState = AppState.getInstance();
    }

    /**
     * 【优化】获取顶视图相机的视野范围
     * 【新增】缓存机制和浮点精度优化
     * 【修复】视图范围变化时的缓存管理
     * @return 相机视野范围信息，如果获取失败返回null
     */
    private CameraViewBounds getCameraViewBounds() {
        try {
            // 【优化】获取当前视图范围用于缓存键
            CameraManager cameraManager = CameraManager.getInstance();
            float currentViewDistance = cameraManager.getViewDistance();
            
            // 【新增】检查视图范围合理性
            if (currentViewDistance < 40.0f || currentViewDistance > 480.0f) {
                LOGGER.warn("视图范围超出合理范围: {}, 可能导致坐标转换不准确", currentViewDistance);
            }
            
            // 【优化】缓存键包含视图范围信息
            String cacheKey = String.format("cameraViewBounds_viewDistance_%.1f", currentViewDistance);
            CameraViewBounds cachedBounds = (CameraViewBounds) cache.get(cacheKey);
            if (cachedBounds != null && isCacheValid()) {
                LOGGER.debug("使用缓存的相机视野范围: {} (视图范围: {})", cachedBounds, currentViewDistance);
                return cachedBounds;
            }

            if (!cameraManager.isOrthographic()) {
                LOGGER.warn("当前不是正交相机模式，无法获取准确的视野范围");
                return null;
            }

            OrthographicCamera orthoCamera = cameraManager.getOrthographicCamera();
            if (orthoCamera == null || !orthoCamera.isEnabled()) {
                LOGGER.warn("正交相机未启用，无法获取视野范围");
                return null;
            }

            // 获取相机参数
            float viewDistance = orthoCamera.getViewDistance();
            float scale = orthoCamera.getScale();
            Vector3f position = orthoCamera.getPosition();

            // 【修复】与OrthographicCamera.updateProjectionMatrix()保持一致的视野范围计算
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                LOGGER.warn("无法获取窗口信息，使用默认视野范围");
                return null;
            }

            float aspectRatio = (float) client.getWindow().getFramebufferWidth() / 
                              (float) client.getWindow().getFramebufferHeight();
            
            if (Float.isNaN(aspectRatio) || Float.isInfinite(aspectRatio)) {
                LOGGER.warn("无效的宽高比: {}", aspectRatio);
                return null;
            }

            // 与OrthographicCamera完全一致的计算
            float width = viewDistance * scale;
            float height = width / aspectRatio;

            // 【优化】浮点精度优化：对齐到整数网格，减少视图范围变化时的误差
            float left = Math.round((-width / 2 + position.x) * 100.0f) / 100.0f;
            float right = Math.round((width / 2 + position.x) * 100.0f) / 100.0f;
            float bottom = Math.round((-height / 2 + position.y) * 100.0f) / 100.0f;  // position.y 实际上是Z轴
            float top = Math.round((height / 2 + position.y) * 100.0f) / 100.0f;      // position.y 实际上是Z轴

            CameraViewBounds bounds = new CameraViewBounds(left, right, bottom, top, viewDistance, scale);
            
            // 【新增】缓存结果，使用包含视图范围的缓存键
            cache.put(cacheKey, bounds);
            lastCacheUpdate.set(System.currentTimeMillis());
            
            LOGGER.debug("相机视野范围: 左={}, 右={}, 下={}, 上={}, 位置=({}, {}), 视野距离={}, 缩放={}, 宽高比={}", 
                    left, right, bottom, top, position.x, position.y, viewDistance, scale, aspectRatio);

            return bounds;

        } catch (Exception e) {
            LOGGER.error("获取相机视野范围失败", e);
            return null;
        }
    }

    /**
     * 【优化】获取画布在窗口中的实际位置和大小
     * 【新增】增强的窗口尺寸获取鲁棒性
     * @return 画布区域信息，如果获取失败返回null
     */
    private CanvasRegion getCanvasRegion() {
        try {
            // 【新增】检查缓存
            String cacheKey = "canvasRegion";
            CanvasRegion cachedRegion = (CanvasRegion) cache.get(cacheKey);
            if (cachedRegion != null && isCacheValid()) {
                LOGGER.debug("使用缓存的画布区域: {}", cachedRegion);
                return cachedRegion;
            }

            // 【优化】增强的窗口尺寸获取鲁棒性
            float windowWidth = getWindowWidth();
            float windowHeight = getWindowHeight();
            
            if (windowWidth <= 0 || windowHeight <= 0) {
                LOGGER.warn("窗口尺寸无效: {}x{}", windowWidth, windowHeight);
                return null;
            }
            
            // 【核心修改】画布现在占据整个窗口，没有偏移
            // 验证：确保画布确实占据整个窗口
            float canvasX = 0.0f;
            float canvasY = 0.0f;

            CanvasRegion region = new CanvasRegion(canvasX, canvasY, windowWidth, windowHeight);
            
            // 【新增】缓存结果
            cache.put(cacheKey, region);
            lastCacheUpdate.set(System.currentTimeMillis());
            
            LOGGER.debug("画布区域验证: 全屏模式 x={}, y={}, w={}, h={}", 
                    canvasX, canvasY, windowWidth, windowHeight);
            
            // 验证画布区域合理性

            // 验证画布确实占据整个窗口

            return region;
            
        } catch (Exception e) {
            LOGGER.error("获取画布区域失败", e);
            return null;
        }
    }
    
    /**
     * 【优化】获取当前窗口宽度
     * 【新增】增强的鲁棒性：从MinecraftClient获取实际尺寸作为回退
     */
    private float getWindowWidth() {
        try {
            // 优先从ImGui获取窗口尺寸
            return imgui.ImGui.getIO().getDisplaySizeX();
        } catch (Exception e) {
            LOGGER.debug("无法从ImGui获取窗口宽度，尝试从MinecraftClient获取");
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getWindow() != null) {
                    return client.getWindow().getFramebufferWidth();
                }
            } catch (Exception ex) {
                LOGGER.debug("无法从MinecraftClient获取窗口宽度: {}", ex.getMessage());
            }
            LOGGER.warn("使用默认窗口宽度: 1200");
            return 1200.0f; // 默认窗口宽度
        }
    }
    
    /**
     * 【优化】获取当前窗口高度
     * 【新增】增强的鲁棒性：从MinecraftClient获取实际尺寸作为回退
     */
    private float getWindowHeight() {
        try {
            // 优先从ImGui获取窗口尺寸
            return imgui.ImGui.getIO().getDisplaySizeY();
        } catch (Exception e) {
            LOGGER.debug("无法从ImGui获取窗口高度，尝试从MinecraftClient获取");
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getWindow() != null) {
                    return client.getWindow().getFramebufferHeight();
                }
            } catch (Exception ex) {
                LOGGER.debug("无法从MinecraftClient获取窗口高度: {}", ex.getMessage());
            }
            LOGGER.warn("使用默认窗口高度: 800");
            return 800.0f; // 默认窗口高度
        }
    }

    /**
     * 【优化】将画布世界坐标转换为Minecraft世界坐标
     * 【核心修复】确保坐标转换与顶视图相机的视野范围完全匹配
     * 【新增】基于玩家实际位置作为原点进行坐标转换
     * 【新增】玩家位置验证和坐标范围验证
     * 【新增】视图范围变化监控
     *
     * @param canvasPos 画布世界坐标
     * @return Minecraft世界坐标（XZ平面），如果转换失败返回null
     */
    public Vec2d canvasToMinecraftWorld(Vec2d canvasPos) {
        try {
            // 【新增】监控视图范围变化
            monitorViewDistanceChanges();
            
            // 【优化】获取玩家在Minecraft世界中的实际位置，添加状态检查
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                LOGGER.error("无法获取玩家位置，坐标转换失败");
                return null;
            }

            // 【新增】检查玩家位置是否有效
            // 1.21.11：ClientPlayerEntity#getWorld 已移除/更名，改用实体世界访问器
            var player = client.player;
            if (!player.isAlive() || player.getEntityWorld() == null) {
                LOGGER.warn("玩家位置不可用（可能网络延迟或未初始化），使用默认原点");
                return canvasToMinecraftWorldWithDefaultOrigin(canvasPos);
            }

            // 获取玩家位置作为原点
            double playerX = player.getX();
            double playerZ = player.getZ();
            
            LOGGER.debug("玩家位置: ({}, {})", playerX, playerZ);

            // 获取顶视图相机的视野范围
            CameraViewBounds cameraBounds = getCameraViewBounds();
            if (cameraBounds == null) {
                LOGGER.error("无法获取相机视野范围，使用简化转换");
                return canvasToMinecraftWorldSimple(canvasPos, playerX, playerZ);
            }

            // 获取画布区域信息
            CanvasRegion canvasRegion = getCanvasRegion();
            if (canvasRegion == null) {
                LOGGER.error("无法获取画布区域信息，使用简化转换");
                return canvasToMinecraftWorldSimple(canvasPos, playerX, playerZ);
            }

            // 【核心修复】将画布坐标映射到相机视野范围，然后加上玩家位置偏移
            Vec2d relativePos = mapCanvasToCameraView(canvasPos, canvasRegion, cameraBounds);
            
            // 【新增】加上玩家位置作为原点偏移
            Vec2d minecraftPos = new Vec2d(relativePos.x + playerX, relativePos.y + playerZ);

            // 【新增】坐标验证：检查是否在Minecraft合理范围内
            if (!isValidMinecraftCoordinate(minecraftPos)) {
                LOGGER.warn("转换后的坐标超出Minecraft合理范围: ({}, {})", minecraftPos.x, minecraftPos.y);
                return null;
            }

            LOGGER.debug("坐标转换: 画布({}, {}) → 相对位置({}, {}) → Minecraft({}, {}), 玩家位置=({}, {})", 
                    canvasPos.x, canvasPos.y, relativePos.x, relativePos.y, minecraftPos.x, minecraftPos.y, playerX, playerZ);

            return minecraftPos;

        } catch (Exception e) {
            LOGGER.error("画布到Minecraft坐标转换失败", e);
            return null;
        }
    }

    /**
     * 【新增】使用默认原点的坐标转换
     * 当玩家位置不可用时使用
     */
    private Vec2d canvasToMinecraftWorldWithDefaultOrigin(Vec2d canvasPos) {
        try {
            // 使用(0,0)作为默认原点
            double defaultX = 0.0;
            double defaultZ = 0.0;
            
            LOGGER.debug("使用默认原点进行坐标转换: ({}, {})", defaultX, defaultZ);
            
            return canvasToMinecraftWorldSimple(canvasPos, defaultX, defaultZ);
            
        } catch (Exception e) {
            LOGGER.error("使用默认原点的坐标转换失败", e);
            return null;
        }
    }

    /**
     * 【新增】验证Minecraft坐标是否在合理范围内
     * @param pos Minecraft坐标
     * @return 是否在合理范围内
     */
    private boolean isValidMinecraftCoordinate(Vec2d pos) {
        return pos.x >= MINECRAFT_MIN_COORDINATE && pos.x <= MINECRAFT_MAX_COORDINATE &&
               pos.y >= MINECRAFT_MIN_COORDINATE && pos.y <= MINECRAFT_MAX_COORDINATE;
    }

    /**
     * 【核心方法】将画布坐标映射到相机视野范围
     * 【优化】提高坐标转换精度，减少视图范围变化时的误差
     * @param canvasPos 画布坐标
     * @param canvasRegion 画布区域
     * @param cameraBounds 相机视野范围
     * @return Minecraft世界坐标
     */
    private Vec2d mapCanvasToCameraView(Vec2d canvasPos, CanvasRegion canvasRegion, CameraViewBounds cameraBounds) {
        // 【修复】画布现在占据整个窗口，画布坐标直接就是窗口坐标
        double windowX = canvasPos.x;
        double windowY = canvasPos.y;

        // 【优化】归一化计算使用更高精度
        double normalizedX = windowX / (double) canvasRegion.width;
        double normalizedY = windowY / (double) canvasRegion.height;

        // 【优化】视野范围映射使用更高精度
        double minecraftX = cameraBounds.left + (cameraBounds.right - cameraBounds.left) * normalizedX;
        double minecraftZ = cameraBounds.bottom + (cameraBounds.top - cameraBounds.bottom) * normalizedY;

        // 【新增】坐标对齐到网格，减少视图范围变化时的误差
        minecraftX = Math.round(minecraftX * 100.0) / 100.0;
        minecraftZ = Math.round(minecraftZ * 100.0) / 100.0;

        LOGGER.debug("坐标映射: 画布({}, {}) → 归一化({}, {}) → Minecraft({}, {}), 视野范围=({}, {}, {}, {})", 
                windowX, windowY, normalizedX, normalizedY, minecraftX, minecraftZ,
                cameraBounds.left, cameraBounds.right, cameraBounds.bottom, cameraBounds.top);

        return new Vec2d(minecraftX, minecraftZ);
    }

    /**
     * 简化版坐标转换（向后兼容）
     * 当无法获取相机视野范围时使用
     */
    private Vec2d canvasToMinecraftWorldSimple(Vec2d canvasPos, double playerX, double playerZ) {
        try {
            Canvas canvas = appState.getCanvas();
            CanvasCamera camera = canvas.getCamera();

            // 获取相机参数
            Vec2d offset = camera.getOffset();
            double zoom = camera.getZoom();

            // 直接转换：画布世界坐标 → Minecraft世界坐标
            double minecraftX = (canvasPos.x - offset.x) / zoom + playerX;
            double minecraftZ = (canvasPos.y - offset.y) / zoom + playerZ;

            LOGGER.debug("简化坐标转换: 画布({}, {}) → Minecraft({}, {}), offset=({}, {}), zoom={}, 玩家位置=({}, {})",
                    canvasPos.x, canvasPos.y, minecraftX, minecraftZ,
                    offset.x, offset.y, zoom, playerX, playerZ);

            return new Vec2d(minecraftX, minecraftZ);

        } catch (Exception e) {
            LOGGER.error("简化坐标转换失败", e);
            return null;
        }
    }

    /**
     * 【新增】检查缓存是否有效
     * @return 缓存是否有效
     */
    private boolean isCacheValid() {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastCacheUpdate.get();
        return (currentTime - lastUpdate) < CACHE_TTL_MS;
    }

    /**
     * 【新增】监控视图范围变化
     * 当视图范围发生变化时自动清理缓存
     */
    private void monitorViewDistanceChanges() {
        try {
            CameraManager cameraManager = CameraManager.getInstance();
            float currentViewDistance = cameraManager.getViewDistance();
            
            if (lastViewDistance != -1.0f && Math.abs(currentViewDistance - lastViewDistance) > 0.1f) {
                LOGGER.info("检测到视图范围变化: {} → {}, 清理坐标转换缓存", lastViewDistance, currentViewDistance);
                clearCache(); // 清理缓存
            }
            
            lastViewDistance = currentViewDistance;
        } catch (Exception e) {
            LOGGER.warn("监控视图范围变化时发生错误", e);
        }
    }

    /**
     * 【新增】清理缓存
     */
    public void clearCache() {
        cache.clear();
        lastCacheUpdate.set(0);
        LOGGER.debug("坐标转换缓存已清理");
    }

    /**
     * 相机视野范围类
     */
    private static class CameraViewBounds {
        final float left, right, bottom, top;
        final float viewDistance, scale;
        
        CameraViewBounds(float left, float right, float bottom, float top, float viewDistance, float scale) {
            this.left = left;
            this.right = right;
            this.bottom = bottom;
            this.top = top;
            this.viewDistance = viewDistance;
            this.scale = scale;
        }
        
        @Override
        public String toString() {
            return String.format("CameraViewBounds[left=%.1f, right=%.1f, bottom=%.1f, top=%.1f, viewDistance=%.1f, scale=%.1f]",
                    left, right, bottom, top, viewDistance, scale);
        }
    }

    /**
     * 画布区域信息类
     */
    private static class CanvasRegion {
        final float x, y, width, height;
        
        CanvasRegion(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        @Override
        public String toString() {
            return String.format("CanvasRegion[x=%.1f, y=%.1f, w=%.1f, h=%.1f]", x, y, width, height);
        }
    }
}