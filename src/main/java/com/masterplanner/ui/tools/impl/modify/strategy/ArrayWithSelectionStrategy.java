package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.geometry.util.PathUtils;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.tools.impl.modify.helper.ArrayHandler;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 阵列工具与选择功能结合策略
 *
 * <p>这个策略结合了选择工具和阵列工具的功能：</p>
 * <ul>
 *   <li><strong>选择模式</strong>：左键点选/框选图形，右键完成选择</li>
 *   <li><strong>阵列模式</strong>：右键后进入阵列模式，支持矩形、环形、路径三种阵列</li>
 *   <li><strong>智能切换</strong>：根据右键操作在选择和阵列间切换</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 1.0 - 阵列选择结合策略
 */
public class ArrayWithSelectionStrategy extends BaseSelectionStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayWithSelectionStrategy.class);
    private static final double GEOMETRY_EPS = 1e-9;

    private static class PathOffsetRelation {
        final double signedDistance;

        PathOffsetRelation(double signedDistance) {
            this.signedDistance = signedDistance;
        }
    }

    // 策略模式枚举
    public enum StrategyMode {
        SELECTION("选择模式", "左键选择图形，右键完成选择"),
        ARRAY("阵列模式", "设置阵列参数并预览");

        private final String displayName;
        private final String description;

        StrategyMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 阵列类型枚举
    public enum ArrayType {
        RECTANGULAR("矩形阵列", "按行列排列图形"),
        CIRCULAR("环形阵列", "按圆形排列图形"),
        PATH("路径阵列", "沿指定路径排列图形");

        private final String displayName;
        private final String description;

        ArrayType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 阵列状态枚举
    public enum ArrayState {
        IDLE("空闲", "等待开始阵列"),
        AWAIT_BASE_POINT("等待基准点", "点击设置阵列基准点"),
        AWAIT_PATH("等待路径", "选择路径对象"),
        PREVIEWING("预览中", "调整参数并预览阵列效果");

        private final String displayName;
        private final String description;

        ArrayState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // 常量
    private static final int ESC_KEY = 27;
    private static final int R_KEY = 82; // R键 - 矩形阵列
    private static final int C_KEY = 67; // C键 - 环形阵列
    private static final int P_KEY = 80; // P键 - 路径阵列
    private static final int UP_KEY = 38; // ↑ 增加行数
    private static final int DOWN_KEY = 40; // ↓ 减少行数
    private static final int LEFT_KEY = 37; // ← 减少列数
    private static final int RIGHT_KEY = 39; // → 增加列数
    private static final double SELECTION_TOLERANCE = 5.0;

    // 策略状态
    private StrategyMode currentMode = StrategyMode.SELECTION;
    private ArrayType arrayType = ArrayType.RECTANGULAR;
    private ArrayState arrayState = ArrayState.IDLE;

    // 阵列配置
    private int rowCount = 2;
    private int columnCount = 2;
    private double spacing = 50.0; // 列间距（矩形阵列）
    private double rowSpacing = 50.0; // 行间距（矩形阵列）
    private double radius = 100.0; // 环形阵列半径
    private int pathCount = 10; // 路径阵列数量

    // 阵列交互状态
    private Vec2d basePoint;
    private Vec2d currentPoint;
    private final List<Vec2d> pathPoints = new ArrayList<>(); // 路径阵列的路径点

    // 阵列处理器和参数
    private ArrayHandler arrayHandler;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    // ====== 画布拖拽锚点（矩形间距 / 环形半径） ======
    private enum DragHandle { NONE, COLUMN_SPACING, ROW_SPACING, CIRCULAR_RADIUS }
    private DragHandle dragHandle = DragHandle.NONE;
    private boolean isDraggingSpacing = false;
    private static final double HANDLE_RADIUS_PX = 6.0;

    // 预览数据
    private final List<Vec2d> previewPositions = new ArrayList<>();
    private final List<Double> previewAngles = new ArrayList<>();

    /**
     * 默认构造函数
     */
    public ArrayWithSelectionStrategy() {
        // 延迟初始化：需要在首次进入阵列模式时拿到 AppState
        this.arrayHandler = null;
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
     * 用于在选择模式和阵列模式之间切换，以及在预览模式下完成阵列
     */
    private ModifyResult handleRightMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            // 在选择模式下右键：完成选择，切换到阵列模式
            if (!selectedShapeIds.isEmpty()) {
                currentMode = StrategyMode.ARRAY;
                selectedShapes = getSelectedShapesFromIds(context);

                // 初始化处理器（需要 AppState）
                if (arrayHandler == null) {
                    try {
                        arrayHandler = new ArrayHandler((com.masterplanner.core.state.AppState) context.getAppState());
                    } catch (Exception e) {
                        arrayHandler = new ArrayHandler(com.masterplanner.core.state.AppState.getInstance());
                    }
                }

                // 按类型进入交互：
                // - 矩形：自动 3×3 预览（无需再点基点）
                // - 环形：点中心后默认 6 个
                // - 路径：左键选择路径
                if (arrayType == ArrayType.RECTANGULAR) {
                    rowCount = 3;
                    columnCount = 3;
                    basePoint = getShapeCenter(selectedShapes.getFirst());
                    arrayState = ArrayState.PREVIEWING;
                    updateArrayPreview();
                    context.setPreviewEnabled(true);
                    context.setStatusMessage("矩形阵列预览：已自动生成 3×3，可拖拽间距锚点或在面板调整，点击“完成”确认");
                } else if (arrayType == ArrayType.CIRCULAR) {
                    rowCount = 6; // 作为“数量（含原图）”
                    arrayState = ArrayState.AWAIT_BASE_POINT;
                    context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，点击设置环形阵列中心（默认 6 个）");
                } else {
                    // PATH
                    pathCount = Math.max(pathCount, 2);
                    basePoint = getShapeCenter(selectedShapes.getFirst()); // 满足 handler 校验需要
                    arrayState = ArrayState.AWAIT_PATH;
                    context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，左键点击选择路径（数量=路径等距点位数，含起终点）");
                }

                LOGGER.info("切换到阵列模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("请先选择要阵列的图形");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在阵列模式下右键
            // 右键统一作为“取消”，完成请使用面板按钮
            resetArrayState();
            currentMode = StrategyMode.SELECTION;
            context.setStatusMessage("阵列已取消");
            LOGGER.info("从阵列模式返回选择模式");
            return ModifyResult.CANCEL;
        }
    }

    /**
     * 处理左键按下
     * 根据当前模式执行不同操作
     */
    private ModifyResult handleLeftMouseDown(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseDown(pos, context);
        } else {
            return handleArrayMouseDown(pos, context);
        }
    }

    /**
     * 处理阵列模式下的左键按下
     */
    private ModifyResult handleArrayMouseDown(Vec2d pos, ModifyToolContext context) {
        if (selectedShapes == null || selectedShapes.isEmpty()) {
            context.setStatusMessage("没有选中的图形可以阵列");
            return ModifyResult.NEED_SELECTION;
        }

        try {
            // 吸附点
            Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 初始化处理器（需要 AppState）
            if (arrayHandler == null) {
                try {
                    arrayHandler = new ArrayHandler((com.masterplanner.core.state.AppState) context.getAppState());
                } catch (Exception e) {
                    arrayHandler = new ArrayHandler(com.masterplanner.core.state.AppState.getInstance());
                }
            }

            // 画布锚点交互：点击开始拖拽，再次点击确认结束
            if (arrayState == ArrayState.PREVIEWING && basePoint != null) {
                if (isDraggingSpacing) {
                    isDraggingSpacing = false;
                    dragHandle = DragHandle.NONE;
                    context.setStatusMessage("参数已确认：可继续拖拽其他锚点，或点击“完成”确认阵列");
                    return ModifyResult.CONTINUE;
                }

                double tol = HANDLE_RADIUS_PX / Math.max(0.1, context.getCamera().getZoom());

                // 矩形阵列：行/列间距锚点
                if (arrayType == ArrayType.RECTANGULAR) {
                    Vec2d colHandle = basePoint.add(new Vec2d(spacing, 0));
                    Vec2d rowHandle = basePoint.add(new Vec2d(0, rowSpacing));
                    if (snapped.distance(colHandle) <= tol) {
                        dragHandle = DragHandle.COLUMN_SPACING;
                        isDraggingSpacing = true;
                        context.setStatusMessage("正在拖拽列间距：移动鼠标调整，单击确认");
                        return ModifyResult.CONTINUE;
                    }
                    if (snapped.distance(rowHandle) <= tol) {
                        dragHandle = DragHandle.ROW_SPACING;
                        isDraggingSpacing = true;
                        context.setStatusMessage("正在拖拽行间距：移动鼠标调整，单击确认");
                        return ModifyResult.CONTINUE;
                    }
                }

                // 环形阵列：半径锚点（沿“源图形方向”的半径线上）
                if (arrayType == ArrayType.CIRCULAR && selectedShapes != null && !selectedShapes.isEmpty()) {
                    Vec2d sourcePos = getShapeCenter(selectedShapes.getFirst());
                    double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);
                    Vec2d radiusHandle = basePoint.add(new Vec2d(radius * Math.cos(startAngle), radius * Math.sin(startAngle)));
                    if (snapped.distance(radiusHandle) <= tol) {
                        dragHandle = DragHandle.CIRCULAR_RADIUS;
                        isDraggingSpacing = true;
                        context.setStatusMessage("正在拖拽半径：移动鼠标调整，单击确认");
                        return ModifyResult.CONTINUE;
                    }
                }
            }

            switch (arrayState) {
                case AWAIT_BASE_POINT -> {
                    // 第一步：设置阵列基准点，立即进入预览模式
                    basePoint = snapped;
                    if (arrayType == ArrayType.RECTANGULAR) {
                        // 矩形阵列：设置基点后立即进入预览模式
                        arrayState = ArrayState.PREVIEWING;
                        updateArrayPreview();
                        context.setPreviewEnabled(true);
                        context.setStatusMessage("矩形阵列预览中：可拖拽间距锚点或在面板调整，点击“完成”确认");
                    } else if (arrayType == ArrayType.CIRCULAR) {
                        // 环形阵列：点中心后立即预览（默认半径=中心到源图形距离）
                        if (selectedShapes != null) {
                            radius = basePoint.distance(getShapeCenter(selectedShapes.getFirst()));
                        }
                        arrayState = ArrayState.PREVIEWING;
                        updateArrayPreview();
                        context.setPreviewEnabled(true);
                        context.setStatusMessage("环形阵列预览中：默认 6 个，可在面板调整数量/半径，点击“完成”确认");
                    } else if (arrayType == ArrayType.PATH) {
                        // 路径阵列：需要选择路径对象
                        arrayState = ArrayState.AWAIT_PATH;
                        context.setStatusMessage("点击选择路径对象");
                    }
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_PATH -> {
                    // 第三步：选择路径对象（仅路径阵列）
                    Shape pathObj = context.findShapeAt(snapped, SELECTION_TOLERANCE);
                    if (pathObj != null) {
                        try {
                            List<Vec2d> pts = pathObj.getPoints();
                            if (pts != null && pts.size() >= 2) {
                                pathPoints.clear();
                                pathPoints.addAll(pts);
                                arrayState = ArrayState.PREVIEWING;
                                updateArrayPreview();
                                context.setPreviewEnabled(true);
                                context.setStatusMessage("已选择路径：可在面板调整点位数（含起终点，沿路径等距），点击“完成”确认");
                            } else {
                                context.setStatusMessage("所选对象无法作为路径（点数不足）");
                            }
                        } catch (Exception e) {
                            context.setStatusMessage("无法获取路径点");
                        }
                    } else {
                        context.setStatusMessage("未选中路径对象");
                    }
                    return ModifyResult.CONTINUE;
                }
                case PREVIEWING -> {
                    // 预览模式下左键不执行任何操作（完成用面板按钮/右键取消）
                    return ModifyResult.IGNORED;
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
        } catch (Exception e) {
            LOGGER.error("阵列策略鼠标按下处理失败: {}", e.getMessage(), e);
            resetArrayState();
            return ModifyResult.CANCEL;
        }
    }

    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseMove(pos, context);
        } else {
            return handleArrayMouseMove(pos, context);
        }
    }

    /**
     * 处理阵列模式下的鼠标移动
     */
    private ModifyResult handleArrayMouseMove(Vec2d pos, ModifyToolContext context) {
        try {
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 拖拽更新（矩形间距 / 环形半径）
            if (arrayState == ArrayState.PREVIEWING && isDraggingSpacing && basePoint != null) {
                if (dragHandle == DragHandle.COLUMN_SPACING) {
                    spacing = Math.max(1.0, Math.abs(currentPoint.x - basePoint.x));
                    updateArrayPreview();
                    context.setPreviewEnabled(true);
                    context.setStatusMessage(String.format("列间距: %.1f（拖拽调整，单击确认）", spacing));
                    return ModifyResult.CONTINUE;
                }
                if (dragHandle == DragHandle.ROW_SPACING) {
                    rowSpacing = Math.max(1.0, Math.abs(currentPoint.y - basePoint.y));
                    updateArrayPreview();
                    context.setPreviewEnabled(true);
                    context.setStatusMessage(String.format("行间距: %.1f（拖拽调整，单击确认）", rowSpacing));
                    return ModifyResult.CONTINUE;
                }
                if (dragHandle == DragHandle.CIRCULAR_RADIUS) {
                    radius = Math.max(1.0, basePoint.distance(currentPoint));

                    // 同步移动原始图形：保持原始图形相对于中心的角度不变，仅改变半径
                    if (selectedShapes != null && !selectedShapes.isEmpty()) {
                        try {
                            Shape original = selectedShapes.getFirst();
                            Vec2d sourcePos = getShapeCenter(original);
                            double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);
                            Vec2d newPos = basePoint.add(new Vec2d(radius * Math.cos(startAngle), radius * Math.sin(startAngle)));
                            Vec2d originalCenter = getShapeCenter(original);
                            Vec2d offset = newPos.subtract(originalCenter);
                            if (offset.length() > 1e-9) {
                                original.translate(offset);
                            }
                        } catch (Exception e) {
                            LOGGER.debug("移动原始图形失败: {}", e.getMessage());
                        }
                    }

                    updateArrayPreview();
                    context.setPreviewEnabled(true);
                    context.setStatusMessage(String.format("半径: %.1f（拖拽调整，单击确认）", radius));
                    return ModifyResult.CONTINUE;
                }
            }

            // 在预览模式下，实时更新预览
            if (arrayState == ArrayState.PREVIEWING) {
                updateArrayPreview();
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
            }
        } catch (Exception e) {
            LOGGER.debug("阵列预览失败: {}", e.getMessage());
        }
        return ModifyResult.CONTINUE;
    }

    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 选择模式：沿用框选释放逻辑
        if (currentMode == StrategyMode.SELECTION) {
            if (button != MOUSE_LEFT || !isSelecting) return ModifyResult.IGNORED;
            return handleSelectionMouseUp(pos, context);
        }

        // 阵列模式：间距拖拽用“点击开始/点击结束”，不依赖 mouseUp
        return ModifyResult.IGNORED;
    }

    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == ESC_KEY) {
            reset();
            context.setStatusMessage("操作已取消");
            return ModifyResult.CANCEL;
        }

        // 在阵列模式下处理键盘事件
        if (currentMode == StrategyMode.ARRAY) {
            return handleArrayKeyDown(keyCode, context);
        }

        return ModifyResult.IGNORED;
    }

    /**
     * 处理阵列模式下的按键
     */
    private ModifyResult handleArrayKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case R_KEY -> {
                arrayType = ArrayType.RECTANGULAR;
                // 只有在预览模式下才更新预览
                if (arrayState == ArrayState.PREVIEWING) {
                    updateArrayPreview();
                }
                context.setStatusMessage("切换到矩形阵列模式");
                return ModifyResult.CONTINUE;
            }
            case C_KEY -> {
                arrayType = ArrayType.CIRCULAR;
                // 只有在预览模式下才更新预览
                if (arrayState == ArrayState.PREVIEWING) {
                    updateArrayPreview();
                }
                context.setStatusMessage("切换到环形阵列模式");
                return ModifyResult.CONTINUE;
            }
            case P_KEY -> {
                arrayType = ArrayType.PATH;
                // 只有在预览模式下才更新预览
                if (arrayState == ArrayState.PREVIEWING) {
                    updateArrayPreview();
                }
                context.setStatusMessage("切换到路径阵列模式");
                return ModifyResult.CONTINUE;
            }
            case UP_KEY -> {
                // 只有在预览模式下才处理参数调整
                if (arrayState == ArrayState.PREVIEWING) {
                    if (arrayType == ArrayType.RECTANGULAR) {
                        // 上箭头：增加行间距
                        rowSpacing = Math.min(rowSpacing + 5.0, 500.0);
                        updateArrayPreview();
                        context.setStatusMessage("行间距: " + String.format("%.1f", rowSpacing));
                    } else if (arrayType == ArrayType.PATH) {
                        pathCount = Math.min(pathCount + 1, 100);
                        updateArrayPreview();
                        context.setStatusMessage("数量: " + pathCount);
                    }
                }
                return ModifyResult.CONTINUE;
            }
            case DOWN_KEY -> {
                // 只有在预览模式下才处理参数调整
                if (arrayState == ArrayState.PREVIEWING) {
                    if (arrayType == ArrayType.RECTANGULAR) {
                        // 下箭头：减少行间距
                        rowSpacing = Math.max(rowSpacing - 5.0, 1.0);
                        updateArrayPreview();
                        context.setStatusMessage("行间距: " + String.format("%.1f", rowSpacing));
                    } else if (arrayType == ArrayType.PATH) {
                        pathCount = Math.max(pathCount - 1, 2);
                        updateArrayPreview();
                        context.setStatusMessage("数量: " + pathCount);
                    }
                }
                return ModifyResult.CONTINUE;
            }
            case LEFT_KEY -> {
                // 只有在预览模式下才处理参数调整
                if (arrayState == ArrayState.PREVIEWING) {
                    if (arrayType == ArrayType.RECTANGULAR) {
                        // 左箭头：减少列间距
                        spacing = Math.max(spacing - 5.0, 1.0);
                        updateArrayPreview();
                        context.setStatusMessage("列间距: " + String.format("%.1f", spacing));
                    }
                }
                return ModifyResult.CONTINUE;
            }
            case RIGHT_KEY -> {
                // 只有在预览模式下才处理参数调整
                if (arrayState == ArrayState.PREVIEWING) {
                    if (arrayType == ArrayType.RECTANGULAR) {
                        // 右箭头：增加列间距
                        spacing = Math.min(spacing + 5.0, 500.0);
                        updateArrayPreview();
                        context.setStatusMessage("列间距: " + String.format("%.1f", spacing));
                    }
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
     * 供面板“完成”按钮调用：构建阵列命令（不在这里执行）
     */
    public ModifyCommand buildArrayCommand() {
        if (arrayHandler == null) return null;
        if (selectedShapes == null || selectedShapes.isEmpty()) return null;
        if (currentMode != StrategyMode.ARRAY || arrayState != ArrayState.PREVIEWING) return null;

        ModifyParameters parameters = new ModifyParameters();
        parameters.setString("arrayType", arrayType.name());
        parameters.setVec2d("basePoint", basePoint != null ? basePoint : getShapeCenter(selectedShapes.getFirst()));

        if (arrayType == ArrayType.RECTANGULAR) {
            parameters.setInt("rowCount", rowCount);
            parameters.setInt("columnCount", columnCount);
            parameters.setDouble("rowSpacing", rowSpacing);
            parameters.setDouble("columnSpacing", spacing);
        } else if (arrayType == ArrayType.CIRCULAR) {
            parameters.setInt("rowCount", rowCount); // 作为数量（总数）
            parameters.setDouble("radius", radius);
            parameters.setDouble("angleStep", 360.0 / Math.max(1, rowCount));
        } else if (arrayType == ArrayType.PATH) {
            parameters.setInt("rowCount", pathCount);
            parameters.setParameter("pathPoints", new ArrayList<>(pathPoints));
        }

        List<Shape> arrayedShapes = arrayHandler.calculateModifiedShapes(selectedShapes, parameters);
        return arrayHandler.createModifyCommand(selectedShapes, arrayedShapes, parameters);
    }

    /**
     * 更新阵列预览
     */
    private void updateArrayPreview() {
        if (selectedShapes == null || selectedShapes.isEmpty() || basePoint == null) {
            return;
        }

        try {
            ModifyParameters parameters = new ModifyParameters();
            parameters.setString("arrayType", arrayType.name());
            parameters.setVec2d("basePoint", basePoint);

            if (arrayType == ArrayType.RECTANGULAR) {
                parameters.setInt("rowCount", rowCount);
                parameters.setInt("columnCount", columnCount);
                parameters.setDouble("rowSpacing", rowSpacing);
                parameters.setDouble("columnSpacing", spacing);
            } else if (arrayType == ArrayType.CIRCULAR) {
                parameters.setInt("rowCount", rowCount); // 作为数量（总数）
                parameters.setDouble("radius", radius);
                parameters.setDouble("angleStep", 360.0 / Math.max(1, rowCount));
            } else if (arrayType == ArrayType.PATH) {
                parameters.setInt("rowCount", pathCount);
                // 使用setParameter存储路径点列表
                parameters.setParameter("pathPoints", pathPoints);
            }

            // 生成预览图形
            List<Shape> preview = arrayHandler.calculateModifiedShapes(selectedShapes, parameters);
            createPreviewShapesFromModified(preview);

            // 计算预览位置和角度
            calculatePreviewPositions(parameters);

        } catch (Exception e) {
            LOGGER.debug("更新阵列预览失败: {}", e.getMessage());
        }
    }

    /**
     * 计算预览位置和角度
     */
    private void calculatePreviewPositions(ModifyParameters parameters) {
        previewPositions.clear();
        previewAngles.clear();

        if (arrayType == ArrayType.RECTANGULAR) {
            // 矩形阵列位置计算
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < columnCount; col++) {
                    if (row == 0 && col == 0) continue; // 跳过原点
                    double x = basePoint.x + col * spacing;
                    double y = basePoint.y + row * rowSpacing;
                    previewPositions.add(new Vec2d(x, y));
                    previewAngles.add(0.0); // 矩形阵列不旋转
                }
            }
        } else if (arrayType == ArrayType.CIRCULAR) {
            // 环形阵列位置计算
            int count = Math.max(1, rowCount);
            double angleStep = (2 * Math.PI) / count;
            Vec2d sourcePos = (selectedShapes != null && !selectedShapes.isEmpty())
                ? getShapeCenter(selectedShapes.getFirst())
                : basePoint;
            double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);
            for (int i = 1; i < count; i++) {
                double a = startAngle + i * angleStep;
                double x = basePoint.x + radius * Math.cos(a);
                double y = basePoint.y + radius * Math.sin(a);
                previewPositions.add(new Vec2d(x, y));
                previewAngles.add(a);
            }
        } else if (arrayType == ArrayType.PATH) {
            // 路径阵列位置计算
            if (pathPoints.size() >= 2) {
                double totalLength = PathUtils.calculatePathLength(pathPoints);
                int count = Math.max(2, pathCount);
                if (totalLength <= GEOMETRY_EPS) {
                    return;
                }

                Vec2d sourceCenter = (selectedShapes != null && !selectedShapes.isEmpty())
                    ? getShapeCenter(selectedShapes.getFirst())
                    : null;
                PathOffsetRelation relation = calculatePathOffsetRelation(sourceCenter);

                double step = totalLength / (count - 1);
                for (int i = 0; i < count; i++) {
                    double distance = i * step;
                    double clampedLength = Math.max(0.0, Math.min(distance, totalLength));
                    Vec2d pathPos = PathUtils.getPositionAtLength(pathPoints, clampedLength);
                    if (pathPos != null) {
                        double tangentAngle = calculatePathTangentAngle(clampedLength);
                        Vec2d normal = new Vec2d(-Math.sin(tangentAngle), Math.cos(tangentAngle));
                        Vec2d pos = pathPos.add(normal.multiply(relation.signedDistance));

                        previewPositions.add(pos);
                        previewAngles.add(tangentAngle);
                    }
                }
            }
        }
    }

    /**
     * 从修改后的图形创建预览图形
     */
    private void createPreviewShapesFromModified(List<Shape> modifiedShapes) {
        if (modifiedShapes == null || modifiedShapes.isEmpty()) {
            previewShapes = null;
            return;
        }

        previewShapes = new ArrayList<>();

        for (Shape shape : modifiedShapes) {
            try {
                // 设置预览样式
                shape.setStyle(ShapeStyle.PREVIEW);

                // 清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
                shape.setSelected(false);
                shape.setHighlighted(false);

                previewShapes.add(shape);
            } catch (Exception e) {
                LOGGER.warn("创建预览图形失败: {}", e.getMessage());
                // 如果设置样式失败，添加原图形
                previewShapes.add(shape);
            }
        }
    }

    /**
     * 重置阵列状态
     */
    private void resetArrayState() {
        arrayState = ArrayState.IDLE;
        basePoint = null;
        currentPoint = null;
        pathPoints.clear();
        previewShapes = null;
        pendingCommand = null;
        previewPositions.clear();
        previewAngles.clear();
    }

    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }

    @Override
    public void reset() {
        LOGGER.debug("ArrayWithSelectionStrategy 重置");

        // 重置选择状态
        resetSelectionState();

        // 重置阵列状态
        resetArrayState();

        // 返回选择模式
        currentMode = StrategyMode.SELECTION;

        LOGGER.debug("ArrayWithSelectionStrategy 重置完成，当前模式: {}", currentMode);
    }

    @Override
    public String getStrategyName() {
        return "阵列选择结合策略";
    }

    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
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
     * 获取当前模式
     */
    public StrategyMode getCurrentMode() {
        return currentMode;
    }

    /**
     * 获取阵列类型
     */
    public ArrayType getArrayType() {
        return arrayType;
    }

    /**
     * 设置阵列类型
     */
    public void setArrayType(ArrayType arrayType) {
        this.arrayType = arrayType;
        // 重置阵列状态，因为切换类型需要重新开始
        if (currentMode == StrategyMode.ARRAY) {
            resetArrayState();
            if (selectedShapes != null && !selectedShapes.isEmpty()) {
                if (arrayType == ArrayType.RECTANGULAR) {
                    rowCount = 3;
                    columnCount = 3;
                    basePoint = getShapeCenter(selectedShapes.getFirst());
                    arrayState = ArrayState.PREVIEWING;
                    updateArrayPreview();
                } else if (arrayType == ArrayType.CIRCULAR) {
                    rowCount = 6;
                    arrayState = ArrayState.AWAIT_BASE_POINT;
                } else {
                    basePoint = getShapeCenter(selectedShapes.getFirst());
                    arrayState = ArrayState.AWAIT_PATH;
                }
            } else {
                arrayState = ArrayState.IDLE;
            }
        }
    }

    /**
     * 获取行数
     */
    public int getRowCount() {
        return rowCount;
    }

    /**
     * 设置行数
     */
    public void setRowCount(int rowCount) {
        this.rowCount = Math.max(1, Math.min(rowCount, 50));
        // 如果在预览模式下，立即更新预览
        if (currentMode == StrategyMode.ARRAY && arrayState == ArrayState.PREVIEWING) {
            updateArrayPreview();
        }
    }

    /**
     * 获取列数
     */
    public int getColumnCount() {
        return columnCount;
    }

    /**
     * 设置列数
     */
    public void setColumnCount(int columnCount) {
        this.columnCount = Math.max(1, Math.min(columnCount, 50));
        // 如果在预览模式下，立即更新预览
        if (currentMode == StrategyMode.ARRAY && arrayState == ArrayState.PREVIEWING) {
            updateArrayPreview();
        }
    }

    /**
     * 获取行间距
     */
    public double getRowSpacing() {
        return rowSpacing;
    }

    /**
     * 设置行间距
     */
    public void setRowSpacing(double rowSpacing) {
        this.rowSpacing = Math.max(1.0, rowSpacing);
        // 如果在预览模式下，立即更新预览
        if (currentMode == StrategyMode.ARRAY && arrayState == ArrayState.PREVIEWING) {
            updateArrayPreview();
        }
    }

    /**
     * 获取列间距
     */
    public double getColumnSpacing() {
        return spacing;
    }

    /**
     * 设置列间距
     */
    public void setColumnSpacing(double columnSpacing) {
        this.spacing = Math.max(1.0, columnSpacing);
        // 如果在预览模式下，立即更新预览
        if (currentMode == StrategyMode.ARRAY && arrayState == ArrayState.PREVIEWING) {
            updateArrayPreview();
        }
    }

    // 角度步长已改为由“数量”推导（360/数量），不再提供手动角度参数

    /**
     * 获取半径
     */
    public double getRadius() {
        return radius;
    }

    /**
     * 设置半径
     */
    public void setRadius(double radius) {
        this.radius = Math.max(1.0, radius);
        if (currentMode == StrategyMode.ARRAY && arrayState == ArrayState.PREVIEWING && arrayType == ArrayType.CIRCULAR) {
            updateArrayPreview();
        }
    }

    /**
     * 获取路径数量
     */
    public int getPathCount() {
        return pathCount;
    }

    public double getCurrentPathLength() {
        if (pathPoints.size() < 2) {
            return 0.0;
        }
        return PathUtils.calculatePathLength(pathPoints);
    }

    /**
     * 设置路径数量
     */
    public void setPathCount(int pathCount) {
        this.pathCount = Math.max(2, Math.min(pathCount, 100));
        if (currentMode == StrategyMode.ARRAY && arrayState == ArrayState.PREVIEWING && arrayType == ArrayType.PATH) {
            updateArrayPreview();
        }
    }

    /**
     * 获取阵列状态
     */
    public ArrayState getArrayState() {
        return arrayState;
    }

    /**
     * 获取预览图形
     */
    public List<Shape> getPreviewShapes() {
        return previewShapes;
    }

    /**
     * 重写选择状态消息，添加阵列相关信息
     */
    @Override
    protected ModifyResult handleSelectionMouseUp(Vec2d pos, ModifyToolContext context) {
        ModifyResult result = super.handleSelectionMouseUp(pos, context);

        // 更新状态消息以包含阵列信息
        if (result == ModifyResult.COMPLETE) {
            int count = getSelectedCount();
            if (count > 0) {
                context.setStatusMessage("已选择 " + count + " 个图形，右键开始阵列操作");
            } else {
                context.setStatusMessage("请选择要阵列的图形");
            }
        }

        return result;
    }

    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreview(context);
        } else {
            renderArrayPreview(context);
        }
    }

    /**
     * 渲染预览（ImGui版本）
     */
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (currentMode == StrategyMode.SELECTION) {
            renderSelectionPreviewImGui(drawList, camera);
        } else {
            renderArrayPreviewImGui(drawList, camera);
        }
    }

    /**
     * 渲染阵列预览
     */
    private void renderArrayPreview(DrawContext context) {
        // 渲染预览图形
        if (previewShapes != null) {
            for (Shape shape : previewShapes) {
                shape.render(context);
            }
        }

        // 渲染预览位置点
        for (int i = 0; i < previewPositions.size(); i++) {
            Vec2d pos = previewPositions.get(i);
            context.drawCircle(pos, 3.0, new Color(0, 255, 0, 180)); // 绿色圆点

            if (arrayType == ArrayType.PATH && i < previewAngles.size()) {
                double angle = previewAngles.get(i);
                Vec2d tip = pos.add(new Vec2d(12.0 * Math.cos(angle), 12.0 * Math.sin(angle)));
                context.drawLine(pos, tip, new Color(0, 255, 255, 220));
            }
        }

        // 矩形阵列：绘制间距锚点（可拖拽）
        if (arrayType == ArrayType.RECTANGULAR && arrayState == ArrayState.PREVIEWING && basePoint != null) {
            Color handleColor = new Color(255, 200, 0, 220);
            Vec2d colHandle = basePoint.add(new Vec2d(spacing, 0));
            Vec2d rowHandle = basePoint.add(new Vec2d(0, rowSpacing));
            context.drawDashedLine(basePoint, colHandle, handleColor);
            context.drawDashedLine(basePoint, rowHandle, handleColor);
            context.drawCircleFilled(colHandle, 4.0f, handleColor);
            context.drawCircleFilled(rowHandle, 4.0f, handleColor);
            context.drawCircleOutline(colHandle, 6.0f, Color.WHITE);
            context.drawCircleOutline(rowHandle, 6.0f, Color.WHITE);
        }

        // 环形阵列：绘制半径锚点（可拖拽）
        if (arrayType == ArrayType.CIRCULAR && arrayState == ArrayState.PREVIEWING && basePoint != null
            && selectedShapes != null && !selectedShapes.isEmpty()) {
            Vec2d sourcePos = getShapeCenter(selectedShapes.getFirst());
            double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);
            Vec2d radiusHandle = basePoint.add(new Vec2d(radius * Math.cos(startAngle), radius * Math.sin(startAngle)));
            Color handleColor = new Color(255, 200, 0, 220);
            context.drawDashedLine(basePoint, radiusHandle, handleColor);
            context.drawCircleFilled(radiusHandle, 4.0f, handleColor);
            context.drawCircleOutline(radiusHandle, 6.0f, Color.WHITE);
        }
    }

    /**
     * 渲染阵列预览（ImGui版本）
     */
    private void renderArrayPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            var theme = ThemeManager.getInstance().getCurrentTheme();
            // 渲染预览位置点
            for (int i = 0; i < previewPositions.size(); i++) {
                Vec2d pos = previewPositions.get(i);
                Vec2d screenPos = camera.worldToScreen(pos);
                drawList.addCircleFilled(
                    (float) screenPos.x, (float) screenPos.y, 3.0f,
                    theme.successText
                );

                if (arrayType == ArrayType.PATH && i < previewAngles.size()) {
                    double angle = previewAngles.get(i);
                    Vec2d worldTip = pos.add(new Vec2d(12.0 * Math.cos(angle), 12.0 * Math.sin(angle)));
                    Vec2d screenTip = camera.worldToScreen(worldTip);
                    drawList.addLine(
                        (float) screenPos.x, (float) screenPos.y,
                        (float) screenTip.x, (float) screenTip.y,
                        theme.accent, 1.5f
                    );
                }
            }

            // 矩形阵列：绘制间距锚点（可拖拽）
            if (arrayType == ArrayType.RECTANGULAR && arrayState == ArrayState.PREVIEWING && basePoint != null) {
                Vec2d colHandle = basePoint.add(new Vec2d(spacing, 0));
                Vec2d rowHandle = basePoint.add(new Vec2d(0, rowSpacing));
                Vec2d sBase = camera.worldToScreen(basePoint);
                Vec2d sCol = camera.worldToScreen(colHandle);
                Vec2d sRow = camera.worldToScreen(rowHandle);
                int color = theme.accent;
                drawList.addLine((float) sBase.x, (float) sBase.y, (float) sCol.x, (float) sCol.y, color, 1.5f);
                drawList.addLine((float) sBase.x, (float) sBase.y, (float) sRow.x, (float) sRow.y, color, 1.5f);
                drawList.addCircleFilled((float) sCol.x, (float) sCol.y, 4.0f, color);
                drawList.addCircleFilled((float) sRow.x, (float) sRow.y, 4.0f, color);
            }

            // 环形阵列：绘制半径锚点（可拖拽）
            if (arrayType == ArrayType.CIRCULAR && arrayState == ArrayState.PREVIEWING && basePoint != null
                && selectedShapes != null && !selectedShapes.isEmpty()) {
                Vec2d sourcePos = getShapeCenter(selectedShapes.getFirst());
                double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);
                Vec2d radiusHandle = basePoint.add(new Vec2d(radius * Math.cos(startAngle), radius * Math.sin(startAngle)));
                Vec2d sBase = camera.worldToScreen(basePoint);
                Vec2d sRad = camera.worldToScreen(radiusHandle);
                int color = theme.accent;
                drawList.addLine((float) sBase.x, (float) sBase.y, (float) sRad.x, (float) sRad.y, color, 1.5f);
                drawList.addCircleFilled((float) sRad.x, (float) sRad.y, 4.0f, color);
            }
        } catch (Exception e) {
            LOGGER.warn("渲染阵列预览时出错: {}", e.getMessage());
        }
    }

    private Vec2d getShapeCenter(Shape shape) {
        if (shape == null) {
            return new Vec2d(0, 0);
        }

        try {
            if (shape.getBoundingBox() != null) {
                return shape.getBoundingBox().getCenter();
            }
        } catch (Exception e) {
            LOGGER.debug("获取图形中心失败，回退到position: {}", e.getMessage());
        }

        Vec2d pos = shape.getPosition();
        return pos != null ? pos : new Vec2d(0, 0);
    }

    private double calculatePathTangentAngle(double targetLength) {
        if (pathPoints.size() < 2) {
            return 0.0;
        }

        double totalLength = PathUtils.calculatePathLength(pathPoints);
        if (totalLength <= GEOMETRY_EPS) {
            return 0.0;
        }

        double clampedLength = Math.max(0.0, Math.min(targetLength, totalLength));

        double accumulatedLength = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            Vec2d prev = pathPoints.get(i - 1);
            Vec2d curr = pathPoints.get(i);
            Vec2d direction = curr.subtract(prev);
            double segmentLength = prev.distance(curr);

            if (segmentLength <= GEOMETRY_EPS || direction.length() <= GEOMETRY_EPS) {
                continue;
            }

            double segmentEnd = accumulatedLength + segmentLength;
            if (clampedLength < segmentEnd - GEOMETRY_EPS) {
                return Math.atan2(direction.y, direction.x);
            }

            if (Math.abs(clampedLength - segmentEnd) <= GEOMETRY_EPS) {
                for (int j = i + 1; j < pathPoints.size(); j++) {
                    Vec2d nextPrev = pathPoints.get(j - 1);
                    Vec2d nextCurr = pathPoints.get(j);
                    Vec2d nextDir = nextCurr.subtract(nextPrev);
                    if (nextDir.length() > GEOMETRY_EPS) {
                        return Math.atan2(nextDir.y, nextDir.x);
                    }
                }
                if (direction.length() > GEOMETRY_EPS) {
                    return Math.atan2(direction.y, direction.x);
                }
            }

            accumulatedLength = segmentEnd;
        }

        for (int i = pathPoints.size() - 1; i >= 1; i--) {
            Vec2d last = pathPoints.get(i);
            Vec2d secondLast = pathPoints.get(i - 1);
            Vec2d direction = last.subtract(secondLast);
            if (direction.length() > GEOMETRY_EPS) {
                return Math.atan2(direction.y, direction.x);
            }
        }
        return 0.0;
    }

    private PathOffsetRelation calculatePathOffsetRelation(Vec2d sourceCenter) {
        if (sourceCenter == null || pathPoints.size() < 2) {
            return new PathOffsetRelation(0.0);
        }

        double minDistance = Double.POSITIVE_INFINITY;
        double bestSignedDistance = 0.0;

        for (int i = 1; i < pathPoints.size(); i++) {
            Vec2d prev = pathPoints.get(i - 1);
            Vec2d curr = pathPoints.get(i);
            Vec2d segment = curr.subtract(prev);
            double segmentLength = segment.length();

            if (segmentLength <= GEOMETRY_EPS) {
                continue;
            }

            double invLenSq = 1.0 / (segmentLength * segmentLength);
            double t = sourceCenter.subtract(prev).dot(segment) * invLenSq;
            t = Math.max(0.0, Math.min(1.0, t));

            Vec2d projection = prev.add(segment.multiply(t));
            Vec2d toSource = sourceCenter.subtract(projection);
            double distance = toSource.length();

            if (distance < minDistance) {
                Vec2d tangent = segment.multiply(1.0 / segmentLength);
                bestSignedDistance = tangent.x * toSource.y - tangent.y * toSource.x;
                minDistance = distance;
            }
        }

        if (!Double.isFinite(minDistance)) {
            return new PathOffsetRelation(0.0);
        }

        return new PathOffsetRelation(bestSignedDistance);
    }
}