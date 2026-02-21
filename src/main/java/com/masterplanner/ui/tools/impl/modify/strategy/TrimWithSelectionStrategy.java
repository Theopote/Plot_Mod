package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.modify.helper.TrimHandler;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.ArrayList;

/**
 * 修剪工具策略 - 重新设计版本
 *
 * <p>模仿CAD软件的修剪工具工作流程：</p>
 * 
 * <h3>点击修剪模式：</h3>
 * <ol>
 *   <li>左键选择边界图形（可多选）</li>
 *   <li>右键完成边界选择</li>
 *   <li>左键点击要修剪的图形一侧（自动检测与边界的交点并执行修剪）</li>
 * </ol>
 * 
 * <h3>栅栏修剪模式：</h3>
 * <ol>
 *   <li>左键选择要修剪的图形（可多选）</li>
 *   <li>右键完成图形选择</li>
 *   <li>左键绘制栅栏区域</li>
 *   <li>右键完成栅栏绘制，自动删除栅栏内部的图形部分</li>
 * </ol>
 *
 * @author MasterPlanner Team
 * @version 2.0 - CAD风格修剪策略
 */
public class TrimWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrimWithSelectionStrategy.class);

    // 修剪类型枚举
    public enum TrimType {
        BOUNDARY("边界修剪", "选择边界图形，然后点击要修剪的图形一侧"),
        FENCE("栅栏修剪", "选择要修剪的图形，然后绘制栅栏区域");

        private final String displayName;
        private final String description;

        TrimType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum TrimMode {
        BOUNDARY("边界修剪", "选择边界图形，然后点击要修剪的图形一侧"),
        FENCE("栅栏修剪", "定义栅栏线进行批量修剪");

        private final String displayName;
        private final String description;

        TrimMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum FenceType {
        POLYLINE("Polyline"),
        RECTANGLE("矩形"),
        CIRCLE("圆形"),
        ELLIPSE("椭圆"),
        REGULAR_POLYGON("正多边形");

        private final String displayName;

        FenceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 修剪状态枚举
    public enum TrimState {
        // 边界修剪状态
        SELECTING_BOUNDARIES("选择边界", "左键选择用作修剪边界的图形，右键完成选择"),
        WAITING_TRIM_CLICK("等待修剪", "左键点击要修剪的图形一侧"),
        BOUNDARY_READY("边界就绪", "边界已选择，可以继续修剪其他图形"),
        
        // 栅栏修剪状态
        SELECTING_TARGETS("选择目标", "左键选择要修剪的图形，右键完成选择"),
        DRAWING_FENCE("绘制栅栏", "左键绘制栅栏区域，右键完成并执行修剪"),
        FENCE_READY("栅栏就绪", "目标已选择，可以继续使用栅栏修剪"),
        
        // 通用状态
        PROCESSING("处理中", "正在执行修剪操作");

        private final String displayName;
        private final String description;

        TrimState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 状态记忆字段 - 在工具切换前保持有效
    private List<Shape> rememberedBoundaryShapes = new ArrayList<>();
    private List<Shape> rememberedTargetShapes = new ArrayList<>();
    private List<Vec2d> rememberedFencePoints = new ArrayList<>();
    private boolean isStateRemembered = false;
    
    // 新增：持续状态标志
    private boolean isBoundaryModePersistent = false; // 边界模式是否处于持续状态
    private boolean isFenceModePersistent = false;    // 栅栏模式是否处于持续状态
    
    /**
     * 记住当前状态，用于后续的修剪操作
     */
    private void rememberState() {
        if (trimType == TrimType.BOUNDARY &&
            (trimState == TrimState.SELECTING_BOUNDARIES || trimState == TrimState.WAITING_TRIM_CLICK)) {
            // 记住边界图形
            rememberedBoundaryShapes = new ArrayList<>(boundaryShapes);
            isStateRemembered = true;
            isBoundaryModePersistent = true; // 进入边界持续状态
            LOGGER.debug("已记住边界图形，数量: {}，进入持续状态", rememberedBoundaryShapes.size());
        } else if (trimType == TrimType.FENCE && trimState == TrimState.SELECTING_TARGETS) {
            // 记住目标图形
            rememberedTargetShapes = new ArrayList<>(targetShapes);
            isStateRemembered = true;
            isFenceModePersistent = true; // 进入栅栏持续状态
            LOGGER.debug("已记住目标图形，数量: {}，进入持续状态", rememberedTargetShapes.size());
        }
    }
    
    /**
     * 恢复记住的状态
     */
    private boolean restoreState() {
        if (isStateRemembered) {
            if (trimType == TrimType.BOUNDARY && !rememberedBoundaryShapes.isEmpty()) {
                // 恢复边界图形选择
                boundaryShapes.clear();
                boundaryShapes.addAll(rememberedBoundaryShapes);
                    for (Shape shape : boundaryShapes) {
                        if (shape != null && !shape.isDeleted()) {
                            shape.setSelected(true);
                        }
                    }
                    trimState = TrimState.BOUNDARY_READY;
                isBoundaryModePersistent = true;
                LOGGER.debug("已恢复边界图形，数量: {}，保持持续状态", boundaryShapes.size());
                return true;
            } else if (trimType == TrimType.FENCE && !rememberedTargetShapes.isEmpty()) {
                // 恢复目标图形选择
                targetShapes.clear();
                targetShapes.addAll(rememberedTargetShapes);
                    for (Shape shape : targetShapes) {
                        if (shape != null && !shape.isDeleted()) {
                            shape.setSelected(true);
                        }
                    }
                    trimState = TrimState.FENCE_READY;
                isFenceModePersistent = true;
                LOGGER.debug("已恢复目标图形，数量: {}，保持持续状态", targetShapes.size());
                return true;
            }
        }
        return false;
    }
    
    /**
     * 清除记住的状态
     */
    private void clearRememberedState() {
        rememberedBoundaryShapes.clear();
        rememberedTargetShapes.clear();
        rememberedFencePoints.clear();
        isStateRemembered = false;
        isBoundaryModePersistent = false;
        isFenceModePersistent = false;
        LOGGER.debug("已清除记住的状态");
    }

    // 常量
    private static final int ESC_KEY = 27;
    private static final int C_KEY = 67; // C键 - 点击修剪
    private static final int F_KEY = 70; // F键 - 栅栏修剪
    private static final double FENCE_POINT_EPSILON = 1e-6;

    // 配置参数
    private double trimTolerance = 5.0;
    private boolean previewEnabled = true;
    private boolean highlightEnabled = true;
    private boolean continuousMode = false;
    private FenceType fenceType = FenceType.POLYLINE;
    private int fencePolygonSides = 6;

    // 当前修剪类型和状态
    private TrimType trimType = TrimType.BOUNDARY;
    private TrimState trimState = TrimState.SELECTING_BOUNDARIES;

    // 点击修剪模式的状态
    private List<Shape> boundaryShapes = new ArrayList<>(); // 边界图形
    private Vec2d trimClickPoint; // 修剪点击位置

    // 栅栏修剪模式的状态
    private List<Shape> targetShapes = new ArrayList<>(); // 要修剪的目标图形
    private final List<Vec2d> fencePoints = new ArrayList<>(); // 栅栏点
    private Vec2d fenceAnchorPoint; // 参数化栅栏第一点
    private Vec2d currentMousePoint; // 当前鼠标位置

    // 修剪处理器和命令
    private TrimHandler trimHandler;
    private ModifyCommand pendingCommand;
    
    // 高亮显示
    private Shape highlightedShape;
    
    // 框选功能状态变量
    private boolean isBoxSelecting = false;
    private Vec2d boxStartPoint;
    private Vec2d boxCurrentPoint;
    private List<Shape> boxSelectedShapes = new ArrayList<>();
    private boolean isLeftToRight = true; // 框选方向：true=从左往右，false=从右往左

    /**
     * 默认构造函数
     */
    public TrimWithSelectionStrategy() {
        // 延迟初始化TrimHandler，在需要时从context获取AppState
        this.trimHandler = null;
        initializeState();
    }
    
    /**
     * 初始化状态
     */
    private void initializeState() {
        // 检查是否有记住的状态需要恢复
        if (isStateRemembered && restoreState()) {
            return;
        }
        
        // 否则初始化到默认状态
        if (trimType == TrimType.BOUNDARY) {
            trimState = TrimState.SELECTING_BOUNDARIES;
        } else {
            trimState = TrimState.SELECTING_TARGETS;
        }
        resetAllSelections();
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button == MOUSE_RIGHT) {
            return handleRightMouseDown(pos, context);
        } else if (button == MOUSE_LEFT) {
            return handleLeftMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 处理右键按下
     * CAD风格：完成当前阶段的选择或绘制
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        switch (trimState) {
            case SELECTING_BOUNDARIES -> {
                // 点击修剪：完成边界选择
                if (boundaryShapes.isEmpty()) {
                    context.setStatusMessage("请先选择边界图形");
                    return ModifyResult.CONTINUE;
                }

                if (continuousMode) {
                    // 记住边界状态，进入持续模式
                    rememberState();
                    trimState = TrimState.BOUNDARY_READY;
                    context.setStatusMessage("已选择 " + boundaryShapes.size() + " 个边界图形，点击要修剪的图形一侧（持续模式）");
                    LOGGER.debug("完成边界选择，进入持续模式");
                } else {
                    trimState = TrimState.WAITING_TRIM_CLICK;
                    context.setStatusMessage("已选择 " + boundaryShapes.size() + " 个边界图形，点击要修剪的图形一侧");
                    LOGGER.debug("完成边界选择，进入单次修剪模式");
                }
                return ModifyResult.CONTINUE;
            }
            
            case SELECTING_TARGETS -> {
                // 栅栏修剪：完成目标图形选择
                if (targetShapes.isEmpty()) {
                    context.setStatusMessage("请先选择要修剪的图形");
                    return ModifyResult.CONTINUE;
                }

                if (continuousMode) {
                    // 记住目标状态，进入持续模式
                    rememberState();
                    trimState = TrimState.FENCE_READY;
                    context.setStatusMessage("已选择 " + targetShapes.size() + " 个图形，可以继续使用栅栏修剪（持续模式）");
                    LOGGER.debug("完成目标图形选择，进入持续模式");
                } else {
                    fencePoints.clear();
                    fenceAnchorPoint = null;
                    trimState = TrimState.DRAWING_FENCE;
                    context.setStatusMessage("已选择 " + targetShapes.size() + " 个图形，开始绘制栅栏");
                    LOGGER.debug("完成目标图形选择，进入单次栅栏绘制模式");
                }
                return ModifyResult.CONTINUE;
            }
            
            case DRAWING_FENCE -> {
                // 栅栏修剪：完成栅栏绘制并执行修剪
                if (fencePoints.size() < 3) {
                    context.setStatusMessage("栅栏至少需要3个点");
                    return ModifyResult.CONTINUE;
                }

                normalizeFencePoints();
                if (fencePoints.size() < 4) {
                    context.setStatusMessage("栅栏点无效，请重新绘制");
                    return ModifyResult.CONTINUE;
                }
                
                return performFenceTrim(context);
            }
            
            case FENCE_READY -> {
                // 栅栏修剪持续模式：右键取消持续状态
                if (isFenceModePersistent) {
                    clearRememberedState();
                    reset();
                    context.setStatusMessage("栅栏持续模式已取消");
                    LOGGER.debug("用户右键取消栅栏持续模式");
                    return ModifyResult.CANCEL;
                }
                return ModifyResult.CONTINUE;
            }
            
            case WAITING_TRIM_CLICK -> {
                // 点击修剪：取消修剪，返回边界选择
                resetClickTrimState();
                context.setStatusMessage("修剪已取消，重新选择边界图形");
                return ModifyResult.CANCEL;
            }
            
            case BOUNDARY_READY -> {
                // 边界修剪持续模式：右键取消持续状态
                if (isBoundaryModePersistent) {
                    clearRememberedState();
                    reset();
                    context.setStatusMessage("边界持续模式已取消");
                    LOGGER.debug("用户右键取消边界持续模式");
                    return ModifyResult.CANCEL;
                }
                return ModifyResult.CONTINUE;
            }
            
            default -> {
                // 其他状态：重置工具
                reset();
                context.setStatusMessage(getInitialStatusMessage());
                return ModifyResult.CANCEL;
            }
        }
    }

    /**
     * 处理左键按下
     * CAD风格：选择图形或执行修剪操作
     * 支持框选功能
     */
    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        Vec2d snappedPos = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        Vec2d worldPos = toWorldPoint(pos, context);
        
        switch (trimState) {
            case SELECTING_BOUNDARIES, SELECTING_TARGETS -> {
                // 在选择模式中支持框选
                // 开始框选
                isBoxSelecting = true;
                boxStartPoint = worldPos;
                boxCurrentPoint = worldPos;
                boxSelectedShapes.clear();
                return ModifyResult.CONTINUE;
            }
            
            case WAITING_TRIM_CLICK, BOUNDARY_READY -> {
                // 点击修剪：执行修剪操作（包括持续模式）
                return performClickTrim(snappedPos, context);
            }
            
            case DRAWING_FENCE -> {
                // 栅栏修剪：添加栅栏点
                return addFencePoint(snappedPos, context);
            }
            
            case FENCE_READY -> {
                // 栅栏修剪持续模式：开始绘制栅栏
                fencePoints.clear();
                fenceAnchorPoint = null;
                trimState = TrimState.DRAWING_FENCE;
                context.setStatusMessage("开始绘制栅栏区域，左键添加点，右键完成");
                LOGGER.debug("从持续模式开始绘制栅栏");
                return ModifyResult.CONTINUE;
            }
            
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }

    /**
     * 添加栅栏点
     */
    private ModifyResult addFencePoint(Vec2d pos, ModifyToolContext context) {
        try {
            if (fenceType != FenceType.POLYLINE) {
                return addParametricFencePoint(pos, context);
            }

            if (!fencePoints.isEmpty() && arePointsNear(fencePoints.getLast(), pos)) {
                return ModifyResult.CONTINUE;
            }
            fencePoints.add(pos);
            context.setStatusMessage("栅栏点 " + fencePoints.size() + " 个，右键完成栅栏绘制");
            LOGGER.debug("添加栅栏点: {}", pos);
            return ModifyResult.CONTINUE;
        } catch (Exception e) {
            LOGGER.error("添加栅栏点失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE;
        }
    }

    private ModifyResult addParametricFencePoint(Vec2d pos, ModifyToolContext context) {
        if (fenceAnchorPoint == null || fencePoints.size() > 1) {
            fenceAnchorPoint = pos;
            fencePoints.clear();
            fencePoints.add(pos);
            context.setStatusMessage("已设置栅栏基点，左键设置第二点，右键完成");
            return ModifyResult.CONTINUE;
        }

        List<Vec2d> generated = buildFencePointsByType(fenceAnchorPoint, pos);
        if (generated.size() < 4) {
            context.setStatusMessage("栅栏范围过小，请重新设置第二点");
            return ModifyResult.CONTINUE;
        }

        fencePoints.clear();
        fencePoints.addAll(generated);
        context.setStatusMessage("已生成" + fenceType.getDisplayName() + "栅栏，右键执行修剪（左键可重设）");
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 在指定位置查找图形
     */
    private Shape findShapeAtPoint(Vec2d pos, List<Shape> shapes, ModifyToolContext context) {
        Shape nearestShape = null;
        double minDistance = Double.MAX_VALUE;
        double worldTolerance = getWorldPickTolerance(context);
        
        for (Shape shape : shapes) {
            try {
                Vec2d closestPoint = shape.getClosestPoint(pos);
                double distance;
                if (closestPoint != null) {
                    distance = closestPoint.distance(pos);
                } else {
                    distance = shape.distanceTo(pos);
                }

                if (distance <= worldTolerance && distance < minDistance) {
                    minDistance = distance;
                    nearestShape = shape;
                }
            } catch (Exception e) {
                LOGGER.debug("计算图形距离失败: {}", e.getMessage());
            }
        }
        
        return nearestShape;
    }

    private double getWorldPickTolerance(ModifyToolContext context) {
        if (context != null && context.getCamera() != null) {
            try {
                return Math.max(context.getCamera().screenToWorldDistance(trimTolerance), 1e-6);
            } catch (Exception e) {
                LOGGER.debug("转换拾取阈值失败，使用默认值: {}", e.getMessage());
            }
        }
        return Math.max(trimTolerance, 1e-6);
    }
    
        /**
     * 计算修剪后的图形
     */
    private List<Shape> calculateTrimmedShapes(Shape targetShape, Vec2d clickPos, List<Shape> boundaryShapes, ModifyToolContext context) {
        try {
            LOGGER.debug("开始计算修剪图形: 目标图形类型={}, 修剪点=({}, {}), 边界图形数量={}", 
                targetShape.getClass().getSimpleName(), clickPos.x, clickPos.y, boundaryShapes.size());
            
            // 确保TrimHandler已初始化
            if (trimHandler == null) {
                LOGGER.debug("TrimHandler未初始化，正在创建...");
                trimHandler = new TrimHandler((com.masterplanner.core.state.AppState) context.getAppState());
                LOGGER.debug("TrimHandler创建成功");
            } else {
                LOGGER.debug("TrimHandler已存在");
            }
            
            // 创建修剪参数
            ModifyParameters parameters = new ModifyParameters();
            parameters.setVec2d("trimPoint", clickPos);
            parameters.setParameter("boundaryShapes", boundaryShapes);
            
            LOGGER.debug("修剪参数创建完成，开始调用TrimHandler.calculateModifiedShapes");

            // 使用TrimHandler计算修剪结果
            List<Shape> result = trimHandler.calculateModifiedShapes(List.of(targetShape), parameters);
            LOGGER.debug("TrimHandler返回修剪结果，图形数量: {}", result.size());
            return result;
        } catch (Exception e) {
            LOGGER.error("计算修剪图形失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 创建修剪命令
     */
    private ModifyCommand createTrimCommand(Shape originalShape, List<Shape> trimmedShapes, ModifyToolContext context) {
        try {
            ModifyParameters parameters = new ModifyParameters();
            parameters.setString("trimType", "CLICK");
            parameters.setVec2d("trimPoint", trimClickPoint);
            parameters.setParameter("boundaryShapes", boundaryShapes);
            
            return trimHandler.createModifyCommand(List.of(originalShape), trimmedShapes, parameters);
        } catch (Exception e) {
            LOGGER.error("创建修剪命令失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (isBoxSelecting) {
            // 框选模式
            boxCurrentPoint = toWorldPoint(pos, context);
            updateBoxSelection(context);
            return ModifyResult.CONTINUE;
        }
        
        currentMousePoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        
        // 更新高亮显示
        updateHighlight(pos, context);
        
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 更新高亮显示
     */
    private void updateHighlight(Vec2d pos, ModifyToolContext context) {
        try {
            // 清除之前的高亮
            if (highlightedShape != null) {
                highlightedShape.setHighlighted(false);
                highlightedShape = null;
            }

            if (!highlightEnabled) {
                return;
            }

            Vec2d highlightPos = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            // 根据当前状态高亮不同的图形
            List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
            Shape nearestShape = findShapeAtPoint(highlightPos, allShapes, context);
            
            if (nearestShape != null) {
                // 避免高亮已选择的图形
                boolean shouldHighlight;
                switch (trimState) {
                    case SELECTING_BOUNDARIES, WAITING_TRIM_CLICK, BOUNDARY_READY ->
                        shouldHighlight = !boundaryShapes.contains(nearestShape);
                    case SELECTING_TARGETS, FENCE_READY ->
                        shouldHighlight = !targetShapes.contains(nearestShape);
                    default -> shouldHighlight = false;
                }
                
                if (shouldHighlight) {
                    nearestShape.setHighlighted(true);
                    highlightedShape = nearestShape;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("更新高亮失败: {}", e.getMessage());
        }
    }



    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (isBoxSelecting) {
            Vec2d worldPos = toWorldPoint(pos, context);
            double dragThreshold = context.getCamera() != null
                ? context.getCamera().screenToWorldDistance(4.0)
                : 4.0;
            // 检查是否为点选（拖动距离小于阈值）
            double dragDistance = boxStartPoint.distance(worldPos);
            if (dragDistance < dragThreshold) { // 拖动阈值
                // 点选模式：选择单个图形
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
                List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
                Shape clickedShape = findShapeAtPoint(snappedPoint, allShapes, context);
                if (clickedShape != null) {
                    boxSelectedShapes.clear();
                    switch (trimState) {
                        case SELECTING_BOUNDARIES -> {
                            if (boundaryShapes.contains(clickedShape)) {
                                boundaryShapes.remove(clickedShape);
                                clickedShape.setSelected(false);
                                context.setStatusMessage(String.format("已取消 1 个边界图形，当前 %d 个", boundaryShapes.size()));
                                return ModifyResult.CONTINUE;
                            }
                            boxSelectedShapes.add(clickedShape);
                        }
                        case SELECTING_TARGETS -> {
                            if (targetShapes.contains(clickedShape)) {
                                targetShapes.remove(clickedShape);
                                clickedShape.setSelected(false);
                                context.setStatusMessage(String.format("已取消 1 个目标图形，当前 %d 个", targetShapes.size()));
                                return ModifyResult.CONTINUE;
                            }
                            boxSelectedShapes.add(clickedShape);
                        }
                        default -> boxSelectedShapes.add(clickedShape);
                    }
                }
            } else {
                // 框选模式：应用最终选择
                boxCurrentPoint = worldPos;
                completeTrimBoxSelection(context);
            }
            
            // 重置框选状态
            isBoxSelecting = false;
            boxStartPoint = null;
            boxCurrentPoint = null;
            
            // 处理选中的图形
            if (!boxSelectedShapes.isEmpty()) {
                return handleBoxSelectedShapes(context);
            }
            return ModifyResult.CONTINUE;
        }
        
        // 修剪工具主要使用点击事件，鼠标释放事件不做特殊处理
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == ESC_KEY) {
            // 取消持续状态
            if (isBoundaryModePersistent || isFenceModePersistent) {
                clearRememberedState();
                reset();
                context.setStatusMessage("持续模式已取消");
                LOGGER.debug("用户按Esc键取消持续模式");
            } else {
                reset();
                context.setStatusMessage("操作已取消");
            }
            return ModifyResult.CANCEL;
        }

        // 处理模式切换快捷键
        return handleTrimKeyDown(keyCode, context);
    }

    /**
     * 处理修剪模式下的按键
     */
    private ModifyResult handleTrimKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case C_KEY -> {
                if (trimType != TrimType.BOUNDARY) {
                    trimType = TrimType.BOUNDARY;
                    initializeState();
                    context.setStatusMessage("切换到边界修剪模式");
                }
                return ModifyResult.CONTINUE;
            }
            case F_KEY -> {
                if (trimType != TrimType.FENCE) {
                    trimType = TrimType.FENCE;
                    initializeState();
                    context.setStatusMessage("切换到栅栏修剪模式");
                }
                return ModifyResult.CONTINUE;
            }
        }
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        return IModifyStrategy.super.onKeyUp(keyCode, context);
    }

    /**
     * 执行点击修剪操作
     */
    private ModifyResult performClickTrim(Vec2d clickPos, ModifyToolContext context) {
        try {
            LOGGER.debug("开始执行点击修剪操作，点击位置=({}, {})", clickPos.x, clickPos.y);
            
            // 查找被点击的图形
            List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
            LOGGER.debug("当前图层图形数量: {}", allShapes.size());
            Shape targetShape = findShapeAtPoint(clickPos, allShapes, context);
            
            if (targetShape == null) {
                LOGGER.debug("未找到要修剪的图形");
                context.setStatusMessage("未找到要修剪的图形");
                return ModifyResult.CONTINUE;
            }

            if (boundaryShapes.contains(targetShape)) {
                LOGGER.debug("点击的是边界图形，忽略修剪");
                context.setStatusMessage("请选择非边界图形进行修剪");
                return ModifyResult.CONTINUE;
            }
            
            LOGGER.debug("找到目标图形: 类型={}", targetShape.getClass().getSimpleName());
            
            // 直接进入修剪计算：由修剪引擎决定是否可修剪，避免前置相交判断误拒绝
            LOGGER.debug("开始基于修剪引擎计算可修剪结果，边界图形数量: {}", boundaryShapes.size());
            
            // 记录修剪点击位置
            trimClickPoint = clickPos;
            
            // 执行修剪操作
            LOGGER.debug("开始调用calculateTrimmedShapes");
            List<Shape> trimmedShapes = calculateTrimmedShapes(targetShape, clickPos, boundaryShapes, context);
            LOGGER.debug("calculateTrimmedShapes返回结果，图形数量: {}", trimmedShapes.size());
            
            if (trimmedShapes.isEmpty()) {
                LOGGER.debug("修剪失败：无法生成修剪图形");
                context.setStatusMessage("修剪失败：无法生成修剪图形");
                return ModifyResult.CONTINUE;
            }

            if (isTrimResultUnchanged(targetShape, trimmedShapes)) {
                LOGGER.debug("修剪结果未发生变化，取消提交命令");
                context.setStatusMessage("修剪结果无变化，请点击要删除的一侧");
                return ModifyResult.CONTINUE;
            }
            
            // 创建修剪命令
            LOGGER.debug("开始创建修剪命令");
            pendingCommand = createTrimCommand(targetShape, trimmedShapes, context);
            if (pendingCommand != null) {
                LOGGER.debug("修剪命令创建成功，开始执行");
                // 执行命令
                context.executeModifyCommand(pendingCommand);
                context.setStatusMessage("修剪完成");
                
                // 如果处于持续模式，回到持续状态；否则重置
                if (isBoundaryModePersistent) {
                    trimState = TrimState.BOUNDARY_READY;
                    context.setStatusMessage("修剪完成，可以继续修剪其他图形（持续模式）");
                    LOGGER.debug("修剪完成，回到持续模式");
                } else {
                    resetClickTrimState();
                }
                return ModifyResult.CONTINUE;
            }
            
            LOGGER.error("创建修剪命令失败");
            context.setStatusMessage("创建修剪命令失败");
            return ModifyResult.CANCEL;
        } catch (Exception e) {
            LOGGER.error("点击修剪操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("修剪失败: " + e.getMessage());
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 执行栅栏修剪操作
     */
    private ModifyResult performFenceTrim(ModifyToolContext context) {
        try {
            LOGGER.debug("开始执行栅栏修剪操作");
            LOGGER.debug("目标图形数量: {}, 栅栏点数: {}", targetShapes.size(), fencePoints.size());
            
            // 确保TrimHandler已初始化
            if (trimHandler == null) {
                LOGGER.debug("TrimHandler未初始化，正在创建...");
                trimHandler = new TrimHandler((com.masterplanner.core.state.AppState) context.getAppState());
                LOGGER.debug("TrimHandler创建成功");
            } else {
                LOGGER.debug("TrimHandler已存在");
            }
            
            // 创建修剪参数
            ModifyParameters parameters = new ModifyParameters();
            parameters.setString("trimType", "FENCE");
            parameters.setParameter("fencePoints", fencePoints);
            parameters.setParameter("targetShapes", targetShapes);
            parameters.setBoolean("fenceMode", true);
            
            LOGGER.debug("修剪参数创建完成，开始调用TrimHandler.calculateModifiedShapes");

            // 生成修剪图形
            List<Shape> trimmedShapes = trimHandler.calculateModifiedShapes(targetShapes, parameters);
            LOGGER.debug("TrimHandler返回修剪结果，图形数量: {}", trimmedShapes.size());
            
            if (trimmedShapes.isEmpty()) {
                context.setStatusMessage("栅栏修剪失败：无法生成修剪图形");
                return ModifyResult.CANCEL;
            }

            // 创建命令
            LOGGER.debug("开始创建修剪命令");
            pendingCommand = trimHandler.createModifyCommand(targetShapes, trimmedShapes, parameters);
            if (pendingCommand != null) {
                LOGGER.debug("修剪命令创建成功，开始执行");
                // 执行命令
                context.executeModifyCommand(pendingCommand);
                context.setStatusMessage("栅栏修剪完成");
                
                // 如果处于持续模式，回到持续状态；否则重置
                if (isFenceModePersistent) {
                    trimState = TrimState.FENCE_READY;
                    context.setStatusMessage("栅栏修剪完成，可以继续使用栅栏修剪（持续模式）");
                    LOGGER.debug("栅栏修剪完成，回到持续模式");
                } else {
                    initializeState();
                }
                return ModifyResult.CONTINUE;
            }

            context.setStatusMessage("创建栅栏修剪命令失败");
            return ModifyResult.CANCEL;
        } catch (Exception e) {
            LOGGER.error("栅栏修剪操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("栅栏修剪失败: " + e.getMessage());
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 重置所有选择
     */
    private void resetAllSelections() {
        // 清除边界图形选择状态
        for (Shape shape : boundaryShapes) {
            shape.setSelected(false);
        }
        boundaryShapes.clear();
        
        // 清除目标图形选择状态
        for (Shape shape : targetShapes) {
            shape.setSelected(false);
        }
        targetShapes.clear();
        
        // 清除框选状态
        isBoxSelecting = false;
        boxStartPoint = null;
        boxCurrentPoint = null;
        boxSelectedShapes.clear();
        
        // 清除其他状态
        fencePoints.clear();
        fenceAnchorPoint = null;
        trimClickPoint = null;
        currentMousePoint = null;
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(false);
        }
        highlightedShape = null;
        pendingCommand = null;
    }
    
    /**
     * 重置点击修剪状态（保持边界选择）
     */
    private void resetClickTrimState() {
        trimClickPoint = null;
        trimState = TrimState.WAITING_TRIM_CLICK;
    }
    
    private Vec2d toWorldPoint(Vec2d pos, ModifyToolContext context) {
        if (context != null && context.getCamera() != null && pos != null) {
            try {
                return context.getCamera().screenToWorld(pos);
            } catch (Exception e) {
                LOGGER.debug("坐标转换失败，回退原始坐标: {}", e.getMessage());
            }
        }
        return pos;
    }

    private boolean isTrimResultUnchanged(Shape originalShape, List<Shape> trimmedShapes) {
        if (trimmedShapes == null || trimmedShapes.size() != 1) {
            return false;
        }

        Shape resultShape = trimmedShapes.getFirst();
        if (resultShape == originalShape) {
            return true;
        }
        if (resultShape == null || originalShape == null) {
            return false;
        }
        if (!resultShape.getClass().equals(originalShape.getClass())) {
            return false;
        }

        final double epsilon = 1e-6;
        try {
            BoundingBox originalBounds = originalShape.getBoundingBox();
            BoundingBox resultBounds = resultShape.getBoundingBox();
            if (originalBounds == null || resultBounds == null) {
                return false;
            }

            return Math.abs(originalBounds.getMinX() - resultBounds.getMinX()) <= epsilon
                && Math.abs(originalBounds.getMinY() - resultBounds.getMinY()) <= epsilon
                && Math.abs(originalBounds.getMaxX() - resultBounds.getMaxX()) <= epsilon
                && Math.abs(originalBounds.getMaxY() - resultBounds.getMaxY()) <= epsilon;
        } catch (Exception e) {
            LOGGER.debug("无变化检测失败，按有变化处理: {}", e.getMessage());
            return false;
        }
    }

    private void normalizeFencePoints() {
        if (fencePoints.size() < 3) {
            return;
        }

        List<Vec2d> normalized = new ArrayList<>();
        for (Vec2d point : fencePoints) {
            if (point == null) {
                continue;
            }
            if (normalized.isEmpty() || !arePointsNear(normalized.getLast(), point)) {
                normalized.add(point);
            }
        }

        if (normalized.size() >= 2 && arePointsNear(normalized.getFirst(), normalized.getLast())) {
            normalized.removeLast();
        }

        fencePoints.clear();
        fencePoints.addAll(normalized);

        if (fencePoints.size() >= 3 && !arePointsNear(fencePoints.getFirst(), fencePoints.getLast())) {
            fencePoints.add(fencePoints.getFirst());
        }
    }

    private boolean arePointsNear(Vec2d a, Vec2d b) {
        if (a == null || b == null) {
            return false;
        }
        return a.distance(b) <= FENCE_POINT_EPSILON;
    }
    
    // ====== 框选相关方法 ======
    
    /**
     * 更新框选临时选择
     */
    private void updateBoxSelection(ModifyToolContext context) {
        if (boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        
        // 确定框选方向
        isLeftToRight = boxCurrentPoint.x >= boxStartPoint.x;
        
        boxSelectedShapes.clear();
        List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
        
        for (Shape shape : allShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                // CAD风格：左到右窗口选择，右到左穿越选择
                if (com.masterplanner.ui.tools.impl.modify.helper.GeometricSelectionHelper.isShapeInRectangleSelection(shape, boxStartPoint, boxCurrentPoint, isLeftToRight)) {
                    boxSelectedShapes.add(shape);
                }
            }
        }
    }
    
    /**
     * 完成框选选择
     */
    private void completeTrimBoxSelection(ModifyToolContext context) {
        updateBoxSelection(context);
        context.setStatusMessage(String.format("框选完成，已选择 %d 个图形", boxSelectedShapes.size()));
    }
    
    /**
     * 处理框选选中的图形
     */
    private ModifyResult handleBoxSelectedShapes(ModifyToolContext context) {
        switch (trimState) {
            case SELECTING_BOUNDARIES -> {
                // 将框选的图形添加到边界选择中
                for (Shape shape : boxSelectedShapes) {
                    if (!boundaryShapes.contains(shape)) {
                        boundaryShapes.add(shape);
                        shape.setSelected(true);
                    }
                }
                context.setStatusMessage(String.format("已选择 %d 个边界图形，右键确认或继续选择", boundaryShapes.size()));
                return ModifyResult.CONTINUE;
            }
            case SELECTING_TARGETS -> {
                // 将框选的图形添加到目标选择中
                for (Shape shape : boxSelectedShapes) {
                    if (!targetShapes.contains(shape)) {
                        targetShapes.add(shape);
                        shape.setSelected(true);
                    }
                }
                context.setStatusMessage(String.format("已选择 %d 个目标图形，右键确认或继续选择", targetShapes.size()));
                return ModifyResult.CONTINUE;
            }
            case WAITING_TRIM_CLICK -> {
                // 在修剪阶段，框选通常不适用，因为需要精确选择修剪位置
                context.setStatusMessage("请点击要修剪的图形位置");
                return ModifyResult.CONTINUE;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
    /**
     * 渲染框选预览
     */
    private void renderBoxSelectionPreview(DrawContext context) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        
        if (isLeftToRight) {
            // 从左往右：实线框
            context.drawRect(boxStartPoint, boxCurrentPoint, Color.WHITE);
        } else {
            // 从右往左：虚线框
            drawDashedRect(context, boxStartPoint, boxCurrentPoint);
        }
    }
    
    /**
     * 渲染框选预览（ImGui版本）
     */
    private void renderBoxSelectionPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        
        // 将世界坐标转换为屏幕坐标
        Vec2d screenStart = camera.worldToScreen(boxStartPoint);
        Vec2d screenEnd = camera.worldToScreen(boxCurrentPoint);
        
        float minX = (float) Math.min(screenStart.x, screenEnd.x);
        float minY = (float) Math.min(screenStart.y, screenEnd.y);
        float maxX = (float) Math.max(screenStart.x, screenEnd.x);
        float maxY = (float) Math.max(screenStart.y, screenEnd.y);
        
        int color = 0xFFFFFFFF; // 白色
        float lineWidth = 1.0f;
        
        if (isLeftToRight) {
            // 从左往右：实线框
            drawList.addRect(minX, minY, maxX, maxY, color, 0.0f, 0, lineWidth);
        } else {
            // 从右往左：虚线框
            drawDashedRectImGui(drawList, minX, minY, maxX, maxY, color);
        }
    }
    
    /**
     * 绘制虚线矩形
     */
    private void drawDashedRect(DrawContext context, Vec2d start, Vec2d end) {
        Vec2d topLeft = new Vec2d(Math.min(start.x, end.x), Math.min(start.y, end.y));
        Vec2d topRight = new Vec2d(Math.max(start.x, end.x), Math.min(start.y, end.y));
        Vec2d bottomLeft = new Vec2d(Math.min(start.x, end.x), Math.max(start.y, end.y));
        Vec2d bottomRight = new Vec2d(Math.max(start.x, end.x), Math.max(start.y, end.y));

        context.drawDashedLine(topLeft, topRight, Color.WHITE);
        context.drawDashedLine(topRight, bottomRight, Color.WHITE);
        context.drawDashedLine(bottomRight, bottomLeft, Color.WHITE);
        context.drawDashedLine(bottomLeft, topLeft, Color.WHITE);
    }
    
    /**
     * 绘制虚线矩形（ImGui版本）
     */
    private void drawDashedRectImGui(ImDrawList drawList, float minX, float minY, float maxX, float maxY, int color) {
        float dashLength = 8.0f;
        float gapLength = 4.0f;

        drawDashedLineImGui(drawList, minX, minY, maxX, minY, color, dashLength, gapLength);
        drawDashedLineImGui(drawList, maxX, minY, maxX, maxY, color, dashLength, gapLength);
        drawDashedLineImGui(drawList, maxX, maxY, minX, maxY, color, dashLength, gapLength);
        drawDashedLineImGui(drawList, minX, maxY, minX, minY, color, dashLength, gapLength);
    }
    
    /**
     * 绘制虚线（ImGui版本）
     */
    private void drawDashedLineImGui(ImDrawList drawList, float x1, float y1, float x2, float y2, int color, float dashLength, float gapLength) {
        float totalLength = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (totalLength < 0.1f) return;

        float dx = (x2 - x1) / totalLength;
        float dy = (y2 - y1) / totalLength;
        float lineWidth = 1.0f;

        float currentPos = 0;
        boolean drawing = true;

        while (currentPos < totalLength) {
            float segmentLength = drawing ? dashLength : gapLength;
            float nextPos = Math.min(currentPos + segmentLength, totalLength);

            if (drawing) {
                float startX = x1 + dx * currentPos;
                float startY = y1 + dy * currentPos;
                float endX = x1 + dx * nextPos;
                float endY = y1 + dy * nextPos;
                drawList.addLine(startX, startY, endX, endY, color, lineWidth);
            }

            currentPos = nextPos;
            drawing = !drawing;
        }
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("TrimWithSelectionStrategy 重置");

        // 清除持续状态
        clearRememberedState();
        
        // 重置所有选择和状态
        resetAllSelections();
        
        // 重置到初始状态
        initializeState();

        LOGGER.debug("TrimWithSelectionStrategy 重置完成，当前状态: {}", trimState);
    }

    @Override
    public String getStrategyName() {
        return "修剪选择结合策略";
    }

    @Override
    public String getStrategyDescription() {
        String baseDescription = trimState.getDescription();
        
        // 添加持续模式标识
        if (isBoundaryModePersistent && trimState == TrimState.BOUNDARY_READY) {
            return baseDescription + "（持续模式）";
        } else if (isFenceModePersistent && trimState == TrimState.FENCE_READY) {
            return baseDescription + "（持续模式）";
        }
        
        return baseDescription;
    }

    @Override
    public boolean requiresSelection() {
        return false; // 这个策略自己处理选择
    }

    @Override
    public int getMinimumSelectionCount() {
        return 0;
    }

    @Override
    public int getMaximumSelectionCount() {
        return IModifyStrategy.super.getMaximumSelectionCount();
    }

    /**
     * 获取修剪状态
     */
    public TrimState getTrimState() {
        return trimState;
    }

    /**
     * 获取修剪类型
     */
    public TrimType getTrimType() {
        return trimType;
    }
    
    /**
     * 获取兼容的修剪模式（向后兼容）
     */
    public TrimMode getTrimMode() {
        return switch (trimType) {
            case BOUNDARY -> TrimMode.BOUNDARY;
            case FENCE -> TrimMode.FENCE;
        };
    }

    /**
     * 设置修剪类型
     */
    public void setTrimType(TrimType trimType) {
        this.trimType = trimType;
        // 重置到新类型对应的初始状态
        initializeState();
    }

    /**
     * 获取选择的图形数量（兼容UI）
     */
    public int getSelectedCount() {
        if (trimType == TrimType.BOUNDARY) {
            return boundaryShapes.size();
        } else {
            return targetShapes.size();
        }
    }
    
    /**
     * 获取初始状态消息
     */
    private String getInitialStatusMessage() {
        if (trimType == TrimType.BOUNDARY) {
            return "选择边界图形，右键完成选择";
        } else {
            return "选择要修剪的图形，右键完成选择";
        }
    }

    /**
     * 获取栅栏点
     */
    public List<Vec2d> getFencePoints() {
        return new ArrayList<>(fencePoints);
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return new ArrayList<>(); // 暂时返回空列表
    }



    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        try {
            if (!previewEnabled) {
                return;
            }

            // 首先渲染框选预览
            renderBoxSelectionPreview(context);
            
            switch (trimState) {
                case DRAWING_FENCE -> renderFencePreview(context);
                case WAITING_TRIM_CLICK -> renderTrimPreview(context);
                default -> { /* 其他状态不需要特殊预览 */ }
            }
        } catch (Exception e) {
            LOGGER.debug("渲染预览失败: {}", e.getMessage());
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        try {
            if (!previewEnabled) {
                return;
            }

            // 首先渲染框选预览
            renderBoxSelectionPreviewImGui(drawList, camera);
            
            switch (trimState) {
                case DRAWING_FENCE -> renderFencePreviewImGui(drawList, camera);
                case WAITING_TRIM_CLICK -> renderTrimPreviewImGui(drawList, camera);
                default -> { /* 其他状态不需要特殊预览 */ }
            }
        } catch (Exception e) {
            LOGGER.debug("渲染ImGui预览失败: {}", e.getMessage());
        }
    }

    /**
     * 渲染修剪预览
     */
    private void renderTrimPreview(DrawContext context) {
        // 在等待修剪点击时，可以渲染一些提示信息
        // 暂时不做特殊渲染
    }
    
    /**
     * 渲染栅栏预览
     */
    private void renderFencePreview(DrawContext context) {
        List<Vec2d> previewPoints = getFencePreviewPoints();

        // 渲染栅栏点
        for (Vec2d point : previewPoints) {
            context.fillCircle(point, 4.0f, new Color(0, 255, 0, 255)); // 绿色圆点
        }

        // 渲染栅栏线
        if (previewPoints.size() >= 2) {
            renderFence(context, previewPoints);
        }

        // 渲染当前点（如果正在构建栅栏）
        if (fenceType == FenceType.POLYLINE && currentMousePoint != null && !fencePoints.isEmpty()) {
            Vec2d lastPoint = fencePoints.getLast();
            context.drawLine(lastPoint, currentMousePoint, new Color(0, 255, 0, 180)); // 绿色预览线
            context.fillCircle(currentMousePoint, 3.0f, new Color(0, 255, 0, 180)); // 绿色预览点
        }
    }

    /**
     * 渲染修剪预览（ImGui版本）
     */
    private void renderTrimPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        // 在等待修剪点击时的ImGui预览
        // 暂时不做特殊渲染
    }
    
    /**
     * 渲染栅栏预览（ImGui版本）
     */
    private void renderFencePreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            List<Vec2d> previewPoints = getFencePreviewPoints();

            // 渲染栅栏点
            for (Vec2d point : previewPoints) {
                Vec2d screenPos = camera.worldToScreen(point);
                drawList.addCircleFilled(
                    (float) screenPos.x, (float) screenPos.y, 4.0f,
                    0xFF00FF00 // 绿色
                );
            }

            if (previewPoints.size() >= 2) {
                for (int i = 0; i < previewPoints.size() - 1; i++) {
                    Vec2d start = camera.worldToScreen(previewPoints.get(i));
                    Vec2d end = camera.worldToScreen(previewPoints.get(i + 1));
                    drawList.addLine(
                        (float) start.x, (float) start.y,
                        (float) end.x, (float) end.y,
                        0xFF00FF00, 2.0f
                    );
                }
            }

            // 渲染当前鼠标预览线
            if (fenceType == FenceType.POLYLINE && currentMousePoint != null && !fencePoints.isEmpty()) {
                Vec2d lastPoint = fencePoints.getLast();
                Vec2d screenLast = camera.worldToScreen(lastPoint);
                Vec2d screenCurrent = camera.worldToScreen(currentMousePoint);
                
                drawList.addLine(
                    (float) screenLast.x, (float) screenLast.y,
                    (float) screenCurrent.x, (float) screenCurrent.y,
                    0xFF00FF00, 2.0f // 绿色线
                );
                
                drawList.addCircleFilled(
                    (float) screenCurrent.x, (float) screenCurrent.y, 3.0f,
                    0xFF00FF00 // 绿色
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染栅栏预览时出错: {}", e.getMessage());
        }
    }

    private void renderFence(DrawContext context, List<Vec2d> points) {
        if (points == null || points.size() < 2) {
            return;
        }

        // 绘制栅栏线
        Color fenceColor = new Color(0, 255, 0, 200); // 绿色半透明
        context.setLineWidth(2.0f);
        
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2d start = points.get(i);
            Vec2d end = points.get(i + 1);
            context.drawLine(start, end, fenceColor);
        }
        
        context.setLineWidth(1.0f); // 恢复默认线宽
    }

    // ====== 配置方法 ======

    /**
     * 设置修剪容差
     */
    public void setTrimTolerance(double tolerance) {
        if (tolerance > 0) {
            this.trimTolerance = tolerance;
            LOGGER.debug("修剪容差已设置为: {}", tolerance);
        } else {
            LOGGER.warn("容差值必须大于0，当前值: {}", tolerance);
        }
    }

    /**
     * 获取修剪容差
     */
    public double getTrimTolerance() {
        return trimTolerance;
    }

    /**
     * 设置预览开关
     */
    public void setPreviewEnabled(boolean enabled) {
        this.previewEnabled = enabled;
        LOGGER.debug("预览开关已设置为: {}", enabled);
    }

    /**
     * 设置高亮开关
     */
    public void setHighlightEnabled(boolean enabled) {
        this.highlightEnabled = enabled;
        LOGGER.debug("高亮开关已设置为: {}", enabled);
    }

    /**
     * 设置连续模式
     */
    public void setContinuousMode(boolean enabled) {
        this.continuousMode = enabled;
        LOGGER.debug("连续模式已设置为: {}", enabled);
    }

    public FenceType getFenceType() {
        return fenceType;
    }

    public void setFenceType(FenceType fenceType) {
        if (fenceType == null) {
            return;
        }
        this.fenceType = fenceType;
        this.fencePoints.clear();
        this.fenceAnchorPoint = null;
        LOGGER.debug("栅栏类型已设置为: {}", fenceType);
    }

    public int getFencePolygonSides() {
        return fencePolygonSides;
    }

    public void setFencePolygonSides(int sides) {
        this.fencePolygonSides = Math.max(3, Math.min(24, sides));
        LOGGER.debug("栅栏正多边形边数已设置为: {}", this.fencePolygonSides);
    }

    private List<Vec2d> getFencePreviewPoints() {
        if (fenceType == FenceType.POLYLINE) {
            return new ArrayList<>(fencePoints);
        }

        if (fencePoints.isEmpty()) {
            return new ArrayList<>();
        }

        if (fencePoints.size() == 1 && currentMousePoint != null) {
            return buildFencePointsByType(fencePoints.getFirst(), currentMousePoint);
        }

        return new ArrayList<>(fencePoints);
    }

    private List<Vec2d> buildFencePointsByType(Vec2d anchor, Vec2d reference) {
        return switch (fenceType) {
            case RECTANGLE -> buildRectangleFence(anchor, reference);
            case CIRCLE -> buildCircleFence(anchor, reference);
            case ELLIPSE -> buildEllipseFence(anchor, reference);
            case REGULAR_POLYGON -> buildRegularPolygonFence(anchor, reference, fencePolygonSides);
            case POLYLINE -> new ArrayList<>(fencePoints);
        };
    }

    private List<Vec2d> buildRectangleFence(Vec2d p1, Vec2d p2) {
        List<Vec2d> points = new ArrayList<>();
        double minX = Math.min(p1.x, p2.x);
        double minY = Math.min(p1.y, p2.y);
        double maxX = Math.max(p1.x, p2.x);
        double maxY = Math.max(p1.y, p2.y);

        if (Math.abs(maxX - minX) <= FENCE_POINT_EPSILON || Math.abs(maxY - minY) <= FENCE_POINT_EPSILON) {
            return points;
        }

        Vec2d a = new Vec2d(minX, minY);
        Vec2d b = new Vec2d(maxX, minY);
        Vec2d c = new Vec2d(maxX, maxY);
        Vec2d d = new Vec2d(minX, maxY);
        points.add(a);
        points.add(b);
        points.add(c);
        points.add(d);
        points.add(a);
        return points;
    }

    private List<Vec2d> buildCircleFence(Vec2d center, Vec2d radiusPoint) {
        List<Vec2d> points = new ArrayList<>();
        double radius = center.distance(radiusPoint);
        if (radius <= FENCE_POINT_EPSILON) {
            return points;
        }

        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double angle = (2.0 * Math.PI * i) / segments;
            points.add(new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)
            ));
        }
        if (!points.isEmpty()) {
            points.add(points.getFirst());
        }
        return points;
    }

    private List<Vec2d> buildEllipseFence(Vec2d center, Vec2d axisPoint) {
        List<Vec2d> points = new ArrayList<>();
        double radiusX = Math.abs(axisPoint.x - center.x);
        double radiusY = Math.abs(axisPoint.y - center.y);
        if (radiusX <= FENCE_POINT_EPSILON || radiusY <= FENCE_POINT_EPSILON) {
            return points;
        }

        int segments = 72;
        for (int i = 0; i < segments; i++) {
            double angle = (2.0 * Math.PI * i) / segments;
            points.add(new Vec2d(
                center.x + radiusX * Math.cos(angle),
                center.y + radiusY * Math.sin(angle)
            ));
        }
        if (!points.isEmpty()) {
            points.add(points.getFirst());
        }
        return points;
    }

    private List<Vec2d> buildRegularPolygonFence(Vec2d center, Vec2d vertexPoint, int sides) {
        List<Vec2d> points = new ArrayList<>();
        int clampedSides = Math.max(3, Math.min(24, sides));
        double radius = center.distance(vertexPoint);
        if (radius <= FENCE_POINT_EPSILON) {
            return points;
        }

        double startAngle = Math.atan2(vertexPoint.y - center.y, vertexPoint.x - center.x);
        for (int i = 0; i < clampedSides; i++) {
            double angle = startAngle + i * 2.0 * Math.PI / clampedSides;
            points.add(new Vec2d(
                center.x + radius * Math.cos(angle),
                center.y + radius * Math.sin(angle)
            ));
        }
        if (!points.isEmpty()) {
            points.add(points.getFirst());
        }
        return points;
    }

}