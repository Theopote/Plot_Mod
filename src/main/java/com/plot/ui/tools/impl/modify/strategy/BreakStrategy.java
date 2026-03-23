package com.plot.ui.tools.impl.modify.strategy;

import com.plot.api.geometry.Vec2d;
import com.plot.core.command.commands.ModifyCommand;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.geometry.shapes.FreeDrawPath;
import com.plot.core.geometry.shapes.BezierCurveShape;
import com.plot.core.geometry.shapes.CableShape;
import com.plot.core.geometry.shapes.SineCurveShape;
import com.plot.core.geometry.shapes.SpiralShape;
import com.plot.core.model.Shape;
import com.plot.ui.tools.impl.modify.helper.IModifyHandler;
import com.plot.ui.tools.impl.modify.dto.ModifyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 打断策略实现 - 策略模式版本
 * 
 * <p>处理图形打断的交互逻辑，支持：</p>
 * <ul>
 *   <li>单点打断模式：在指定点打断图形</li>
 *   <li>两点打断模式：在两点间移除图形部分</li>
 *   <li>连续打断模式：连续打断多个图形</li>
 *   <li>实时预览效果</li>
 * </ul>
 * 
 * <p><strong>交互流程：</strong></p>
 * <ol>
 *   <li>点击选择要打断的图形</li>
 *   <li>点击设置打断点（单点模式）或第一个打断点（两点模式）</li>
 *   <li>两点模式下再点击设置第二个打断点</li>
 *   <li>自动执行打断操作</li>
 * </ol>
 * 
 * @author Plot Team
 * @version 1.0 - 打断策略
 */
