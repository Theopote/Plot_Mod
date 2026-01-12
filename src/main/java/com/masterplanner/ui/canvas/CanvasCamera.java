package com.masterplanner.ui.canvas;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.geometry.Matrix3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.api.model.IDirty;

/**
 * 画布相机，处理视图变换
 */
public class CanvasCamera implements UIComponent, IDirty {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/CanvasCamera");
    
    private Vec2d position;      // 相机位置
    private float zoom;          // 缩放比例
    private float rotation;      // 旋转角度
    private Matrix3d transform;  // 变换矩阵
    private Matrix3d inverse;    // 逆变换矩阵

    private Vec2d offset;

    private boolean isDirty = false;
    
    public CanvasCamera() {
        LOGGER.debug("Creating new CanvasCamera instance...");
        // 在构造函数中初始化基本字段
        this.position = new Vec2d(0, 0);
        this.offset = new Vec2d(0, 0);  // 确保offset初始化为0
        this.zoom = 1.0f;
        this.rotation = 0.0f;
        this.transform = new Matrix3d();  // 初始化变换矩阵
        this.inverse = new Matrix3d();    // 初始化逆变换矩阵
        updateTransform();                // 更新变换矩阵
        LOGGER.debug("CanvasCamera初始化完成: position={}, offset={}, zoom={}, rotation={}", 
            position, offset, zoom, rotation);
    }
    
    /**
     * 更新相机状态
     */
    public void update() {
        updateTransform();
    }

    /**
     * 旋转相机
     */
    public void rotate(float angle) {
        rotation += angle;
        updateTransform();
        markDirty();
    }
    
    /**
     * 更新变换矩阵
     */
    private void updateTransform() {
        if (transform == null) {
            transform = new Matrix3d();
        }
        
        try {
            // 构建变换矩阵
            // 正确的变换顺序：M = T * R * S
            // 1. 先创建各个变换矩阵
            Matrix3d scaling = new Matrix3d();
            scaling.setScale(zoom, zoom);
            
            Matrix3d rotationMatrix = new Matrix3d();
            rotationMatrix.setRotation(this.rotation);
            
            Matrix3d translation = new Matrix3d();
            translation.setTranslation(position.x, position.y);
            
            // 2. 按照正确的顺序组合变换
            // 先缩放，再旋转，最后平移
            transform = new Matrix3d(); // 重置为单位矩阵
            transform = translation.multiply(rotationMatrix.multiply(scaling));
            
            // 3. 计算逆变换矩阵
            inverse = transform.inverse();
            
            LOGGER.debug("变换矩阵已更新: position={}, zoom={}, rotation={}, offset={}", 
                position, zoom, rotation, offset);
                
            markDirty();
        } catch (Exception e) {
            LOGGER.error("更新变换矩阵时出错", e);
        }
    }
    
    /**
     * 屏幕坐标转世界坐标
     */
    public Vec2d screenToWorld(Vec2d screenPos) {
        if (screenPos == null) return null;
        
        // 确保变换矩阵已更新
        if (isDirty) {
            updateTransform();
        }
        
        // 考虑offset偏移 - 从屏幕坐标中减去偏移量
        Vec2d adjustedScreenPos = new Vec2d(
            screenPos.x - offset.x,
            screenPos.y - offset.y
        );
        
        // 使用逆变换矩阵将屏幕坐标转换为世界坐标
        Vec2d worldPos = inverse.transform(adjustedScreenPos);
        
        LOGGER.debug("屏幕坐标转世界坐标: 原始屏幕坐标={}, 调整后屏幕坐标={}, 世界坐标={}, offset={}, zoom={}", 
            screenPos, adjustedScreenPos, worldPos, offset, zoom);
            
        return worldPos;
    }
    
    /**
     * 世界坐标转屏幕坐标
     */
    public Vec2d worldToScreen(Vec2d worldPos) {
        if (worldPos == null) return null;
        
        // 确保变换矩阵已更新
        if (isDirty) {
            updateTransform();
        }
        
        // 使用变换矩阵将世界坐标转换为屏幕坐标
        Vec2d screenPos = transform.transform(worldPos);
        
        // 考虑offset偏移 - 添加偏移量
        Vec2d adjustedScreenPos = new Vec2d(
            screenPos.x + offset.x,
            screenPos.y + offset.y
        );
        
        LOGGER.debug("世界坐标转屏幕坐标: 世界坐标={}, 变换后坐标={}, 调整后屏幕坐标={}, offset={}, zoom={}", 
            worldPos, screenPos, adjustedScreenPos, offset, zoom);
            
        return adjustedScreenPos;
    }
    
    /**
     * 将屏幕像素距离转换为世界坐标距离
     * @param pixelDistance 屏幕像素距离
     * @return 世界坐标距离
     */
    public double screenToWorldDistance(double pixelDistance) {
        if (pixelDistance <= 0) {
            return 0.0;
        }
        
        // 确保变换矩阵已更新
        if (isDirty) {
            updateTransform();
        }
        
        // 使用缩放比例的反比来转换距离
        // 缩放比例越大，屏幕上的像素距离对应的世界距离越小
        return pixelDistance / zoom;
    }
    
    // Getters
    public float getZoom() {
        return zoom;
    }
    
    public float getRotation() {
        return rotation;
    }
    
    public Vec2d getPosition() {
        return position;
    }
    
    public Vec2d getOffset() {
        return offset;
    }

    // Setters
    public void setZoom(float zoom) {
        if (zoom >= 0.1f && zoom <= 10.0f) {
            this.zoom = zoom;
            updateTransform();
            markDirty();
        }
    }
    
    public void setRotation(float rotation) {
        this.rotation = rotation;
        updateTransform();
        markDirty();
    }
    
    public void setPosition(Vec2d position) {
        if (position != null) {
            this.position = position;
            updateTransform();
            markDirty();
        }
    }
    
    /**
     * 重置相机状态
     */
    public void reset() {
        position = new Vec2d(0, 0);
        zoom = 1.0f;
        rotation = 0.0f;
        offset = new Vec2d(0, 0);
        updateTransform();
        markDirty();
    }
    
    public void setCanvas(Canvas canvas) {
    }
    
    /**
     * 设置坐标偏移
     * @param offset 偏移量
     */
    public void setOffset(Vec2d offset) {
        if (offset == null) {
            LOGGER.warn("尝试设置null偏移，使用(0,0)代替");
            this.offset = new Vec2d(0, 0);
            return;
        }
        
        LOGGER.debug("设置坐标偏移: {}", offset);
        this.offset = offset;
        markDirty();
    }
    
    @Override
    public void init() {
        try {
            LOGGER.debug("Initializing CanvasCamera...");
            // 重置相机到初始状态
            reset();
            LOGGER.debug("CanvasCamera initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize CanvasCamera", e);
            throw new RuntimeException("Failed to initialize CanvasCamera", e);
        }
    }

    @Override
    public void close() throws Exception {
        LOGGER.debug("Disposing CanvasCamera resources...");
        // 清理资源
    }

    @Override
    public void render() {
        // 相机本身不需要渲染，只需要更新状态
        update();
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void clearDirty() {
        isDirty = false;
    }

    @Override
    public void markDirty() {
        isDirty = true;
    }
} 