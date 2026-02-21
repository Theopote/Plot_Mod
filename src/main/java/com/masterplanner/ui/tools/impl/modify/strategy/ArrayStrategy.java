package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.api.geometry.util.PathUtils;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.graphics.style.ShapeStyle;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.tools.impl.modify.helper.ArrayHandler;
import com.masterplanner.ui.tools.impl.modify.dto.ModifyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 阵列策略实现 - 策略模式版本
 * 
 * <p>处理图形阵列复制的交互逻辑，支持：</p>
 * <ul>
 *   <li>矩形阵列：按行列排列图形</li>
 *   <li>环形阵列：按圆形排列图形</li>
 *   <li>路径阵列：沿指定路径排列图形</li>
 *   <li>可调节的间距、数量和角度</li>
 * </ul>
 * 
 * <p><strong>交互流程：</strong></p>
 * <ol>
 *   <li>选择要复制的源图形</li>
 *   <li>设置阵列基准点</li>
 *   <li>根据阵列类型设置参数</li>
 *   <li>预览并确认阵列效果</li>
 * </ol>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 阵列策略
 */
public class ArrayStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayStrategy.class);
    private static final double GEOMETRY_EPS = 1e-9;

    private static class PathOffsetRelation {
        final double baseTangentAngle;
        final double signedDistance;

        PathOffsetRelation(double baseTangentAngle, double signedDistance) {
            this.baseTangentAngle = baseTangentAngle;
            this.signedDistance = signedDistance;
        }
    }
    
    // 鼠标按键常量
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    
    // 键盘按键常量
    private static final int ESC_KEY = 27;
    private static final int R_KEY = 82; // R键 - 矩形阵列
    private static final int C_KEY = 67; // C键 - 环形阵列
    private static final int P_KEY = 80; // P键 - 路径阵列
    private static final int UP_KEY = 38; // ↑ 增加行数
    private static final int DOWN_KEY = 40; // ↓ 减少行数
    private static final int LEFT_KEY = 37; // ← 减少列数
    private static final int RIGHT_KEY = 39; // → 增加列数
    
    // 选择容差
    private static final double SELECTION_TOLERANCE = 5.0;
    
    // 性能优化：预览更新频率控制
    private long lastPreviewUpdate = 0;
    private static final long PREVIEW_UPDATE_INTERVAL = 16; // 60fps
    
    /**
     * 阵列类型枚举
     */
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
    
    /**
     * 阵列状态枚举
     */
    private enum ArrayState {
        IDLE(),
        SOURCE_SELECTED(),
        BASE_SET(),
        PREVIEWING();

        ArrayState() {
        }
    }
    
    // 策略状态
    private ArrayType currentType = ArrayType.RECTANGULAR;
    private ArrayState currentState = ArrayState.IDLE;
    
    // 阵列配置
    private int rowCount = 2;
    private int columnCount = 2;
    private double spacing = 50.0; // 列间距（矩形阵列）
    private double rowSpacing = 50.0; // 行间距（矩形阵列）
    private double angle = 45.0; // 环形阵列角度间隔
    private double radius = 100.0; // 环形阵列半径
    
    // 交互状态
    private Shape sourceShape;
    private Vec2d basePoint;
    private Vec2d currentPoint;
    private Vec2d endPoint;
    private final List<Vec2d> pathPoints = new ArrayList<>(); // 路径阵列的路径点
    private boolean waitingPickPath = false;
    private boolean waitingPickObjects = false;
    
    // 预览数据
    private final List<Vec2d> previewPositions = new ArrayList<>();
    private final List<Double> previewAngles = new ArrayList<>(); // 与位置对应的旋转角（弧度），仅环形使用
    private double pathBaseTangentAngle = 0.0;
    
    // 性能缓存
    private Vec2d cachedBasePoint;
    private int cachedRowCount;
    private int cachedColumnCount;
    private double cachedSpacing;
    private double cachedRowSpacing;
    private double cachedRadius;
    private double cachedAngle;
    private boolean previewNeedsUpdate = true;
    
    // 处理器和命令状态
    private ArrayHandler arrayHandler;
    private ModifyCommand pendingCommand;

    /**
     * 构造函数（依赖注入方式）
     * @param arrayHandler 阵列处理器
     */
    public ArrayStrategy(ArrayHandler arrayHandler) {
        this.arrayHandler = arrayHandler;
        LOGGER.debug("ArrayStrategy 已创建（依赖注入），类型: {}", currentType.getDisplayName());
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            if (button == MOUSE_RIGHT && currentState != ArrayState.IDLE) {
                // 右键取消阵列
                reset();
                context.setStatusMessage("阵列已取消");
                return ModifyResult.CANCEL;
            }
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

            // 路径阵列：拾取流程优先处理
            if (currentType == ArrayType.PATH) {
                if (waitingPickPath) {
                    Shape pathShape = context.findShapeAt(snappedPoint, SELECTION_TOLERANCE);
                    if (pathShape != null) {
                        try {
                            List<Vec2d> pts = pathShape.getPoints();
                            if (pts != null && pts.size() >= 2) {
                                pathPoints.clear();
                                pathPoints.addAll(pts);
                                waitingPickPath = false;
                                currentState = ArrayState.PREVIEWING;
                                updateArrayPreview();
                                context.setPreviewEnabled(true);
                                context.setStatusMessage("已拾取路径，调整点位数（含起终点，沿路径等距）后点击完成");
                            } else {
                                context.setStatusMessage("所选对象无法作为路径（点数不足）");
                            }
                            return ModifyResult.CONTINUE;
                        } catch (Exception e) {
                            context.setStatusMessage("无法获取路径点");
                            return ModifyResult.CONTINUE;
                        }
                    } else {
                        context.setStatusMessage("未选中路径对象");
                        return ModifyResult.CONTINUE;
                    }
                }
                if (waitingPickObjects) {
                    ModifyResult r = selectSourceShape(snappedPoint, context);
                    if (r == ModifyResult.CONTINUE) {
                        waitingPickObjects = false;
                        // 若已有路径，则直接进入预览
                        if (!pathPoints.isEmpty()) {
                            currentState = ArrayState.PREVIEWING;
                            updateArrayPreview();
                        }
                        context.setPreviewEnabled(true);
                        context.setStatusMessage("已拾取物件，调整点位数（含起终点，沿路径等距）后点击完成");
                    }
                    return ModifyResult.CONTINUE;
                }
            }
            
            switch (currentState) {
                case IDLE -> {
                    return selectSourceShape(snappedPoint, context);
                }
                case SOURCE_SELECTED -> {
                    return setBasePoint(snappedPoint, context);
                }
                case BASE_SET -> {
                    return setArrayParameters(snappedPoint, context);
                }
                case PREVIEWING -> {
                    return confirmArray(context);
                }
                default -> {
                    return ModifyResult.IGNORED;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("阵列策略鼠标按下处理失败: {}", e.getMessage(), e);
            reset();
            return ModifyResult.CANCEL;
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        try {
            // 获取吸附后的点
            currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            if (currentState == ArrayState.BASE_SET) {
                // 性能优化：限制预览更新频率
                updateArrayPreviewWithThrottling();
                context.setPreviewEnabled(true);
            }
            
            return ModifyResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("阵列策略鼠标移动处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE; // 移动失败不应该取消操作
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            return ModifyResult.IGNORED;
        }
        
        try {
            // 获取吸附后的点
            endPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
            
            // 鼠标释放时通常不需要特殊处理，主要逻辑在onMouseDown中
            return ModifyResult.CONTINUE;
            
        } catch (Exception e) {
            LOGGER.error("阵列策略鼠标释放处理失败: {}", e.getMessage(), e);
            return ModifyResult.CONTINUE;
        }
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        if (keyCode == ESC_KEY) {
            reset();
            context.setStatusMessage("阵列已取消");
            return ModifyResult.CANCEL;
        }
        
        // 快捷键切换阵列类型
        ArrayType newType = getArrayTypeFromKey(keyCode);
        if (newType != null) {
            setArrayType(newType);
            context.setStatusMessage(String.format("已切换到%s模式", newType.getDisplayName()));
            return ModifyResult.CONTINUE;
        }
        
        // 新增快捷键：参数调整
        switch (keyCode) {
            case UP_KEY -> {
                if (currentType == ArrayType.RECTANGULAR) {
                    setRowCount(getRowCount() + 1);
                    context.setStatusMessage("行数已增加");
                    return ModifyResult.CONTINUE;
                }
            }
            case DOWN_KEY -> {
                if (currentType == ArrayType.RECTANGULAR) {
                    setRowCount(Math.max(1, getRowCount() - 1));
                    context.setStatusMessage("行数已减少");
                    return ModifyResult.CONTINUE;
                }
            }
            case LEFT_KEY -> {
                if (currentType == ArrayType.RECTANGULAR) {
                    setColumnCount(Math.max(1, getColumnCount() - 1));
                    context.setStatusMessage("列数已减少");
                    return ModifyResult.CONTINUE;
                }
            }
            case RIGHT_KEY -> {
                if (currentType == ArrayType.RECTANGULAR) {
                    setColumnCount(getColumnCount() + 1);
                    context.setStatusMessage("列数已增加");
                    return ModifyResult.CONTINUE;
                }
            }
        }
        
        return ModifyResult.IGNORED;
    }
    
    /**
     * 根据按键获取阵列类型
     */
    private ArrayType getArrayTypeFromKey(int keyCode) {
        return switch (keyCode) {
            case R_KEY -> ArrayType.RECTANGULAR;
            case C_KEY -> ArrayType.CIRCULAR;
            case P_KEY -> ArrayType.PATH;
            default -> null;
        };
    }
    
    /**
     * 选择源图形
     */
    private ModifyResult selectSourceShape(Vec2d point, ModifyToolContext context) {
        // 查找点击位置的图形
        Shape clickedShape = context.findShapeAt(point, SELECTION_TOLERANCE);
        if (clickedShape == null) {
            context.setStatusMessage("请点击要创建阵列的图形");
            return ModifyResult.CONTINUE;
        }

        sourceShape = clickedShape;
        context.setPreviewEnabled(true);

        // 对于矩形/环形：允许在图形外任何位置设基点
        // 因此状态转为 SOURCE_SELECTED，等待用户点任意位置作为 basePoint
        currentState = ArrayState.SOURCE_SELECTED;
        context.setStatusMessage("点击任意位置设置阵列基准/中心点");
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 设置基准点
     */
    private ModifyResult setBasePoint(Vec2d point, ModifyToolContext context) {
        basePoint = point;
        context.setPreviewEnabled(true);

        switch (currentType) {
            case RECTANGULAR -> {
                // 直接进入预览，行/列数与间距由面板调整
                currentState = ArrayState.PREVIEWING;
                updateArrayPreview();
                updateSmartStatusMessage(context);
                return ModifyResult.CONTINUE;
            }
            case CIRCULAR -> {
                // 默认半径取源图形位置到中心的距离，等角分布
                if (sourceShape != null) {
                    radius = basePoint.distance(getShapeCenter(sourceShape));
                }
                currentState = ArrayState.PREVIEWING;
                updateArrayPreview();
                updateSmartStatusMessage(context);
                return ModifyResult.CONTINUE;
            }
            case PATH -> {
                currentState = ArrayState.BASE_SET;
                updateSmartStatusMessage(context);
                return ModifyResult.CONTINUE;
            }
            default -> {
                return ModifyResult.CONTINUE;
            }
        }
    }
    
    /**
     * 设置阵列参数
     */
    private ModifyResult setArrayParameters(Vec2d point, ModifyToolContext context) {
        switch (currentType) {
            case RECTANGULAR -> {
                return setRectangularParameters(point, context);
            }
            case CIRCULAR -> {
                return setCircularParameters(point, context);
            }
            case PATH -> {
                // 路径阵列由“拾取路径/拾取物件”按钮驱动，此处不再通过点击加点
                context.setStatusMessage("请使用面板按钮拾取路径与物件，然后按路径等距点位数量点击完成");
                return ModifyResult.CONTINUE;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
    /**
     * 设置矩形阵列参数
     */
    private ModifyResult setRectangularParameters(Vec2d point, ModifyToolContext context) {
        if (basePoint != null) {
            Vec2d offset = point.subtract(basePoint);
            // 仅作为直观初值：用当前拖拽设置 rowSpacing/spacing 的初始值，不再自动算数量
            spacing = Math.max(10.0, Math.abs(offset.x));
            rowSpacing = Math.max(10.0, Math.abs(offset.y));
            
            currentState = ArrayState.PREVIEWING;
            updateArrayPreview();
            
            context.setStatusMessage(String.format("矩形阵列: %dx%d, 行间距: %.1f, 列间距: %.1f，点完成", 
                rowCount, columnCount, rowSpacing, spacing));
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 设置环形阵列参数
     */
    private ModifyResult setCircularParameters(Vec2d point, ModifyToolContext context) {
        if (basePoint != null) {
            radius = basePoint.distance(point);
            angle = 360.0 / Math.max(3, rowCount); // 至少3个图形
            
            currentState = ArrayState.PREVIEWING;
            updateArrayPreview();
            
            context.setStatusMessage(String.format("环形阵列: %d个, 半径: %.1f, 点击确认", 
                rowCount, radius));
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    /**
     * 确认阵列
     */
    private ModifyResult confirmArray(ModifyToolContext context) {
        // 检查是否可以确认阵列
        if (!canConfirmArray()) {
            context.setStatusMessage("无法确认阵列：缺少必要条件");
            return ModifyResult.CONTINUE;
        }
        
        // 创建阵列命令
        pendingCommand = createArrayCommand();
        
        if (pendingCommand != null) {
            context.setStatusMessage(String.format("阵列创建完成，共 %d 个图形", 
                previewPositions.size()));
            return ModifyResult.COMPLETE;
        } else {
            context.setStatusMessage("创建阵列命令失败");
            return ModifyResult.CANCEL;
        }
    }
    
    /**
     * 更新阵列预览
     */
    private void updateArrayPreview() {
        // 检查是否需要更新预览
        if (!previewNeedsUpdate && isPreviewCacheValid()) {
            return;
        }
        
        previewPositions.clear();
        previewAngles.clear();
        
        if (sourceShape == null || basePoint == null) {
            return;
        }
        
        switch (currentType) {
            case RECTANGULAR -> calculateRectangularArray();
            case CIRCULAR -> calculateCircularArray();
            case PATH -> calculatePathArray();
        }
        
        // 更新缓存
        updatePreviewCache();
        previewNeedsUpdate = false;
    }
    
    /**
     * 性能优化：带频率限制的预览更新
     */
    private void updateArrayPreviewWithThrottling() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPreviewUpdate < PREVIEW_UPDATE_INTERVAL) {
            return; // 跳过更新，避免过于频繁
        }
        
        updateArrayPreview();
        lastPreviewUpdate = currentTime;
    }
    
    /**
     * 检查预览缓存是否有效
     */
    private boolean isPreviewCacheValid() {
        return cachedBasePoint != null && 
               cachedBasePoint.equals(basePoint) &&
               cachedRowCount == rowCount &&
               cachedColumnCount == columnCount &&
               cachedSpacing == spacing &&
               cachedRowSpacing == rowSpacing &&
               cachedRadius == radius &&
               cachedAngle == angle;
    }
    
    /**
     * 更新预览缓存
     */
    private void updatePreviewCache() {
        cachedBasePoint = basePoint;
        cachedRowCount = rowCount;
        cachedColumnCount = columnCount;
        cachedSpacing = spacing;
        cachedRowSpacing = rowSpacing;
        cachedRadius = radius;
        cachedAngle = angle;
    }
    
    /**
     * 计算矩形阵列位置
     */
    private void calculateRectangularArray() {
        Vec2d sourcePos = getShapeCenter(sourceShape);
        Vec2d offset = basePoint.subtract(sourcePos);
        
        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < columnCount; col++) {
                if (row == 0 && col == 0) continue; // 跳过原始位置
                
                Vec2d arrayPos = sourcePos.add(new Vec2d(
                    offset.x + col * spacing,
                    offset.y + row * rowSpacing
                ));
                previewPositions.add(arrayPos);
                previewAngles.add(0.0);
            }
        }
    }
    
    /**
     * 计算环形阵列位置
     */
    private void calculateCircularArray() {
        // 与 ArrayHandler 保持一致：以源图形当前位置为起始角度，可选地使用面板传入的角度步长（度）
        int count = Math.max(1, rowCount);
        Vec2d sourcePos = getShapeCenter(sourceShape);
        double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);

        // 面板中的 angle 字段以度为单位，优先使用它作为步长；否则按数量等分 2π
        double angleStepRad;
        if (Double.isFinite(angle) && angle > 0.0) {
            angleStepRad = Math.toRadians(angle);
        } else {
            angleStepRad = (2 * Math.PI) / count;
        }

        for (int i = 1; i < count; i++) { // 从1开始，跳过原始位置
            double currentAngle = startAngle + i * angleStepRad;
            Vec2d arrayPos = basePoint.add(new Vec2d(
                radius * Math.cos(currentAngle),
                radius * Math.sin(currentAngle)
            ));
            previewPositions.add(arrayPos);
            previewAngles.add(currentAngle);
        }
    }
    
    /**
     * 计算路径阵列位置
     */
    private void calculatePathArray() {
        if (pathPoints.size() < 2) {
            return;
        }
        
        int count = Math.max(2, rowCount);
        // 沿路径等间距分布，支持沿路径切线旋转
        double totalLength = calculatePathLength();
        if (totalLength <= GEOMETRY_EPS) {
            return;
        }

        double stepLength = totalLength / (count - 1);
        Vec2d sourceCenter = getShapeCenter(sourceShape);
        PathOffsetRelation relation = calculatePathOffsetRelation(sourceCenter);
        pathBaseTangentAngle = relation.baseTangentAngle;
        
        for (int i = 0; i < count; i++) { // 全路径均分：包含起点与终点
            double targetLength = i * stepLength;
            double clampedLength = Math.max(0.0, Math.min(targetLength, totalLength));
            Vec2d pathPos = getPositionAtLength(clampedLength);
            if (pathPos != null) {
                double tangentAngle = calculatePathTangentAngle(clampedLength);
                Vec2d normal = new Vec2d(-Math.sin(tangentAngle), Math.cos(tangentAngle));
                Vec2d arrayPos = pathPos.add(normal.multiply(relation.signedDistance));
                previewPositions.add(arrayPos);
                
                // 计算路径切线角度
                previewAngles.add(tangentAngle);
            }
        }
    }
    
    /**
     * 计算路径上指定长度处的切线角度
     */
    private double calculatePathTangentAngle(double targetLength) {
        if (pathPoints.size() < 2) {
            return 0.0;
        }

        double totalLength = calculatePathLength();
        if (totalLength <= GEOMETRY_EPS) {
            return 0.0;
        }

        double clampedLength = Math.max(0.0, Math.min(targetLength, totalLength));
        
        // 找到目标长度所在的路径段
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
                // 命中拐点时优先取下一有效段方向
                for (int j = i + 1; j < pathPoints.size(); j++) {
                    Vec2d nextPrev = pathPoints.get(j - 1);
                    Vec2d nextCurr = pathPoints.get(j);
                    Vec2d nextDir = nextCurr.subtract(nextPrev);
                    if (nextDir.length() > GEOMETRY_EPS) {
                        return Math.atan2(nextDir.y, nextDir.x);
                    }
                }
                return Math.atan2(direction.y, direction.x);
            }
            
            accumulatedLength = segmentEnd;
        }
        
        // 如果超出路径长度，使用最后一段的方向
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
    
    /**
     * 计算路径总长度
     */
    private double calculatePathLength() {
        return PathUtils.calculatePathLength(pathPoints);
    }
    
    /**
     * 获取路径上指定长度处的位置
     */
    private Vec2d getPositionAtLength(double targetLength) {
        return PathUtils.getPositionAtLength(pathPoints, targetLength);
    }
    
    /**
     * 创建阵列命令
     */
    private ModifyCommand createArrayCommand() {
        try {
            if (sourceShape != null && !previewPositions.isEmpty()) {
                // 使用ArrayHandler创建命令
                if (arrayHandler != null) {
                    List<Shape> sourceShapes = List.of(sourceShape);
                    
                    // 创建参数对象
                    ModifyParameters parameters = new ModifyParameters();
                    parameters.setString("arrayType", currentType.name());
                    parameters.setVec2d("basePoint", basePoint);
                    parameters.setInt("rowCount", rowCount);
                    parameters.setInt("columnCount", columnCount);
                    parameters.setDouble("rowSpacing", rowSpacing);
                    parameters.setDouble("columnSpacing", spacing);
                    parameters.setDouble("radius", radius);
                    parameters.setDouble("angleStep", angle);
                    
                    // 如果有路径点，添加到参数中
                    if (!pathPoints.isEmpty()) {
                        parameters.setParameter("pathPoints", new ArrayList<>(pathPoints));
                    }
                    
                    // 使用ArrayHandler计算修改后的图形
                    List<Shape> arrayedShapes = arrayHandler.calculateModifiedShapes(sourceShapes, parameters);
                    
                    if (arrayedShapes != null && !arrayedShapes.isEmpty()) {
                        return arrayHandler.createModifyCommand(sourceShapes, arrayedShapes, parameters);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("创建阵列命令失败: {}", e.getMessage(), e);
        }
        return null;
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    /**
     * 供面板“完成”按钮调用：尝试构建命令
     */
    public ModifyCommand buildArrayCommand() {
        pendingCommand = createArrayCommand();
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        currentState = ArrayState.IDLE;
        sourceShape = null;
        basePoint = null;
        currentPoint = null;
        endPoint = null;
        pathPoints.clear();
        previewPositions.clear();
        previewAngles.clear();
        pendingCommand = null;
        
        // 清除缓存
        cachedBasePoint = null;
        previewNeedsUpdate = true;
        
        LOGGER.debug("阵列策略已重置");
    }
    
    @Override
    public String getStrategyName() {
        return "阵列策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return "用于创建图形阵列复制的策略，支持多种阵列类型";
    }
    
    @Override
    public boolean requiresSelection() {
        return false; // 阵列工具在交互过程中选择图形
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 1; // 至少需要1个图形作为源
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return 1; // 一次只能对一个图形创建阵列
    }
    
    // ====== 配置方法 ======
    
    /**
     * 设置阵列类型
     */
    public void setArrayType(ArrayType type) {
        if (this.currentType != type) {
            this.currentType = type;
            // 重置路径点（如果切换到非路径模式）
            if (type != ArrayType.PATH) {
                pathPoints.clear();
            }
            LOGGER.debug("阵列类型已切换为: {}", type.getDisplayName());
        }
    }
    
    /**
     * 获取当前类型
     */
    public ArrayType getCurrentType() {
        return currentType;
    }
    
    /**
     * 设置行数
     */
    public void setRowCount(int count) {
        this.rowCount = Math.max(1, count);
        LOGGER.debug("行数已设置为: {}", this.rowCount);
    }
    
    /**
     * 获取行数
     */
    public int getRowCount() {
        return rowCount;
    }
    
    /**
     * 设置列数
     */
    public void setColumnCount(int count) {
        this.columnCount = Math.max(1, count);
        LOGGER.debug("列数已设置为: {}", this.columnCount);
    }
    
    /**
     * 获取列数
     */
    public int getColumnCount() {
        return columnCount;
    }
    
    /**
     * 设置间距
     */
    public void setSpacing(double spacing) {
        this.spacing = Math.max(1.0, spacing);
        LOGGER.debug("间距已设置为: {}", this.spacing);
    }
    
    public void setRowSpacing(double spacing) {
        this.rowSpacing = Math.max(1.0, spacing);
        LOGGER.debug("行间距已设置为: {}", this.rowSpacing);
    }
    
    /**
     * 获取间距
     */
    public double getSpacing() {
        return spacing;
    }
    
    public double getRowSpacing() {
        return rowSpacing;
    }
    
    /**
     * 设置角度（环形阵列）
     */
    public void setAngle(double angle) {
        this.angle = angle;
        LOGGER.debug("角度已设置为: {}", this.angle);
    }
    
    /**
     * 获取角度
     */
    public double getAngle() {
        return angle;
    }
    
    /**
     * 设置半径（环形阵列）
     */
    public void setRadius(double radius) {
        this.radius = Math.max(1.0, radius);
        LOGGER.debug("半径已设置为: {}", this.radius);
    }
    
    /**
     * 获取半径
     */
    public double getRadius() {
        return radius;
    }
    
    // ====== 渲染方法 ======
    
    /**
     * 渲染预览
     */
    public void renderPreview(DrawContext context) {
        if (context == null) {
            return;
        }
        
        try {
            // 高亮显示源图形
            if (sourceShape != null) {
                renderSourceHighlight(context);
            }
            
            // 渲染基准点
            if (basePoint != null) {
                renderBasePoint(context);
            }
            
            // 渲染阵列预览
            if (!previewPositions.isEmpty()) {
                renderArrayPreview(context);
            }
            
            // 渲染路径（路径阵列）
            if (currentType == ArrayType.PATH && !pathPoints.isEmpty()) {
                renderPathPreview(context);
            }
            
        } catch (Exception e) {
            LOGGER.warn("阵列预览渲染失败: {}", e.getMessage());
        }
    }
    
    /**
     * 配置变更后刷新预览
     */
    public void refreshPreviewAfterConfigChange() {
        if (sourceShape == null) return;
        if (currentType == ArrayType.PATH && pathPoints.size() < 2) return;
        if ((currentType == ArrayType.RECTANGULAR || currentType == ArrayType.CIRCULAR) && basePoint == null) return;
        
        // 标记预览需要更新
        previewNeedsUpdate = true;
        updateArrayPreview();
    }
    
    /** 启动拾取路径模式（下一次点击选择路径图形） */
    public void beginPickPath() { waitingPickPath = true; waitingPickObjects = false; }
    /** 启动拾取物件模式（下一次点击选择源图形） */
    public void beginPickObjects() { waitingPickObjects = true; waitingPickPath = false; }
    
    /**
     * 渲染源图形高亮
     */
    private void renderSourceHighlight(DrawContext context) {
        // 这里需要根据具体的Shape接口来实现高亮渲染
        // sourceShape.drawHighlight(context, highlightColor);
    }
    
    /**
     * 渲染基准点
     */
    private void renderBasePoint(DrawContext context) {
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color pointColor = withAlpha(toColor(theme.errorText), 200);
        context.drawCircleFilled(basePoint, 4.0f, pointColor);
        context.drawCircleOutline(basePoint, 6.0f, toColor(theme.text));
    }
    
    /**
     * 渲染阵列预览
     */
    private void renderArrayPreview(DrawContext context) {
        if (sourceShape == null) return;
        var theme = ThemeManager.getInstance().getCurrentTheme();
        Color fallbackOutlineColor = withAlpha(toColor(theme.infoText), 100);
        Color pathDirectionColor = withAlpha(toColor(theme.accent), 220);

        for (int i = 0; i < previewPositions.size(); i++) {
            Vec2d pos = previewPositions.get(i);
            double ang = (i < previewAngles.size()) ? previewAngles.get(i) : 0.0;
            try {
                Shape clone = sourceShape.clone();
                if (currentType == ArrayType.CIRCULAR) {
                    // 使用与 ArrayHandler 相同的逻辑：平移并按角度增量旋转（调用子类实现）
                    Vec2d sourcePos = getShapeCenter(sourceShape);
                    double startAngle = Math.atan2(sourcePos.y - basePoint.y, sourcePos.x - basePoint.x);
                    double delta = ang - startAngle; // ang 在 previewAngles 中为绝对角度

                    Vec2d cloneCenter = getShapeCenter(clone);
                    Vec2d offset = pos.subtract(cloneCenter);
                    if (offset.length() > 1e-6) clone.translate(offset);
                    if (Math.abs(delta) > 1e-9) clone.rotate(delta, pos);
                } else if (currentType == ArrayType.PATH) {
                    Vec2d cloneCenter = getShapeCenter(clone);
                    Vec2d offset = pos.subtract(cloneCenter);
                    if (offset.length() > 1e-6) clone.translate(offset);

                    double delta = ang - pathBaseTangentAngle;
                    if (Math.abs(delta) > 1e-9) clone.rotate(delta, pos);
                } else {
                    // 其他阵列类型仅平移
                    Vec2d cloneCenter = getShapeCenter(clone);
                    Vec2d offset = pos.subtract(cloneCenter);
                    if (offset.length() > 1e-6) clone.translate(offset);
                }
                clone.setStyle(ShapeStyle.PREVIEW);
                // 修复：使用render方法而不是draw方法，确保应用正确的样式
                clone.render(context);
            } catch (Exception e) {
                context.drawCircleOutline(pos, 8.0f, fallbackOutlineColor);
            }

            if (currentType == ArrayType.PATH && i < previewAngles.size()) {
                double tangentAngle = previewAngles.get(i);
                Vec2d tip = pos.add(new Vec2d(12.0 * Math.cos(tangentAngle), 12.0 * Math.sin(tangentAngle)));
                context.drawLine(pos, tip, pathDirectionColor);
            }
        }
    }
    
    /**
     * 渲染路径预览
     */
    private void renderPathPreview(DrawContext context) {
        if (pathPoints.size() < 2) {
            return;
        }
        
        Color pathColor = withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().warningText), 200);
        
        // 绘制路径线段
        for (int i = 1; i < pathPoints.size(); i++) {
            context.drawLine(pathPoints.get(i - 1), pathPoints.get(i), pathColor);
        }
        
        // 绘制路径点
        for (Vec2d point : pathPoints) {
            context.drawCircleFilled(point, 3.0f, pathColor);
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
     * 智能状态消息更新
     */
    public void updateSmartStatusMessage(ModifyToolContext context) {
        if (context == null) return;
        
        if (sourceShape == null) {
            context.setStatusMessage("点击选择要阵列的图形");
            return;
        }
        
        if (currentType != ArrayType.PATH && basePoint == null) {
            context.setStatusMessage("点击设置阵列基准点");
            return;
        }
        
        switch (currentType) {
            case RECTANGULAR -> context.setStatusMessage(String.format("矩形阵列预览中：%dx%d，行间距%.1f，列间距%.1f",
                getRowCount(), getColumnCount(), getRowSpacing(), getSpacing()));
            case CIRCULAR -> context.setStatusMessage(String.format("环形阵列预览中：%d个，半径%.1f",
                getRowCount(), getRadius()));
            case PATH -> {
                if (pathPoints.size() < 2) {
                    context.setStatusMessage("请使用面板拾取路径对象");
                } else {
                    context.setStatusMessage(String.format("路径阵列预览中：点位数%d（含起终点，沿路径等距），路径长度%.1f", 
                        getRowCount(), calculatePathLength()));
                }
            }
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

    private PathOffsetRelation calculatePathOffsetRelation(Vec2d sourceCenter) {
        if (sourceCenter == null || pathPoints.size() < 2) {
            return new PathOffsetRelation(0.0, 0.0);
        }

        double minDistance = Double.POSITIVE_INFINITY;
        double bestAngle = 0.0;
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
                double signed = tangent.x * toSource.y - tangent.y * toSource.x;

                minDistance = distance;
                bestSignedDistance = signed;
                bestAngle = Math.atan2(tangent.y, tangent.x);
            }
        }

        if (!Double.isFinite(minDistance)) {
            return new PathOffsetRelation(calculatePathTangentAngle(0.0), 0.0);
        }

        return new PathOffsetRelation(bestAngle, bestSignedDistance);
    }

    /**
     * 检查是否可以确认阵列
     */
    public boolean canConfirmArray() {
        if (sourceShape == null) return false;
        
        switch (currentType) {
            case RECTANGULAR, CIRCULAR -> {
                return basePoint != null;
            }
            case PATH -> {
                return pathPoints.size() >= 2 && Math.max(2, rowCount) >= 2;
            }
            default -> {
                return false;
            }
        }
    }
}