public class BreakStrategy implements IModifyStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(BreakStrategy.class);
    
    // 打断模式枚举
    public enum BreakMode {
        SINGLE_POINT("单点打断", "在指定点打断图形"),
        TWO_POINT("两点打断", "在两点间移除图形部分");
        
        private final String displayName;
        private final String description;
        
        BreakMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 打断状态枚举
    public enum BreakState {
        SELECTING_SHAPE("选择图形", "点击选择要打断的图形"),
        SETTING_SECOND_POINT("设置第二点", "点击设置第二个打断点"),
        PROCESSING("处理中", "正在执行打断操作");
        
        private final String displayName;
        private final String description;
        
        BreakState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    // 常量
    private static final double SELECTION_TOLERANCE = 5.0;
    private static final int MOUSE_LEFT = 0;
    private static final int ESC_KEY = 27;
    private static final int T_KEY = 84;
    
    // 策略状态
    private BreakMode currentMode = BreakMode.SINGLE_POINT;
    private BreakState currentState = BreakState.SELECTING_SHAPE;
    private Shape targetShape;
    private Vec2d firstBreakPoint;
    private Vec2d secondBreakPoint;
    private Shape highlightedShape;
    private Vec2d currentMousePoint;
    
    // 处理器和参数
    private ModifyParameters breakParameters;
    private ModifyCommand pendingCommand;
    // 断开瞬间的视觉反馈（红叉）
    @SuppressWarnings("unused")
    private Vec2d lastBreakMark;
    @SuppressWarnings("unused")
    private long lastBreakMarkAtMs;
    @SuppressWarnings("unused")
    private static final long BREAK_MARK_DURATION_MS = 1500; // 1.5秒
    
    /**
     * 默认构造函数
     */
    public BreakStrategy() {
        this.breakParameters = new ModifyParameters();
    }
    
    /**
     * 设置打断模式
     */
    public void setBreakMode(BreakMode mode) {
        if (this.currentMode != mode) {
            reset(); // 切换模式时重置状态
            this.currentMode = mode;
            LOGGER.debug("打断模式已切换为: {}", mode.getDisplayName());
        }
    }

    @Override
    public ModifyResult onMouseDown(Vec2d pos, int button, ModifyToolContext context) {
        if (button != MOUSE_LEFT) {
            return ModifyResult.IGNORED;
        }
        
        switch (currentState) {
            case SELECTING_SHAPE -> {
                // 使用捕捉后的世界坐标
                Vec2d worldPos = resolveWorldPoint(pos, context);
                // 直接点选形状，无框选
                Shape clickedShape = findShapeAtPoint(worldPos, context);
                if (clickedShape == null) {
                    context.setStatusMessage("未找到可打断对象");
                    return ModifyResult.IGNORED;
                }
                
                // 验证是否支持打断操作

                targetShape = clickedShape;
                targetShape.setHighlighted(true);
                
                // 投影点击点到目标图形上（世界坐标）
                Vec2d projectedPoint = projectPointOnTargetShape(worldPos);
                
                if (currentMode == BreakMode.SINGLE_POINT) {
                    // 单点模式：验证打断点是否有效（使用相机像素换算的世界容差）
                    if (isValidBreakPoint(projectedPoint, targetShape, context)) {
                        firstBreakPoint = projectedPoint;
                        return performBreakOperation(context);
                    } else {
                        context.setStatusMessage("点击位置无效，请重新选择");
                        return ModifyResult.CONTINUE;
                    }
                } else {
                    // 两点模式：设置第一个打断点
                    if (isValidBreakPoint(projectedPoint, targetShape, context)) {
                        firstBreakPoint = projectedPoint;
                        currentState = BreakState.SETTING_SECOND_POINT;
                        context.setStatusMessage("点击设置第二个打断点");
                        LOGGER.debug("两点模式：已设置第一点 {}，等待第二点", firstBreakPoint);
                    } else {
                        context.setStatusMessage("第一个点击位置无效，请重新选择");
                    }
                    return ModifyResult.CONTINUE;
                }
            }
            case SETTING_SECOND_POINT -> {
                return setSecondBreakPoint(pos, context);
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d pos, ModifyToolContext context) {
        // 记录当前鼠标位置用于视觉预览（优先捕捉后的世界坐标）
        Vec2d worldPos = resolveWorldPoint(pos, context);
		currentMousePoint = worldPos;
        
        switch (currentState) {
            case SELECTING_SHAPE -> {
                // 高亮鼠标下的可选图形
                Shape shape = findShapeAtPoint(worldPos, context);
                updateHighlight(shape);
                return shape != null ? ModifyResult.CONTINUE : ModifyResult.IGNORED;
            }
            case SETTING_SECOND_POINT -> {
                if (currentMode == BreakMode.TWO_POINT) {
                    // 修复：使用与点击时相同的宽松验证逻辑
                    Vec2d projected = projectPointOnTargetShape(worldPos);
                    if (isValidBreakPointForTwoPoint(projected, targetShape, context)) {
                        // 检查与第一个点的距离，避免过近预览
                        if (!areBreakPointsTooClose(firstBreakPoint, projected, context)) {
                            secondBreakPoint = projected;
                            currentMousePoint = projected; // 让预览更稳定
                        } else {
                            // 两点过近时不显示预览，但保持交互状态
                            secondBreakPoint = null;
                            currentMousePoint = worldPos;
                        }
                    } else {
                        // 尝试使用最近点作为备选
                        Vec2d closestPoint = targetShape.getClosestPoint(worldPos);
                        if (isValidBreakPointForTwoPoint(closestPoint, targetShape, context)) {
                            if (!areBreakPointsTooClose(firstBreakPoint, closestPoint, context)) {
                                secondBreakPoint = closestPoint;
                                currentMousePoint = closestPoint;
                            } else {
                                secondBreakPoint = null;
                                currentMousePoint = worldPos;
                            }
                        } else {
                            secondBreakPoint = null;
                            currentMousePoint = worldPos;
                        }
                    }
                    return ModifyResult.CONTINUE;
                }
                return ModifyResult.IGNORED;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d pos, int button, ModifyToolContext context) {
        // 打断工具主要使用点击操作，不需要特殊的释放处理
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case ESC_KEY -> {
                reset();
                context.setStatusMessage(getInitialStatusMessage());
                return ModifyResult.CANCEL;
            }
            case T_KEY -> {
                // 切换打断模式
                BreakMode newMode = (currentMode == BreakMode.SINGLE_POINT) ? 
                    BreakMode.TWO_POINT : BreakMode.SINGLE_POINT;
                setBreakMode(newMode);
                
                String statusMessage = String.format("已切换到%s模式", newMode.getDisplayName());
                context.setStatusMessage(statusMessage);
                return ModifyResult.CONTINUE;
            }
            default -> {
                return ModifyResult.IGNORED;
            }
        }
    }
    
	private ModifyResult setSecondBreakPoint(Vec2d pos, ModifyToolContext context) {
        // 修复：使用与单点模式相同的优化逻辑
        // 1. 使用捕捉后的世界坐标
        Vec2d worldPos = resolveWorldPoint(pos, context);
        
        // 2. 先尝试投影到目标图形，然后验证投影结果
        Vec2d projectedSecondPoint = projectPointOnTargetShape(worldPos);
        
        // 3. 验证投影后的点是否在目标图形附近（使用更宽松的容差）
        if (!isValidBreakPointForTwoPoint(projectedSecondPoint, targetShape, context)) {
            // 4. 如果投影点无效，尝试直接检测是否点击了目标图形
            Shape shapeAtClick = findShapeAtPoint(worldPos, context);
            if (shapeAtClick == targetShape) {
                // 在目标图形上但投影失败，使用最近点作为断点
                Vec2d closestPoint = targetShape.getClosestPoint(worldPos);
                if (isValidBreakPointForTwoPoint(closestPoint, targetShape, context)) {
                    projectedSecondPoint = closestPoint;
                    LOGGER.debug("两点模式：使用最近点作为第二个断点 {}", closestPoint);
                } else {
                    context.setStatusMessage("请点击更靠近线条的位置设置第二个打断点");
                    return ModifyResult.CONTINUE;
                }
            } else {
                context.setStatusMessage("请在同一图形上点击设置第二个打断点");
                return ModifyResult.CONTINUE;
            }
        }
        
        secondBreakPoint = projectedSecondPoint;
        
        // 两点过近保护（使用更合理的最小距离）
        if (areBreakPointsTooClose(firstBreakPoint, secondBreakPoint, context)) {
            context.setStatusMessage("两个打断点太近，请选择更远的第二个点");
            return ModifyResult.CONTINUE;
        }
        
        // 两点模式：执行打断
        LOGGER.debug("两点模式：已设置第二点 {}，开始执行打断操作", secondBreakPoint);
        return performBreakOperation(context);
    }
    
    private ModifyResult performBreakOperation(ModifyToolContext context) {
        if (targetShape == null || firstBreakPoint == null) {
            context.setStatusMessage("打断操作参数不完整");
            return ModifyResult.CANCEL;
        }
        
        if (currentMode == BreakMode.TWO_POINT && secondBreakPoint == null) {
            context.setStatusMessage("两点打断模式需要设置第二个打断点");
            return ModifyResult.CANCEL;
        }
        
        currentState = BreakState.PROCESSING;
        
        try {
            LOGGER.debug("开始执行打断操作: 模式={}, 目标图形={}, 第一点={}, 第二点={}", 
                currentMode.getDisplayName(), 
                targetShape.getClass().getSimpleName(),
                firstBreakPoint,
                secondBreakPoint);
            
            // 创建打断参数（包含 appState）
            createBreakParameters(context);
            
            // 执行打断逻辑并创建命令
            pendingCommand = createBreakCommand(context);
            
            if (pendingCommand != null) {
                LOGGER.debug("打断操作成功: 模式={}, 创建了命令", currentMode.getDisplayName());
                context.setStatusMessage(String.format("%s完成", currentMode.getDisplayName()));

                // 设置断开瞬间的反馈位置（单点模式在第一点；两点模式暂取第一点）
                try {
                    if (firstBreakPoint != null) {
                        lastBreakMark = firstBreakPoint;
                        lastBreakMarkAtMs = System.currentTimeMillis();
                    }
                } catch (Exception ignored) {}
                
                // 打断后为连续操作做准备（保持工具可继续下一次打断）
                prepareForNextOperation(context);
                
                // 始终走 COMPLETE，让上层统一执行命令
                return ModifyResult.COMPLETE;
            } else {
                LOGGER.warn("创建打断命令失败");
                context.setStatusMessage("创建打断命令失败，请重试");
                return ModifyResult.CANCEL;
            }
            
        } catch (Exception e) {
            LOGGER.error("执行打断操作失败: {}", e.getMessage(), e);
            context.setStatusMessage("打断操作执行失败: " + e.getMessage());
            return ModifyResult.CANCEL;
        }
    }
    
    private void createBreakParameters(ModifyToolContext context) {
        breakParameters.clear();
        // 修复：直接使用已投影的点，避免重复投影
        breakParameters.setVec2d("firstBreakPoint", firstBreakPoint);
        breakParameters.setParameter("targetShape", targetShape);
        // 不再因闭合图形将单点模式覆盖为两点模式
        String modeStr = (currentMode != null) ? currentMode.toString() : "SINGLE_POINT";
        breakParameters.setParameter("breakMode", modeStr);
        
        if (currentMode == BreakMode.TWO_POINT && secondBreakPoint != null) {
            // 修复：直接使用已投影的点，避免重复投影
            breakParameters.setVec2d("secondBreakPoint", secondBreakPoint);
        }
        
        try {
            // 传递具体 AppState（处理器需要 com.plot.core.state.AppState）
            if (context != null) {
                // 新增：传递上下文给处理器，用于错误反馈
                breakParameters.setParameter("context", context);
                
                Object apiState = context.getAppState();
                if (apiState instanceof com.plot.core.state.AppState concrete) {
                    breakParameters.setParameter("appState", concrete);
                } else {
                    // 兜底：尝试全局实例
                    try {
                        com.plot.core.state.AppState concrete = com.plot.core.state.AppState.getInstance();
                        if (concrete != null) {
                            breakParameters.setParameter("appState", concrete);
                        }
                    } catch (Throwable t) {
                        // 忽略
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("设置 appState 到参数时出错: {}", e.getMessage());
        }
    }
    
    private void prepareForNextOperation(ModifyToolContext context) {
        // 清除高亮和重置状态
        if (targetShape != null) {
            targetShape.setHighlighted(false);
        }
        
        targetShape = null;
        firstBreakPoint = null;
        secondBreakPoint = null;
        currentState = BreakState.SELECTING_SHAPE;
        
        context.setStatusMessage(getInitialStatusMessage());
    }
    
    private ModifyCommand createBreakCommand(ModifyToolContext context) {
        try {
            // 验证必要参数
            if (targetShape == null) {
                LOGGER.warn("创建打断命令失败：目标图形为空");
                return null;
            }
            
            if (firstBreakPoint == null) {
                LOGGER.warn("创建打断命令失败：第一个打断点为空");
                return null;
            }
            
            if (currentMode == BreakMode.TWO_POINT && secondBreakPoint == null) {
                LOGGER.warn("创建打断命令失败：两点模式但第二个打断点为空");
                return null;
            }
            
            LOGGER.debug("创建打断命令: 目标图形={}, 第一点={}, 第二点={}, 模式={}", 
                targetShape.getClass().getSimpleName(), firstBreakPoint, secondBreakPoint, currentMode);
            
            // 使用ModifyAdapter和BreakHandler执行打断操作
            com.plot.ui.tools.impl.modify.adapter.ModifyAdapter adapter = 
                com.plot.ui.tools.impl.modify.adapter.ModifyAdapter.getInstance();
            
            // 创建打断参数（复用已构建的参数并补齐 appState）
            ModifyParameters breakParams =
                (this.breakParameters != null) ? this.breakParameters : new ModifyParameters();
            
            // 设置打断模式
            String modeStr = (currentMode != null) ? currentMode.toString() : "SINGLE_POINT";
            breakParams.setParameter("breakMode", modeStr);
            
            // 设置打断点
            breakParams.setVec2d("firstBreakPoint", firstBreakPoint);
            if (secondBreakPoint != null) {
                breakParams.setVec2d("secondBreakPoint", secondBreakPoint);
            }
            
            // 设置目标图形
            breakParams.setParameter("targetShape", targetShape);
            
            // 确保包含 appState
            try {
                if (breakParams.getParameter("appState") == null && context != null && context.getAppState() != null) {
                    breakParams.setParameter("appState", context.getAppState());
                }
            } catch (Exception e) {
                LOGGER.debug("补齐 appState 参数时出错: {}", e.getMessage());
            }
            
            // 执行打断操作
            IModifyHandler.ModifyResult result = adapter.performModification(
                IModifyHandler.ModifyType.BREAK, 
                java.util.List.of(targetShape), 
                breakParams
            );
            
            if (result.isSuccess()) {
                LOGGER.debug("打断操作成功: 原始图形=1, 新图形={}", 
                    result.getModifiedShapes().size());
                return result.getCommand();
            } else {
                LOGGER.warn("打断操作失败: {}", result.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.error("创建打断命令时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }
    

    
    private Shape findShapeAtPoint(Vec2d point, ModifyToolContext context) {
        try {
            // 使用上下文提供的统一选择检测（容差按像素传入，内部处理坐标系转换与精确几何）
            Shape shape = context.findShapeAt(point, SELECTION_TOLERANCE);
            if (shape != null) {
                LOGGER.debug("找到目标图形: {}", shape.getClass().getSimpleName());
            }
            return shape;
        } catch (Exception e) {
            LOGGER.error("查找图形时发生错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    
    

    
    private void updateHighlight(Shape shape) {
        // 清除之前的高亮
        if (highlightedShape != null && highlightedShape != targetShape) {
            highlightedShape.setHighlighted(false);
        }
        
        // 设置新的高亮
        if (shape != null && shape != targetShape) {
            highlightedShape = shape;
            shape.setHighlighted(true);
        } else {
            highlightedShape = null;
        }
    }
    
    private String getInitialStatusMessage() {
        return String.format("选择要打断的对象，按T键切换模式（当前：%s）", 
            currentMode.getDisplayName());
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        return pendingCommand;
    }
    
    @Override
    public void reset() {
        LOGGER.debug("打断策略重置");
        
        // 清除高亮
        if (targetShape != null) {
            targetShape.setHighlighted(false);
        }
        if (highlightedShape != null) {
            highlightedShape.setHighlighted(false);
        }
        
        // 重置状态
        targetShape = null;
        firstBreakPoint = null;
        secondBreakPoint = null;
        highlightedShape = null;
        currentMousePoint = null;
        currentState = BreakState.SELECTING_SHAPE;
        pendingCommand = null;
        
        if (breakParameters != null) {
            breakParameters.clear();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "打断策略";
    }
    
    @Override
    public String getStrategyDescription() {
        return currentMode.getDescription();
    }
    
    @Override
    public boolean requiresSelection() {
        return false; // 打断工具自己管理图形选择
    }
    
    @Override
    public int getMinimumSelectionCount() {
        return 0; // 不依赖预选择
    }
    
    @Override
    public int getMaximumSelectionCount() {
        return 0; // 不依赖预选择
    }
    
    // ====== 访问器方法 ======
    
    public BreakMode getCurrentMode() {
        return currentMode;
    }
    
    public BreakState getCurrentState() {
        return currentState;
    }
    
    public Shape getTargetShape() {
        return targetShape;
    }
    
    public Vec2d getFirstBreakPoint() {
        return firstBreakPoint;
    }
    
    public Vec2d getSecondBreakPoint() {
        return secondBreakPoint;
    }

    public Vec2d getCurrentMousePoint() {
        return currentMousePoint;
    }

    // ====== 辅助方法：几何投影与邻近性判断 ======

    /**
     * 将任意点击点投影到目标图形的轮廓上，减少误差并稳定预览/计算
     */
    private Vec2d projectPointOnTargetShape(Vec2d pos) {
        try {
            if (targetShape == null || pos == null) return pos;
            
            // 对于LineShape，使用有限线段投影
            if (targetShape instanceof LineShape lineShape) {
                Vec2d start = lineShape.getStart();
                Vec2d end = lineShape.getEnd();
                return projectPointOnSegment(pos, start, end);
            }
            
            // 对于"转为Polyline再断"的复杂形状，找到最近的线段进行投影
            if (targetShape instanceof FreeDrawPath || 
                targetShape instanceof BezierCurveShape || 
                targetShape instanceof CableShape || 
                targetShape instanceof SineCurveShape || 
                targetShape instanceof SpiralShape) {
                return projectPointOnComplexShape(pos);
            }
            
            // 对于其他图形，使用通用的最近点方法
            Vec2d closest = targetShape.getClosestPoint(pos);
            return closest != null ? closest : pos;
            
        } catch (Exception e) {
            LOGGER.debug("投影点到目标图形时发生错误: {}", e.getMessage());
            return pos;
        }
    }
    
    /**
     * 将点投影到复杂形状的最近线段上
     * 修复：使用与breakShape相同的线段查找逻辑，确保投影点和断开点一致
     */
    private Vec2d projectPointOnComplexShape(Vec2d pos) {
        try {
            List<Vec2d> points = getShapePoints();
            if (points == null || points.size() < 2) return pos;
            
            // 应用坐标变换，确保pos在正确的坐标系中
            Vec2d localPos = pos;
            if (targetShape.getTransform() != null) {
                localPos = targetShape.getTransform().inverseTransform(pos);
            }
            
            // 使用与GeometryUtils.findSegmentContainingPoint相同的逻辑
            int segmentIndex = com.plot.core.geometry.GeometryUtils.findSegmentContainingPoint(points, localPos);
            if (segmentIndex >= 0) {
                Vec2d segStart = points.get(segmentIndex);
                Vec2d segEnd = points.get(segmentIndex + 1);
                Vec2d projected = projectPointOnSegment(localPos, segStart, segEnd);
                
                // 将投影点变换回世界坐标系
                if (targetShape.getTransform() != null) {
                    projected = targetShape.getTransform().transform(projected);
                }
                return projected;
            }
            
            // 回退：找到距离最近的线段
            Vec2d closest = null;
            double minDistance = Double.MAX_VALUE;
            
            for (int i = 0; i < points.size() - 1; i++) {
                Vec2d segStart = points.get(i);
                Vec2d segEnd = points.get(i + 1);
                Vec2d projected = projectPointOnSegment(localPos, segStart, segEnd);
                double distance = localPos.distance(projected);
                
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = projected;
                }
            }
            
            // 将结果变换回世界坐标系
            if (closest != null && targetShape.getTransform() != null) {
                closest = targetShape.getTransform().transform(closest);
            }
            
            return closest != null ? closest : pos;
        } catch (Exception e) {
            LOGGER.debug("投影到复杂形状时发生错误: {}", e.getMessage());
            return pos;
        }
    }
    
    /**
     * 获取目标形状的点列表
     */
    private List<Vec2d> getShapePoints() {
        if (targetShape instanceof FreeDrawPath freeDrawPath) {
            return freeDrawPath.getPoints();
        } else if (targetShape instanceof BezierCurveShape bezierCurve) {
            return bezierCurve.getCurvePoints();
            } else if (targetShape instanceof CableShape catenaryLine) {
            return catenaryLine.getPoints();
        } else if (targetShape instanceof SineCurveShape sineCurve) {
            return sineCurve.getPoints();
        } else if (targetShape instanceof SpiralShape spiralShape) {
            return spiralShape.getPoints();
        }
        return null;
    }
    
    /**
     * 将点投影到有限线段上（不延伸到线段外）
     */
    private Vec2d projectPointOnSegment(Vec2d point, Vec2d segStart, Vec2d segEnd) {
        if (segStart.equals(segEnd)) return segStart;
        
        Vec2d v = segEnd.subtract(segStart);
        Vec2d w = point.subtract(segStart);
        
        double c1 = w.dot(v);
        double c2 = v.dot(v);
        
        if (c1 <= 0) return segStart;  // 投影在起点外，返回起点
        if (c1 >= c2) return segEnd;   // 投影在终点外，返回终点
        
        double b = c1 / c2;
        return segStart.add(v.multiply(b));  // 投影在线段内
    }

    /**
     * 计算世界坐标容差（由像素容差转换）
     */
    private double getWorldTolerance(ModifyToolContext context) {
        double tolWorld = SELECTION_TOLERANCE;
        try {
            var cam = context != null ? context.getCamera() : null;
            if (cam != null) {
                tolWorld = cam.screenToWorldDistance(SELECTION_TOLERANCE);
            }
        } catch (Exception ignored) {}
        return tolWorld;
    }

    /**
     * 判断两断点是否过近，避免退化段
     * 修复：使用更合理的最小距离，避免两点打断过于严格
     */
    private boolean areBreakPointsTooClose(Vec2d a, Vec2d b, ModifyToolContext context) {
        if (a == null || b == null) return true;
        try {
            double tol = getWorldTolerance(context);
            // 使用更合理的最小距离：至少是容差的2倍，确保有足够的打断区间
            double minDistance = Math.max(tol * 2.0, 1.0); // 最小1个单位的距离
            boolean tooClose = a.distance(b) <= minDistance;
            
            if (tooClose) {
                LOGGER.debug("两个打断点过近: 距离={}, 最小距离={}", a.distance(b), minDistance);
            }
            
            return tooClose;
        } catch (Exception e) {
            LOGGER.debug("检查两点距离时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断给定点是否是有效的打断点
     */
    private boolean isValidBreakPoint(Vec2d point, Shape shape, ModifyToolContext context) {
        if (point == null || shape == null) return false;
        
        try {
            double tol = getWorldTolerance(context);
            // 统一策略：将点击点投影到目标图形再校验距离，等效"默认捕捉到线"
            Vec2d projected = projectPointOnTargetShape(point);
            double distance = shape.getDistanceToPoint(projected);
            return distance <= tol * 1.1; // 略放宽
            
        } catch (Exception e) {
            LOGGER.debug("验证打断点时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 针对两点打断模式的更宽松的点验证
     * 参考单点打断的成功经验，提供更友好的交互体验
     */
    private boolean isValidBreakPointForTwoPoint(Vec2d point, Shape shape, ModifyToolContext context) {
        if (point == null || shape == null) return false;
        
        try {
            double tol = getWorldTolerance(context);
            
            // 1. 直接检查点到图形的距离（更直接的方法）
            double directDistance = shape.getDistanceToPoint(point);
            if (directDistance <= tol * 2.0) { // 对两点模式更宽松
                return true;
            }
            
            // 2. 检查是否在图形的最近点附近
            Vec2d closestPoint = shape.getClosestPoint(point);
            if (closestPoint != null) {
                double distanceToClosest = point.distance(closestPoint);
                if (distanceToClosest <= tol * 1.5) { // 比单点模式稍宽松
                    return true;
                }
            }
            
            // 3. 回退到投影检查（与单点模式相同）
            Vec2d projected = projectPointOnTargetShape(point);
            double projectedDistance = shape.getDistanceToPoint(projected);
            return projectedDistance <= tol * 1.2; // 比单点模式稍宽松
            
        } catch (Exception e) {
            LOGGER.debug("验证两点打断点时发生错误: {}", e.getMessage());
            return false;
        }
    }

    // ====== 辅助：优先返回捕捉后的世界坐标 ======
    private Vec2d resolveWorldPoint(Vec2d screenPoint, ModifyToolContext context) {
        try {
            if (context == null) return screenPoint;
            var cam = context.getCamera();
            if (context.getSnapHandler() != null && cam != null) {
                Vec2d snapped = context.getSnapHandler().getSnappedWorldPoint(screenPoint, cam);
                if (snapped != null) return snapped;
            }
            return cam != null ? cam.screenToWorld(screenPoint) : screenPoint;
        } catch (Exception e) {
            return screenPoint;
        }
    }
} 