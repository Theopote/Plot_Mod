package com.masterplanner.ui.tools.impl.modify;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.model.ICanvas;
import com.masterplanner.api.snap.ISnapManager;
import com.masterplanner.api.state.IAppState;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.log.LogManager;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.infrastructure.coordinate.CoordinateTransformer;
import com.masterplanner.infrastructure.event.EventListener;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.infrastructure.event.tool.ToolConfigEvent;
import com.masterplanner.ui.component.ToolPanelIcons;

import java.awt.Color;

/**
 * 标注工具
 * 用于在画布上标注距离、角度、半径和面积
 * 单位：画布上投影下方的方块数量
 */
public class AnnotationTool extends BaseTool {
    
    private static final String TOOL_ID = "annotation";
    private static final String TOOL_NAME = "标注";
    private static final String TOOL_DESCRIPTION = "标注距离、角度、半径和面积";
    
    /**
     * 标注模式枚举
     */
    public enum AnnotationMode {
        DISTANCE("distance", "距离"),    // 两点距离
        ANGLE("angle", "角度"),          // 角度
        RADIUS("radius", "半径"),        // 半径
        AREA("area", "面积");            // 面积（区域内方块数量）
        
        private final String id;
        private final String displayName;
        
        AnnotationMode(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() {
            return id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static AnnotationMode fromId(String id) {
            for (AnnotationMode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            return DISTANCE; // 默认返回距离模式
        }
    }
    
    // 工具状态
    private final ICanvas canvas;
    private AnnotationMode currentMode = AnnotationMode.DISTANCE;
    
    // 标注状态
    private Vec2d firstPoint = null;
    private Vec2d secondPoint = null;
    private Vec2d thirdPoint = null; // 用于角度和半径
    private boolean isMeasuring = false;
    
    // 坐标转换器
    private final CoordinateTransformer coordinateTransformer;
    
    /**
     * 构造函数（依赖注入）
     */
    public AnnotationTool(IAppState appState, ISnapManager snapManager) {
        super(TOOL_ID, TOOL_DESCRIPTION, ToolPanelIcons.ANNOTATION, TOOL_NAME);
        this.canvas = appState.getCanvas();
        this.coordinateTransformer = CoordinateTransformer.getInstance();
        
        // 监听工具配置事件
        eventBus.subscribe(ToolConfigEvent.class, new AnnotationToolConfigListener());
        
        LogManager.getInstance().debug("AnnotationTool 已创建");
    }
    
    /**
     * 工具配置监听器
     */
    private class AnnotationToolConfigListener implements EventListener {
        @Override
        public void onEvent(Event event) {
            if (!(event instanceof ToolConfigEvent configEvent)) {
                return;
            }
            
            if (!TOOL_ID.equals(configEvent.getToolId())) {
                return;
            }
            
            String key = configEvent.getConfigKey();
            String value = configEvent.getNewValue() != null ? configEvent.getNewValue().toString() : null;
            
            try {
                if ("mode".equals(key)) {
                    currentMode = AnnotationMode.fromId(value);
                    LogManager.getInstance().debug("AnnotationTool: 模式更新为 {}", currentMode.getDisplayName());
                    resetMeasurement();
                }
            } catch (Exception e) {
                LogManager.getInstance().error("AnnotationTool: 处理配置事件失败: {}", e.getMessage(), e);
            }
        }
    }
    
    @Override
    protected void onActivate() {
        super.onActivate();
        resetMeasurement();
        LogManager.getInstance().debug("AnnotationTool 已激活");
    }
    
    @Override
    protected void onDeactivate() {
        super.onDeactivate();
        resetMeasurement();
        LogManager.getInstance().debug("AnnotationTool 已停用");
    }
    
    /**
     * 重置测量状态
     */
    private void resetMeasurement() {
        firstPoint = null;
        secondPoint = null;
        thirdPoint = null;
        isMeasuring = false;
        previewShape = null;
    }
    
    @Override
    public boolean onMouseDown(Vec2d worldPoint, int button) {
        if (button == 0) { // 左键
            if (!isMeasuring) {
                // 开始新的测量
                firstPoint = worldPoint;
                isMeasuring = true;
                LogManager.getInstance().debug("AnnotationTool: 开始测量，第一点: {}", worldPoint);
                return true;
            } else {
                // 根据模式处理第二点或第三点
                switch (currentMode) {
                    case DISTANCE:
                    case RADIUS:
                        if (secondPoint == null) {
                            secondPoint = worldPoint;
                            // 完成测量
                            completeMeasurement();
                        }
                        break;
                    case ANGLE:
                        if (secondPoint == null) {
                            secondPoint = worldPoint;
                            LogManager.getInstance().debug("AnnotationTool: 第二点: {}", worldPoint);
                        } else if (thirdPoint == null) {
                            thirdPoint = worldPoint;
                            // 完成测量
                            completeMeasurement();
                        }
                        break;
                    case AREA:
                        // 面积模式需要多个点，暂时使用两点作为矩形
                        if (secondPoint == null) {
                            secondPoint = worldPoint;
                            // 完成测量
                            completeMeasurement();
                        }
                        break;
                }
                return true;
            }
        } else if (button == 1) { // 右键取消
            resetMeasurement();
            return true;
        }
        return false;
    }
    
    @Override
    public boolean onMouseMove(Vec2d pos) {
        if (isMeasuring) {
            // 更新预览
            switch (currentMode) {
                case DISTANCE:
                    if (firstPoint != null) {
                        secondPoint = pos;
                        updateDistancePreview();
                    }
                    break;
                case ANGLE:
                    if (firstPoint != null && secondPoint != null) {
                        thirdPoint = pos;
                        updateAnglePreview();
                    } else if (firstPoint != null) {
                        secondPoint = pos;
                        updateAnglePreview();
                    }
                    break;
                case RADIUS:
                    if (firstPoint != null) {
                        secondPoint = pos;
                        updateRadiusPreview();
                    }
                    break;
                case AREA:
                    if (firstPoint != null) {
                        secondPoint = pos;
                        updateAreaPreview();
                    }
                    break;
            }
            canvas.refresh();
            return true;
        }
        return false;
    }
    
    /**
     * 完成测量并创建标注
     */
    private void completeMeasurement() {
        String result = calculateMeasurement();
        LogManager.getInstance().info("AnnotationTool: 测量完成 - {}", result);
        
        // TODO: 创建标注图形并添加到画布
        // 这里可以创建一个 AnnotationShape 来显示标注结果
        
        // 重置状态，准备下一次测量
        resetMeasurement();
    }
    
    /**
     * 计算测量结果
     */
    private String calculateMeasurement() {
        switch (currentMode) {
            case DISTANCE:
                return calculateDistance();
            case ANGLE:
                return calculateAngle();
            case RADIUS:
                return calculateRadius();
            case AREA:
                return calculateArea();
            default:
                return "";
        }
    }
    
    /**
     * 计算两点距离（以方块为单位）
     */
    private String calculateDistance() {
        if (firstPoint == null || secondPoint == null) {
            return "";
        }
        
        // 将画布坐标转换为Minecraft世界坐标
        Vec2d world1 = coordinateTransformer.canvasToMinecraftWorld(firstPoint);
        Vec2d world2 = coordinateTransformer.canvasToMinecraftWorld(secondPoint);
        
        if (world1 == null || world2 == null) {
            // 如果转换失败，使用画布坐标直接计算
            double dx = secondPoint.x - firstPoint.x;
            double dy = secondPoint.y - firstPoint.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            return String.format("%.2f 方块", distance);
        }
        
        // 计算Minecraft世界坐标中的距离（以方块为单位）
        double dx = world2.x - world1.x;
        double dz = world2.y - world1.y; // 注意：y对应Z轴
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        return String.format("%.2f 方块", distance);
    }
    
    /**
     * 计算角度（度数）
     */
    private String calculateAngle() {
        if (firstPoint == null || secondPoint == null || thirdPoint == null) {
            return "";
        }
        
        // 计算三个点形成的角度
        Vec2d v1 = new Vec2d(secondPoint.x - firstPoint.x, secondPoint.y - firstPoint.y);
        Vec2d v2 = new Vec2d(thirdPoint.x - secondPoint.x, thirdPoint.y - secondPoint.y);
        
        // 计算角度（弧度）
        double dot = v1.x * v2.x + v1.y * v2.y;
        double mag1 = Math.sqrt(v1.x * v1.x + v1.y * v1.y);
        double mag2 = Math.sqrt(v2.x * v2.x + v2.y * v2.y);
        
        if (mag1 == 0 || mag2 == 0) {
            return "0°";
        }
        
        double cosAngle = dot / (mag1 * mag2);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle)); // 限制在[-1, 1]范围内
        double angleRad = Math.acos(cosAngle);
        double angleDeg = Math.toDegrees(angleRad);
        
        return String.format("%.2f°", angleDeg);
    }
    
