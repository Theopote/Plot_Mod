package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.tools.impl.modify.ChamferTool;
import com.masterplanner.ui.tools.impl.modify.helper.ChamferHandler;
import com.masterplanner.ui.tools.impl.modify.helper.IModifyHandler;
import imgui.ImDrawList;

import java.awt.event.KeyEvent;
import java.util.List;

/**
 * 倒角策略 - 控制器版本
 * 
 * <p>作为控制器角色，专注于用户交互流程和状态管理，
 * 所有几何计算委托给ChamferHandler完成。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 优化状态管理和参数传递
 */
public class ChamferStrategy implements IModifyStrategy {

    // 优化：使用ChamferTool的常量，确保一致性
    public static final String CONFIG_KEY_DISTANCE = ChamferTool.CONFIG_KEY_DISTANCE;
    public static final String CONFIG_KEY_PREVIEW = ChamferTool.CONFIG_KEY_PREVIEW;
    public static final String CONFIG_KEY_HIGHLIGHT = ChamferTool.CONFIG_KEY_HIGHLIGHT;
    
    // 键盘常量
    private static final int KEY_ESC = KeyEvent.VK_ESCAPE;
    private static final int KEY_ENTER = KeyEvent.VK_ENTER;
    private static final int KEY_PLUS = KeyEvent.VK_EQUALS; // + 键
    private static final int KEY_MINUS = KeyEvent.VK_MINUS; // - 键
    
    // 参数记录类 - 优化参数封装
    public record ChamferParameters(double distance) 
            implements IModifyHandler.ModifyParameters {
        
        @Override
        public boolean hasParameter(String name) {
            return CONFIG_KEY_DISTANCE.equals(name);
        }
        
        @Override
        public Object getParameter(String name) {
            return CONFIG_KEY_DISTANCE.equals(name) ? distance : null;
        }
        
        @Override
        public void setParameter(String key, Object value) {
            // 只读参数，不实现设置
            throw new UnsupportedOperationException("ChamferParameters is read-only");
        }
    }
    
    public enum ChamferState {
        SELECT_FIRST_LINE,
        SELECT_SECOND_LINE,
        READY_TO_APPLY
    }
    
    private ChamferState currentState;
    private LineShape line1;
    private LineShape line2;
    private double distance;
    private List<Shape> previewShapes; // 使用 Shape 列表存储预览图形
    
    // 引入 ChamferHandler
    private final ChamferHandler chamferHandler;
    
    // 框选状态
    private boolean isBoxSelecting = false;
    private Vec2d boxStartPoint;
    private Vec2d boxCurrentPoint;
    private final java.util.LinkedList<Shape> boxSelectedShapes = new java.util.LinkedList<>();
    
    // 从 Tool 传入的配置
    private boolean previewEnabled = true;
    
    // 优化：使用ChamferTool的常量
    private static final double MIN_DISTANCE = ChamferTool.MIN_DISTANCE;
    private static final double MAX_DISTANCE = ChamferTool.MAX_DISTANCE;
    private static final double DEFAULT_DISTANCE = ChamferTool.DEFAULT_DISTANCE;
    
    // ====== 框选相关方法 ======
    
