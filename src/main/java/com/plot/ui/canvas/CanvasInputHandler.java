package com.plot.ui.canvas;

import com.plot.api.geometry.Vec2d;
import com.plot.api.model.ILayer;
import com.plot.camera.CameraManager;
import com.plot.core.command.CommandManager;
import com.plot.core.command.commands.DeleteShapesCommand;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.tool.BaseTool;
// import com.plot.infrastructure.event.mouse.KeyEvent; // 未使用
import com.plot.infrastructure.event.mouse.MouseEvent;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 画布输入处理类 (已优化)
 * <p>
 * 负责处理鼠标和键盘输入，包括选择、拖动和工具交互。
 * <p>
 * 优化点:
 * 1. 移除了本地的selectedShapes列表，统一使用AppState作为唯一数据源，避免状态不一致。
 * 2. 重构handleInput方法，将其拆分为更小、职责更单一的私有方法。
 * 3. 简化了输入逻辑，避免了重复调用。
 * 4. 改进了光标设置逻辑，移除了反射和静态标志。
 */
public class CanvasInputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasInputHandler.class);

    private final CanvasCore core;
    private final AppState appState;

    // 拖动状态相关
    private Vec2d lastDragPos;
    private boolean isDraggingShapes = false;
    
    // 鼠标中键拖动平移状态
    private Vec2d lastMiddleButtonDragPos = null;
    private boolean isPanningWithMiddleButton = false;

    // 修复：跟踪修饰键状态变化，将 Ctrl/Shift 变更转发给当前工具
    private boolean wasCtrlDown = false;
    private boolean wasShiftDown = false;

    // 事件处理器
    private Consumer<MouseEvent> onMouseClicked;
    private Consumer<MouseEvent> onMouseReleased;
    private Consumer<MouseEvent> onMouseDragged;
    private Consumer<MouseEvent> onMouseMoved;
    // private Consumer<KeyEvent> onKeyPressed; // 未使用，保留注释避免告警

    /**
     * 构造函数
     * @param core 画布核心对象
     */
    public CanvasInputHandler(CanvasCore core) {
        this.core = core;
        this.appState = AppState.getInstance(); // 缓存AppState实例
    }

    /**
     * 处理所有输入的主入口
     */
    public void handleInput() {
        try {
            // 优先处理键盘事件（不依赖hover），保证Ctrl+A等快捷键始终可用
            BaseTool currentTool = appState.getCurrentTool();
            if (currentTool != null) {
                handleKeyboardEvents(currentTool);
            }

            // 使用“画布屏幕区域”判断鼠标是否在画布上（避免依赖 ImGui 当前窗口上下文）
            Vec2d mouseScreenPos = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
            if (!core.isScreenPosInsideCanvas(mouseScreenPos)) {
                return; // 鼠标不在画布上时，不处理鼠标事件
            }

            // 如果鼠标/控件输入被其他UI窗口捕获（例如工具栏、右侧面板），不要触发画布绘制
            // 这样可以避免“画布在最上面”或“点击面板却在画布上画线”的错觉
            try {
                if (ImGui.getIO().getWantCaptureMouse()) {
                    return;
                }
            } catch (Exception ignored) {}

            // 获取通用状态（鼠标相关）
            Vec2d worldPos = core.screenToWorld(mouseScreenPos);

            if (currentTool == null) {
                LOGGER.warn("当前工具为null，输入事件可能无法完整处理");
                return;
            }

            // 更新光标
            updateCursor(currentTool);

            // 处理鼠标移动 (对工具预览很重要)
            handleMouseMove(worldPos, currentTool);

            // 处理鼠标事件 (点击、拖动、释放)
            handleMouseEvents(worldPos, currentTool);
            
            // 处理鼠标中键拖动平移（优先处理，不受工具影响）
            handleMiddleButtonPan();

            // 键盘事件已在函数开头处理过

        } catch (Exception e) {
            LOGGER.error("处理Canvas输入时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据当前工具更新光标形状
     * @param currentTool 当前激活的工具
     */
    private void updateCursor(BaseTool currentTool) {
        String cursorType = getCursorTypeForTool(currentTool);
        
        if (!cursorType.equals(core.getCursor())) {
            LOGGER.debug("设置光标: {} -> {}", core.getCursor(), cursorType);
            core.setCursor(cursorType);
        }
    }

    /**
     * 根据工具类型获取对应的光标类型
     * @param currentTool 当前工具
     * @return 光标类型字符串
     */
    private String getCursorTypeForTool(BaseTool currentTool) {
        // 优先使用ICursorProvider接口
        if (currentTool instanceof ICursorProvider) {
            return ((ICursorProvider) currentTool).getCursorType();
        }
        
        // 回退到基于工具ID的判断
        String toolId = currentTool.getId();
        if (currentTool instanceof com.plot.ui.tools.impl.drawing.DrawingTool) {
            return ICursorProvider.CursorTypes.CROSSHAIR; // 绘制工具使用十字光标
        } else if ("eraser".equals(toolId)) {
            return ICursorProvider.CursorTypes.ERASER; // 橡皮擦工具使用橡皮擦光标
        } else if ("select".equals(toolId)) {
            return ICursorProvider.CursorTypes.POINTER; // 选择工具使用指针光标
        } else if ("pan".equals(toolId)) {
            return ICursorProvider.CursorTypes.HAND; // 平移工具使用手型光标
        } else if ("text".equals(toolId)) {
            return ICursorProvider.CursorTypes.TEXT; // 文本工具使用文本光标
        } else if ("move".equals(toolId)) {
            return ICursorProvider.CursorTypes.MOVE; // 移动工具使用移动光标
        } else if ("resize".equals(toolId)) {
            return ICursorProvider.CursorTypes.RESIZE; // 调整大小工具使用调整光标
        }
        
        return ICursorProvider.CursorTypes.DEFAULT; // 默认为箭头
    }

    /**
     * 处理鼠标的移动，并通知当前工具
     * @param worldPos 当前鼠标的世界坐标
     * @param currentTool 当前激活的工具
     */
    private void handleMouseMove(Vec2d worldPos, BaseTool currentTool) {
        currentTool.onMouseMove(worldPos);
        core.markDirty(CanvasCore.DirtyType.TOOL_PREVIEW);

        if (onMouseMoved != null) {
            onMouseMoved.accept(new MouseEvent(MouseEvent.Type.MOVED, worldPos, new Vec2d(0, 0)));
        }
    }

    /**
     * 处理鼠标的点击、拖动和释放事件
     * @param worldPos 当前鼠标的世界坐标
     * @param currentTool 当前激活的工具
     */
    private void handleMouseEvents(Vec2d worldPos, BaseTool currentTool) {
        // 如果正在使用鼠标中键平移，不处理其他鼠标事件
        if (isPanningWithMiddleButton) {
            return;
        }
        
        boolean isInSelectionMode = currentTool.isSelecting();
        List<Shape> selectedShapes = appState.getSelectedShapes(); // 直接从AppState获取

        // 处理左键点击
        if (ImGui.isMouseClicked(0)) {
            handleLeftClick(worldPos, currentTool, isInSelectionMode);
        }

        // 处理右键点击
        if (ImGui.isMouseClicked(1)) {
            handleRightClick(worldPos, currentTool);
        }

        // 处理拖动
        if (ImGui.isMouseDown(0)) {
            handleDrag(worldPos, currentTool, isInSelectionMode, selectedShapes);
        }

        // 处理释放
        if (ImGui.isMouseReleased(0)) {
            handleMouseRelease(worldPos, currentTool);
        }
    }
    
    /**
     * 处理鼠标中键拖动平移视图
     */
    private void handleMiddleButtonPan() {
        CameraManager cameraManager = CameraManager.getInstance();
        
        // 检查视图是否被锁定
        boolean isLocked = cameraManager.getOrthographicCamera().isLocked();
        
        // 如果视图被锁定，不允许拖动
        if (isLocked) {
            // 如果正在拖动，立即停止
            if (isPanningWithMiddleButton) {
                isPanningWithMiddleButton = false;
                lastMiddleButtonDragPos = null;
                cameraManager.setPanning(false);
                LOGGER.debug("视图已锁定，停止拖动");
            }
            return;
        }
        
        // 检查是否按下鼠标中键（按钮索引2）
        if (ImGui.isMouseClicked(2)) {
            // 鼠标中键刚按下，记录初始位置
            Vec2d currentScreenPos = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
            lastMiddleButtonDragPos = currentScreenPos;
            isPanningWithMiddleButton = true;
            // 标记开始拖动，跳过区块更新
            cameraManager.setPanning(true);
            LOGGER.debug("鼠标中键按下，开始平移: 初始位置=({}, {})", currentScreenPos.x, currentScreenPos.y);
        } else if (ImGui.isMouseDown(2) && isPanningWithMiddleButton && lastMiddleButtonDragPos != null) {
            // 鼠标中键按下且正在拖动
            Vec2d currentScreenPos = new Vec2d(ImGui.getMousePosX(), ImGui.getMousePosY());
            
            // 计算屏幕像素增量
            double screenDeltaX = currentScreenPos.x - lastMiddleButtonDragPos.x;
            double screenDeltaY = currentScreenPos.y - lastMiddleButtonDragPos.y;
            
            // 如果拖动增量足够大，进行平移
            if (Math.abs(screenDeltaX) > 0.1 || Math.abs(screenDeltaY) > 0.1) {
                // 简化坐标转换：直接使用屏幕增量，根据视图范围缩放
                // 使用固定的缩放因子，使移动更稳定和可预测
                float viewDistance = cameraManager.getViewDistance();
                // 使用较小的缩放因子，使屏幕移动距离与 Minecraft 移动距离接近 1:1
                // 视图范围越大，缩放因子越小
                float panSensitivity = Math.max(0.05f, Math.min(0.2f, 5.0f / viewDistance));
                
                // 直接使用屏幕增量，不依赖复杂的坐标转换
                // 屏幕向右移动 -> 视图向左移动（负X）
                // 屏幕向下移动 -> 视图向下移动（负Y，因为屏幕Y向下）
                double worldDeltaX = -screenDeltaX * panSensitivity; // X轴方向相反
                double worldDeltaY = -screenDeltaY * panSensitivity; // Y轴方向相反（屏幕Y向下）
                
                // 获取当前平移值并累加
                float currentPanX = cameraManager.getPanX();
                float currentPanY = cameraManager.getPanY();
                
                // 更新平移值
                float newPanX = (float) (currentPanX + worldDeltaX);
                float newPanY = (float) (currentPanY + worldDeltaY);
                
                // 设置新的平移值（拖动过程中跳过区块更新）
                cameraManager.setPanXY(newPanX, newPanY, true);
                
                // 更新最后位置
                lastMiddleButtonDragPos = currentScreenPos;
                
                // 拖动过程中不标记画布需要更新，避免触发不必要的重绘和更新
                // 只在拖动结束后才更新画布
                
                LOGGER.debug("鼠标中键拖动平移: 屏幕增量=({}, {}), 世界增量=({}, {}), 新平移值=({}, {})", 
                    screenDeltaX, screenDeltaY, worldDeltaX, worldDeltaY, newPanX, newPanY);
            }
        } else if (ImGui.isMouseReleased(2)) {
            // 鼠标中键释放，重置状态
            isPanningWithMiddleButton = false;
            lastMiddleButtonDragPos = null;
            // 标记拖动结束，执行一次完整的更新（包括区块更新）
            cameraManager.setPanning(false);
            // 拖动结束后，标记画布需要更新
            core.markDirty(CanvasCore.DirtyType.CAMERA);
            LOGGER.debug("鼠标中键释放，结束平移");
        } else if (!ImGui.isMouseDown(2)) {
            // 鼠标中键未按下，重置状态
            isPanningWithMiddleButton = false;
            lastMiddleButtonDragPos = null;
            // 确保拖动状态被清除
            cameraManager.setPanning(false);
        }
    }
    
    /**
     * 处理左键点击
     */
    private void handleLeftClick(Vec2d worldPos, BaseTool currentTool, boolean isInSelectionMode) {
        LOGGER.debug("鼠标左键点击: 世界坐标=({}, {}), 当前工具={}", 
            worldPos.x, worldPos.y, currentTool.getId());
        
        if (isInSelectionMode) {
            handleSelectionClick(worldPos);
        }
        
        currentTool.onMouseDown(worldPos, 0);
        core.markDirty(CanvasCore.DirtyType.CONTENT);

        if (onMouseClicked != null) {
            onMouseClicked.accept(new MouseEvent(MouseEvent.Type.CLICKED, worldPos, new Vec2d(0, 0)));
        }
    }

    /**
     * 处理右键点击
     */
    private void handleRightClick(Vec2d worldPos, BaseTool currentTool) {
        LOGGER.debug("鼠标右键点击: 世界坐标=({}, {}), 当前工具={}", 
            worldPos.x, worldPos.y, currentTool.getId());
        
        currentTool.onMouseDown(worldPos, 1);
        core.markDirty(CanvasCore.DirtyType.CONTENT);
    }

    /**
     * 处理拖动事件
     */
    private void handleDrag(Vec2d worldPos, BaseTool currentTool, boolean isInSelectionMode, List<Shape> selectedShapes) {
        // 检查是否开始拖动已选中的图形（仅当点在图形轮廓上）
        if (isInSelectionMode && !selectedShapes.isEmpty() && !isDraggingShapes) {
            // 只有当鼠标在已选中的图形上按下时才开始拖动
            for (Shape shape : selectedShapes) {
                // 将像素容差转换为世界距离
                double worldTol = 5.0;
                try {
                    worldTol = core.getCamera() != null ? core.getCamera().screenToWorldDistance(5.0) : 5.0;
                } catch (Exception ignored) {}
                if (com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper
                        .isPointOnShape(shape, worldPos, worldTol)) {
                    isDraggingShapes = true;
                    lastDragPos = worldPos;
                    LOGGER.debug("开始拖动 {} 个图形", selectedShapes.size());
                    break;
                }
            }
        }
        
        // 如果正在拖动图形
        if (isDraggingShapes) {
            Vec2d delta = worldPos.subtract(lastDragPos);
            if (delta.length() > 0.01) { // 避免微小抖动
                for (Shape shape : selectedShapes) {
                    shape.translate(delta);
                }
                lastDragPos = worldPos;
                core.markDirty(CanvasCore.DirtyType.CONTENT);
            }
        } else {
            // 否则，是工具的拖动行为 (如绘制、拖动画布等)
            if (onMouseDragged != null) {
                Vec2d delta = new Vec2d(ImGui.getMouseDragDeltaX(), ImGui.getMouseDragDeltaY());
                onMouseDragged.accept(new MouseEvent(MouseEvent.Type.DRAGGED, worldPos, delta));
            }
        }
    }

    /**
     * 处理鼠标释放
     */
    private void handleMouseRelease(Vec2d worldPos, BaseTool currentTool) {
        if (isDraggingShapes) {
            isDraggingShapes = false;
            // 可以在此创建并执行一个MoveCommand，以便撤销
            LOGGER.debug("结束拖动图形");
        }
        lastDragPos = null;

        currentTool.onMouseUp(worldPos, 0);
        core.markDirty(CanvasCore.DirtyType.CONTENT);
        appState.getCanvas().refresh(); // 如果有必要，强制刷新

        if (onMouseReleased != null) {
            onMouseReleased.accept(new MouseEvent(MouseEvent.Type.RELEASED, worldPos, new Vec2d(0, 0)));
        }
    }

    /**
     * 处理键盘输入事件
     * @param currentTool 当前激活的工具
     */
    private void handleKeyboardEvents(BaseTool currentTool) {
        // Escape: 取消选择或取消工具操作
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            handleEscapeKey(currentTool);
        }

        // Delete: 删除选中图形
        if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
            deleteSelectedShapes();
        }
        
        // Ctrl+A: 全选（跨所有可见图层）
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.A)) {
            selectAllShapesInAllLayers();
        }

        // 将 Ctrl / Shift 的状态变化转发给工具（用于镜像等临时模式）
        boolean ctrlDown = ImGui.getIO().getKeyCtrl();
        if (ctrlDown != wasCtrlDown) {
            if (ctrlDown) {
                try { currentTool.onKeyDown(17); } catch (Exception ignored) {}
            } else {
                try { currentTool.onKeyUp(17); } catch (Exception ignored) {}
            }
            wasCtrlDown = ctrlDown;
        }

        boolean shiftDown = ImGui.getIO().getKeyShift();
        if (shiftDown != wasShiftDown) {
            if (shiftDown) {
                try { currentTool.onKeyDown(16); } catch (Exception ignored) {}
            } else {
                try { currentTool.onKeyUp(16); } catch (Exception ignored) {}
            }
            wasShiftDown = shiftDown;
        }
    }
    
    /**
     * 处理Escape键
     */
    private void handleEscapeKey(BaseTool currentTool) {
        LOGGER.debug("Escape键按下");
        
        if (!appState.getSelectedShapes().isEmpty()) {
            appState.clearSelection();
            core.markDirty(CanvasCore.DirtyType.CONTENT);
            LOGGER.debug("已通过Escape取消选择");
        } else {
            try {
                currentTool.cancel();
            } catch (Throwable t) {
                LOGGER.error("Escape取消当前工具时发生异常", t);
            }
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 删除选中的图形
     */
    private void deleteSelectedShapes() {
        List<Shape> selectedShapes = appState.getSelectedShapes();
        if (selectedShapes.isEmpty()) {
            LOGGER.debug("Delete键按下，但没有选中的图形");
            return;
        }
        
        LOGGER.debug("准备通过命令删除 {} 个图形", selectedShapes.size());
        try {
            // 使用Command模式执行删除，以支持撤销
            DeleteShapesCommand deleteCommand = new DeleteShapesCommand(new ArrayList<>(selectedShapes));
            CommandManager.getInstance().executeCommand(deleteCommand);
            // 命令执行成功后会自动清空AppState中的选择
            core.markDirty(CanvasCore.DirtyType.CONTENT);
            LOGGER.debug("删除命令执行成功");
        } catch (Exception e) {
            LOGGER.error("执行删除命令时出错: {}", e.getMessage(), e);
            // 降级处理
            appState.deleteSelectedShapes();
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 全选当前图层中的所有图形
     */
    private void selectAllShapesInAllLayers() {
        appState.clearSelection();
        int count = 0;
        for (ILayer layer : core.getLayers()) {
            if (layer != null && layer.isVisible() && !layer.isLocked()) {
                for (Shape shape : layer.getShapes()) {
                    if (shape != null && shape.isVisible() && !shape.isDeleted()) {
                        appState.addSelectedShape(shape);
                        count++;
                    }
                }
            }
        }
        core.markDirty(CanvasCore.DirtyType.CONTENT);
        LOGGER.debug("已全选所有可见图层中的 {} 个图形", count);
    }

    /**
     * 处理选择模式下的点击逻辑
     * @param worldPos 点击位置的世界坐标
     */
    private void handleSelectionClick(Vec2d worldPos) {
        // 检查是否点击了已选中的图形（仅当点在图形轮廓上）
        // 注：若未来需要“点击已选中时不清空”的特殊逻辑，可在此使用该结果
        // boolean clickedOnSelected = appState.getSelectedShapes().stream().anyMatch(
        //     s -> com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper
        //             .isPointOnShape(s, worldPos, worldTol)
        // );
        
        // 只有当没有按住Shift键，且点击到了空白区域时，才清空选择
        // 将像素容差转换为世界距离
        double worldTol = 5.0;
        try {
            worldTol = core.getCamera() != null ? core.getCamera().screenToWorldDistance(5.0) : 5.0;
        } catch (Exception ignored) {}
        Shape clickedShape = findShapeAtOutline(worldPos, worldTol);
        if (!ImGui.getIO().getKeyShift() && clickedShape == null) {
            appState.clearSelection();
            core.markDirty(CanvasCore.DirtyType.CONTENT);
            return;
        }

        // 如果点击到了某个图形
        if (clickedShape != null) {
            boolean isShiftPressed = ImGui.getIO().getKeyShift();
            boolean isAlreadySelected = appState.getSelectedShapes().contains(clickedShape);
            
            if (isShiftPressed) {
                // Shift模式：反转选中状态
                if (isAlreadySelected) {
                    appState.removeSelectedShape(clickedShape);
                } else {
                    appState.addSelectedShape(clickedShape);
                }
            } else {
                // 普通模式：如果没点中已选图形，则只选中当前图形
                if (!isAlreadySelected) {
                    appState.clearSelection();
                    appState.addSelectedShape(clickedShape);
                }
                // 如果点击了已选中的图形，则什么都不做，准备拖动
            }
            core.markDirty(CanvasCore.DirtyType.CONTENT);
        }
    }
    
    /**
     * 在指定位置查找最上层的图形
     * @param worldPos 世界坐标
     * @return 找到的图形，否则为null
     */
    private Shape findShapeAtOutline(Vec2d worldPos, double worldTolerance) {
        List<ILayer> layers = core.getLayers();
        if (layers.isEmpty()) {
            return null;
        }
        
        // 从上层图层开始反向遍历
        for (int i = layers.size() - 1; i >= 0; i--) {
            ILayer layer = layers.get(i);
            if (layer.isVisible() && !layer.isLocked()) {
                List<Shape> shapes = layer.getShapes();
                // 从图层中的最上层图形开始反向遍历
                for (int j = shapes.size() - 1; j >= 0; j--) {
                    Shape shape = shapes.get(j);
                    if (com.plot.ui.tools.impl.modify.helper.GeometricSelectionHelper
                            .isPointOnShape(shape, worldPos, worldTolerance)) {
                        return shape;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取当前选中的图形列表 (直接从AppState获取)
     * @return 选中图形的只读列表
     */
    public List<Shape> getSelectedShapes() {
        return appState.getSelectedShapes();
    }

    /**
     * 关闭资源
     */
    public void close() {
        LOGGER.debug("释放CanvasInputHandler资源...");
        
        // 清理事件处理器
        onMouseClicked = null;
        onMouseReleased = null;
        onMouseDragged = null;
        onMouseMoved = null;
        // onKeyPressed = null;
    }
}