    /**
     * 计算半径（以方块为单位）
     */
    private String calculateRadius() {
        if (firstPoint == null || secondPoint == null) {
            return "";
        }
        
        // 将画布坐标转换为Minecraft世界坐标
        Vec2d world1 = coordinateTransformer.canvasToMinecraftWorld(firstPoint);
        Vec2d world2 = coordinateTransformer.canvasToMinecraftWorld(secondPoint);
        
        if (world1 == null || world2 == null) {
            // 如果转换失败，使用画布坐标直接计算
            double dx = secondPoint.x - firstPoint.x;
            double dy = secondPoint.y - firstPoint.y;
            double radius = Math.sqrt(dx * dx + dy * dy);
            return String.format("%.2f 方块", radius);
        }
        
        // 计算Minecraft世界坐标中的半径
        double dx = world2.x - world1.x;
        double dz = world2.y - world1.y;
        double radius = Math.sqrt(dx * dx + dz * dz);
        
        return String.format("%.2f 方块", radius);
    }
    
    /**
     * 计算面积（区域内方块数量）
     */
    private String calculateArea() {
        if (firstPoint == null || secondPoint == null) {
            return "";
        }
        
        // 将画布坐标转换为Minecraft世界坐标
        Vec2d world1 = coordinateTransformer.canvasToMinecraftWorld(firstPoint);
        Vec2d world2 = coordinateTransformer.canvasToMinecraftWorld(secondPoint);
        
        if (world1 == null || world2 == null) {
            // 如果转换失败，使用画布坐标直接计算矩形面积
            double width = Math.abs(secondPoint.x - firstPoint.x);
            double height = Math.abs(secondPoint.y - firstPoint.y);
            double area = width * height;
            return String.format("%.2f 方块²", area);
        }
        
        // 计算矩形区域的面积（以方块为单位）
        double width = Math.abs(world2.x - world1.x);
        double height = Math.abs(world2.y - world1.y);
        double area = width * height;
        
        return String.format("%.2f 方块²", area);
    }
    
    /**
     * 更新距离预览
     */
    private void updateDistancePreview() {
        // TODO: 创建预览图形显示距离标注
        previewShape = null;
    }
    
    /**
     * 更新角度预览
     */
    private void updateAnglePreview() {
        // TODO: 创建预览图形显示角度标注
        previewShape = null;
    }
    
    /**
     * 更新半径预览
     */
    private void updateRadiusPreview() {
        // TODO: 创建预览图形显示半径标注
        previewShape = null;
    }
    
    /**
     * 更新面积预览
     */
    private void updateAreaPreview() {
        // TODO: 创建预览图形显示面积标注
        previewShape = null;
    }
    
    @Override
    public void render(DrawContext context) {
        // TODO: 渲染标注预览和结果
        if (previewShape != null) {
            previewShape.render(context);
        }
    }
    
    /**
     * 获取当前模式
     */
    public AnnotationMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 设置模式
     */
    public void setMode(AnnotationMode mode) {
        this.currentMode = mode;
        resetMeasurement();
    }
}
