package com.masterplanner.camera;

import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrthographicCamera {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/OrthographicCamera");
    
    private final Matrix4f projectionMatrix;
    private final Vector3f position;  // 使用 final，通过方法修改内部值
    private float scale = 1.0f;
    private float viewDistance = 40.0f;
    private boolean enabled = false;
    private boolean locked = false; // 添加锁定状态标志

    private float near = 0.05f;
    private float far = 1000.0f;
    
    // 标志：是否正在拖动（用于跳过投影矩阵更新以提高性能）
    private boolean isPanning = false;

    public OrthographicCamera() {
        this.projectionMatrix = new Matrix4f();
        this.position = new Vector3f(0, 0, viewDistance);
        updateProjectionMatrix();
    }

    /**
     * 设置相机启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            updateProjectionMatrix();
        }
    }

    /**
     * 获取相机启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置相机位置，使用同步方法避免并发问题
     */
    public synchronized void setPosition(Vector3f newPosition) {
        setPosition(newPosition, false);
    }
    
    /**
     * 设置相机位置，使用同步方法避免并发问题
     * @param newPosition 新位置
     * @param skipUpdate 是否跳过投影矩阵更新（拖动时使用）
     */
    public synchronized void setPosition(Vector3f newPosition, boolean skipUpdate) {
        try {
            if (newPosition != null && !locked) { // 检查锁定状态
                // 限制X和Y的范围在-50到50之间
                this.position.x = Math.max(-50.0f, Math.min(50.0f, newPosition.x));
                this.position.y = Math.max(-50.0f, Math.min(50.0f, newPosition.y));
                // Z轴位置由viewDistance控制
                this.position.z = newPosition.z;
                if (!skipUpdate) {
                    updateProjectionMatrix();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error setting camera position", e);
        }
    }
    
    /**
     * 设置是否正在拖动
     */
    public void setPanning(boolean panning) {
        this.isPanning = panning;
        // 拖动结束时，更新投影矩阵
        if (!panning) {
            updateProjectionMatrix();
        }
    }

    /**
     * 获取相机位置
     */
    public Vector3f getPosition() {
        return new Vector3f(position); // 返回副本避免外部直接修改
    }

    /**
     * 设置缩放比例，添加安全检查
     */
    public synchronized void setScale(float scale) {
        try {
            if (!locked) { // 检查锁定状态
                this.scale = Math.max(0.1f, Math.min(10.0f, scale));
                updateProjectionMatrix();
            }
        } catch (Exception e) {
            LOGGER.error("Error setting camera scale", e);
        }
    }

    /**
     * 设置视图距离，添加安全检查
     */
    public synchronized void setViewDistance(float distance) {
        try {
            if (!locked) { // 检查锁定状态
                this.viewDistance = Math.max(40.0f, Math.min(480.0f, distance));
                updateProjectionMatrix();
            }
        } catch (Exception e) {
            LOGGER.error("Error setting view distance", e);
        }
    }

    /**
     * 更新投影矩阵，添加安全检查
     */
    private synchronized void updateProjectionMatrix() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null || !client.isRunning() || client.getWindow().getFramebufferWidth() <= 0 || client.getWindow().getFramebufferHeight() <= 0) {
                LOGGER.warn("Cannot update projection matrix: Invalid game state or window");
                return;
            }

            float aspectRatio = (float) client.getWindow().getFramebufferWidth() / 
                              (float) client.getWindow().getFramebufferHeight();
            
            if (Float.isNaN(aspectRatio) || Float.isInfinite(aspectRatio)) {
                LOGGER.warn("Invalid aspect ratio calculated: {}", aspectRatio);
                return;
            }
            
            float width = viewDistance * scale;
            float height = width / aspectRatio;

            // 正交投影的参数
            float left = -width / 2 + position.x;
            float right = width / 2 + position.x;
            float bottom = -height / 2 + position.y;
            float top = height / 2 + position.y;

            projectionMatrix.identity().setOrtho(left, right, bottom, top, near, far);
        } catch (Exception e) {
            LOGGER.error("Error updating projection matrix", e);
        }
    }

    /**
     * 获取投影矩阵
     */
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix); // 返回副本避免外部修改
    }

    // 添加 getter 方法
    public float getViewDistance() {
        return viewDistance;
    }

    public float getScale() {
        return scale;
    }

    public float getNear() {
        return near;
    }

    public float getFar() {
        return far;
    }

    // 添加 setter 方法
    public void setNear(float near) {
        try {
            if (!locked) {
                this.near = Math.max(0.01f, Math.min(10.0f, near));
                updateProjectionMatrix();
            }
        } catch (Exception e) {
            LOGGER.error("Error setting near plane distance", e);
        }
    }

    public void setFar(float far) {
        try {
            if (!locked) {
                this.far = Math.max(100.0f, Math.min(2000.0f, far));
                updateProjectionMatrix();
            }
        } catch (Exception e) {
            LOGGER.error("Error setting far plane distance", e);
        }
    }

    // 添加重置方法
    public void resetToDefaults() {
        scale = 1.0f;
        viewDistance = 50.0f;
        near = 0.05f;
        far = 1000.0f;
        updateProjectionMatrix();
    }

    /**
     * 设置相机锁定状态
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * 获取相机锁定状态
     */
    public boolean isLocked() {
        return locked;
    }
} 