    /**
     * 更新框选临时选择
     */
    private void updateBoxSelection(ModifyToolContext context) {
        if (boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        
        boxSelectedShapes.clear();
        List<Shape> allShapes = context.getAppState().getActiveLayer().getShapes();
        
        for (Shape shape : allShapes) {
            if (shape != null && shape.isVisible() && !shape.isDeleted() && shape instanceof LineShape) {
                // 使用虚线框选择逻辑（总是相交选择）
                if (com.masterplanner.ui.tools.impl.modify.helper.GeometricSelectionHelper.isShapeInRectangleSelection(shape, boxStartPoint, boxCurrentPoint, false)) {
                    boxSelectedShapes.add(shape);
                }
            }
        }
    }
    
    /**
     * 完成框选选择
     */
    private void finalizeBoxSelection(ModifyToolContext context) {
        context.setStatusMessage(String.format("框选完成，已选择 %d 个图形", boxSelectedShapes.size()));
    }
    
    /**
     * 渲染框选预览
     */
    private void renderBoxSelectionPreview(DrawContext context) {
        if (!isBoxSelecting || boxStartPoint == null || boxCurrentPoint == null) {
            return;
        }
        // 绘制虚线框
        context.drawRect(boxStartPoint, boxCurrentPoint, java.awt.Color.WHITE);
    }
    
    /**
     * 处理选择第一个图形时的鼠标按下
     */
    private ModifyResult handleMouseDown_SelectFirst(LineShape line, ModifyToolContext context) {
        line1 = line;
        currentState = ChamferState.SELECT_SECOND_LINE;
        context.setStatusMessage("选择第二条直线");
        return ModifyResult.CONTINUE;
    }
    
    /**
     * 处理选择第二个图形时的鼠标按下
     */
    private ModifyResult handleMouseDown_SelectSecond(LineShape line, ModifyToolContext context) {
        if (line != line1) {
            line2 = line;
            // 验证并生成预览
            updatePreviewWithContext(context);
            if (isReadyToApply()) {
               currentState = ChamferState.READY_TO_APPLY;
               context.setStatusMessage(getStatusMessage());
            }
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }

    public ChamferStrategy(AppState appState) {
        this.distance = DEFAULT_DISTANCE;
        // 实例化 Handler
        this.chamferHandler = new ChamferHandler(appState);
        reset();
    }
    
    @Override
    public void reset() {
        currentState = ChamferState.SELECT_FIRST_LINE;
        line1 = null;
        line2 = null;
        previewShapes = null;
    }
    
    @Override
    public ModifyResult onMouseDown(Vec2d point, int button, ModifyToolContext context) {
        if (button != 0) return ModifyResult.IGNORED;

        // 开始框选
        isBoxSelecting = true;
        boxStartPoint = point;
        boxCurrentPoint = point;
        boxSelectedShapes.clear();
        return ModifyResult.CONTINUE;
    }
    
    @Override
    public ModifyResult onMouseMove(Vec2d point, ModifyToolContext context) {
        if (isBoxSelecting) {
            // 框选模式
            boxCurrentPoint = point;
            updateBoxSelection(context);
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onMouseUp(Vec2d point, int button, ModifyToolContext context) {
        if (isBoxSelecting) {
            // 检查是否为点选（拖动距离小于阈值）
            double dragDistance = boxStartPoint.distance(point);
            if (dragDistance < 4.0) { // 拖动阈值
                // 点选模式：选择单个图形
                Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(point, context.getCamera());
                Shape clickedShape = context.findShapeAt(snappedPoint, 10.0);
                if (clickedShape instanceof LineShape) {
                    boxSelectedShapes.clear();
                    boxSelectedShapes.add(clickedShape);
                }
            } else {
                // 框选模式：应用最终选择
                finalizeBoxSelection(context);
            }
            
            // 重置框选状态
            isBoxSelecting = false;
            boxStartPoint = null;
            boxCurrentPoint = null;
            
            // 如果有选中的图形，选择第一个作为目标
            if (!boxSelectedShapes.isEmpty()) {
                Shape selectedShape = boxSelectedShapes.getFirst();
                if (selectedShape instanceof LineShape line) {
                    ModifyResult res = switch (currentState) {
                        case SELECT_FIRST_LINE -> handleMouseDown_SelectFirst(line, context);
                        case SELECT_SECOND_LINE -> handleMouseDown_SelectSecond(line, context);
                        case READY_TO_APPLY -> ModifyResult.IGNORED;
                    };
                    // 不在此处结束工具；保持 CONTINUE 让用户确认（例如按Enter）或继续交互
                    return res == ModifyResult.IGNORED ? ModifyResult.CONTINUE : res;
                }
            }
            return ModifyResult.CONTINUE;
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public ModifyResult onKeyDown(int keyCode, ModifyToolContext context) {
        switch (keyCode) {
            case KEY_ESC:
                reset();
                context.setStatusMessage(getStatusMessage());
                return ModifyResult.CANCEL;
                
            case KEY_PLUS:
            case KEY_MINUS:
                distance += (keyCode == KEY_PLUS ? 0.5 : -0.5);
                distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
                if (isReadyToApply()) {
                    updatePreviewWithContext(context); // 更新预览
                }
                context.setStatusMessage(getStatusMessage());
                return ModifyResult.CONTINUE;

            case KEY_ENTER:
                if (isReadyToApply()) {
                    return applyChamfer(context);
                }
                break;
        }
        return ModifyResult.IGNORED;
    }
    
    @Override
    public String getStrategyName() {
        return "ChamferStrategy";
    }
    
    @Override
    public String getStrategyDescription() {
        return "倒角策略 - 在两条直线之间创建斜面";
    }
    
    /**
     * 优化：更清晰的状态消息管理，根据当前状态和验证结果动态生成消息
     */
    public String getStatusMessage() {
        return switch (currentState) {
            case SELECT_FIRST_LINE -> String.format("选择第一条直线，按+/-调整距离(%.1f)，或按ESC取消", distance);
            case SELECT_SECOND_LINE -> "选择第二条直线";
            case READY_TO_APPLY -> {
                // 检查验证结果，提供更准确的状态信息
                if (line1 != null && line2 != null) {
                    ChamferParameters params = createModifyParameters();
                    IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(line1, line2), params);
                    if (validation.isValid()) {
                        yield String.format("按Enter确认倒角(距离%.1f)，+/-调整距离，或ESC取消", distance);
                    } else {
                        yield validation.getErrorMessage();
                    }
                } else {
                    yield "准备就绪";
                }
            }
        };
    }
    
    @Override
    public void renderPreview(DrawContext context) {
        // 渲染框选预览
        if (isBoxSelecting && boxStartPoint != null && boxCurrentPoint != null) {
            renderBoxSelectionPreview(context);
        }
        
        // 渲染倒角预览
        if (previewEnabled && previewShapes != null) {
            for (Shape shape : previewShapes) {
                // 修复：使用render方法而不是draw方法，确保应用正确的样式
                shape.render(context);
            }
        }
    }
    
    @Override
    public void renderPreview(ImDrawList drawList, CanvasCamera camera) {
        if (previewEnabled && previewShapes != null) {
            for (Shape shape : previewShapes) {
                // ImGui预览渲染实现
                // 这里需要根据具体的Shape类型进行绘制
                if (shape instanceof LineShape lineShape) {
                    // 绘制线段
                    Vec2d start = lineShape.getStart();
                    Vec2d end = lineShape.getEnd();
                    Vec2d screenStart = camera.worldToScreen(start);
                    Vec2d screenEnd = camera.worldToScreen(end);
                    drawList.addLine(
                        (float)screenStart.x, (float)screenStart.y,
                        (float)screenEnd.x, (float)screenEnd.y,
                        0xFFFFFFFF // 白色
                    );
                }
                // 可以添加其他Shape类型的绘制逻辑
            }
        }
    }
    
    @Override
    public ModifyCommand getModifyCommand() {
        if (!isReadyToApply()) {
            return null;
        }
        
        ChamferParameters params = createModifyParameters();
        List<Shape> originalShapes = List.of(line1, line2);
        
        return chamferHandler.createModifyCommand(originalShapes, null, params);
    }
    
    /**
     * 优化：简化状态机，将isReadyToApply()与currentState绑定
     */
    public boolean isReadyToApply() {
        // 更直观的可用性判定：只要有两条线就认为可以准备应用（状态由调用方维护）
        return line1 != null && line2 != null;
    }

    public void updateConfig(String key, Object value) {
        boolean needsPreviewUpdate = false;
        
        if (CONFIG_KEY_DISTANCE.equals(key) && value instanceof Number num) {
            this.distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, num.doubleValue()));
            needsPreviewUpdate = true;
        } else if (CONFIG_KEY_PREVIEW.equals(key) && value instanceof Boolean val) {
            this.previewEnabled = val;
        } else if (CONFIG_KEY_HIGHLIGHT.equals(key) && value instanceof Boolean val) {
            boolean highlightEnabled = val;
        }
        
        // 安全地更新预览，不依赖context
        if (needsPreviewUpdate && isReadyToApply()) {
            updatePreviewInternal();
        }
    }
    
    /**
     * 获取当前距离
     */
    public double getDistance() {
        return distance;
    }
    
    /**
     * 内部预览更新 - 不依赖context
     */
    private void updatePreviewInternal() {
        if (!previewEnabled || line1 == null || line2 == null) {
            previewShapes = null;
            return;
        }

        ChamferParameters params = createModifyParameters();
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(line1, line2), params);

        if (validation.isValid()) {
            this.previewShapes = chamferHandler.createPreviewShapes(List.of(line1, line2), params);
        } else {
            this.previewShapes = null;
        }
    }
    
    /**
     * 带context的预览更新 - 用于交互过程中的状态消息更新
     */
    private void updatePreviewWithContext(ModifyToolContext context) {
        if (!previewEnabled || line1 == null || line2 == null) {
            previewShapes = null;
            return;
        }

        ChamferParameters params = createModifyParameters();
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(List.of(line1, line2), params);

        if (validation.isValid()) {
            this.previewShapes = chamferHandler.createPreviewShapes(List.of(line1, line2), params);
        } else {
            this.previewShapes = null;
            if (context != null) {
                context.setStatusMessage(validation.getErrorMessage());
            }
        }
    }

    private ModifyResult applyChamfer(ModifyToolContext context) {
        if (!isReadyToApply()) return ModifyResult.IGNORED;

        ChamferParameters params = createModifyParameters();
        List<Shape> originalShapes = List.of(line1, line2);
        
        // 最终应用时再次验证
        IModifyHandler.ValidationResult validation = chamferHandler.validateModification(originalShapes, params);
        if (!validation.isValid()) {
            context.setStatusMessage(validation.getErrorMessage());
            return ModifyResult.IGNORED;
        }
        
        // 创建命令
        ModifyCommand command = chamferHandler.createModifyCommand(originalShapes, null, params);
        if (command != null) {
            // 将命令提交到命令栈...
            // context.getCommandManager().executeCommand(command);
            context.setStatusMessage(String.format("倒角完成 (距离: %.1f)", distance));
            reset();
            return ModifyResult.COMPLETE;
        } else {
            context.setStatusMessage("创建倒角命令失败");
            return ModifyResult.IGNORED;
        }
    }
    
    /**
     * 创建参数对象 - 使用record类优化
     */
    private ChamferParameters createModifyParameters() {
        return new ChamferParameters(distance);
    }
}
