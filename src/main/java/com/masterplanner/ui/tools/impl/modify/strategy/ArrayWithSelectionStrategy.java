package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.geometry.util.PathUtils;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
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
        AWAIT_SECOND_POINT("等待第二点", "点击设置阵列方向或半径"),
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
    private double angle = 45.0; // 环形阵列角度间隔
    private double radius = 100.0; // 环形阵列半径
    private int pathCount = 10; // 路径阵列数量

    // 阵列交互状态
    private Vec2d basePoint;
    private Vec2d secondPoint;
    private Vec2d currentPoint;
    private final List<Vec2d> pathPoints = new ArrayList<>(); // 路径阵列的路径点
    private Shape pathShape; // 路径对象

    // 阵列处理器和参数
    private ArrayHandler arrayHandler;
    private List<Shape> previewShapes;
    private ModifyCommand pendingCommand;

    // 预览数据
    private final List<Vec2d> previewPositions = new ArrayList<>();
    private final List<Double> previewAngles = new ArrayList<>();

    /**
     * 默认构造函数
     */
    public ArrayWithSelectionStrategy() {
        ModifyParameters arrayParameters = new ModifyParameters();
        // 需要传入AppState，这里暂时使用null，实际使用时需要从上下文获取
        this.arrayHandler = new ArrayHandler(null);
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
                arrayState = ArrayState.AWAIT_BASE_POINT;
                context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，点击设置阵列基准点");
                LOGGER.info("切换到阵列模式，已选择 {} 个图形", selectedShapeIds.size());
                return ModifyResult.CONTINUE;
            } else {
                context.setStatusMessage("请先选择要阵列的图形");
                return ModifyResult.NEED_SELECTION;
            }
        } else {
            // 在阵列模式下右键
            if (arrayState == ArrayState.PREVIEWING) {
                // 在预览模式下右键：完成阵列
                return performArray(selectedShapes, context);
            } else {
                // 在其他阵列状态下右键：取消阵列，返回选择模式
                resetArrayState();
                currentMode = StrategyMode.SELECTION;
                context.setStatusMessage("阵列已取消");
                LOGGER.info("从阵列模式返回选择模式");
                return ModifyResult.CANCEL;
            }
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

            switch (arrayState) {
                case AWAIT_BASE_POINT -> {
                    // 第一步：设置阵列基准点，立即进入预览模式
                    basePoint = snapped;
                    if (arrayType == ArrayType.RECTANGULAR) {
                        // 矩形阵列：设置基点后立即进入预览模式
                        arrayState = ArrayState.PREVIEWING;
                        updateArrayPreview();
                        context.setPreviewEnabled(true);
                        context.setStatusMessage("矩形阵列预览中，调整参数后右键完成");
                    } else if (arrayType == ArrayType.CIRCULAR) {
                        // 环形阵列：需要第二点设置半径
                        arrayState = ArrayState.AWAIT_SECOND_POINT;
                        context.setStatusMessage("已设置基准点，点击设置半径点");
                    } else if (arrayType == ArrayType.PATH) {
                        // 路径阵列：需要选择路径对象
                        arrayState = ArrayState.AWAIT_PATH;
                        context.setStatusMessage("点击选择路径对象");
                    }
                    return ModifyResult.CONTINUE;
                }
                case AWAIT_SECOND_POINT -> {
                    // 第二步：根据阵列类型设置第二点
                    secondPoint = snapped;
                    if (arrayType == ArrayType.CIRCULAR) {
                        // 环形阵列进入预览
                        arrayState = ArrayState.PREVIEWING;
                        updateArrayPreview();
                        context.setPreviewEnabled(true);
                        context.setStatusMessage("环形阵列预览中，调整参数后右键完成");
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
                                pathShape = pathObj;
                                arrayState = ArrayState.PREVIEWING;
                                updateArrayPreview();
                                context.setPreviewEnabled(true);
                                context.setStatusMessage("已选择路径，调整数量后点击完成");
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
                    // 预览模式下左键不执行任何操作，只通过右键完成
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

            // 在等待第二点时，显示预览线
            if (arrayState == ArrayState.AWAIT_SECOND_POINT && basePoint != null) {
                context.setPreviewEnabled(true);
                return ModifyResult.CONTINUE;
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
        // 参考SelectionStrategy的条件检查
        if (button != MOUSE_LEFT || !isSelecting) {
            return ModifyResult.IGNORED;
        }

        if (currentMode == StrategyMode.SELECTION) {
            return handleSelectionMouseUp(pos, context);
        } else {
            // 阵列模式下不处理鼠标释放
            return ModifyResult.IGNORED;
        }
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
                        pathCount = Math.max(pathCount - 1, 1);
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
     * 执行阵列操作
     */
    private ModifyResult performArray(List<Shape> shapes, ModifyToolContext context) {
        try {
            // 创建阵列参数
            ModifyParameters parameters = new ModifyParameters();
            parameters.setString("arrayType", arrayType.name());
            parameters.setVec2d("basePoint", basePoint);
            
            if (arrayType == ArrayType.RECTANGULAR) {
                parameters.setInt("rowCount", rowCount);
                parameters.setInt("columnCount", columnCount);
                parameters.setDouble("rowSpacing", rowSpacing);
                parameters.setDouble("columnSpacing", spacing);
            } else if (arrayType == ArrayType.CIRCULAR) {
                parameters.setDouble("radius", radius);
                parameters.setDouble("angle", angle);
            } else if (arrayType == ArrayType.PATH) {
                parameters.setInt("pathCount", pathCount);
                // 使用setParameter存储路径点列表
                parameters.setParameter("pathPoints", pathPoints);
            }

            // 生成阵列图形
            List<Shape> arrayedShapes = arrayHandler.calculateModifiedShapes(shapes, parameters);
            if (arrayedShapes.isEmpty()) {
                context.setStatusMessage("阵列失败：无法生成阵列图形");
                return ModifyResult.CANCEL;
            }

            // 创建命令
            pendingCommand = arrayHandler.createModifyCommand(shapes, arrayedShapes, parameters);
            if (pendingCommand != null) {
                // 执行命令
                context.executeModifyCommand(pendingCommand);
                context.setStatusMessage("阵列完成");
                resetArrayState();
                // 返回选择模式
                currentMode = StrategyMode.SELECTION;
                return ModifyResult.COMPLETE;
            }

            context.setStatusMessage("创建阵列命令失败");
            return ModifyResult.CANCEL;
        } catch (Exception e) {
            LOGGER.error("阵列操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("阵列失败: " + e.getMessage());
            return ModifyResult.CANCEL;
        }
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
                double currentRadius = radius;
                if (secondPoint != null) {
                    currentRadius = basePoint.distance(secondPoint);
                }
                parameters.setDouble("radius", currentRadius);
                parameters.setDouble("angle", angle);
            } else if (arrayType == ArrayType.PATH) {
                parameters.setInt("pathCount", pathCount);
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
            double currentRadius = radius;
            if (secondPoint != null) {
                currentRadius = basePoint.distance(secondPoint);
            }
            double angleStep = Math.toRadians(angle);
            for (int i = 1; i < 10; i++) { // 预览10个位置
                double angle = i * angleStep;
                double x = basePoint.x + currentRadius * Math.cos(angle);
                double y = basePoint.y + currentRadius * Math.sin(angle);
                previewPositions.add(new Vec2d(x, y));
                previewAngles.add(angle);
            }
        } else if (arrayType == ArrayType.PATH) {
            // 路径阵列位置计算
            if (pathPoints.size() >= 2) {
                double totalLength = PathUtils.calculatePathLength(pathPoints);
                double step = totalLength / (pathCount + 1);
                for (int i = 1; i <= pathCount; i++) {
                    double distance = i * step;
                    Vec2d pos = PathUtils.getPositionAtLength(pathPoints, distance);
                    if (pos != null) {
                        previewPositions.add(pos);
                        // 计算路径角度（简化版本）
                        double angle = 0.0; // 暂时设为0，实际应该计算路径切线角度
                        previewAngles.add(angle);
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
        secondPoint = null;
        currentPoint = null;
        pathPoints.clear();
        pathShape = null;
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
            arrayState = ArrayState.AWAIT_BASE_POINT;
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

    /**
     * 获取角度
     */
    public double getAngle() {
        return angle;
    }

    /**
     * 设置角度
     */
    public void setAngle(double angle) {
        this.angle = Math.max(1.0, Math.min(angle, 360.0));
    }

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
    }

    /**
     * 获取路径数量
     */
    public int getPathCount() {
        return pathCount;
    }

    /**
     * 设置路径数量
     */
    public void setPathCount(int pathCount) {
        this.pathCount = Math.max(1, Math.min(pathCount, 100));
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
        for (Vec2d pos : previewPositions) {
            context.drawCircle(pos, 3.0, new Color(0, 255, 0, 180)); // 绿色圆点
        }

        // 渲染辅助线
        if (arrayState == ArrayState.AWAIT_SECOND_POINT && basePoint != null && currentPoint != null) {
            context.drawDashedLine(basePoint, currentPoint, new Color(0, 255, 0, 180)); // 绿色虚线
        }
    }

    /**
     * 渲染阵列预览（ImGui版本）
     */
    private void renderArrayPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        try {
            // 渲染预览位置点
            for (Vec2d pos : previewPositions) {
                Vec2d screenPos = camera.worldToScreen(pos);
                drawList.addCircleFilled(
                    (float) screenPos.x, (float) screenPos.y, 3.0f,
                    0xFF00FF00 // 绿色
                );
            }

            // 渲染辅助线
            if (arrayState == ArrayState.AWAIT_SECOND_POINT && basePoint != null && currentPoint != null) {
                Vec2d screenStart = camera.worldToScreen(basePoint);
                Vec2d screenEnd = camera.worldToScreen(currentPoint);
                drawList.addLine(
                    (float) screenStart.x, (float) screenStart.y,
                    (float) screenEnd.x, (float) screenEnd.y,
                    0xFF00FF00, 2.0f // 绿色线
                );
            }
        } catch (Exception e) {
            LOGGER.warn("渲染阵列预览时出错: {}", e.getMessage());
        }
    }
}