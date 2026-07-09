package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.graphics.DrawContext;
import com.plot.core.model.Shape;
import com.plot.ui.canvas.CanvasCamera;
import com.plot.ui.theme.ThemeManager;
import com.plot.ui.tools.impl.modify.helper.ExtendHandler;
import com.plot.ui.tools.impl.modify.dto.ExtendParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import com.plot.utils.PlotI18n;

/**
 * 延伸工具与选择功能结合策略 - 优化版本
 *
 * <p>这个策略结合了选择工具和延伸工具的功能，采用简化的状态机管理：</p>
 * <ul>
 *   <li><strong>统一状态管理</strong>：使用ExtendState统一管理所有状态，避免冗余</li>
 *   <li><strong>智能状态转换</strong>：基于右键操作在选择和延伸间智能切换</li>
 *   <li><strong>框选支持</strong>：支持框选多个图形，自动进入延伸模式</li>
 *   <li><strong>边界持久化</strong>：边界选择后保持激活状态，支持连续延伸操作</li>
 *   <li><strong>性能优化</strong>：边界图形缓存，提升多次延伸操作性能</li>
 * </ul>
 *
 * @author Plot Team
 * @version 4.0 - 状态机优化版本
 */
public class ExtendWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendWithSelectionStrategy.class);



    // 延伸模式枚举（已弃用，保留以兼容性）
    @Deprecated
    public enum ExtendMode {
        STANDARD("mode.plot.extend.mode.standard", "mode.plot.extend.mode.standard.desc"),
        PROJECT("mode.plot.extend.mode.project", "mode.plot.extend.mode.project.desc");

        private final String nameKey;
        private final String descKey;

        ExtendMode(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 延伸状态枚举 - 简化状态机
    public enum ExtendState {
        SELECTING_BOUNDARY("mode.plot.extend.state.selecting_boundary", "mode.plot.extend.state.selecting_boundary.desc"),
        EXTENDING("mode.plot.extend.state.extending", "mode.plot.extend.state.extending.desc");

        private final String nameKey;
        private final String descKey;

        ExtendState(String nameKey, String descKey) {
            this.nameKey = nameKey;
            this.descKey = descKey;
        }

        public String getDisplayName() { return PlotI18n.modeLabel(nameKey); }
        public String getDescription() { return PlotI18n.modeLabel(descKey); }
    }

    // 常量 - 使用KeyEvent常量替代硬编码值
    // 注意：ESC键需要支持两种键码格式：AWT (27) 和 GLFW (256)
    private static final int ESC_KEY_AWT = KeyEvent.VK_ESCAPE; // 27
    private static final int ESC_KEY_GLFW = 256; // GLFW格式的ESC键码
    private static final int SHIFT_KEY = KeyEvent.VK_SHIFT; // Shift键 - 多选模式
    
    /**
     * 检查是否为ESC键（支持AWT和GLFW两种键码格式）
     */
    private static boolean isEscapeKey(int keyCode) {
        return keyCode == ESC_KEY_AWT || keyCode == ESC_KEY_GLFW;
    }

    // 策略状态
    private ExtendState extendState = ExtendState.SELECTING_BOUNDARY; // 初始状态为选择边界
    private boolean isShiftPressed = false; // Shift键状态 - 多选模式

    // 延伸交互状态
    private List<Shape> boundaryShapes = new ArrayList<>(); // 边界图形
    private List<Shape> cachedBoundaryShapes; // 缓存的边界图形
    private java.util.Set<Shape> cachedBoundarySet; // 缓存的边界图形HashSet，提升contains操作性能
    private com.plot.core.spatial.SpatialIndex boundarySpatialIndex; // 边界空间索引
    private Shape highlightedShape; // 高亮图形
    private Vec2d extendPoint; // 延伸点
    private Vec2d targetPoint; // 目标点
    private Vec2d currentPoint;

    // 框选状态 - 从ExtendStrategy合并
    private boolean isBoxSelecting = false;
    private Vec2d boxStartPoint;
    private Vec2d boxCurrentPoint;
    private List<Shape> boxSelectedShapes = new ArrayList<>();

    // 延伸处理器和参数
    private ExtendHandler extendHandler;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    // 批量命令收集
    private List<Shape> pendingOriginalShapes = new ArrayList<>();
    private List<Shape> pendingModifiedShapes = new ArrayList<>();

    // 配置参数
    private double extendTolerance = 15.0; // 图形检测容差（增大以便更容易选择）
    private double endpointTolerance = 100.0; // 端点检测容差（增大以便更容易选择，特别是对于大图形）

    /**
     * 依赖注入构造函数（推荐）
     *
     * @param appState 应用状态管理器，不能为空
     */
    public ExtendWithSelectionStrategy(com.plot.core.state.AppState appState) {
        this.extendHandler = new ExtendHandler(appState); // 传入有效的 AppState

        if (appState == null) {
            LOGGER.warn("ExtendWithSelectionStrategy: AppState 为空，可能导致延伸操作失败");
        } else {
            LOGGER.debug("ExtendWithSelectionStrategy: 已成功注入 AppState");
        }
    }

    // ====== 框选相关方法 - 从ExtendStrategy合并 ======

    /**
     * 更新框选临时选择
     */
    private void updateBoxSelection(ModifyToolContext context) {
        if (boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }

        boxSelectedShapes.clear();
        List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();

        // 判断框选方向：从左到右为包含选择，从右到左为相交选择
        boolean isLeftToRight = boxStartPoint.x <= boxCurrentPoint.x;
        // 从左到右使用包含选择，从右到左使用相交选择

        for (Shape shape : allShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                // 根据框选方向选择不同的选择逻辑
                if (com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper.isShapeInRectangleSelection(
                    shape, boxStartPoint, boxCurrentPoint, isLeftToRight)) {
                    boxSelectedShapes.add(shape);
                }
            }
        }

        LOGGER.debug("框选更新完成，方向: {}, 选择模式: {}, 选中图形数: {}",
            isLeftToRight ? "左到右" : "右到左",
            isLeftToRight ? "包含选择" : "相交选择",
            boxSelectedShapes.size());
    }

    /**
     * 完成框选操作
     */
    protected void finalizeBoxSelection(ModifyToolContext context) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }

        LOGGER.debug("开始完成框选操作，当前状态: {}, 框选结果数量: {}", extendState, boxSelectedShapes.size());

        // 直接处理框选结果，避免调用handleBoxSelectedShapes
        if (!isShiftPressed) {
            // 没有按Shift键：清空之前的选择，替换为新的框选结果
            super.clearSelection(context);
            LOGGER.debug("框选模式：替换选择");
        } else {
            // 按了Shift键：添加到现有选择中
            LOGGER.debug("框选模式：添加到选择");
        }

        // 添加框选结果，避免重复
        int addedCount = 0;
        for (Shape shape : boxSelectedShapes) {
            if (!selectedShapeIds.contains(shape.getId())) {
                selectedShapeIds.add(shape.getId());
                super.updateShapeSelection(shape, true, context);
                addedCount++;
            }
        }

        // 更新状态消息
        if (isShiftPressed) {
            context.setStatusMessage(String.format("已添加到选择，新增 %d 个图形，总计 %d 个",
                addedCount, selectedShapeIds.size()));
        } else {
            context.setStatusMessage(String.format("已选择 %d 个图形", selectedShapeIds.size()));
        }

        // 重置框选状态
        isBoxSelecting = false;
        boxStartPoint = null;
        boxCurrentPoint = null;

        // 自动切换模式：如果框选后有选中的图形，自动进入延伸模式
        if (!selectedShapeIds.isEmpty() && extendState == ExtendState.SELECTING_BOUNDARY) {
            extendState = ExtendState.EXTENDING;
            selectedShapes = getSelectedShapesFromIds(context);
            boundaryShapes = new ArrayList<>(selectedShapes);
            cacheBoundaryShapes(context); // 缓存边界图形，提升性能

            // 框选成功消息
            String boxSelectMessage = String.format(
                "框选完成！已选择 %d 个边界图形，自动进入延伸模式。点击要延伸的图形端点执行延伸，ESC重新选择边界",
                boundaryShapes.size());
            context.setStatusMessage(boxSelectMessage);
            LOGGER.info("框选后自动切换到延伸模式，边界图形数量: {}", boundaryShapes.size());
        }

        LOGGER.debug("框选操作完成，新状态: {}, 边界图形数量: {}", extendState, boundaryShapes.size());
    }



    /**
     * 渲染框选预览
     */
    private void renderBoxSelectionPreview(DrawContext context) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        Color selectionColor = toColor(ThemeManager.getInstance().getCurrentTheme().text);

        // 判断框选方向：从左到右为实线，从右到左为虚线
        boolean isLeftToRight = boxStartPoint.x <= boxCurrentPoint.x;

        if (isLeftToRight) {
            // 从左到右：实线框
            context.drawRect(boxStartPoint, boxCurrentPoint, selectionColor);
        } else {
            // 从右到左：虚线框
            drawDashedRect(context, boxStartPoint, boxCurrentPoint);
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
        Color selectionColor = toColor(ThemeManager.getInstance().getCurrentTheme().text);

        context.drawDashedLine(topLeft, topRight, selectionColor);
        context.drawDashedLine(topRight, bottomRight, selectionColor);
        context.drawDashedLine(bottomRight, bottomLeft, selectionColor);
        context.drawDashedLine(bottomLeft, topLeft, selectionColor);
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        LOGGER.debug("===== ExtendWithSelectionStrategy.onMouseDown 被调用 =====");
        LOGGER.debug("鼠标按下，位置: {}, 按钮: {}, 当前状态: {}, 框选状态: {}", pos, button, extendState, isBoxSelecting);

        if (button == MOUSE_LEFT) {
            // 在边界选择模式和延伸模式下都支持框选
            if (extendState == ExtendState.SELECTING_BOUNDARY) {
                isBoxSelecting = true;
                boxStartPoint = pos;
                boxCurrentPoint = pos;
                boxSelectedShapes.clear();
                LOGGER.debug("开始边界框选，设置框选状态为true");
            } else if (extendState == ExtendState.EXTENDING) {
                // 在延伸模式下，先设置框选状态，但会在鼠标移动时判断是否为真正的框选
                isBoxSelecting = true;
                boxStartPoint = pos;
                boxCurrentPoint = pos;
                boxSelectedShapes.clear();
                LOGGER.debug("开始延伸目标框选，设置框选状态为true");
            } else {
                LOGGER.debug("其他状态下左键按下，不启动框选");
            }
            return ModifyResult.CONTINUE;
        } else if (button == MOUSE_RIGHT) {
            LOGGER.debug("右键按下，调用handleRightMouseDown");
            return handleRightMouseDown(pos, context);
        }
        return ModifyResult.IGNORED;
    }

    /**
     * 缓存边界图形，提升多次延伸操作的性能
     *
     * <p><strong>性能优化：</strong>同时缓存List和HashSet，避免高频操作中的重复创建：</p>
     * <ul>
     *   <li>List缓存：用于顺序访问和空间索引创建</li>
     *   <li>HashSet缓存：用于contains操作，提升过滤效率</li>
     *   <li>空间索引：用于高效的几何查询</li>
     * </ul>
     */
    private void cacheBoundaryShapes(ModifyToolContext context) {
        try {
            // 缓存边界图形列表
            cachedBoundaryShapes = new ArrayList<>(boundaryShapes);

            // 缓存边界图形HashSet，提升contains操作性能
            cachedBoundarySet = new java.util.HashSet<>(boundaryShapes);

            // 尝试获取空间索引并创建子集
            try {
                com.plot.core.spatial.SpatialIndex mainSpatialIndex = null;
                // 尝试从AppState获取空间索引
                if (context.getAppState() instanceof com.plot.core.state.AppState appState) {
                    mainSpatialIndex = appState.getSpatialIndex();
                }

                if (mainSpatialIndex != null) {
                    // 创建边界图形的空间索引子集
                    boundarySpatialIndex = createBoundarySpatialIndex(cachedBoundaryShapes);
                } else {
                    boundarySpatialIndex = null;
                    LOGGER.debug("主空间索引不可用，跳过边界空间索引创建");
                }
            } catch (Exception e) {
                LOGGER.debug("获取空间索引失败: {}", e.getMessage());
                boundarySpatialIndex = null;
            }

            LOGGER.debug("缓存边界图形完成，List数量: {}, HashSet数量: {}, 空间索引: {}",
                cachedBoundaryShapes.size(), cachedBoundarySet.size(),
                boundarySpatialIndex != null ? "已创建" : "未创建");
        } catch (Exception e) {
            LOGGER.warn("缓存边界图形失败: {}", e.getMessage());
            cachedBoundaryShapes = new ArrayList<>(boundaryShapes);
            cachedBoundarySet = new java.util.HashSet<>(boundaryShapes);
            boundarySpatialIndex = null;
        }
    }

    /**
     * 创建边界图形的空间索引子集
     *
     * <p>为边界图形创建专门的空间索引，提升延伸计算的性能：</p>
     * <ul>
     *   <li>计算边界图形的总包围盒</li>
     *   <li>创建四叉树空间索引</li>
     *   <li>插入所有边界图形</li>
     *   <li>提供高效的边界查询</li>
     * </ul>
     *
     * @param boundaries 边界图形列表
     * @return 边界图形的空间索引，如果创建失败则返回null
     */
    private com.plot.core.spatial.SpatialIndex createBoundarySpatialIndex(List<Shape> boundaries) {
        try {
            if (boundaries == null || boundaries.isEmpty()) {
                LOGGER.debug("边界图形列表为空，跳过空间索引创建");
                return null;
            }

            // 计算所有边界图形的总包围盒
            com.plot.api.geometry.IBoundingBox bounds = calculateBoundaryBounds(boundaries);
            if (bounds == null) {
                LOGGER.warn("无法计算边界图形的包围盒，跳过空间索引创建");
                return null;
            }

            // 创建四叉树空间索引
            com.plot.core.spatial.SpatialIndex boundaryIndex =
                new com.plot.core.spatial.QuadtreeSpatialIndex(bounds);

            // 插入所有边界图形到空间索引
            int insertedCount = 0;
            for (Shape boundary : boundaries) {
                try {
                    if (boundary != null && boundary.isVisible() && !boundary.isDeleted()) {
                        boundaryIndex.insert(boundary);
                        insertedCount++;
                    }
                } catch (Exception e) {
                    LOGGER.debug("插入边界图形到空间索引失败: {}, 图形ID: {}", e.getMessage(), boundary.getId());
                }
            }

            if (insertedCount > 0) {
                LOGGER.debug("成功创建包含 {} 个图形的边界空间索引，总边界图形数: {}",
                    insertedCount, boundaries.size());
                return boundaryIndex;
            } else {
                LOGGER.warn("没有成功插入任何边界图形到空间索引");
                return null;
            }

        } catch (Exception e) {
            LOGGER.warn("创建边界空间索引失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 计算边界图形的总包围盒 - 重构版本，使用现有的BoundingBox类
     *
     * @param boundaries 边界图形列表
     * @return 总包围盒，如果计算失败则返回null
     */
    private com.plot.api.geometry.IBoundingBox calculateBoundaryBounds(List<Shape> boundaries) {
        try {
            if (boundaries == null || boundaries.isEmpty()) {
                return null;
            }

            // 使用现有的BoundingBox.createFromShapes方法
            com.plot.core.geometry.BoundingBox baseBounds =
                com.plot.core.geometry.BoundingBox.createFromShapes(boundaries);

            if (baseBounds == null) {
                LOGGER.warn("没有找到有效的边界图形包围盒");
                return null;
            }

            // 计算扩展边距：5%的边距，最小10单位
            double width = baseBounds.getWidth();
            double height = baseBounds.getHeight();
            double margin = Math.max(10.0, Math.max(width, height) * 0.05);

            // 使用BoundingBox的expand方法创建扩展后的包围盒
            return baseBounds.expand(margin);

        } catch (Exception e) {
            LOGGER.error("计算边界图形总包围盒失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 处理右键按下
     * 用于在边界选择和延伸模式之间切换
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("右键按下，当前状态: {}, 选中图形数量: {}, 边界图形数量: {}",
            extendState, selectedShapeIds.size(), boundaryShapes.size());

        if (extendState == ExtendState.SELECTING_BOUNDARY) {
            // 在边界选择模式下右键：确认边界选择，进入延伸模式
            if (!selectedShapeIds.isEmpty()) {
                selectedShapes = getSelectedShapesFromIds(context);
                boundaryShapes = new ArrayList<>(selectedShapes);
                cacheBoundaryShapes(context); // 缓存边界图形，提升性能
                extendState = ExtendState.EXTENDING;

                // 状态消息
                String confirmMessage = String.format(
                    "边界已确认！已选择 %d 个边界图形，点击要延伸的图形端点执行延伸，ESC重新选择边界",
                    boundaryShapes.size());
                context.setStatusMessage(confirmMessage);
                LOGGER.info("右键确认边界选择，进入延伸模式，边界图形数量: {}", boundaryShapes.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("status.plot.extend.select_boundary_empty");
                return ModifyResult.NEED_SELECTION;
            }
        } else if (extendState == ExtendState.EXTENDING) {
            // 在延伸模式下，右键不执行任何操作，避免干扰延伸操作
            LOGGER.debug("在延伸模式下右键，不执行任何操作");
            return ModifyResult.IGNORED;
        }

        LOGGER.debug("右键按下未处理，返回IGNORED");
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (isBoxSelecting) {
            // 框选模式
            boxCurrentPoint = pos;
            updateBoxSelection(context);
            return ModifyResult.CONTINUE;
        }

        // 统一基于ExtendState处理逻辑
        return switch (extendState) {
            case SELECTING_BOUNDARY -> handleSelectionMouseMove(pos, context);
            case EXTENDING -> handleExtendMouseMove(pos, context);
        };
    }

    /**
     * 处理延伸模式下的鼠标移动
     */
    private ModifyResult handleExtendMouseMove(Vec2d pos, ModifyToolContext context) {
        try {
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 在延伸模式下，只有当鼠标在可延伸的图形上或附近时才更新预览
            if (!boundaryShapes.isEmpty()) {
                // 先查找鼠标位置下的图形
                Shape shapeAtPoint = context.findShapeAt(currentPoint, extendTolerance);
                
                if (shapeAtPoint != null && !boundaryShapes.contains(shapeAtPoint)) {
                    // 找到了非边界图形，尝试查找延伸目标
                    ExtendTargetInfo targetInfo = findExtendTarget(currentPoint, context);
                    if (targetInfo != null) {
                        updateExtendPreview(currentPoint, context);
                        context.setPreviewEnabled(true);
                    } else {
                        // 鼠标在图形上但无法延伸（例如不在端点附近），清除预览
                        clearPreview();
                        context.setPreviewEnabled(false);
                    }
                } else {
                    // 鼠标不在可延伸图形上，清除预览
                    clearPreview();
                    context.setPreviewEnabled(false);
                }
            } else {
                // 没有边界图形，清除预览
                clearPreview();
                context.setPreviewEnabled(false);
            }
            return ModifyResult.CONTINUE;
        } catch (Exception e) {
            LOGGER.debug("延伸预览失败: {}", e.getMessage());
            clearPreview();
            context.setPreviewEnabled(false);
            return ModifyResult.CONTINUE;
        }
    }
    
    /**
     * 清除预览状态
     */
    private void clearPreview() {
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(false);
            highlightedShape = null;
        }
        extendPoint = null;
        targetPoint = null;
        previewShapes = null;
    }

    // 常量定义
    private static final double DRAG_CLICK_THRESHOLD = 4.0; // 拖动阈值，小于此值视为点选

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        LOGGER.debug("===== ExtendWithSelectionStrategy.onMouseUp 被调用 =====");
        LOGGER.debug("鼠标释放，位置: {}, 按钮: {}, 当前状态: {}, 框选状态: {}", pos, button, extendState, isBoxSelecting);

        if (button == MOUSE_LEFT) {
            return handleLeftMouseUp(pos, context);
        } else if (button == MOUSE_RIGHT) {
            return handleRightMouseUp(pos, context);
        }

        return ModifyResult.IGNORED;
    }

    /**
     * 处理左键释放
     */
    private ModifyResult handleLeftMouseUp(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("handleLeftMouseUp: 当前状态 = {}, 位置 = {}", extendState, pos);
        // 根据当前状态处理左键释放
        return switch (extendState) {
            case SELECTING_BOUNDARY -> {
                LOGGER.debug("调用 handleBoundarySelectionLeftMouseUp");
                yield handleBoundarySelectionLeftMouseUp(pos, context);
            }
            case EXTENDING -> {
                LOGGER.debug("调用 handleExtendTargetLeftMouseUp");
                yield handleExtendTargetLeftMouseUp(pos, context);
            }
        };
    }

    /**
     * 处理边界选择模式下的左键释放
     */
    private ModifyResult handleBoundarySelectionLeftMouseUp(Vec2d pos, ModifyToolContext context) {
        try {
            if (isBoxSelecting && boxStartPoint != null) {
                // 计算拖动距离，判断是点选还是框选
                double dragDistance = boxStartPoint.distance(pos);

                if (dragDistance < DRAG_CLICK_THRESHOLD) {
                    // 点选模式：选择单个边界图形
                    return handleBoundaryPointSelection(pos, context);
                } else {
                    // 框选模式：应用最终选择
                    return handleBoundaryBoxSelection(context);
                }
            } else {
                // 如果没有启动框选（可能是在非边界选择模式下），直接执行点选
                return handleBoundaryPointSelection(pos, context);
            }
        } finally {
            // 无论成功失败，都要重置框选状态
            resetBoxSelectionState();
        }
    }

    /**
     * 处理延伸目标选择模式下的左键释放
     */
    private ModifyResult handleExtendTargetLeftMouseUp(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("===== handleExtendTargetLeftMouseUp 被调用 =====");
        LOGGER.debug("位置: {}, 边界图形数量: {}, 框选状态: {}", pos, boundaryShapes.size(), isBoxSelecting);

        if (boundaryShapes.isEmpty()) {
            LOGGER.warn("边界图形列表为空");
            context.setStatusMessage("status.plot.extend.select_boundary");
            return ModifyResult.IGNORED;
        }

        try {
            if (isBoxSelecting && boxStartPoint != null) {
                // 计算拖动距离，判断是点选还是框选
                double dragDistance = boxStartPoint.distance(pos);
                LOGGER.debug("延伸模式左键释放，拖动距离: {}, 阈值: {}", dragDistance, DRAG_CLICK_THRESHOLD);

                if (dragDistance < DRAG_CLICK_THRESHOLD) {
                    // 点选模式：直接执行延伸
                    LOGGER.debug("检测到点选模式，执行延伸点选");
                    return handleExtendPointSelection(pos, context);
                } else {
                    // 框选模式：框选多个延伸目标并执行延伸
                    LOGGER.debug("检测到框选模式，执行延伸框选");
                    return handleExtendBoxSelection(context);
                }
            } else {
                // 如果没有启动框选，直接执行点选延伸
                LOGGER.debug("没有框选状态，直接执行点选延伸");
                return handleExtendPointSelection(pos, context);
            }
        } finally {
            // 无论成功失败，都要重置框选状态
            resetBoxSelectionState();
        }
    }

    /**
     * 处理延伸点选
     */
    private ModifyResult handleExtendPointSelection(Vec2d pos, ModifyToolContext context) {
        try {
            LOGGER.debug("===== handleExtendPointSelection 开始 =====");
            LOGGER.debug("点击位置: {}, 边界图形数量: {}", pos, boundaryShapes.size());

            // 只有当存在预览时才允许执行延伸操作
            if (previewShapes == null || previewShapes.isEmpty() || extendPoint == null) {
                LOGGER.debug("没有预览图形，不允许执行延伸操作");
                context.setStatusMessage("status.plot.extend.hover_shape");
                return ModifyResult.CONTINUE;
            }

            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            LOGGER.debug("延伸点选，吸附后的点: {}", snappedPoint);

            // 查找延伸目标
            ExtendTargetInfo targetInfo = findExtendTarget(snappedPoint, context);
            if (targetInfo != null) {
                LOGGER.debug("找到延伸目标: 图形ID = {}, 延伸点 = {}",
                    targetInfo.targetShape.getId(), targetInfo.extendPoint);
                // 执行延伸操作
                ModifyResult result = performExtend(targetInfo, context);
                LOGGER.debug("延伸操作结果: {}", result);
                return result;
            } else {
                LOGGER.debug("未找到可延伸的目标，点击位置: {}", snappedPoint);
                context.setStatusMessage("status.plot.extend.hover_and_click");
                return ModifyResult.CONTINUE;
            }
        } catch (Exception e) {
            LOGGER.error("延伸操作失败: {}", e.getMessage(), e);
            context.setStatusMessage(PlotI18n.status("status.plot.extend.failed", e.getMessage()));
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理延伸框选
     */
    private ModifyResult handleExtendBoxSelection(ModifyToolContext context) {
        try {
            LOGGER.debug("处理延伸框选，框选图形数量: {}", boxSelectedShapes.size());

            if (boxSelectedShapes.isEmpty()) {
                context.setStatusMessage("status.plot.extend.box_no_shapes");
                return ModifyResult.CONTINUE;
            }

            // 对每个框选的图形执行延伸
            // 创建副本以避免在遍历时修改列表导致的ConcurrentModificationException
            List<Shape> shapesToExtend = new ArrayList<>(boxSelectedShapes);
            int successCount = 0;
            for (Shape shape : shapesToExtend) {
                try {
                    // 查找图形的延伸点
                    ExtendTargetInfo targetInfo = findExtendTargetForShape(shape, context);
                    if (targetInfo != null) {
                        // 执行延伸操作
                        ModifyResult result = performExtend(targetInfo, context);
                        if (result == ModifyResult.COMPLETE) {
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("延伸图形失败: {}, 图形ID: {}", e.getMessage(), shape.getId());
                }
            }

            if (successCount > 0) {
                // 延伸框选成功后自动重置状态
                resetExtendState();
                extendState = ExtendState.SELECTING_BOUNDARY;
                
                String message = String.format("延伸框选完成！成功延伸 %d 个图形，工具已重置，请重新选择边界图形开始下一次延伸", successCount);
                context.setStatusMessage(message);
                LOGGER.debug("延伸框选完成，工具已重置，准备下一次延伸");
                return ModifyResult.COMPLETE;
            } else {
                context.setStatusMessage("status.plot.extend.box_no_endpoints");
                return ModifyResult.CONTINUE;
            }
        } catch (Exception e) {
            LOGGER.error("延伸框选操作失败: {}", e.getMessage(), e);
            context.setStatusMessage(PlotI18n.status("status.plot.extend.box_failed", e.getMessage()));
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 为特定图形查找延伸目标
     */
    private ExtendTargetInfo findExtendTargetForShape(Shape shape, ModifyToolContext context) {
        try {
            // 获取图形的端点
            List<Vec2d> endpoints = shape.getEndpoints();
            if (endpoints.isEmpty()) {
                return null;
            }

            // 为每个端点查找最佳延伸目标
            ExtendTargetInfo bestTarget = null;
            double bestDistance = Double.MAX_VALUE;

            for (Vec2d endpoint : endpoints) {
                ExtendTargetInfo targetInfo = findExtendTarget(endpoint, context);
                if (targetInfo != null) {
                    double distance = endpoint.distance(targetInfo.extendPoint);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestTarget = targetInfo;
                    }
                }
            }

            return bestTarget;
        } catch (Exception e) {
            LOGGER.warn("查找图形延伸目标失败: {}, 图形ID: {}", e.getMessage(), shape.getId());
            return null;
        }
    }

    /**
     * 处理边界点选
     */
    private ModifyResult handleBoundaryPointSelection(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("检测到边界点选模式，拖动距离: {}", boxStartPoint != null ? boxStartPoint.distance(pos) : "null");

        // 获取吸附后的点
        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        // 查找点击的图形
        Shape clickedShape = context.findShapeAt(snappedPoint, extendTolerance);
        if (clickedShape != null) {
            return handleBoundaryShapeSelection(clickedShape, context);
        } else {
            LOGGER.debug("点选失败，未找到图形，位置: {}", snappedPoint);
            context.setStatusMessage("status.plot.extend.shape_not_found");
            return ModifyResult.CONTINUE;
        }
    }

    /**
     * 处理边界框选
     */
    private ModifyResult handleBoundaryBoxSelection(ModifyToolContext context) {
        LOGGER.debug("检测到边界框选模式，执行框选完成");

        try {
            // 应用最终框选结果
            finalizeBoxSelection(context);
            LOGGER.debug("边界框选完成，当前状态: {}", extendState);
            return ModifyResult.COMPLETE;
        } catch (Exception e) {
            LOGGER.error("边界框选完成失败: {}", e.getMessage(), e);
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理边界图形选择
     */
    private ModifyResult handleBoundaryShapeSelection(Shape clickedShape, ModifyToolContext context) {
        // 智能点选逻辑：根据Shift键状态决定是替换还是添加选择
        if (!isShiftPressed) {
            // 没有按Shift键：清空之前的选择，替换为新的点选结果
            super.clearSelection(context);
            LOGGER.debug("边界点选模式：替换选择");
        } else {
            // 按了Shift键：添加到现有选择中
            LOGGER.debug("边界点选模式：添加到选择");
        }

        // 添加点选结果
        if (!selectedShapeIds.contains(clickedShape.getId())) {
            selectedShapeIds.add(clickedShape.getId());
            super.updateShapeSelection(clickedShape, true, context);

            // 更新状态消息
            if (isShiftPressed) {
                String addMessage = String.format(
                    "已添加边界图形 %s，总计 %d 个边界图形，右键确认选择",
                    clickedShape.getId(), selectedShapeIds.size());
                context.setStatusMessage(addMessage);
            } else {
                String selectMessage = String.format(
                    "已选择边界图形 %s，总计 %d 个边界图形，右键确认选择",
                    clickedShape.getId(), selectedShapeIds.size());
                context.setStatusMessage(selectMessage);
            }
        } else if (isShiftPressed) {
            // Shift+点击已选中的图形：取消选择
            selectedShapeIds.remove(clickedShape.getId());
            super.updateShapeSelection(clickedShape, false, context);
            String deselectMessage = String.format(
                "已取消选择边界图形 %s，总计 %d 个边界图形",
                clickedShape.getId(), selectedShapeIds.size());
            context.setStatusMessage(deselectMessage);
        }

        return ModifyResult.CONTINUE;
    }

    /**
     * 处理右键释放
     */
    private ModifyResult handleRightMouseUp(Vec2d pos, ModifyToolContext context) {
        // 右键释放不执行任何操作，避免与右键按下冲突
        return ModifyResult.IGNORED;
    }

    /**
     * 重置框选状态
     */
    private void resetBoxSelectionState() {
        isBoxSelecting = false;
        boxStartPoint = null;
        boxCurrentPoint = null;
        LOGGER.debug("框选状态已重置");
    }

    /**
     * 执行延伸操作 - 优化版本，支持批量命令收集
     */
    private ModifyResult performExtend(ExtendTargetInfo targetInfo, ModifyToolContext context) {
        try {
            LOGGER.debug("开始执行延伸操作，目标图形: {}, 延伸点: {}, 自动模式",
                targetInfo.targetShape.getId(), targetInfo.extendPoint);

            // 创建延伸参数（自动模式）
            ExtendParameters parameters = new ExtendParameters(
                targetInfo.extendPoint,
                getEffectiveBoundaries(), // 使用有效的边界图形
                extendTolerance,
                endpointTolerance
            );

            // 执行延伸操作
            List<Shape> modifiedShapes = extendHandler.calculateModifiedShapes(
                List.of(targetInfo.targetShape), parameters);

            if (modifiedShapes != null && !modifiedShapes.isEmpty()) {
                // 立即执行延伸操作，让用户看到效果
                ModifyCommand command = extendHandler.createModifyCommand(
                    List.of(targetInfo.targetShape),
                    modifiedShapes,
                    parameters
                );

                if (command != null) {
                    // 立即执行命令，让用户看到延伸效果
                    context.executeModifyCommand(command);

                    // 延伸成功后自动重置状态，开始下一次延伸
                    resetExtendState();
                    extendState = ExtendState.SELECTING_BOUNDARY;

                    // 动态状态消息：提示用户延伸完成并已重置
                    String successMessage = String.format(
                        "延伸成功！图形 %s 已延伸，工具已重置，请重新选择边界图形开始下一次延伸",
                        targetInfo.targetShape.getId());
                    context.setStatusMessage(successMessage);

                    LOGGER.debug("延伸操作完成并立即执行，工具已重置，准备下一次延伸");
                    // 返回COMPLETE表示操作完成，状态已重置
                    return ModifyResult.COMPLETE;
                } else {
                    LOGGER.warn("创建延伸命令失败");
                    context.setStatusMessage("status.plot.extend.command_failed");
                    return ModifyResult.CANCEL;
                }
            } else {
                // 更具体的失败消息
                String failureMessage = String.format(
                    "延伸失败：无法计算延伸结果，请确保边界有效（当前边界数: %d）",
                    getEffectiveBoundaries().size());
                context.setStatusMessage(failureMessage);
                LOGGER.warn("延伸操作失败：无法计算修改后的图形");
                return ModifyResult.CANCEL;
            }
        } catch (Exception e) {
            LOGGER.error("执行延伸操作时出错: {}", e.getMessage(), e);

            // 更具体的错误消息
            String errorMessage = String.format(
                "延伸操作失败: %s，边界数: %d，请检查边界图形是否有效",
                e.getMessage(), getEffectiveBoundaries().size());
            context.setStatusMessage(errorMessage);
            return ModifyResult.CANCEL;
        }
    }


    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        LOGGER.debug("ExtendWithSelectionStrategy.onKeyDown 被调用: keyCode={}, 当前状态={}", keyCode, extendState);
        
        if (isEscapeKey(keyCode)) {
            // 无论当前处于什么状态，按Esc键都完全重置工具，回到初始状态
            // 相当于重新激活延伸工具，需要重新选择边界图形
            LOGGER.info("Esc键按下（keyCode={}），完全重置延伸工具到初始状态", keyCode);
            
            // 清除预览状态
            clearPreview();
            context.setPreviewEnabled(false);
            
            // 清空全局选择状态（清空AppState中的选择）
            context.clearSelection();
            
            // 完全重置工具状态（就像刚激活工具一样）
            reset();
            
            // 设置初始状态消息（与工具激活时的消息一致）
            context.setStatusMessage("status.plot.extend.initial");
            
            return ModifyResult.CANCEL;
        }

        // 处理Shift键按下
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = true;
            LOGGER.debug("Shift键按下，启用多选模式");
            return ModifyResult.CONTINUE;
        }

        // 统一基于ExtendState处理键盘事件
        return switch (extendState) {
            case EXTENDING -> handleExtendKeyDown(keyCode, context);
            default -> ModifyResult.IGNORED;
        };
    }

    /**
     * 处理延伸模式下的按键
     */
    private ModifyResult handleExtendKeyDown(int keyCode, ModifyToolContext context) {
        // P键切换模式功能已移除，现在使用自动模式
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyUp(int keyCode, ModifyToolContext context) {
        // 处理Shift键释放
        if (keyCode == SHIFT_KEY) {
            isShiftPressed = false;
            LOGGER.debug("Shift键释放，禁用多选模式");
            return ModifyResult.CONTINUE;
        }

        return IModifyStrategy.super.onKeyUp(keyCode, context);
    }

    // ====== 配置更新方法 - 从ExtendStrategy合并 ======

    /**
     * 更新配置
     *
     * @param key 配置键
     * @param value 配置值
     */
    public void updateConfig(String key, Object value) {
        switch (key) {
            case "mode" -> // 模式配置已移除，现在使用自动模式
                    LOGGER.debug("延伸工具现在使用自动模式，忽略模式配置: {}", value);
            case "tolerance" -> {
                if (value instanceof String toleranceStr) {
                    try {
                        double tolerance = Double.parseDouble(toleranceStr);
                        if (tolerance > 0) {
                            setExtendTolerance(tolerance);
                            LOGGER.debug("延伸容差已更新为: {}", tolerance);
                        } else {
                            LOGGER.warn("延伸容差必须大于0: {}", toleranceStr);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("无效的容差值: {}", toleranceStr);
                    }
                }
            }
            case "endpointTolerance" -> {
                if (value instanceof String toleranceStr) {
                    try {
                        double tolerance = Double.parseDouble(toleranceStr);
                        if (tolerance > 0) {
                            setEndpointTolerance(tolerance);
                            LOGGER.debug("端点容差已更新为: {}", tolerance);
                        } else {
                            LOGGER.warn("端点容差必须大于0: {}", toleranceStr);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warn("无效的端点容差值: {}", toleranceStr);
                    }
                }
            }
            default -> LOGGER.debug("未知的配置键: {}", key);
        }
    }

    /**
         * 延伸目标信息类
         */
        private record ExtendTargetInfo(Shape targetShape, Vec2d extendPoint) {
        }

    /**
     * 查找延伸目标
     */
    private ExtendTargetInfo findExtendTarget(Vec2d point, ModifyToolContext context) {
        LOGGER.debug("===== findExtendTarget 开始 =====");
        // 查找点击的图形
        Shape targetShape = context.findShapeAt(point, extendTolerance);
        LOGGER.debug("findExtendTarget: 点击位置 {}, 容差 {}, 找到图形: {}, 边界图形数: {}",
            point, extendTolerance, targetShape != null ? targetShape.getId() : "null", boundaryShapes.size());

        if (targetShape == null) {
            LOGGER.debug("findExtendTarget: 未找到目标图形");
            return null;
        }

        if (boundaryShapes.contains(targetShape)) {
            LOGGER.debug("findExtendTarget: 点击的是边界图形 {}, 跳过延伸", targetShape.getId());
            return null;
        }

        // 查找端点
        LOGGER.debug("findExtendTarget: 开始查找端点，图形: {}", targetShape.getId());
        ExtendEndpointInfo endpointInfo = findExtendEndpoint(targetShape, point);
        if (endpointInfo == null) {
            LOGGER.debug("findExtendTarget: 未找到合适的端点，图形: {}", targetShape.getId());
            return null; // 没有找到合适的端点
        }

        LOGGER.debug("findExtendTarget: 找到端点 {}, 是起点: {}", endpointInfo.endpoint, endpointInfo.isStartEndpoint);

        // 计算延伸方向
        Vec2d extendDirection = calculateExtendDirection(targetShape, endpointInfo.endpoint, endpointInfo.isStartEndpoint);
        if (extendDirection == null) {
            LOGGER.debug("findExtendTarget: 无法确定延伸方向，图形: {}", targetShape.getId());
            return null; // 无法确定延伸方向
        }

        LOGGER.debug("findExtendTarget: 成功创建延伸目标，图形: {}, 自动模式", targetShape.getId());

        return new ExtendTargetInfo(targetShape, endpointInfo.endpoint);
    }


    /**
     * 获取有效的边界图形列表，优先使用缓存
     */
    private List<Shape> getEffectiveBoundaries() {
        // 优先使用缓存的边界图形，如果没有缓存则使用原始边界
        if (cachedBoundaryShapes != null && !cachedBoundaryShapes.isEmpty()) {
            return cachedBoundaryShapes;
        }
        return boundaryShapes;
    }


    /**
         * 延伸端点信息类
         */
        private record ExtendEndpointInfo(Vec2d endpoint, boolean isStartEndpoint) {
    }

    /**
     * 查找延伸端点 - 改进版本，支持动态容差调整
     */
    private ExtendEndpointInfo findExtendEndpoint(Shape shape, Vec2d point) {
        LOGGER.debug("===== findExtendEndpoint 开始 =====");
        LOGGER.debug("findExtendEndpoint: 图形={}, 点击位置={}, 端点容差={}",
            shape.getId(), point, endpointTolerance);
        try {
            List<Vec2d> endpoints = shape.getEndpoints();
            if (endpoints == null || endpoints.size() < 2) {
                LOGGER.debug("findExtendEndpoint: 图形 {} 没有足够的端点，端点数: {}",
                    shape.getId(), endpoints != null ? endpoints.size() : 0);
                return null;
            }

            // 获取起点和终点
            Vec2d startEndpoint = endpoints.getFirst();
            Vec2d endEndpoint = endpoints.getLast();

            LOGGER.debug("findExtendEndpoint: 图形 {} 起点: {}, 终点: {}, 点击位置: {}, 容差: {}",
                shape.getId(), startEndpoint, endEndpoint, point, endpointTolerance);

            // 计算点击点到两个端点的距离
            double distanceToStart = point.distance(startEndpoint);
            double distanceToEnd = point.distance(endEndpoint);

            // 计算动态容差：基于图形大小调整容差
            double dynamicTolerance = calculateDynamicTolerance(shape, startEndpoint, endEndpoint);

            LOGGER.debug("findExtendEndpoint: 距离计算 - 到起点: {}, 到终点: {}, 基础容差: {}, 动态容差: {}",
                distanceToStart, distanceToEnd, endpointTolerance, dynamicTolerance);

            // 检查是否在端点容差范围内（使用动态容差）
            boolean nearStart = distanceToStart <= dynamicTolerance;
            boolean nearEnd = distanceToEnd <= dynamicTolerance;

            LOGGER.info("findExtendEndpoint: 端点检测结果 - 靠近起点: {}, 靠近终点: {} (距离起点: {}, 距离终点: {}, 动态容差: {})", 
                    nearStart, nearEnd, 
                    String.format("%.2f", distanceToStart), 
                    String.format("%.2f", distanceToEnd), 
                    String.format("%.2f", dynamicTolerance));

            if (!nearStart && !nearEnd) {
                LOGGER.info("findExtendEndpoint: ❌ 点击位置不在任何端点附近 (距离起点: {}, 距离终点: {}, 动态容差: {})",
                        String.format("%.2f", distanceToStart), 
                        String.format("%.2f", distanceToEnd), 
                        String.format("%.2f", dynamicTolerance));
                return null; // 点击位置不在任何端点附近
            }

            // 如果只在一个端点附近，选择该端点
            if (nearStart && !nearEnd) {
                LOGGER.info("findExtendEndpoint: ✅ 选择起点 (只靠近起点)");
                return new ExtendEndpointInfo(startEndpoint, true);
            }
            if (!nearStart) {
                LOGGER.info("findExtendEndpoint: ✅ 选择终点 (只靠近终点)");
                return new ExtendEndpointInfo(endEndpoint, false);
            }

            // 如果同时在两个端点附近，选择距离更近的端点
            if (distanceToStart <= distanceToEnd) {
                LOGGER.info("findExtendEndpoint: ✅ 选择起点 (两个端点都靠近，但起点更近)");
                return new ExtendEndpointInfo(startEndpoint, true);
            } else {
                LOGGER.info("findExtendEndpoint: ✅ 选择终点 (两个端点都靠近，但终点更近)");
                return new ExtendEndpointInfo(endEndpoint, false);
            }

        } catch (Exception e) {
            LOGGER.warn("查找图形端点失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算动态容差 - 基于图形大小调整端点检测容差
     */
    private double calculateDynamicTolerance(Shape shape, Vec2d startEndpoint, Vec2d endEndpoint) {
        try {
            // 计算图形的基本尺寸
            double shapeLength = startEndpoint.distance(endEndpoint);

            // 对于不同类型的图形，使用不同的容差策略
            if (shape instanceof com.plot.core.geometry.shapes.ArcShape arc) {
                // 对于圆弧，容差应该基于半径
                double radius = arc.getRadius();

                // 检查是否为半圆
                double startAngle = Math.atan2(startEndpoint.y - arc.getCenter().y, startEndpoint.x - arc.getCenter().x);
                double endAngle = Math.atan2(endEndpoint.y - arc.getCenter().y, endEndpoint.x - arc.getCenter().x);

                // 规范化角度到[0, 2π)范围
                startAngle = normalizeAngle(startAngle);
                endAngle = normalizeAngle(endAngle);

                // 计算角度差
                double angleDiff = Math.abs(endAngle - startAngle);
                if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;

                boolean isSemicircle = Math.abs(angleDiff - Math.PI) < 0.1;

                LOGGER.debug("ExtendWithSelectionStrategy - 圆弧角度检测: startAngle={}, endAngle={}, angleDiff={}, isSemicircle={}", String.format("%.2f", Math.toDegrees(startAngle)), String.format("%.2f", Math.toDegrees(endAngle)), String.format("%.2f", Math.toDegrees(angleDiff)), isSemicircle);

                // 判断圆弧的弧度大小
                boolean isSmallArc = angleDiff < Math.PI / 2;

                if (isSemicircle) {
                    // 半圆：使用更大的容差
                    double semicircleTolerance = Math.max(endpointTolerance * 5.0, radius * 0.2);
                    LOGGER.debug("ExtendWithSelectionStrategy - 半圆容差: {}, 半径: {}, 基础容差: {}", String.format("%.2f", semicircleTolerance), String.format("%.2f", radius), String.format("%.2f", endpointTolerance));
                    return semicircleTolerance;
                } else if (isSmallArc) {
                    // 小弧度圆弧：使用较大的容差，因为端点检测可能更困难
                    double smallArcTolerance = Math.max(endpointTolerance * 3.0, radius * 0.15);
                    LOGGER.debug("ExtendWithSelectionStrategy - 小弧度圆弧容差: {}, 半径: {}, 基础容差: {}, 角度差: {}", String.format("%.2f", smallArcTolerance), String.format("%.2f", radius), String.format("%.2f", endpointTolerance), String.format("%.2f", Math.toDegrees(angleDiff)));
                    return smallArcTolerance;
                } else {
                    // 普通圆弧：容差基于半径的百分比，最小为基础容差
                    double normalTolerance = Math.max(endpointTolerance, radius * 0.1);
                    LOGGER.debug("ExtendWithSelectionStrategy - 普通圆弧容差: {}, 半径: {}, 基础容差: {}", String.format("%.2f", normalTolerance), String.format("%.2f", radius), String.format("%.2f", endpointTolerance));
                    return normalTolerance;
                }
            } else if (shape instanceof com.plot.core.geometry.shapes.CircleShape circle) {
                // 圆形不支持延伸，但为了完整性
                double radius = circle.getRadius();
                return Math.max(endpointTolerance, radius * 0.1);
            } else {
                // 对于直线和多段线，容差基于长度
                // 长图形的容差应该更大，但不要过大
                double lengthBasedTolerance = Math.min(shapeLength * 0.05, endpointTolerance * 3);
                return Math.max(endpointTolerance, lengthBasedTolerance);
            }
        } catch (Exception e) {
            LOGGER.warn("计算动态容差失败: {}", e.getMessage());
            return endpointTolerance; // 失败时使用基础容差
        }
    }

    /**
     * 规范化单个角度，确保角度在 [0, 2π) 范围内
     */
    private double normalizeAngle(double angle) {
        while (angle < 0) angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
        return angle;
    }

    /**
     * 计算延伸方向
     */
    private Vec2d calculateExtendDirection(Shape shape, Vec2d extendPoint, boolean isStartEndpoint) {
        try {
            // 对于直线 - 使用instanceof进行类型检查，更安全更高效
            if (shape instanceof com.plot.core.geometry.shapes.LineShape lineShape) {
                List<Vec2d> points = lineShape.getPoints();
                if (points != null && points.size() >= 2) {
                    Vec2d start = points.getFirst();
                    Vec2d end = points.getLast();
                    if (isStartEndpoint) {
                        return start.subtract(end).normalize();
                    } else {
                        return end.subtract(start).normalize();
                    }
                }
            }

            // 对于多段线 - 使用instanceof进行类型检查，更安全更高效
            if (shape instanceof com.plot.core.geometry.shapes.PolylineShape polylineShape) {
                List<Vec2d> points = polylineShape.getPoints();
                if (points != null && points.size() >= 2) {
                    if (isStartEndpoint) {
                        // 延伸起点：从第二个点指向第一个点的方向
                        return points.get(0).subtract(points.get(1)).normalize();
                    } else {
                        // 延伸终点：从倒数第二个点指向最后一个点的方向
                        int lastIndex = points.size() - 1;
                        return points.get(lastIndex).subtract(points.get(lastIndex - 1)).normalize();
                    }
                }
            }

            // 对于圆弧，需要特殊处理，考虑圆弧的方向（顺时针/逆时针）
            if (shape instanceof com.plot.core.geometry.shapes.ArcShape arc) {
                Vec2d center = arc.getCenter();
                double radius = arc.getRadius();
                
                // 验证点是否在圆弧上
                double distanceToCenter = extendPoint.distance(center);
                if (Math.abs(distanceToCenter - radius) > 0.001) {
                    LOGGER.debug("圆弧延伸方向计算 - 点不在圆弧上，距离圆心: {:.2f}, 半径: {:.2f}", 
                        distanceToCenter, radius);
                    // 如果点不在圆弧上，尝试使用端点位置
                    List<Vec2d> endpoints = arc.getEndpoints();
                    if (endpoints != null && endpoints.size() >= 2) {
                        Vec2d targetPoint = isStartEndpoint ? endpoints.get(0) : endpoints.get(1);
                        extendPoint = targetPoint;
                    }
                }
                
                // 计算径向向量（从圆心指向端点）
                Vec2d radial = extendPoint.subtract(center).normalize();
                
                // 计算圆弧的方向（顺时针或逆时针）
                boolean isClockwise = isArcClockwise(arc);
                
                // 计算切线方向
                // 顺时针圆弧：切线方向为径向向量逆时针旋转90度
                // 逆时针圆弧：切线方向为径向向量顺时针旋转90度
                Vec2d tangent = isClockwise ? 
                    new Vec2d(radial.y, -radial.x) :  // 顺时针：逆时针旋转90度
                    new Vec2d(-radial.y, radial.x);   // 逆时针：顺时针旋转90度
                
                LOGGER.debug("圆弧延伸方向计算 - 端点: {}, 是起点: {}, 径向: ({}, {}), 顺时针: {}, 切线: ({}, {})", 
                    extendPoint, isStartEndpoint,
                    String.format("%.2f", radial.x), String.format("%.2f", radial.y),
                    isClockwise,
                    String.format("%.2f", tangent.x), String.format("%.2f", tangent.y));
                
                return tangent.normalize();
            }

            // 对于其他图形，尝试使用切线方向
            Vec2d tangent = shape.getTangentAt(extendPoint);
            if (tangent != null) {
                return tangent.normalize();
            }

            // 回退方案：从图形中心指向端点的方向
            Vec2d center = shape.getPosition();
            if (center != null) {
                return extendPoint.subtract(center).normalize();
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("计算延伸方向失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断圆弧是否为顺时针方向
     */
    private boolean isArcClockwise(com.plot.core.geometry.shapes.ArcShape arc) {
        try {
            List<Vec2d> endPoints = arc.getEndpoints();
            if (endPoints == null || endPoints.size() < 2) {
                return false; // 默认逆时针
            }
            
            Vec2d startPoint = endPoints.get(0);
            Vec2d endPoint = endPoints.get(1);
            Vec2d center = arc.getCenter();
            
            // 计算起始角度和结束角度
            double startAngle = Math.atan2(startPoint.y - center.y, startPoint.x - center.x);
            double endAngle = Math.atan2(endPoint.y - center.y, endPoint.x - center.x);
            
            // 规范化角度
            startAngle = normalizeAngle(startAngle);
            endAngle = normalizeAngle(endAngle);
            
            // 获取圆弧的实际角度（考虑ArcShape的normalizeAngles处理）
            // 由于ArcShape的endAngle可能在[startAngle, startAngle+2π)范围内
            // 我们需要考虑这种情况
            double actualEndAngle = arc.getEndAngle();
            double actualStartAngle = arc.getStartAngle();
            
            // 计算角度差
            double angleDiff = actualEndAngle - actualStartAngle;
            
            // 如果角度差大于π，说明圆弧跨越了半圆
            // 对于跨越半圆的情况，我们需要判断实际的方向
            // 但通常如果角度差在(0, π)范围内是逆时针，在(π, 2π)范围内需要判断
            if (angleDiff > Math.PI) {
                // 如果角度差大于π，可能是长弧，需要判断实际方向
                // 通过比较端点与圆心的角度来判断
                double computedAngleDiff = endAngle - startAngle;
                if (computedAngleDiff > Math.PI) computedAngleDiff -= 2 * Math.PI;
                if (computedAngleDiff < -Math.PI) computedAngleDiff += 2 * Math.PI;
                
                // 正角度差表示逆时针，负角度差表示顺时针
                boolean isClockwise = computedAngleDiff < 0;
                
                LOGGER.debug("圆弧方向判断（长弧）- 起始角度: {:.2f}, 结束角度: {:.2f}, 角度差: {:.2f}, 顺时针: {}", 
                    Math.toDegrees(startAngle), Math.toDegrees(endAngle), 
                    Math.toDegrees(computedAngleDiff), isClockwise);
                
                return isClockwise;
            }
            
            // 对于短弧（角度差小于π），使用实际的角度差判断
            // 但ArcShape的normalizeAngles确保endAngle >= startAngle
            // 所以如果角度差在[0, π)范围内，通常是逆时针
            // 但我们需要检查实际的端点位置来判断真正的方向
            
            // 通过计算叉积来判断方向
            Vec2d vec1 = startPoint.subtract(center);
            Vec2d vec2 = endPoint.subtract(center);
            double crossProduct = vec1.x * vec2.y - vec1.y * vec2.x;
            
            // 叉积的符号表示方向：正为逆时针，负为顺时针
            boolean isClockwise = crossProduct < 0;
            
            LOGGER.debug("圆弧方向判断 - 起始角度: {:.2f}, 结束角度: {:.2f}, 角度差: {:.2f}, 叉积: {:.2f}, 顺时针: {}", 
                Math.toDegrees(startAngle), Math.toDegrees(endAngle), 
                Math.toDegrees(angleDiff), crossProduct, isClockwise);
            
            return isClockwise;
            
        } catch (Exception e) {
            LOGGER.warn("判断圆弧方向失败: {}", e.getMessage());
            return false; // 默认逆时针
        }
    }

    /**
     * 更新延伸预览
     */
    private void updateExtendPreview(Vec2d point, ModifyToolContext context) {
        // 查找延伸目标
        ExtendTargetInfo targetInfo = findExtendTarget(point, context);
        if (targetInfo != null) {
            highlightedShape = targetInfo.targetShape;
            extendPoint = targetInfo.extendPoint;

            // 生成预览图形 - 传递端点容差
            try {
                ExtendParameters parameters = new ExtendParameters(
                    targetInfo.extendPoint,
                    getEffectiveBoundaries(), // 使用有效的边界图形
                    extendTolerance,
                    endpointTolerance // 传递端点容差
                );

                previewShapes = extendHandler.createPreviewShapes(
                    List.of(targetInfo.targetShape), parameters);

                // 计算目标点
                if (previewShapes != null && !previewShapes.isEmpty()) {
                    Shape previewShape = previewShapes.getFirst();
                    List<Vec2d> previewEndpoints = previewShape.getEndpoints();
                    if (previewEndpoints != null && !previewEndpoints.isEmpty()) {
                        // 找到距离原延伸点最远的端点作为目标点
                        Vec2d farthestEndpoint = null;
                        double maxDistance = 0;
                        for (Vec2d endpoint : previewEndpoints) {
                            double distance = targetInfo.extendPoint.distance(endpoint);
                            if (distance > maxDistance) {
                                maxDistance = distance;
                                farthestEndpoint = endpoint;
                            }
                        }
                        targetPoint = farthestEndpoint;
                    }
                } else {
                    targetPoint = null;
                    // 备用预览：如果无法创建预览图形，提供虚线预览到视口边缘
                    LOGGER.debug("无法创建延伸预览，将提供备用虚线预览");
                }
            } catch (Exception e) {
                LOGGER.warn("创建延伸预览失败: {}", e.getMessage());
                previewShapes = null;
                targetPoint = null;
            }
        } else {
            // 没有找到可延伸的目标，但仍然高亮显示鼠标下的图形
            highlightedShape = context.findShapeAt(point, extendTolerance);
            extendPoint = null;
            targetPoint = null;
            previewShapes = null;
        }
    }

    /**
     * 重置延伸状态
     */
    private void resetExtendState() {
        LOGGER.debug("重置延伸状态，当前状态: {}", extendState);

        // 先清除预览状态
        clearPreview();

        // 先重置选择状态，清空图形选择和高亮（这会清空boundaryShapes）
        resetSelectionState();

        // 清空缓存，确保状态重置时正确清理
        cachedBoundaryShapes = null;
        boundarySpatialIndex = null;
        LOGGER.debug("清空边界图形缓存");

        // 重置延伸状态
        extendState = ExtendState.SELECTING_BOUNDARY;
        currentPoint = null;
        pendingCommand = null;
        pendingOriginalShapes.clear();
        pendingModifiedShapes.clear();

        LOGGER.debug("延伸状态重置完成，新状态: {}", extendState);
    }

    @Override
    public ModifyCommand getModifyCommand() {
        if (pendingCommand == null && !pendingOriginalShapes.isEmpty() && !pendingModifiedShapes.isEmpty()) {
            pendingCommand = extendHandler.createModifyCommand(
                pendingOriginalShapes, pendingModifiedShapes, new ExtendParameters(
                    extendPoint, // 使用当前的延伸点作为参数
                    getEffectiveBoundaries(), // 使用当前的边界图形作为参数
                    extendTolerance, // 使用当前的容差作为参数
                    endpointTolerance // 使用当前的端点容差作为参数
                )
            );
            pendingOriginalShapes.clear();
            pendingModifiedShapes.clear();
        }
        return pendingCommand;
    }

    /**
     * 确保批量命令被正确提交
     * 在工具停用或切换时调用此方法
     */
    public void ensureCommandSubmitted() {
        if (!pendingOriginalShapes.isEmpty() && !pendingModifiedShapes.isEmpty()) {
            LOGGER.debug("确保批量命令提交，待处理延伸操作数量: {}", pendingOriginalShapes.size());
            // 创建并提交命令
            getModifyCommand();
        }
    }

    @Override
    public void reset() {
        LOGGER.debug("ExtendWithSelectionStrategy 重置");

        // 确保批量命令被正确提交
        ensureCommandSubmitted();

        // 重置选择状态
        resetSelectionState();

        // 重置延伸状态
        resetExtendState();

        // 完全重置时清空所有缓存
        cachedBoundaryShapes = null;
        boundarySpatialIndex = null;

        // 确保初始状态
        extendState = ExtendState.SELECTING_BOUNDARY;

        LOGGER.debug("ExtendWithSelectionStrategy 重置完成，当前状态: {}", extendState);
    }

    /**
     * 重置选择状态
     */
    protected void resetSelectionState() {
        LOGGER.debug("重置选择状态");

        // 清空边界图形选择状态
        for (Shape shape : boundaryShapes) {
            if (shape != null) {
                shape.setSelected(false);
            }
        }
        boundaryShapes.clear();

        // 清空高亮状态
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(false);
            highlightedShape = null;
        }

        // 重置框选状态
        isBoxSelecting = false;
        boxStartPoint = null;
        boxCurrentPoint = null;
        boxSelectedShapes.clear();

        LOGGER.debug("选择状态重置完成");
    }

    @Override
    public String getStrategyName() {
        return "延伸选择结合策略";
    }

    @Override
    public String getStrategyDescription() {
        return extendState.getDescription();
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
     * 获取延伸模式（已弃用，现在使用自动模式）
     */
    @Deprecated
    public ExtendMode getExtendMode() {
        return ExtendMode.STANDARD; // 返回默认值以保持兼容性
    }

    /**
     * 设置延伸模式（已弃用，现在使用自动模式）
     */
    @Deprecated
    public void setExtendMode(ExtendMode extendMode) {
        // 忽略设置，现在使用自动模式
        LOGGER.debug("延伸工具现在使用自动模式，忽略模式设置: {}", extendMode);
    }

    /**
     * 获取延伸状态
     */
    public ExtendState getExtendState() {
        return extendState;
    }

    /**
     * 获取边界图形
     */
    public List<Shape> getBoundaryShapes() {
        return new ArrayList<>(boundaryShapes);
    }

    /**
     * 获取高亮图形
     */
    public Shape getHighlightedShape() {
        return highlightedShape;
    }

    /**
     * 获取延伸点
     */
    public Vec2d getExtendPoint() {
        return extendPoint;
    }

    /**
     * 获取目标点
     */
    public Vec2d getTargetPoint() {
        return targetPoint;
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    /**
     * 获取延伸容差
     */
    public double getExtendTolerance() {
        return extendTolerance;
    }

    /**
     * 设置延伸容差
     */
    public void setExtendTolerance(double tolerance) {
        this.extendTolerance = tolerance;
    }

    /**
     * 获取端点容差
     */
    public double getEndpointTolerance() {
        return endpointTolerance;
    }

    /**
     * 设置端点容差
     */
    public void setEndpointTolerance(double tolerance) {
        this.endpointTolerance = tolerance;
    }

    /**
     * 重写选择状态消息，添加延伸相关信息
     */
    @Override
    protected ModifyResult handleSelectionMouseUp(Vec2d pos, ModifyToolContext context) {
        ModifyResult result = super.handleSelectionMouseUp(pos, context);

                    // 更新状态消息以包含延伸信息
            if (result == ModifyResult.COMPLETE) {
                int count = getSelectedCount();
                if (count > 0) {
                    String selectionMessage = String.format(
                        "已选择 %d 个边界图形，右键确认边界选择并开始延伸操作",
                        count);
                    context.setStatusMessage(selectionMessage);
                } else {
                    context.setStatusMessage("status.plot.extend.select_boundary_required");
                }
            }

        return result;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        // 渲染框选预览
        if (isBoxSelecting && boxStartPoint != null && boxCurrentPoint != null) {
            renderBoxSelectionPreview(context);
        }

        if (extendState == ExtendState.SELECTING_BOUNDARY) {
            renderSelectionPreview(context);
        } else if (extendState == ExtendState.EXTENDING) {
            renderExtendPreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        // 渲染框选预览
        if (isBoxSelecting && boxStartPoint != null && boxCurrentPoint != null) {
            renderBoxSelectionPreviewImGui(drawList, camera);
        }

        if (extendState == ExtendState.SELECTING_BOUNDARY) {
            renderSelectionPreviewImGui(drawList, camera);
        } else if (extendState == ExtendState.EXTENDING) {
            renderExtendPreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染延伸预览
     */
    private void renderExtendPreview(DrawContext context) {
        // 渲染预览图形
        if (previewShapes != null && !previewShapes.isEmpty()) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        } else if (extendPoint != null && !boundaryShapes.isEmpty()) {
            // 备用预览：如果无法创建预览图形，提供虚线预览到视口边缘
            renderFallbackPreview(context);
        }

        // 渲染延伸点（红色圆点）
        if (extendPoint != null) {
            context.fillCircle(extendPoint, 4.0f, toColor(ThemeManager.getInstance().getCurrentTheme().errorText));
        }

        // 渲染目标点（绿色或蓝色圆点）
        if (targetPoint != null) {
            Color color = toColor(ThemeManager.getInstance().getCurrentTheme().infoText);
            context.fillCircle(targetPoint, 4.0f, color);
        }

        // 高亮目标图形
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(true);
        }
    }

    /**
     * 渲染备用预览（当无法创建预览图形时）
     */
    private void renderFallbackPreview(DrawContext context) {
        if (extendPoint == null || boundaryShapes.isEmpty() || highlightedShape == null) {
            return;
        }

        try {
            // 计算延伸方向
            Vec2d extendDirection = calculateExtendDirection(highlightedShape, extendPoint, true);

            if (extendDirection != null) {
                // 绘制虚线预览到视口边缘
                double previewDistance = 1000.0; // 预览距离
                Vec2d previewEnd = extendPoint.add(extendDirection.multiply(previewDistance));
                Color previewColor = withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().warningText), 180);

                // 使用虚线绘制预览
                context.drawDashedLine(extendPoint, previewEnd, previewColor);

                // 在预览线末端绘制小圆点
                context.fillCircle(previewEnd, 3.0f, previewColor);
            }
        } catch (Exception e) {
            LOGGER.debug("渲染备用预览失败: {}", e.getMessage());
        }
    }

    private static Color toColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return new Color(red, green, blue, alpha);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /**
     * 渲染延伸预览（ImGui版本）- 优化版本，支持动态调整渲染参数
     */
    private void renderExtendPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            // 根据相机缩放动态调整渲染参数
            float zoom = camera != null ? camera.getZoom() : 1.0f;

            // 动态调整点大小和线宽
            float basePointSize = 4.0f;
            float pointSize = basePointSize * zoom;
            // 限制点大小在合理范围内
            pointSize = Math.max(2.0f, Math.min(pointSize, 12.0f));

            // 动态调整线宽
            float baseLineWidth = 2.0f;
            float lineWidth = baseLineWidth * zoom;
            // 限制线宽在合理范围内
            lineWidth = Math.max(1.0f, Math.min(lineWidth, 6.0f));

            // 渲染延伸点（红色圆点）
            if (extendPoint != null) {
                Vec2d screenPos = null;
                if (camera != null) {
                    screenPos = camera.worldToScreen(extendPoint);
                }
                if (screenPos != null) {
                    drawList.addCircleFilled(
                        (float) screenPos.x, (float) screenPos.y, pointSize,
                        theme.errorText
                    );
                }
            }

            // 渲染目标点（绿色或蓝色圆点）
            if (targetPoint != null) {
                Vec2d screenPos = Objects.requireNonNull(camera).worldToScreen(targetPoint);
                int color = theme.infoText;
                drawList.addCircleFilled(
                    (float) screenPos.x, (float) screenPos.y, pointSize,
                    color
                );
            }

        } catch (Exception e) {
            LOGGER.warn("渲染延伸预览时出错: {}", e.getMessage());
        }
    }

    /**
     * 渲染框选预览（ImGui版本）
     */
    private void renderBoxSelectionPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }

        try {
            // 转换世界坐标到屏幕坐标
            Vec2d screenStart = camera.worldToScreen(boxStartPoint);
            Vec2d screenEnd = camera.worldToScreen(boxCurrentPoint);

            // 判断框选方向：从左到右为实线，从右到左为虚线
            boolean isLeftToRight = boxStartPoint.x <= boxCurrentPoint.x;

            // 计算矩形坐标
            float x1 = (float) Math.min(screenStart.x, screenEnd.x);
            float y1 = (float) Math.min(screenStart.y, screenEnd.y);
            float x2 = (float) Math.max(screenStart.x, screenEnd.x);
            float y2 = (float) Math.max(screenStart.y, screenEnd.y);

            if (isLeftToRight) {
                // 从左到右：实线框（白色）
                drawList.addRect(
                    x1, y1, x2, y2,
                    ThemeManager.getInstance().getCurrentTheme().text,
                    0.0f, // 无圆角
                    0, // 无标志
                    2.0f // 线宽
                );
            } else {
                // 从右到左：虚线框（白色虚线）
                // ImGui没有直接的虚线绘制，我们使用多个短线段来模拟虚线
                drawDashedRectImGui(drawList, x1, y1, x2, y2);
            }
        } catch (Exception e) {
            LOGGER.warn("渲染框选预览时出错: {}", e.getMessage());
        }
    }

    /**
     * 绘制虚线矩形（ImGui版本）
     */
    private void drawDashedRectImGui(ImDrawList drawList, float x1, float y1, float x2, float y2) {
        float dashLength = 8.0f;
        float gapLength = 4.0f;

        // 上边
        drawDashedLineImGui(drawList, x1, y1, x2, y1, dashLength, gapLength);
        // 右边
        drawDashedLineImGui(drawList, x2, y1, x2, y2, dashLength, gapLength);
        // 下边
        drawDashedLineImGui(drawList, x2, y2, x1, y2, dashLength, gapLength);
        // 左边
        drawDashedLineImGui(drawList, x1, y2, x1, y1, dashLength, gapLength);
    }

    /**
     * 绘制虚线（ImGui版本）
     */
    private void drawDashedLineImGui(ImDrawList drawList, float x1, float y1, float x2, float y2,
                                   float dashLength, float gapLength) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < dashLength) {
            // 如果线段太短，直接绘制
            drawList.addLine(x1, y1, x2, y2, ThemeManager.getInstance().getCurrentTheme().text, 2.0f);
            return;
        }

        float unitX = dx / length;
        float unitY = dy / length;

        float currentLength = 0.0f;
        boolean drawDash = true;

        while (currentLength < length) {
            float nextLength = currentLength + (drawDash ? dashLength : gapLength);
            if (nextLength > length) {
                nextLength = length;
            }

            if (drawDash) {
                float startX = x1 + currentLength * unitX;
                float startY = y1 + currentLength * unitY;
                float endX = x1 + nextLength * unitX;
                float endY = y1 + nextLength * unitY;

                drawList.addLine(startX, startY, endX, endY, ThemeManager.getInstance().getCurrentTheme().text, 2.0f);
            }

            currentLength = nextLength;
            drawDash = !drawDash;
        }
    }
}