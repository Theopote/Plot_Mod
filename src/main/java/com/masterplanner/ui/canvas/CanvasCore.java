package com.masterplanner.ui.canvas;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.graphics.IShapeStyle;
import com.masterplanner.api.graphics.ITextStyle;
import com.masterplanner.api.model.ICanvas;
import com.masterplanner.api.model.ILayer;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.graphics.style.TextStyle;
import com.masterplanner.core.layer.LayerManager;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.infrastructure.event.EventBus;
import com.masterplanner.infrastructure.event.view.OpacityChangeEvent;
import com.masterplanner.ui.theme.UITheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.EnumSet;

/**
 * 画布核心类
 * 
 * 负责画布的基本状态管理和ICanvas接口的实现，包括尺寸、透明度、缩放、
 * 图层管理等核心属性和方法。已优化脏标记系统和坐标转换。
 */
public class CanvasCore implements ICanvas {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasCore.class);

    /**
     * 脏标记类型枚举
     */
    public enum DirtyType {
        LAYOUT,     // 布局需要更新
        CONTENT,    // 内容需要重绘
        TOOL_PREVIEW, // 工具预览需要更新
        CAMERA,     // 相机变换需要更新
        STYLE       // 样式需要更新
    }

    private final CanvasCamera camera;
    private final CoordinateTransform coordinateTransform;
    private float zoom = 100.0f;
    private int width = 800;
    private int height = 600;
    private volatile float opacity;
    private final Object opacityLock = new Object();
    private ShapeStyle currentShapeStyle;
    private TextStyle currentTextStyle;
    private String currentCursor = "default";
    private LayerManager layerManager;
    private ILayer currentLayer;
    private final EventBus eventBus;
    
    // 优化后的脏标记系统
    private final EnumSet<DirtyType> dirtyFlags = EnumSet.noneOf(DirtyType.class);
    private final Object dirtyLock = new Object();
    
    // 性能优化：缓存常用计算结果
    private volatile long lastUpdateTime = 0;

    // 添加静态标志，防止递归调用
    private static volatile boolean isPublishingCursorEvent = false;

    // ====== Canvas 屏幕区域（由 CanvasRenderer 每帧同步）======
    // 用于在 ImGui 窗口上下文之外也能可靠判断“鼠标是否在画布上”
    private volatile float canvasScreenX = 0.0f;
    private volatile float canvasScreenY = 0.0f;
    private volatile float canvasScreenW = 0.0f;
    private volatile float canvasScreenH = 0.0f;

    /**
     * 内部坐标转换类，统一管理各种坐标系转换
     */
    private static class CoordinateTransform {
        private final CanvasCamera camera;
        
        public CoordinateTransform(CanvasCamera camera) {
            this.camera = camera;
        }
        
        public Vec2d worldToCanvas(Vec2d worldPoint) {
            return camera != null ? camera.worldToScreen(worldPoint) : worldPoint;
        }
        
        public Vec2d canvasToWorld(Vec2d canvasPoint) {
            return camera != null ? camera.screenToWorld(canvasPoint) : canvasPoint;
        }
        
        public Vec2d screenToWorld(Vec2d screenPos) {
            return camera != null ? camera.screenToWorld(screenPos) : screenPos;
        }
        
        public Vec2d worldToScreen(Vec2d worldPos) {
            return camera != null ? camera.worldToScreen(worldPos) : worldPos;
        }
    }

    /**
     * 构造函数 (推荐使用)
     * @param appState 应用程序状态，提供所有需要的依赖
     * @throws IllegalArgumentException 如果传入的appState为null或其LayerManager为null
     */
    public CanvasCore(AppState appState) {
        if (appState == null) {
            throw new IllegalArgumentException("AppState不能为null");
        }
        if (appState.getLayerManager() == null) {
            throw new IllegalArgumentException("AppState中的LayerManager不能为null");
        }
        
        this.eventBus = EventBus.getInstance();
        this.camera = new CanvasCamera();
        this.coordinateTransform = new CoordinateTransform(camera);
        this.opacity = UITheme.Canvas.DEFAULT_OPACITY;
        
        // 直接使用AppState中的LayerManager，确保一致性
        this.layerManager = appState.getLayerManager();
        
        // 继承已存在的活动图层
        this.currentLayer = layerManager.getActiveLayer();
        if (this.currentLayer == null) {
            LOGGER.debug("CanvasCore: 图层管理器没有活动图层，使用第一个可用图层");
            List<ILayer> layers = layerManager.getLayers();
            if (!layers.isEmpty()) {
                this.currentLayer = layers.getFirst();
                layerManager.setActiveLayer(this.currentLayer);
            }
        }
        
        // 初始化默认样式
        initializeDefaultStyles();
        
        // 初始时全部标记为脏
        markAllDirty();
    }

    /**
     * 构造函数 (兼容性保留)
     * @param layerManager 图层管理器
     * @throws IllegalArgumentException 如果传入的layerManager为null
     * @deprecated 建议使用 CanvasCore(AppState) 构造函数
     */
    @Deprecated
    public CanvasCore(LayerManager layerManager) {
        if (layerManager == null) {
            throw new IllegalArgumentException("LayerManager不能为null");
        }
        
        this.eventBus = EventBus.getInstance();
        this.camera = new CanvasCamera();
        this.coordinateTransform = new CoordinateTransform(camera);
        this.opacity = UITheme.Canvas.DEFAULT_OPACITY;
        
        // 简单引用传入的图层管理器，不进行重新创建
        this.layerManager = layerManager;
        
        // 继承已存在的活动图层
        this.currentLayer = layerManager.getActiveLayer();
        if (this.currentLayer == null) {
            LOGGER.debug("CanvasCore: 图层管理器没有活动图层，使用第一个可用图层");
            List<ILayer> layers = layerManager.getLayers();
            if (!layers.isEmpty()) {
                this.currentLayer = layers.getFirst();
                layerManager.setActiveLayer(this.currentLayer);
            }
        }
        
        // 初始化默认样式
        initializeDefaultStyles();
        
        // 初始时全部标记为脏
        markAllDirty();
    }

    /**
     * 无参构造函数，优先使用AppState中的组件
     */
    public CanvasCore() {
        this(getAppStateOrThrow());
    }
    
    /**
     * 获取AppState实例，如果不存在则抛出异常
     * 这确保了CanvasCore始终与AppState保持一致
     */
    private static AppState getAppStateOrThrow() {
        AppState appState = AppState.getInstance();
        if (appState == null) {
            throw new IllegalStateException("AppState必须在创建CanvasCore之前初始化");
        }
        if (appState.getLayerManager() == null) {
            throw new IllegalStateException("AppState中的LayerManager必须在创建CanvasCore之前初始化");
        }
        LOGGER.debug("CanvasCore: 使用AppState实例进行初始化");
        return appState;
    }

    public int getWidth() { 
        return width; 
    }
    
    public int getHeight() { 
        return height; 
    }
    
    public void setSize(int width, int height) { 
        if (this.width != width || this.height != height) {
            this.width = width; 
            this.height = height; 
            markDirty(DirtyType.LAYOUT, DirtyType.CONTENT);
        }
    }
    
    @Override 
    public List<ILayer> getLayers() { 
        return new ArrayList<>(layerManager.getLayers()); 
    }
    
    @Override 
    public ILayer getCurrentLayer() { 
        return currentLayer; 
    }
    
    @Override 
    public void setCurrentLayer(ILayer layer) { 
        if (layer != null && layerManager.getLayers().contains(layer) && currentLayer != layer) {
            this.currentLayer = layer; 
            layerManager.setActiveLayer(layer); 
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("当前图层已更改为: {}", layer.getName());
        }
    }
    
    @Override 
    public void addLayer(ILayer layer) { 
        if (layer != null) {
            layerManager.addLayer(layer); 
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("新图层已添加: {}", layer.getName());
        }
    }
    
    @Override 
    public void removeLayer(ILayer layer) { 
        if (layer != null) {
            layerManager.removeLayer(layer); 
            if (currentLayer == layer) {
                currentLayer = layerManager.getActiveLayer();
            }
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("图层已移除: {}", layer.getName());
        }
    }
    
    @Override 
    public Vec2d getOrigin() { 
        return new Vec2d(0, 0); 
    }
    
    @Override 
    public void setOrigin(Vec2d origin) { 
        if (camera != null) {
            Vec2d currentPos = camera.getPosition();
            if (!currentPos.equals(origin)) {
                camera.setPosition(origin);
                markDirty(DirtyType.CAMERA, DirtyType.CONTENT);
            }
        }
    }
    
    @Override 
    public double getScale() { 
        return zoom / 100.0; 
    }
    
    @Override 
    public void setScale(double scale) { 
        float newZoom = (float)(scale * 100.0);
        if (Math.abs(this.zoom - newZoom) > 0.001f) {
            this.zoom = newZoom; 
            if (camera != null) {
                camera.setZoom(this.zoom);
                markDirty(DirtyType.CAMERA, DirtyType.CONTENT);
            }
        }
    }
    
    @Override 
    public double getRotation() { 
        return camera != null ? camera.getRotation() : 0.0; 
    }
    
    @Override 
    public void setRotation(double rotation) { 
        if (camera != null) {
            float currentRotation = camera.getRotation();
            if (Math.abs(currentRotation - rotation) > 0.001) {
                camera.setRotation((float)rotation);
                markDirty(DirtyType.CAMERA, DirtyType.CONTENT);
            }
        }
    }
    
    @Override 
    public Vec2d worldToCanvas(Vec2d worldPoint) { 
        return coordinateTransform.worldToCanvas(worldPoint);
    }
    
    @Override 
    public Vec2d canvasToWorld(Vec2d canvasPoint) { 
        return coordinateTransform.canvasToWorld(canvasPoint);
    }
    
    @Override 
    public ITextStyle getCurrentTextStyle() { 
        return currentTextStyle; 
    }
    
    @Override 
    public void setCurrentTextStyle(ITextStyle style) { 
        if (style instanceof TextStyle) {
            TextStyle newStyle = (TextStyle) style.clone();
            if (!newStyle.equals(this.currentTextStyle)) {
                this.currentTextStyle = newStyle;
                markDirty(DirtyType.STYLE);
                LOGGER.debug("更新当前文本样式: {}", style);
            }
        } else {
            LOGGER.warn("尝试设置无效的文本样式类型");
        }
    }
    
    @Override 
    public IShapeStyle getCurrentShapeStyle() { 
        return currentShapeStyle; 
    }
    
    @Override 
    public void setCurrentShapeStyle(IShapeStyle style) { 
        if (style instanceof ShapeStyle) {
            ShapeStyle newStyle = (ShapeStyle) style.clone();
            if (!newStyle.equals(this.currentShapeStyle)) {
                this.currentShapeStyle = newStyle;
                markDirty(DirtyType.STYLE);
                LOGGER.debug("更新当前形状样式: {}", style);
            }
        } else {
            LOGGER.warn("尝试设置无效的形状样式类型");
        }
    }
    
    @Override 
    public String getCursor() { 
        return currentCursor; 
    }
    
    @Override 
    public void setCursor(String cursorType) { 
        if (cursorType == null) cursorType = "default";
        
        if (!cursorType.equals(this.currentCursor)) {
            this.currentCursor = cursorType;
            
            // 发布光标改变事件，使用静态标志防止递归
            if (!isPublishingCursorEvent) {
                try {
                    isPublishingCursorEvent = true;
                    // 这里可以发布光标改变事件
                    LOGGER.debug("光标类型已更改为: {}", cursorType);
                } catch (Exception e) {
                    LOGGER.error("发布光标改变事件时出错", e);
                } finally {
                    isPublishingCursorEvent = false;
                }
            }
        }
    }
    
    @Override 
    public void addShape(Shape shape) { 
        if (shape == null) {
            LOGGER.warn("尝试添加空图形");
            return;
        }
        
        ILayer activeLayer = getActiveLayer();
        if (activeLayer == null) {
            LOGGER.error("无法添加图形：当前没有活动图层");
            return;
        }
        
        try {
            LOGGER.debug("添加图形到活动图层: {}", activeLayer.getName());
            activeLayer.addShape(shape);
            
            // 确保图形有样式
            if (shape.getStyle() == null) {
                IShapeStyle style = getCurrentShapeStyle();
                if (style != null) {
                    shape.setStyle(style.clone());
                    LOGGER.debug("已应用当前样式到图形");
                } else {
                    // 创建默认样式
                    ShapeStyle defaultStyle = new ShapeStyle();
                    defaultStyle.setStrokeColor(java.awt.Color.BLACK);
                    defaultStyle.setStrokeWidth(2.0f);
                    shape.setStyle(defaultStyle);
                    LOGGER.debug("已应用默认样式到图形");
                }
            }
            
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("图形添加成功");
        } catch (Exception e) {
            LOGGER.error("添加图形失败", e);
        }
    }
    
    @Override 
    public void removeShape(Shape shape) { 
        if (shape == null) {
            LOGGER.warn("尝试移除空图形");
            return;
        }
        
        ILayer activeLayer = getActiveLayer();
        if (activeLayer == null) {
            LOGGER.error("无法移除图形：当前没有活动图层");
            return;
        }
        
        try {
            LOGGER.debug("从活动图层移除图形: {}", activeLayer.getName());
            activeLayer.removeShape(shape);
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("图形移除成功");
        } catch (Exception e) {
            LOGGER.error("移除图形失败", e);
        }
    }
    
    @Override 
    public void clear() { 
        LOGGER.debug("清空画布");
        try {
            List<ILayer> layers = layerManager.getLayers();
            for (ILayer layer : layers) {
                layer.clear();
            }
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("画布清空成功");
        } catch (Exception e) {
            LOGGER.error("清空画布失败", e);
        }
    }
    
    @Override 
    public List<Shape> getShapes() { 
        List<Shape> allShapes = new ArrayList<>();
        List<ILayer> layers = layerManager.getLayers();
        for (ILayer layer : layers) {
            allShapes.addAll(layer.getShapes());
        }
        return allShapes;
    }
    
    @Override 
    public Vec2d screenToWorld(Vec2d screenPos) { 
        return coordinateTransform.screenToWorld(screenPos);
    }
    
    @Override 
    public Vec2d worldToScreen(Vec2d worldPos) { 
        return coordinateTransform.worldToScreen(worldPos);
    }

    /**
     * 设置透明度（线程安全）
     * @param opacity 透明度值（0.0-1.0）
     */
    public void setOpacity(float opacity) {
        opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        
        synchronized (opacityLock) {
            if (Math.abs(this.opacity - opacity) > 0.001f) {
                this.opacity = opacity;
                markDirty(DirtyType.CONTENT);
                
                                 // 发布透明度改变事件
                 try {
                     eventBus.publish(new OpacityChangeEvent(opacity));
                 } catch (Exception e) {
                     LOGGER.error("发布透明度改变事件失败", e);
                 }
            }
        }
    }

    /**
     * 获取透明度（线程安全）
     * @return 透明度值
     */
    public float getOpacity() {
        synchronized (opacityLock) {
            return opacity;
        }
    }

    /**
     * 设置图层管理器
     * @param layerManager 新的图层管理器
     */
    public void setLayerManager(LayerManager layerManager) {
        if (layerManager == null) {
            LOGGER.warn("尝试设置空的图层管理器");
            return;
        }
        
        if (this.layerManager != layerManager) {
            LOGGER.debug("设置新的图层管理器");
            this.layerManager = layerManager;
            this.currentLayer = layerManager.getActiveLayer();
            
            if (this.currentLayer == null) {
                List<ILayer> layers = layerManager.getLayers();
                if (!layers.isEmpty()) {
                    this.currentLayer = layers.getFirst();
                    layerManager.setActiveLayer(this.currentLayer);
                }
            }
            
            markDirty(DirtyType.CONTENT);
            LOGGER.debug("图层管理器设置完成");
        }
    }

    /**
     * 获取当前使用的图层管理器
     * @return 当前图层管理器实例
     */
    public LayerManager getLayerManager() {
        return layerManager;
    }

    /**
     * 初始化默认样式
     */
    private void initializeDefaultStyles() {
        if (currentShapeStyle == null) {
            currentShapeStyle = new ShapeStyle();
        }
        
        if (currentTextStyle == null) {
            currentTextStyle = new TextStyle.Builder()
                .color(java.awt.Color.BLACK)
                .fontSize(12.0f)
                .build();
        }
    }

    /**
     * 获取相机对象
     * @return 相机对象
     */
    public CanvasCamera getCamera() {
        return camera;
    }

    /**
     * 由 CanvasRenderer 在每帧渲染时同步画布的屏幕区域（绝对屏幕坐标）。
     * 注意：这里的 (x,y,w,h) 通常来自 ImGui 的 windowPos/windowSize。
     */
    public void setCanvasScreenBounds(float x, float y, float w, float h) {
        this.canvasScreenX = x;
        this.canvasScreenY = y;
        this.canvasScreenW = Math.max(0.0f, w);
        this.canvasScreenH = Math.max(0.0f, h);
    }

    public float getCanvasScreenX() { return canvasScreenX; }
    public float getCanvasScreenY() { return canvasScreenY; }
    public float getCanvasScreenW() { return canvasScreenW; }
    public float getCanvasScreenH() { return canvasScreenH; }

    /**
     * 判断某个屏幕坐标是否落在最近一次渲染同步到的画布区域内。
     */
    public boolean isScreenPosInsideCanvas(Vec2d screenPos) {
        if (screenPos == null) return false;
        float w = canvasScreenW;
        float h = canvasScreenH;
        if (w <= 1.0f || h <= 1.0f) {
            // 尚未同步到有效区域（例如首帧/初始化阶段）
            return false;
        }
        double x0 = canvasScreenX;
        double y0 = canvasScreenY;
        double x1 = x0 + w;
        double y1 = y0 + h;
        return screenPos.x >= x0 && screenPos.x <= x1 && screenPos.y >= y0 && screenPos.y <= y1;
    }

    /**
     * 标记指定类型为脏（优化版本）
     * @param types 需要标记的脏类型
     */
    public void markDirty(DirtyType... types) {
        if (types.length == 0) return;
        
        synchronized (dirtyLock) {
            dirtyFlags.addAll(Arrays.asList(types));
            lastUpdateTime = System.currentTimeMillis();
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("标记脏类型: {}", java.util.Arrays.toString(types));
        }
    }

    /**
     * 标记所有类型为脏
     */
    public void markAllDirty() {
        synchronized (dirtyLock) {
            dirtyFlags.addAll(EnumSet.allOf(DirtyType.class));
            lastUpdateTime = System.currentTimeMillis();
        }
        LOGGER.debug("标记所有内容为脏");
    }

    /**
     * 检查特定类型是否为脏
     */
    public boolean isDirty(DirtyType type) {
        synchronized (dirtyLock) {
            return dirtyFlags.contains(type);
        }
    }

    /**
     * 清除指定的脏标记
     */
    public void clearDirty(DirtyType... types) {
        synchronized (dirtyLock) {
            for (DirtyType type : types) {
                dirtyFlags.remove(type);
            }
        }
    }

    /**
     * 清除所有脏标记
     */
    public void clearAllDirtyFlags() {
        synchronized (dirtyLock) {
            dirtyFlags.clear();
        }
    }
    
    /**
     * 初始化核心组件
     */
    public void init() {
        try {
            LOGGER.debug("初始化CanvasCore...");
            
            // 初始化相机
            if (camera != null) {
                camera.init();
            }
            
            markAllDirty();
            LOGGER.debug("CanvasCore初始化成功");
        } catch (Exception e) {
            LOGGER.error("初始化CanvasCore失败", e);
            throw new RuntimeException("初始化CanvasCore失败", e);
        }
    }
    
    /**
     * 关闭资源
     */
    public void close() throws Exception {
        LOGGER.debug("释放CanvasCore资源...");
        
        // 清理脏标记
        clearAllDirtyFlags();
        
        // 关闭相机
        if (camera != null) {
            camera.close();
        }
        
        // 清理样式引用
        currentShapeStyle = null;
        currentTextStyle = null;
        
        LOGGER.debug("CanvasCore资源释放完成");
    }
    
    /**
     * 刷新画布
     * 实现ICanvas接口的refresh方法
     */
    @Override
    public void refresh() {
        LOGGER.debug("刷新画布...");
        // 标记所有内容需要重绘
        markAllDirty();
    }
    
    /**
     * 获取活动图层
     * @return 当前活动图层
     */
    public ILayer getActiveLayer() {
        return layerManager != null ? layerManager.getActiveLayer() : null;
    }

    // 向后兼容的方法
    @Deprecated
    public void markLayoutDirty() {
        markDirty(DirtyType.LAYOUT);
    }

    @Deprecated
    public void markToolPreviewDirty() {
        markDirty(DirtyType.TOOL_PREVIEW);
    }

    @Deprecated
    public boolean isLayoutDirty() {
        return isDirty(DirtyType.LAYOUT);
    }

    @Deprecated
    public boolean isContentDirty() {
        return isDirty(DirtyType.CONTENT);
    }

    @Deprecated
    public boolean isToolPreviewDirty() {
        return isDirty(DirtyType.TOOL_PREVIEW);
    }

    @Deprecated
    public void clearDirtyFlags() {
        clearAllDirtyFlags();
    }
} 