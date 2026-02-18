package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 包围盒控制点管理器
 * 
 * <p>专门负责管理包围盒和8个控制点的计算、渲染和交互：</p>
 * <ul>
 *   <li>计算包围盒和控制点位置</li>
 *   <li>渲染控制点和包围盒</li>
 *   <li>处理控制点的点击命中测试</li>
 *   <li>处理控制点的框选和拖拽</li>
 * </ul>
 * 
 * <p><strong>坐标系约定：</strong></p>
 * <ul>
 *   <li>世界坐标系：Y轴向上（数学坐标系）</li>
 *   <li>屏幕坐标系：Y轴向下（屏幕左上角为原点）</li>
 *   <li>CanvasCamera.worldToScreen 负责坐标系转换</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 控制点管理分离
 */
public class BoundingBoxControlManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundingBoxControlManager.class);
    
    // 控制点常量
    private static final double CONTROL_POINT_SIZE = 8.0; // 控制点大小
    private static final double CONTROL_POINT_HIT_DISTANCE = 12.0; // 控制点点击检测距离
    private static final double CONTROL_POINT_HOVER_DISTANCE = 24.0; // 控制点悬停检测距离
    private static final double ROTATION_ICON_HOVER_DISTANCE = 14.0; // 旋转图标悬停检测距离
    
         // 渲染常量
     private static final float DASH_LENGTH = 8.0f;
     private static final float GAP_LENGTH = 4.0f;
     
     // 旋转相关常量
     private static final double ROTATION_ICON_SIZE = 16.0; // 旋转图标大小
     private static final double ROTATION_ICON_OFFSET = 20.0; // 旋转图标距离控制点的偏移
     private static final double ROTATION_ARC_RADIUS = 30.0; // 旋转弧线半径

    /**
     * 颜色配置类
     * 
     * <p>集中管理所有渲染颜色，支持主题化配置</p>
     */
    public static class ColorConfig {
        // 包围盒颜色
        public static final int BOUNDING_BOX_COLOR = 0x6400FFFF; // 半透明青色
        public static final int PREVIEW_BOX_COLOR = 0xFF00A5FF; // 橙色
        
        // 控制点颜色
        public static final int CONTROL_POINT_SELECTED_COLOR = 0xFFFF0000; // 红色（主要控制点）
        public static final int CONTROL_POINT_SECONDARY_COLOR = 0xFFFFA500; // 橙色（其他选中控制点）
        public static final int CONTROL_POINT_NORMAL_COLOR = 0xFF0000FF; // 蓝色（普通控制点）
        public static final int CONTROL_POINT_BORDER_COLOR = 0xFF000000; // 黑色边框
        
                 // 选择框颜色
         public static final int SELECTION_BOX_COLOR = 0xFFFFFFFF; // 白色边框
         
         // 旋转相关颜色
         public static final int ROTATION_ICON_COLOR = 0xFF00FF00; // 绿色旋转图标
         public static final int ROTATION_ARC_COLOR = 0xFF00FF00; // 绿色旋转弧线
         public static final int ROTATION_ANGLE_TEXT_COLOR = 0xFFFFFFFF; // 白色角度文本
         
         // DrawContext 颜色
         public static final Color BOUNDING_BOX_DRAW_COLOR = new Color(0, 255, 255, 100); // 半透明青色
         public static final Color PREVIEW_BOX_DRAW_COLOR = new Color(255, 165, 0, 200); // 橙色半透明
         public static final Color SELECTION_BOX_DRAW_COLOR = Color.WHITE; // 白色
         public static final Color CONTROL_POINT_PRIMARY_DRAW_COLOR = Color.RED; // 红色
         public static final Color CONTROL_POINT_SECONDARY_DRAW_COLOR = Color.ORANGE; // 橙色
         public static final Color CONTROL_POINT_NORMAL_DRAW_COLOR = Color.BLUE; // 蓝色
         public static final Color CONTROL_POINT_BORDER_DRAW_COLOR = Color.BLACK; // 黑色

         // 悬停颜色
         public static final int CONTROL_POINT_HOVER_COLOR = 0xFF00FF00; // 绿色悬停
         public static final Color CONTROL_POINT_HOVER_DRAW_COLOR = new Color(0, 255, 0, 200); // 半透明绿色悬停
    }
    
    // 控制点类型枚举
    public enum ControlPointType {
        TOP_LEFT("左上角", 0, true),      // 角点，支持旋转
        TOP_CENTER("上中点", 1, false),   // 中点，不支持旋转
        TOP_RIGHT("右上角", 2, true),     // 角点，支持旋转
        CENTER_RIGHT("右中点", 3, false), // 中点，不支持旋转
        BOTTOM_RIGHT("右下角", 4, true),  // 角点，支持旋转
        BOTTOM_CENTER("下中点", 5, false), // 中点，不支持旋转
        BOTTOM_LEFT("左下角", 6, true),   // 角点，支持旋转
        CENTER_LEFT("左中点", 7, false);  // 中点，不支持旋转

        private final String displayName;
        private final int index;
        private final boolean supportsRotation; // 是否支持旋转

        ControlPointType(String displayName, int index, boolean supportsRotation) {
            this.displayName = displayName;
            this.index = index;
            this.supportsRotation = supportsRotation;
        }

        public String getDisplayName() { return displayName; }
        public int getIndex() { return index; }
        public boolean supportsRotation() { return supportsRotation; }
    }
    
    // 包围盒和控制点状态
    private Vec2d boundingBoxMin; // 包围盒最小点
    private Vec2d boundingBoxMax; // 包围盒最大点
    private Vec2d[] controlPoints = new Vec2d[8]; // 8个控制点
    
    // 预览状态
    private Vec2d previewBoundingBoxMin; // 预览包围盒最小点
    private Vec2d previewBoundingBoxMax; // 预览包围盒最大点
    private Vec2d[] previewControlPoints = new Vec2d[8]; // 8个预览控制点
    
    // 选择状态
    private List<ControlPointType> selectedControlPoints = new ArrayList<>(); // 当前选中的控制点列表
    private ControlPointType primaryControlPoint; // 主要控制点（用于拖拽）
    private ControlPointType hoveredControlPoint; // 悬停的控制点
    private boolean rotationIconsEnabled = true; // 是否启用旋转图示
    
    // 框选状态
    private Vec2d selectionStartPoint; // 框选开始点
    private Vec2d selectionEndPoint; // 框选结束点
    private boolean isSelecting = false; // 是否正在框选
    
    // 旋转状态
    private boolean isRotating = false; // 是否正在旋转
    private Vec2d rotationCenter; // 旋转中心点
    private double currentRotationAngle = 0.0; // 当前旋转角度（弧度）
    private double startRotationAngle = 0.0; // 开始旋转时的角度
    private ControlPointType rotatingControlPoint; // 正在旋转的控制点
    
    // 性能优化：缓存机制
    private long lastCalculationTime = 0; // 上次计算时间
    private static final long CALCULATION_CACHE_DURATION = 16; // 缓存持续时间（毫秒，约60FPS）
    private boolean calculationCacheValid = false; // 计算缓存是否有效
    private List<Shape> lastShapes; // 上次计算的图形列表
    private int lastShapesHash; // 上次图形的哈希值
    
    // 控制点缓存
    private Vec2d[] lastControlPoints; // 上次计算的控制点
    private Vec2d lastBoundingBoxMin; // 上次包围盒最小点
    private Vec2d lastBoundingBoxMax; // 上次包围盒最大点
    
    // 用户反馈接口
    public interface StatusMessageCallback {
        void setStatusMessage(String message);
    }
    
    // 渲染接口 - 统一渲染逻辑
    public interface Renderer {
        void renderBoundingBox(Vec2d min, Vec2d max, boolean isPreview);
        void renderControlPoint(Vec2d position, int index, boolean isSelected, boolean isPrimary);
        void renderPreviewControlPoint(Vec2d position, int index);
        void renderSelectionBox(Vec2d start, Vec2d end);
        void renderRotationIcon(Vec2d position, boolean isActive);
        void renderRotationArc(Vec2d center, double radius, double startAngle, double endAngle);
    }
    
    private StatusMessageCallback statusCallback; // 状态消息回调
    
    /**
     * 内部数据结构：包围盒计算结果
     */
    private record BoundingBox(Vec2d min, Vec2d max) {}
    
    /**
     * 设置状态消息回调
     */
    public void setStatusMessageCallback(StatusMessageCallback callback) {
        this.statusCallback = callback;
    }
    
    /**
     * 发送状态消息给用户
     */
    private void sendStatusMessage(String message) {
        if (statusCallback != null) {
            try {
                statusCallback.setStatusMessage(message);
            } catch (Exception e) {
                LOGGER.warn("发送状态消息失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 构造函数
     */
    public BoundingBoxControlManager() {
        // 初始化控制点数组
        for (int i = 0; i < 8; i++) {
            controlPoints[i] = new Vec2d(0, 0);
            previewControlPoints[i] = new Vec2d(0, 0);
        }
    }
    
    /**
     * 计算包围盒
     * 
     * <p>参考 SketchUp、Illustrator 等专业软件的设计：</p>
     * <ul>
     *   <li>保持原始包围盒的稳定性</li>
     *   <li>避免频繁重新计算导致的"跳跃"</li>
     *   <li>提供平滑的视觉反馈</li>
     *   <li>加强数据验证，处理无效点</li>
     *   <li>性能优化：缓存机制减少重复计算</li>
     *   <li>重构：使用通用边界计算方法消除代码重复</li>
     *   <li>健壮性：改进缓存键计算，提高缓存可靠性</li>
     * </ul>
     */
    public void calculateBoundingBox(List<Shape> shapes) {
        // 性能优化：检查缓存是否有效（使用更可靠的哈希值计算）
        int shapesHash = calculateShapesContentHash(shapes);
        long currentTime = System.currentTimeMillis();
        if (calculationCacheValid && shapesHash == lastShapesHash && 
            (currentTime - lastCalculationTime) < CALCULATION_CACHE_DURATION) {
            LOGGER.debug("使用缓存的包围盒计算结果（内容未变化）");
            return;
        }

        // 更新缓存状态
        lastShapes = shapes != null ? new ArrayList<>(shapes) : null;
        lastShapesHash = shapesHash;
        
        long startTime = System.nanoTime();
        
        // 使用通用的边界计算方法
        BoundingBox bounds = computeBoundsForShapes(shapes, "原始图形");
        
        if (bounds != null) {
            this.boundingBoxMin = bounds.min;
            this.boundingBoxMax = bounds.max;
        } else {
            // 处理没有有效点的情况
            LOGGER.warn("没有找到有效的图形点，使用默认包围盒");
            sendStatusMessage("无效图形点，无法计算包围盒，使用默认包围盒");
            this.boundingBoxMin = new Vec2d(0, 0);
            this.boundingBoxMax = new Vec2d(100, 100);
        }
        
        // 计算控制点位置
        calculateControlPoints();
        
        // 性能监控
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // 转换为毫秒
        
        LOGGER.debug("计算包围盒完成: min=({}, {}), max=({}, {}), 耗时: {}ms", 
                    boundingBoxMin.x, boundingBoxMin.y, boundingBoxMax.x, boundingBoxMax.y, duration);
        
        updateCalculationCache();
    }
    
    /**
     * 更新计算缓存状态
     */
    private void updateCalculationCache() {
        lastCalculationTime = System.currentTimeMillis();
        calculationCacheValid = true;
    }
    
    /**
     * 计算图形内容的哈希值
     * 
     * <p>改进的缓存键计算方法，通过计算所有图形点的哈希值来提高缓存可靠性。
     * 相比简单的 shapes.hashCode()，这种方法能更准确地检测内容变化。</p>
     * 
     * @param shapes 要计算哈希值的图形列表
     * @return 基于图形内容计算的哈希值
     */
    private int calculateShapesContentHash(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return 0;
        }
        
        int hash = 1;
        for (Shape shape : shapes) {
            if (shape == null) continue;
            
            // 计算图形类型哈希
            hash = 31 * hash + shape.getClass().getSimpleName().hashCode();
            
            // 计算图形点的哈希值
            List<Vec2d> points = shape.getPoints();
            if (points != null) {
                for (Vec2d point : points) {
                    if (point != null) {
                        // 使用双精度值的哈希算法
                        hash = 31 * hash + Double.hashCode(point.x);
                        hash = 31 * hash + Double.hashCode(point.y);
                    }
                }
            }
        }
        
        return hash;
    }
    
    /**
     * 计算图形的边界框
     * 
     * <p>这是一个私有的、单一职责的方法，封装了通用的边界计算逻辑。
     * 用于消除 calculateBoundingBox 和 calculatePreviewBoundingBoxAndControlPoints 中的代码重复。</p>
     * 
     * @param shapes 要计算边界的图形列表
     * @param contextName 上下文名称，用于日志记录（如"原始图形"、"预览图形"）
     * @return 计算得到的边界框，如果没有有效点则返回 null
     */
    private BoundingBox computeBoundsForShapes(List<Shape> shapes, String contextName) {
        if (shapes == null || shapes.isEmpty()) {
            LOGGER.debug("{} 列表为空，无法计算边界", contextName);
            return null;
        }

        // 计算所有图形的统一边界框
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        boolean hasValidPoints = false;
        int processedShapes = 0;
        int skippedShapes = 0;

        for (Shape shape : shapes) {
            if (shape == null) {
                LOGGER.warn("跳过空图形");
                skippedShapes++;
                continue;
            }

            // 优先使用图形自身包围盒：可正确反映 transform 矩阵后的几何范围
            // 这对 SpiralShape 等“点集不直接包含 transform 效果”的图形尤为关键。
            try {
                com.masterplanner.core.geometry.BoundingBox shapeBounds = shape.getBoundingBox();
                if (shapeBounds != null) {
                    minX = Math.min(minX, shapeBounds.getMinX());
                    minY = Math.min(minY, shapeBounds.getMinY());
                    maxX = Math.max(maxX, shapeBounds.getMaxX());
                    maxY = Math.max(maxY, shapeBounds.getMaxY());
                    hasValidPoints = true;
                    processedShapes++;
                    continue;
                }
            } catch (Exception e) {
                LOGGER.debug("图形 {} 的包围盒计算失败，回退到点集计算: {}",
                        shape.getClass().getSimpleName(), e.getMessage());
            }
            
            List<Vec2d> points = shape.getPoints();
            if (points == null || points.isEmpty()) {
                LOGGER.warn("图形 {} 点列表为空，跳过", shape.getClass().getSimpleName());
                skippedShapes++;
                continue;
            }
            
            // 检查是否包含无效点
            if (points.contains(null)) {
                LOGGER.warn("图形 {} 包含无效点，跳过", shape.getClass().getSimpleName());
                skippedShapes++;
                continue;
            }
            
                for (Vec2d point : points) {
                if (point == null) {
                    LOGGER.warn("图形 {} 包含空点，跳过", shape.getClass().getSimpleName());
                    continue;
                }
                
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                hasValidPoints = true;
            }
            processedShapes++;
        }
        
        // 如果没有有效点，返回 null
        if (!hasValidPoints) {
            LOGGER.error("没有找到有效的{}点，无法计算边界", contextName);
            sendStatusMessage("无效" + contextName + "点，请检查选择");
            return null;
        }
        
        // 仅在跳过图形较多时记录警告，不提示用户避免频繁干扰
        if (skippedShapes > 0) {
            LOGGER.warn("跳过 {} 个无效图形", skippedShapes);
        }

        // 确保边界框有最小尺寸，避免过小的边界框
        double minSize = 10.0; // 最小边界框尺寸
        double width = maxX - minX;
        double height = maxY - minY;
        
        if (width < minSize) {
            double centerX = (minX + maxX) / 2;
            minX = centerX - minSize / 2;
            maxX = centerX + minSize / 2;
        }
        
        if (height < minSize) {
            double centerY = (minY + maxY) / 2;
            minY = centerY - minSize / 2;
            maxY = centerY + minSize / 2;
        }

        LOGGER.debug("计算{}边界完成: min=({}, {}), max=({}, {}), 处理图形: {}/{}", 
                    contextName, minX, minY, maxX, maxY, processedShapes, processedShapes + skippedShapes);
        
        return new BoundingBox(new Vec2d(minX, minY), new Vec2d(maxX, maxY));
    }
    
    /**
     * 计算控制点位置
     */
    public void calculateControlPoints() {
        if (boundingBoxMin == null || boundingBoxMax == null) {
            return;
        }

        // 检查控制点缓存
        if (lastControlPoints != null && boundingBoxMin.equals(lastBoundingBoxMin) && 
            boundingBoxMax.equals(lastBoundingBoxMax)) {
            controlPoints = lastControlPoints.clone();
            LOGGER.debug("使用缓存的控制点计算结果");
            return;
        }

        double minX = boundingBoxMin.x;
        double minY = boundingBoxMin.y;
        double maxX = boundingBoxMax.x;
        double maxY = boundingBoxMax.y;
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;

        // 8个控制点：角点 + 中点
        controlPoints[0] = new Vec2d(minX, maxY); // 左上角
        controlPoints[1] = new Vec2d(centerX, maxY); // 上中点
        controlPoints[2] = new Vec2d(maxX, maxY); // 右上角
        controlPoints[3] = new Vec2d(maxX, centerY); // 右中点
        controlPoints[4] = new Vec2d(maxX, minY); // 右下角
        controlPoints[5] = new Vec2d(centerX, minY); // 下中点
        controlPoints[6] = new Vec2d(minX, minY); // 左下角
        controlPoints[7] = new Vec2d(minX, centerY); // 左中点
        
        // 更新控制点缓存
        lastControlPoints = controlPoints.clone();
        lastBoundingBoxMin = boundingBoxMin;
        lastBoundingBoxMax = boundingBoxMax;
    }
    
    /**
     * 查找点击的控制点
     */
    public ControlPointType findClickedControlPoint(Vec2d point) {
        for (ControlPointType controlPointType : ControlPointType.values()) {
            Vec2d controlPoint = controlPoints[controlPointType.getIndex()];
            if (controlPoint != null && point.distance(controlPoint) <= CONTROL_POINT_HIT_DISTANCE) {
                return controlPointType;
            }
        }
        return null;
    }
    
    /**
     * 更新悬停的控制点
     */
    public void updateHoveredControlPoint(Vec2d point) {
        hoveredControlPoint = findHoveredControlPoint(point);
    }

    private ControlPointType findHoveredControlPoint(Vec2d point) {
        if (point == null) {
            return null;
        }

        for (ControlPointType controlPointType : ControlPointType.values()) {
            Vec2d controlPoint = controlPoints[controlPointType.getIndex()];
            if (controlPoint == null) {
                continue;
            }

            if (point.distance(controlPoint) <= CONTROL_POINT_HOVER_DISTANCE) {
                return controlPointType;
            }

            if (rotationIconsEnabled && controlPointType.supportsRotation()) {
                Vec2d iconPosition = calculateRotationIconPosition(controlPointType, controlPoint);
                if (iconPosition != null && point.distance(iconPosition) <= ROTATION_ICON_HOVER_DISTANCE) {
                    return controlPointType;
                }
            }
        }

        return null;
    }

    /**
     * 设置旋转图示是否启用
     */
    public void setRotationIconsEnabled(boolean enabled) {
        this.rotationIconsEnabled = enabled;
    }

    /**
     * 获取旋转图示是否启用
     */
    public boolean isRotationIconsEnabled() {
        return rotationIconsEnabled;
    }
    
    /**
     * 清除悬停状态
     */
    public void clearHoveredControlPoint() {
        hoveredControlPoint = null;
    }
    
    /**
     * 获取悬停的控制点
     */
    public ControlPointType getHoveredControlPoint() {
        return hoveredControlPoint;
    }
    
    /**
     * 开始框选控制点
     */
    public void startControlPointSelection(Vec2d startPoint) {
        isSelecting = true;
        selectionStartPoint = startPoint;
        selectionEndPoint = startPoint;
    }
    
    /**
     * 更新框选控制点
     */
    public void updateControlPointSelection(Vec2d endPoint) {
        if (!isSelecting) {
            return;
        }
        
        selectionEndPoint = endPoint;
        updateSelectedControlPoints();
    }
    
    /**
     * 完成框选控制点
     */
    public void finishControlPointSelection() {
        isSelecting = false;
        updateSelectedControlPoints();
        // 清理框选状态，避免状态残留
        selectionStartPoint = null;
        selectionEndPoint = null;
        LOGGER.debug("框选控制点完成，已清理选择状态");
    }
    
    /**
     * 更新选中的控制点
     */
    private void updateSelectedControlPoints() {
        if (selectionStartPoint == null || selectionEndPoint == null) {
            return;
        }

        selectedControlPoints.clear();
        
        // 计算框选矩形
        double minX = Math.min(selectionStartPoint.x, selectionEndPoint.x);
        double maxX = Math.max(selectionStartPoint.x, selectionEndPoint.x);
        double minY = Math.min(selectionStartPoint.y, selectionEndPoint.y);
        double maxY = Math.max(selectionStartPoint.y, selectionEndPoint.y);

        // 检查每个控制点是否在框选范围内
        for (ControlPointType controlPointType : ControlPointType.values()) {
            Vec2d controlPoint = controlPoints[controlPointType.getIndex()];
            if (controlPoint != null) {
                if (controlPoint.x >= minX && controlPoint.x <= maxX &&
                    controlPoint.y >= minY && controlPoint.y <= maxY) {
                    selectedControlPoints.add(controlPointType);
                }
            }
        }

        // 设置主要控制点
        if (!selectedControlPoints.isEmpty()) {
            // 使用最后一个被添加的点作为主控制点，更符合用户的预期
            // 这样在框选时，用户最后覆盖到的控制点会成为主控制点
            primaryControlPoint = selectedControlPoints.getLast();
        }
    }
    
    /**
     * 选择单个控制点
     */
    public void selectControlPoint(ControlPointType controlPointType, boolean clearPrevious) {
        if (clearPrevious) {
            selectedControlPoints.clear();
        }
        
        if (!selectedControlPoints.contains(controlPointType)) {
            selectedControlPoints.add(controlPointType);
        }
        
        primaryControlPoint = controlPointType;
    }
    
    /**
     * 清除控制点选择
     */
    public void clearControlPointSelection() {
        selectedControlPoints.clear();
        primaryControlPoint = null;
    }
    
    /**
     * 计算预览包围盒和控制点
     * 
     * <p>参考专业软件的设计，实现稳定的预览效果：</p>
     * <ul>
     *   <li>基于原始包围盒进行变换，而不是重新计算</li>
     *   <li>保持控制点的相对位置稳定</li>
     *   <li>避免预览时的"跳跃"现象</li>
     *   <li>加强数据验证，处理无效点</li>
     *   <li>重构：使用通用边界计算方法消除代码重复</li>
     * </ul>
     */
    public void calculatePreviewBoundingBoxAndControlPoints(List<Shape> previewShapes) {
        if (previewShapes == null || previewShapes.isEmpty()) {
            // 如果没有预览图形，使用原始包围盒
            previewBoundingBoxMin = boundingBoxMin;
            previewBoundingBoxMax = boundingBoxMax;
            calculatePreviewControlPoints();
            return;
        }

        // 使用通用的边界计算方法
        BoundingBox bounds = computeBoundsForShapes(previewShapes, "预览图形");
        
        if (bounds != null) {
            this.previewBoundingBoxMin = bounds.min;
            this.previewBoundingBoxMax = bounds.max;
        } else {
            // 如果预览图形无效，回退到原始包围盒
            LOGGER.warn("没有找到有效的预览图形点，使用原始包围盒");
            sendStatusMessage("无效预览图形点，使用原始包围盒");
            this.previewBoundingBoxMin = this.boundingBoxMin;
            this.previewBoundingBoxMax = this.boundingBoxMax;
        }
        
        // 计算预览控制点
        calculatePreviewControlPoints();
        
        LOGGER.debug("计算预览包围盒: min=({}, {}), max=({}, {})", 
                    previewBoundingBoxMin.x, previewBoundingBoxMin.y, 
                    previewBoundingBoxMax.x, previewBoundingBoxMax.y);
    }
    
    /**
     * 计算预览控制点位置
     */
    private void calculatePreviewControlPoints() {
        if (previewBoundingBoxMin == null || previewBoundingBoxMax == null) {
            return;
        }

        double minX = previewBoundingBoxMin.x;
        double minY = previewBoundingBoxMin.y;
        double maxX = previewBoundingBoxMax.x;
        double maxY = previewBoundingBoxMax.y;
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;

        // 8个预览控制点：角点 + 中点
        previewControlPoints[0] = new Vec2d(minX, maxY); // 左上角
        previewControlPoints[1] = new Vec2d(centerX, maxY); // 上中点
        previewControlPoints[2] = new Vec2d(maxX, maxY); // 右上角
        previewControlPoints[3] = new Vec2d(maxX, centerY); // 右中点
        previewControlPoints[4] = new Vec2d(maxX, minY); // 右下角
        previewControlPoints[5] = new Vec2d(centerX, minY); // 下中点
        previewControlPoints[6] = new Vec2d(minX, minY); // 左下角
        previewControlPoints[7] = new Vec2d(minX, centerY); // 左中点
    }
    
    /**
     * 清除预览状态
     */
    public void clearPreviewState() {
        previewBoundingBoxMin = null;
        previewBoundingBoxMax = null;
        for (int i = 0; i < 8; i++) {
            previewControlPoints[i] = new Vec2d(0, 0);
        }
    }
    
    /**
     * 重置状态
     */
    public void reset() {
        boundingBoxMin = null;
        boundingBoxMax = null;
        for (int i = 0; i < 8; i++) {
            controlPoints[i] = new Vec2d(0, 0);
            previewControlPoints[i] = new Vec2d(0, 0);
        }
                 selectedControlPoints.clear();
         primaryControlPoint = null;
         selectionStartPoint = null;
         selectionEndPoint = null;
         isSelecting = false;
         
         // 清理旋转状态
         isRotating = false;
         rotationCenter = null;
         currentRotationAngle = 0.0;
         startRotationAngle = 0.0;
         rotatingControlPoint = null;
         
         clearPreviewState();
        
        // 清除所有缓存
        invalidateCalculationCache();
    }
    
    /**
     * 使计算缓存失效
     */
    public void invalidateCalculationCache() {
        calculationCacheValid = false;
        lastShapes = null;
        lastShapesHash = 0;
        lastControlPoints = null;
        lastBoundingBoxMin = null;
        lastBoundingBoxMax = null;
        LOGGER.debug("计算缓存已失效");
    }
    
    /**
     * 渲染包围盒和控制点（DrawContext版本）
     */
    public void render(DrawContext context, boolean showPreview) {
        // 渲染原始包围盒
        if (boundingBoxMin != null && boundingBoxMax != null) {
            renderBoundingBox(context);
        }
        
        // 渲染预览包围盒（如果正在拖拽）
        if (showPreview && previewBoundingBoxMin != null && previewBoundingBoxMax != null) {
            renderPreviewBoundingBox(context);
        }
        
        // 渲染控制点
        renderControlPoints(context);
        
        // 渲染预览控制点（如果正在拖拽）
        if (showPreview) {
            renderPreviewControlPoints(context);
        }
        
        // 渲染框选矩形（如果正在框选控制点）
        if (isSelecting && selectionStartPoint != null && selectionEndPoint != null) {
            context.drawRect(selectionStartPoint, selectionEndPoint, ColorConfig.SELECTION_BOX_DRAW_COLOR);
        }
    }
    
    /**
     * 使用统一渲染接口进行渲染
     * 
     * <p>这是推荐的渲染方法，通过统一的渲染接口实现渲染逻辑的分离。
     * 管理器只负责计算坐标，具体的绘制由渲染器实现。</p>
     * 
     * @param renderer 渲染器实现
     * @param camera 相机（用于坐标转换）
     * @param showPreview 是否显示预览
     */
    public void render(Renderer renderer, CanvasCamera camera, boolean showPreview) {
        try {
            if (renderer == null || camera == null) {
                LOGGER.error("渲染上下文无效：renderer={}, camera={}", renderer, camera);
                sendStatusMessage("渲染上下文无效，无法显示包围盒");
                return;
            }
            
            // 渲染原始包围盒
            if (boundingBoxMin != null && boundingBoxMax != null) {
                renderer.renderBoundingBox(boundingBoxMin, boundingBoxMax, false);
            }
            
            // 渲染预览包围盒（如果正在拖拽）
            if (showPreview && previewBoundingBoxMin != null && previewBoundingBoxMax != null) {
                renderer.renderBoundingBox(previewBoundingBoxMin, previewBoundingBoxMax, true);
            }
            
            // 渲染控制点
            for (int i = 0; i < controlPoints.length; i++) {
                Vec2d controlPoint = controlPoints[i];
                if (controlPoint != null) {
                    ControlPointType controlPointType = ControlPointType.values()[i];
                    boolean isSelected = selectedControlPoints.contains(controlPointType);
                    boolean isPrimary = primaryControlPoint == controlPointType;
                    renderer.renderControlPoint(controlPoint, i, isSelected, isPrimary);
                }
            }
            
            // 渲染预览控制点（如果正在拖拽）
            if (showPreview) {
                for (int i = 0; i < previewControlPoints.length; i++) {
                    Vec2d controlPoint = previewControlPoints[i];
                    if (controlPoint != null) {
                        renderer.renderPreviewControlPoint(controlPoint, i);
                    }
                }
            }
            
            // 渲染控制点框选矩形
            if (isSelecting && selectionStartPoint != null && selectionEndPoint != null) {
                renderer.renderSelectionBox(selectionStartPoint, selectionEndPoint);
            }
            
            // 渲染旋转图标（只在角点显示）
            if (boundingBoxMin != null && boundingBoxMax != null) {
                ControlPointType[] cornerPoints = {
                    ControlPointType.TOP_LEFT, ControlPointType.TOP_RIGHT,
                    ControlPointType.BOTTOM_RIGHT, ControlPointType.BOTTOM_LEFT
                };
                
                for (ControlPointType cornerPoint : cornerPoints) {
                    Vec2d controlPoint = controlPoints[cornerPoint.getIndex()];
                    if (controlPoint != null) {
                        Vec2d iconPosition = calculateRotationIconPosition(cornerPoint, controlPoint);
                        boolean isActive = isRotating && rotatingControlPoint == cornerPoint;
                        renderer.renderRotationIcon(iconPosition, isActive);
                    }
                }
            }
            
            // 渲染旋转弧线（如果正在旋转）
            if (isRotating && rotationCenter != null) {
                renderer.renderRotationArc(rotationCenter, ROTATION_ARC_RADIUS, 0, currentRotationAngle);
            }
            
        } catch (Exception e) {
            LOGGER.error("统一渲染失败: {}", e.getMessage(), e);
            sendStatusMessage("渲染失败，请检查图形数据");
        }
    }
    
    /**
     * 渲染包围盒和控制点（ImGui版本）
     */
    public void renderImGui(ImDrawList drawList, CanvasCamera camera, boolean showPreview) {
        try {
            if (drawList == null || camera == null) {
                LOGGER.error("渲染上下文无效：drawList={}, camera={}", drawList, camera);
                sendStatusMessage("渲染上下文无效，无法显示包围盒");
                return;
            }
            
            // 渲染原始包围盒（半透明）
            if (boundingBoxMin != null && boundingBoxMax != null) {
                Vec2d screenMin = camera.worldToScreen(boundingBoxMin);
                Vec2d screenMax = camera.worldToScreen(boundingBoxMax);
                
                float[] coords = validateAndSortScreenCoordinates(screenMin, screenMax);
                if (coords != null) {
                    // 绘制虚线矩形边框 - 拉伸模式下使用青色虚线（半透明）
                    drawDashedRectForStretch(drawList, coords[0], coords[1], coords[2], coords[3]);
                } else {
                    LOGGER.warn("坐标转换失败：screenMin={}, screenMax={}", screenMin, screenMax);
                }
            }
            
            // 渲染预览包围盒（如果正在拖拽）
            if (showPreview && previewBoundingBoxMin != null && previewBoundingBoxMax != null) {
                Vec2d screenMin = camera.worldToScreen(previewBoundingBoxMin);
                Vec2d screenMax = camera.worldToScreen(previewBoundingBoxMax);
                
                float[] coords = validateAndSortScreenCoordinates(screenMin, screenMax);
                if (coords != null) {
                    // 绘制实线矩形边框 - 预览使用橙色实线
                    drawList.addRect(coords[0], coords[1], coords[2], coords[3], ColorConfig.PREVIEW_BOX_COLOR, 0.0f, 0, 3.0f);
                } else {
                    LOGGER.warn("预览坐标转换失败：screenMin={}, screenMax={}", screenMin, screenMax);
                }
            }

            // 渲染控制点
            for (int i = 0; i < controlPoints.length; i++) {
                Vec2d controlPoint = controlPoints[i];
                if (controlPoint != null) {
                    Vec2d screenPos = camera.worldToScreen(controlPoint);
                    if (screenPos != null) {
                    int color = getControlPointColor(i);

                    // 绘制控制点（小方块）
                    float halfSize = (float) (CONTROL_POINT_SIZE / 2);
                    drawList.addRectFilled(
                        (float) screenPos.x - halfSize, (float) screenPos.y - halfSize,
                        (float) screenPos.x + halfSize, (float) screenPos.y + halfSize,
                        color
                    );
                    
                    // 绘制控制点边框
                    drawList.addRect(
                        (float) screenPos.x - halfSize, (float) screenPos.y - halfSize,
                        (float) screenPos.x + halfSize, (float) screenPos.y + halfSize,
                            ColorConfig.CONTROL_POINT_BORDER_COLOR, 1.0f
                    );
                    } else {
                        LOGGER.warn("控制点 {} 坐标转换失败", i);
                    }
                }
            }
            
            // 渲染预览控制点（如果正在拖拽）
            if (showPreview) {
                for (int i = 0; i < previewControlPoints.length; i++) {
                    Vec2d controlPoint = previewControlPoints[i];
                    if (controlPoint != null) {
                        Vec2d screenPos = camera.worldToScreen(controlPoint);
                        if (screenPos != null) {
                        // 绘制预览控制点（小方块）
                        float halfSize = (float) (CONTROL_POINT_SIZE / 2);
                        drawList.addRectFilled(
                            (float) screenPos.x - halfSize, (float) screenPos.y - halfSize,
                            (float) screenPos.x + halfSize, (float) screenPos.y + halfSize,
                                ColorConfig.PREVIEW_BOX_COLOR
                        );

                        // 绘制预览控制点边框
                        drawList.addRect(
                            (float) screenPos.x - halfSize, (float) screenPos.y - halfSize,
                            (float) screenPos.x + halfSize, (float) screenPos.y + halfSize,
                                ColorConfig.PREVIEW_BOX_COLOR, 1.0f
                        );
                        } else {
                            LOGGER.warn("预览控制点 {} 坐标转换失败", i);
                        }
                    }
                }
            }
            
                         // 渲染控制点框选矩形（ImGui版本）
             if (isSelecting && selectionStartPoint != null && selectionEndPoint != null) {
                 Vec2d screenStart = camera.worldToScreen(selectionStartPoint);
                 Vec2d screenEnd = camera.worldToScreen(selectionEndPoint);
                 
                                  float[] coords = validateAndSortScreenCoordinates(screenStart, screenEnd);
                  if (coords != null) {
                      drawList.addRect(coords[0], coords[1], coords[2], coords[3], ColorConfig.SELECTION_BOX_COLOR, 0.0f, 0, 1.0f); // 白色边框
                 } else {
                     LOGGER.warn("框选坐标转换失败：screenStart={}, screenEnd={}", screenStart, screenEnd);
                 }
             }
             
             // 渲染旋转图标（只在角点显示）
             renderRotationIcons(drawList, camera);
             
             // 渲染旋转弧线（如果正在旋转）
             if (isRotating && rotationCenter != null) {
                 renderRotationArc(drawList, camera);
             }
        } catch (NullPointerException e) {
            LOGGER.error("渲染空指针异常: {}", e.getMessage(), e);
            sendStatusMessage("渲染失败：空指针错误");
        } catch (IllegalArgumentException e) {
            LOGGER.error("渲染参数异常: {}", e.getMessage(), e);
            sendStatusMessage("渲染失败：参数错误");
        } catch (RuntimeException e) {
            // 捕获 ImGui 特定异常（如上下文失效）
            LOGGER.error("渲染运行时异常: {}", e.getMessage(), e);
            sendStatusMessage("渲染失败，请检查图形数据");
        } catch (Exception e) {
            LOGGER.error("渲染未知异常: {}", e.getMessage(), e);
            sendStatusMessage("渲染失败，请检查图形数据");
        }
    }
    
    /**
     * 渲染包围盒
     */
    private void renderBoundingBox(DrawContext context) {
        // 绘制包围盒边框 - 拉伸模式下使用虚线，颜色为青色
        context.setLineWidth(2.0f);
        
        // 绘制虚线矩形边框
        Vec2d topLeft = new Vec2d(boundingBoxMin.x, boundingBoxMax.y);
        Vec2d topRight = new Vec2d(boundingBoxMax.x, boundingBoxMax.y);
        Vec2d bottomRight = new Vec2d(boundingBoxMax.x, boundingBoxMin.y);
        Vec2d bottomLeft = new Vec2d(boundingBoxMin.x, boundingBoxMin.y);
        
        // 使用虚线绘制包围盒
        context.drawDashedLine(topLeft, topRight, ColorConfig.BOUNDING_BOX_DRAW_COLOR);
        context.drawDashedLine(topRight, bottomRight, ColorConfig.BOUNDING_BOX_DRAW_COLOR);
        context.drawDashedLine(bottomRight, bottomLeft, ColorConfig.BOUNDING_BOX_DRAW_COLOR);
        context.drawDashedLine(bottomLeft, topLeft, ColorConfig.BOUNDING_BOX_DRAW_COLOR);
        
        context.setLineWidth(1.0f); // 恢复默认线宽
    }
    
    /**
     * 渲染预览包围盒
     */
    private void renderPreviewBoundingBox(DrawContext context) {
        // 绘制预览包围盒边框 - 使用实线，颜色为橙色
        context.setLineWidth(3.0f);
        
        // 绘制实线矩形边框
        Vec2d topLeft = new Vec2d(previewBoundingBoxMin.x, previewBoundingBoxMax.y);
        Vec2d topRight = new Vec2d(previewBoundingBoxMax.x, previewBoundingBoxMax.y);
        Vec2d bottomRight = new Vec2d(previewBoundingBoxMax.x, previewBoundingBoxMin.y);
        Vec2d bottomLeft = new Vec2d(previewBoundingBoxMin.x, previewBoundingBoxMin.y);
        
        // 使用实线绘制预览包围盒
        context.drawLine(topLeft, topRight, ColorConfig.PREVIEW_BOX_DRAW_COLOR);
        context.drawLine(topRight, bottomRight, ColorConfig.PREVIEW_BOX_DRAW_COLOR);
        context.drawLine(bottomRight, bottomLeft, ColorConfig.PREVIEW_BOX_DRAW_COLOR);
        context.drawLine(bottomLeft, topLeft, ColorConfig.PREVIEW_BOX_DRAW_COLOR);
        
        context.setLineWidth(1.0f); // 恢复默认线宽
    }
    
    /**
     * 渲染预览控制点
     */
    private void renderPreviewControlPoints(DrawContext context) {
        for (Vec2d controlPoint : previewControlPoints) {
            if (controlPoint != null) {
                context.fillRect(
                    new Vec2d(controlPoint.x - CONTROL_POINT_SIZE / 2, controlPoint.y + CONTROL_POINT_SIZE / 2),
                    new Vec2d(controlPoint.x + CONTROL_POINT_SIZE / 2, controlPoint.y - CONTROL_POINT_SIZE / 2),
                    ColorConfig.PREVIEW_BOX_DRAW_COLOR
                );
                context.drawRect(
                    new Vec2d(controlPoint.x - CONTROL_POINT_SIZE / 2, controlPoint.y + CONTROL_POINT_SIZE / 2),
                    new Vec2d(controlPoint.x + CONTROL_POINT_SIZE / 2, controlPoint.y - CONTROL_POINT_SIZE / 2),
                    ColorConfig.PREVIEW_BOX_DRAW_COLOR // 使用配置的颜色
                );
            }
        }
    }
    
    /**
     * 渲染控制点
     */
    private void renderControlPoints(DrawContext context) {
        for (int i = 0; i < controlPoints.length; i++) {
            Vec2d controlPoint = controlPoints[i];
            if (controlPoint != null) {
                context.fillRect(
                    new Vec2d(controlPoint.x - CONTROL_POINT_SIZE / 2, controlPoint.y + CONTROL_POINT_SIZE / 2),
                    new Vec2d(controlPoint.x + CONTROL_POINT_SIZE / 2, controlPoint.y - CONTROL_POINT_SIZE / 2),
                    getControlPointColorForDrawContext(i)
                );
                context.drawRect(
                    new Vec2d(controlPoint.x - CONTROL_POINT_SIZE / 2, controlPoint.y + CONTROL_POINT_SIZE / 2),
                    new Vec2d(controlPoint.x + CONTROL_POINT_SIZE / 2, controlPoint.y - CONTROL_POINT_SIZE / 2),
                    ColorConfig.CONTROL_POINT_BORDER_DRAW_COLOR
                );
            }
        }
    }
    
    /**
     * 获取控制点颜色（DrawContext版本）
     */
    private Color getControlPointColorForDrawContext(int i) {
        ControlPointType controlPointType = ControlPointType.values()[i];

        // 检查悬停状态
        if (hoveredControlPoint == controlPointType) {
            return ColorConfig.CONTROL_POINT_HOVER_DRAW_COLOR; // 悬停状态显示为绿色
        }

        if (selectedControlPoints.contains(controlPointType)) {
            if (primaryControlPoint == controlPointType) {
                return ColorConfig.CONTROL_POINT_PRIMARY_DRAW_COLOR; // 主要控制点显示为红色
            } else {
                return ColorConfig.CONTROL_POINT_SECONDARY_DRAW_COLOR; // 其他选中控制点显示为橙色
            }
        } else {
            return ColorConfig.CONTROL_POINT_NORMAL_DRAW_COLOR; // 普通控制点显示为蓝色
        }
    }
    
    /**
     * 获取控制点颜色（ARGB格式）
     */
    private int getControlPointColor(int i) {
        ControlPointType controlPointType = ControlPointType.values()[i];

        // 检查悬停状态
        if (hoveredControlPoint == controlPointType) {
            return ColorConfig.CONTROL_POINT_HOVER_COLOR; // 悬停状态显示为绿色
        }

        if (selectedControlPoints.contains(controlPointType)) {
            if (primaryControlPoint == controlPointType) {
                return ColorConfig.CONTROL_POINT_SELECTED_COLOR; // 红色（主要控制点）
            } else {
                return ColorConfig.CONTROL_POINT_SECONDARY_COLOR; // 橙色（其他选中控制点）
            }
        } else {
            return ColorConfig.CONTROL_POINT_NORMAL_COLOR; // 蓝色（普通控制点）
        }
    }
    
    /**
     * 绘制虚线矩形（拉伸模式专用）
     */
    private void drawDashedRectForStretch(ImDrawList drawList, float minX, float minY, float maxX, float maxY) {
        drawDashedLineForStretch(drawList, minX, minY, maxX, minY);
        drawDashedLineForStretch(drawList, maxX, minY, maxX, maxY);
        drawDashedLineForStretch(drawList, maxX, maxY, minX, maxY);
        drawDashedLineForStretch(drawList, minX, maxY, minX, minY);
    }
    
    /**
     * 绘制虚线（拉伸模式专用）
     * 
     * <p>修复了无限循环 Bug：确保 currentPos 和 drawing 状态在每次循环中正确更新</p>
     */
    private void drawDashedLineForStretch(ImDrawList drawList, float x1, float y1, float x2, float y2) {
        float totalLength = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (totalLength < 0.1f) return;

        float dx = (x2 - x1) / totalLength;
        float dy = (y2 - y1) / totalLength;
        float lineWidth = 2.0f;

        float currentPos = 0;
        boolean drawing = true;

        while (currentPos < totalLength) {
            float segmentLength = drawing ? DASH_LENGTH : GAP_LENGTH;
            float nextPos = Math.min(currentPos + segmentLength, totalLength);

            if (drawing) {
                float startX = x1 + dx * currentPos;
                float startY = y1 + dy * currentPos;
                float endX = x1 + dx * nextPos;
                float endY = y1 + dy * nextPos;
                drawList.addLine(startX, startY, endX, endY, ColorConfig.BOUNDING_BOX_COLOR, lineWidth);
            }

            // 修复：确保这两行代码在 if 语句之外，避免无限循环
            currentPos = nextPos;
            drawing = !drawing;
        }
    }
    
    /**
     * 获取包围盒最小点
     */
    public Vec2d getBoundingBoxMin() { return boundingBoxMin; }
    
    /**
     * 获取包围盒最大点
     */
    public Vec2d getBoundingBoxMax() { return boundingBoxMax; }
    
    /**
     * 获取预览包围盒最小点
     */
    public Vec2d getPreviewBoundingBoxMin() { return previewBoundingBoxMin; }
    
    /**
     * 获取预览包围盒最大点
     */
    public Vec2d getPreviewBoundingBoxMax() { return previewBoundingBoxMax; }

    /**
     * 获取控制点数组
     */
    public Vec2d[] getControlPoints() { return controlPoints.clone(); }
    public List<ControlPointType> getSelectedControlPoints() { return new ArrayList<>(selectedControlPoints); }
    public ControlPointType getPrimaryControlPoint() { return primaryControlPoint; }
    public void setPrimaryControlPoint(ControlPointType controlPointType) { this.primaryControlPoint = controlPointType; }
    public boolean isSelecting() { return isSelecting; }
    public Vec2d getSelectionStartPoint() { return selectionStartPoint; }
    public Vec2d getSelectionEndPoint() { return selectionEndPoint; }
    
    /**
     * 验证屏幕坐标的正确性
      * 
      * <p>确保屏幕坐标满足 min <= max 的关系，避免坐标系翻转问题</p>
      * 
      * @param screenMin 屏幕最小坐标
      * @param screenMax 屏幕最大坐标
      * @return 排序后的屏幕坐标数组 [minX, minY, maxX, maxY]
      */
    private float[] validateAndSortScreenCoordinates(Vec2d screenMin, Vec2d screenMax) {
        if (screenMin == null || screenMax == null) {
            return null;
        }
        
        // 确保屏幕坐标的正确排序，避免坐标系翻转问题
        float minX = (float) Math.min(screenMin.x, screenMax.x);
        float minY = (float) Math.min(screenMin.y, screenMax.y);
        float maxX = (float) Math.max(screenMin.x, screenMax.x);
        float maxY = (float) Math.max(screenMin.y, screenMax.y);
        
        return new float[]{minX, minY, maxX, maxY};
    }

    /**
     * 计算旋转图标的位置
     * 
     * @param controlPointType 控制点类型
     * @param controlPoint 控制点位置
     * @return 旋转图标位置
     */
    private Vec2d calculateRotationIconPosition(ControlPointType controlPointType, Vec2d controlPoint) {
        // 在控制点外侧添加旋转图标
        double offsetX = 0;
        double offsetY = 0;
        
        switch (controlPointType) {
            case TOP_LEFT -> { offsetX = -ROTATION_ICON_OFFSET; offsetY = -ROTATION_ICON_OFFSET; }
            case TOP_RIGHT -> { offsetX = ROTATION_ICON_OFFSET; offsetY = -ROTATION_ICON_OFFSET; }
            case BOTTOM_RIGHT -> { offsetX = ROTATION_ICON_OFFSET; offsetY = ROTATION_ICON_OFFSET; }
            case BOTTOM_LEFT -> { offsetX = -ROTATION_ICON_OFFSET; offsetY = ROTATION_ICON_OFFSET; }
            case TOP_CENTER, BOTTOM_CENTER, CENTER_LEFT, CENTER_RIGHT -> {
                // 中点不显示旋转图标
                offsetX = 0;
                offsetY = 0;
            }
        }
        
        return new Vec2d(controlPoint.x + offsetX, controlPoint.y + offsetY);
    }

    /**
     * 是否应当渲染指定角点的旋转图示：
     * - 旋转图示开关开启，且
     * - 正在该角点旋转，或鼠标悬停在该角点附近
     */
    private boolean shouldRenderRotationIcon(ControlPointType cornerPoint) {
        if (!rotationIconsEnabled || cornerPoint == null || !cornerPoint.supportsRotation()) {
            return false;
        }
        if (isRotating && rotatingControlPoint == cornerPoint) {
            return true;
        }
        return hoveredControlPoint == cornerPoint;
    }

    /**
     * 渲染旋转图标
     */
    private void renderRotationIcons(ImDrawList drawList, CanvasCamera camera) {
        if (boundingBoxMin == null || boundingBoxMax == null || !rotationIconsEnabled) {
            return;
        }
        
        // 只渲染四个角点的旋转图标
        ControlPointType[] cornerPoints = {
            ControlPointType.TOP_LEFT,
            ControlPointType.TOP_RIGHT,
            ControlPointType.BOTTOM_RIGHT,
            ControlPointType.BOTTOM_LEFT
        };
        
        for (ControlPointType cornerPoint : cornerPoints) {
            if (!shouldRenderRotationIcon(cornerPoint)) {
                continue;
            }
            Vec2d controlPoint = controlPoints[cornerPoint.getIndex()];
            if (controlPoint == null) continue;
            
            // 计算旋转图标位置
            Vec2d iconPosition = calculateRotationIconPosition(cornerPoint, controlPoint);
            Vec2d screenPos = camera.worldToScreen(iconPosition);
            
            if (screenPos != null) {
                // 绘制旋转图标（圆形）
                float radius = (float) (ROTATION_ICON_SIZE / 2);
                drawList.addCircleFilled(
                    (float) screenPos.x, (float) screenPos.y, radius,
                    ColorConfig.ROTATION_ICON_COLOR
                );
                // 绘制旋转图标的边框
                drawList.addCircle(
                    (float) screenPos.x, (float) screenPos.y, radius,
                    ColorConfig.CONTROL_POINT_BORDER_COLOR, 0, 2.0f
                );
                
                // 绘制旋转箭头（简单的弧形）
                drawRotationArrow(drawList, screenPos, radius);
            }
        }
    }
    
    /**
     * 绘制旋转箭头
     */
    private void drawRotationArrow(ImDrawList drawList, Vec2d center, float radius) {
        // 绘制一个简单的弧形箭头表示旋转
        float outerRadius = radius * 0.9f;
        
        // 绘制弧形（使用多条线段模拟弧形）
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            float angle1 = -0.5f + (1.0f / segments) * i;
            float angle2 = -0.5f + (1.0f / segments) * (i + 1);
            
            float x1 = (float) (center.x + outerRadius * Math.cos(angle1));
            float y1 = (float) (center.y + outerRadius * Math.sin(angle1));
            float x2 = (float) (center.x + outerRadius * Math.cos(angle2));
            float y2 = (float) (center.y + outerRadius * Math.sin(angle2));
            
            drawList.addLine(x1, y1, x2, y2, ColorConfig.CONTROL_POINT_BORDER_COLOR, 2.0f);
        }
        
        // 绘制箭头头部
        float arrowAngle = 0.5f; // 30度
        float arrowX = (float) (center.x + outerRadius * Math.cos(arrowAngle));
        float arrowY = (float) (center.y + outerRadius * Math.sin(arrowAngle));
        
        // 绘制小三角形箭头
        float arrowSize = 3.0f;
        drawList.addTriangleFilled(
            arrowX, arrowY,
            arrowX - arrowSize, arrowY - arrowSize,
            arrowX - arrowSize, arrowY + arrowSize,
            ColorConfig.CONTROL_POINT_BORDER_COLOR
        );
    }
    
    /**
     * 渲染旋转弧线
     */
    private void renderRotationArc(ImDrawList drawList, CanvasCamera camera) {
        if (rotationCenter == null) {
            return;
        }
        
        Vec2d screenCenter = camera.worldToScreen(rotationCenter);
        if (screenCenter == null) {
            return;
        }
        
        float radius = (float) ROTATION_ARC_RADIUS;
        
        // 绘制旋转弧线（使用多条线段模拟弧形）
        int segments = Math.max(8, (int) Math.abs(currentRotationAngle * 4)); // 根据角度调整段数
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (currentRotationAngle * i / segments);
            float angle2 = (float) (currentRotationAngle * (i + 1) / segments);
            
            float x1 = (float) (screenCenter.x + radius * Math.cos(angle1));
            float y1 = (float) (screenCenter.y + radius * Math.sin(angle1));
            float x2 = (float) (screenCenter.x + radius * Math.cos(angle2));
            float y2 = (float) (screenCenter.y + radius * Math.sin(angle2));
            
            drawList.addLine(x1, y1, x2, y2, ColorConfig.ROTATION_ARC_COLOR, 3.0f);
        }
        
        // 绘制角度文本
        double degrees = Math.toDegrees(currentRotationAngle);
        String angleText = String.format("%.1f°", degrees);
        
        // 计算文本位置（在弧线外侧）
        float textAngle = (float) (currentRotationAngle / 2); // 在弧线中点
        float textX = (float) (screenCenter.x + (radius + 15) * Math.cos(textAngle));
        float textY = (float) (screenCenter.y + (radius + 15) * Math.sin(textAngle));
        
        // 绘制文本背景
        float textWidth = angleText.length() * 8.0f; // 估算文本宽度
        float textHeight = 16.0f;
        drawList.addRectFilled(
            textX - textWidth/2 - 4, textY - textHeight/2 - 2,
            textX + textWidth/2 + 4, textY + textHeight/2 + 2,
            0x80000000 // 半透明黑色背景
        );
        
        // 绘制文本边框（这里需要 ImGui 的文本绘制功能，暂时用矩形代替）
        drawList.addRect(
            textX - textWidth/2 - 4, textY - textHeight/2 - 2,
            textX + textWidth/2 + 4, textY + textHeight/2 + 2,
            ColorConfig.ROTATION_ANGLE_TEXT_COLOR, 0.0f, 0, 1.0f
        );
    }
}