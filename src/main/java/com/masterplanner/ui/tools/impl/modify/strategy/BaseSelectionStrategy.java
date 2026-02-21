package com.masterplanner.ui.tools.impl.modify.strategy;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.BoundingBox;
import com.masterplanner.core.graphics.DrawContext;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.canvas.CanvasCamera;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.tools.impl.modify.helper.GeometricSelectionHelper;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyResult;
import com.masterplanner.ui.tools.impl.modify.strategy.IModifyStrategy.ModifyToolContext;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基础选择策略 - 通用选择逻辑
 *
 * <p>提供点选和框选的基本功能，供其他工具策略继承使用：</p>
 * <ul>
 *   <li>点选：单击选择单个图形</li>
 *   <li>框选：拖拽选择多个图形</li>
 *   <li>多选：Ctrl键配合进行添加/移除选择</li>
 *   <li>选择状态管理</li>
 * </ul>
 *
 * @author MasterPlanner Team
 * @version 1.0 - 基础选择策略
 */
public abstract class BaseSelectionStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseSelectionStrategy.class);

    // 常量
    protected static final double SELECTION_TOLERANCE = 5.0;
    protected static final double DRAG_THRESHOLD = 4.0;
    protected static final int MOUSE_LEFT = 0;
    protected static final int MOUSE_RIGHT = 1;
    
    // 渲染常量
    private static final int SELECTION_ALPHA = 255;

    // 选择状态
    protected boolean isSelecting = false;
    protected boolean isPointSelecting = false;
    protected boolean isLeftToRight = true;
    protected boolean isCtrlPressed = false;

    // 坐标状态
    protected Vec2d startPoint;
    protected Vec2d currentPoint;
    protected Vec2d initialClickPoint;

    // 选择数据
    protected final Set<String> selectedShapeIds = new HashSet<>();
    protected final Set<String> tempSelectedShapeIds = new HashSet<>();
    protected List<Shape> selectedShapes;

    /**
     * 处理选择模式下的左键按下
     */
    protected ModifyResult handleSelectionMouseDown(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("BaseSelectionStrategy 处理选择鼠标按下: pos={}", pos);
        
        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        startPoint = snappedPoint;
        currentPoint = snappedPoint;
        initialClickPoint = snappedPoint;
        isSelecting = true;
        isPointSelecting = true;

        LOGGER.debug("设置选择状态: isSelecting={}, isPointSelecting={}", true, true);

        // 检查Ctrl键状态用于多选
        try {
            isCtrlPressed = imgui.ImGui.getIO().getKeyCtrl();
        } catch (Exception ignored) {}

        // 参考SelectionStrategy，对于Ctrl+点击立即处理
        if (isCtrlPressed) {
            Shape clickedShape = context.findShapeAt(pos, SELECTION_TOLERANCE);
            if (clickedShape != null) {
                LOGGER.debug("Ctrl+点击到图形 {}", clickedShape.getId());
                if (selectedShapeIds.contains(clickedShape.getId())) {
                    selectedShapeIds.remove(clickedShape.getId());
                    updateShapeSelection(clickedShape, false, context);
                    LOGGER.debug("取消选择图形 {}", clickedShape.getId());
                } else {
                    selectedShapeIds.add(clickedShape.getId());
                    updateShapeSelection(clickedShape, true, context);
                    LOGGER.debug("选中图形 {}", clickedShape.getId());
                }
                context.setStatusMessage(String.format("已选择 %d 个图形，右键开始操作", selectedShapeIds.size()));
            }
        }

        LOGGER.debug("开始选择操作，起点: {}, 当前选中图形数: {}", startPoint, selectedShapeIds.size());
        return ModifyResult.CONTINUE;
    }

    /**
     * 处理选择模式下的鼠标移动
     */
    protected ModifyResult handleSelectionMouseMove(Vec2d pos, ModifyToolContext context) {
        if (!isSelecting) {
            return ModifyResult.IGNORED;
        }

        Vec2d snappedPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());
        currentPoint = snappedPoint;

        // 检查是否从点选切换到框选
        if (isPointSelecting && initialClickPoint != null) {
            double moveDistance = initialClickPoint.distance(snappedPoint);
            if (moveDistance > DRAG_THRESHOLD) {
                isPointSelecting = false;
                LOGGER.debug("从点选切换到框选模式，移动距离: {}", moveDistance);
            }
        }

        if (!isPointSelecting) {
            // 框选模式：确定选择方向并更新临时选择
            if (startPoint != null) {
                isLeftToRight = pos.x >= startPoint.x;
                updateTemporarySelection(context);
            }
        }

        return ModifyResult.CONTINUE;
    }

    /**
     * 处理选择模式下的鼠标释放（参考SelectionStrategy的逻辑）
     */
    protected ModifyResult handleSelectionMouseUp(Vec2d pos, ModifyToolContext context) {
        LOGGER.debug("BaseSelectionStrategy 处理选择鼠标释放: pos={}, isPointSelecting={}", pos, isPointSelecting);
        
        currentPoint = context.getSnapHandler().getSnappedWorldPoint(pos, context.getCamera());

        if (isPointSelecting) {
            // 参考SelectionStrategy：点选模式的最终处理，统一处理所有点选逻辑
            if (!isCtrlPressed) {
                // 如果不是Ctrl模式，先清除所有之前的选择
                clearSelection(context);
                
                Shape clickedShape = context.findShapeAt(pos, SELECTION_TOLERANCE);
                if (clickedShape != null) {
                    // 如果点到了图形，就选中它
                    selectedShapeIds.add(clickedShape.getId());
                    updateShapeSelection(clickedShape, true, context);
                    LOGGER.debug("完成点选，选中图形 {}", clickedShape.getId());
                } else {
                    // 如果点在空白处，选择集已在上面被清空
                    LOGGER.debug("点击空白区域，清除所有选择");
                }
            }
            // 如果是Ctrl模式，由于MouseDown已经处理了，这里不需要做任何事
            
            LOGGER.debug("完成点选，选中 {} 个形状", selectedShapeIds.size());
            context.setStatusMessage("已选择 " + selectedShapeIds.size() + " 个图形，右键开始操作");
        } else {
            // 框选模式：在finalizeBoxSelection中处理选择清除逻辑
            finalizeBoxSelection(context);
            LOGGER.debug("完成框选，选中 {} 个形状", selectedShapeIds.size());
            context.setStatusMessage("框选完成，已选择 " + selectedShapeIds.size() + " 个图形，右键开始操作");
        }

        // 重置选择状态
        isSelecting = false;
        isPointSelecting = false;
        clearTemporarySelection(context);

        LOGGER.debug("选择操作完成，最终选中图形数: {}", selectedShapeIds.size());
        return ModifyResult.COMPLETE;
    }

    /**
     * 更新图形选择状态
     */
    protected void updateShapeSelection(Shape shape, boolean selected, ModifyToolContext context) {
        if (shape != null) {
            shape.setSelected(selected);
            if (selected) {
                context.addSelectedShape(shape);
            } else {
                shape.setHighlighted(false);
                context.removeSelectedShape(shape);
            }
        }
    }

    /**
     * 清除所有选择
     */
    protected void clearSelection(ModifyToolContext context) {
        try {
            List<Shape> allShapes = com.masterplanner.core.state.AppState.getInstance().getShapes();
            for (Shape shape : allShapes) {
                if (selectedShapeIds.contains(shape.getId())) {
                    shape.setSelected(false);
                    shape.setHighlighted(false);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("清除选择状态时出错: {}", e.getMessage());
        }

        context.clearSelection();
        selectedShapeIds.clear();
    }

    /**
     * 更新临时选择（框选预览）
     */
    protected void updateTemporarySelection(ModifyToolContext context) {
        if (startPoint == null || currentPoint == null) return;

        clearTemporarySelection(context);

        List<Shape> shapesInArea = context.findShapesInArea(startPoint, currentPoint);

        for (Shape shape : shapesInArea) {
            if (isShapeInSelection(shape, startPoint, currentPoint, isLeftToRight)) {
                tempSelectedShapeIds.add(shape.getId());
                if (!shape.isSelected()) {
                    shape.setHighlighted(true);
                }
            }
        }
    }

    /**
     * 清除临时选择
     */
    protected void clearTemporarySelection(ModifyToolContext context) {
        if (!tempSelectedShapeIds.isEmpty()) {
            try {
                List<Shape> allShapes = com.masterplanner.core.state.AppState.getInstance().getShapes();
                for (Shape shape : allShapes) {
                    if (tempSelectedShapeIds.contains(shape.getId()) && !shape.isSelected()) {
                        shape.setHighlighted(false);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("清除临时选择高亮时出错: {}", e.getMessage());
            }
        }
        tempSelectedShapeIds.clear();
    }

    /**
     * 完成框选
     */
    protected void finalizeBoxSelection(ModifyToolContext context) {
        if (startPoint == null || currentPoint == null) return;

        if (!isCtrlPressed) {
            clearSelection(context);
        }

        List<Shape> shapesInArea = context.findShapesInArea(startPoint, currentPoint);

        for (Shape shape : shapesInArea) {
            if (isShapeInSelection(shape, startPoint, currentPoint, isLeftToRight)) {
                if (isCtrlPressed && selectedShapeIds.contains(shape.getId())) {
                    selectedShapeIds.remove(shape.getId());
                    updateShapeSelection(shape, false, context);
                } else if (!selectedShapeIds.contains(shape.getId())) {
                    selectedShapeIds.add(shape.getId());
                    updateShapeSelection(shape, true, context);
                }
            }
        }
    }

    /**
     * 检查图形是否在选择区域内
     */
    protected boolean isShapeInSelection(Shape shape, Vec2d start, Vec2d end, boolean leftToRight) {
        try {
            return GeometricSelectionHelper.isShapeInRectangleSelection(shape, start, end, leftToRight);
        } catch (Exception e) {
            LOGGER.warn("精确几何选择检测失败: {}", e.getMessage());

            // 回退到包围框检测
            BoundingBox shapeBounds = shape.getBoundingBox();
            if (shapeBounds == null) {
                return false;
            }

            double minX = Math.min(start.x, end.x);
            double minY = Math.min(start.y, end.y);
            double maxX = Math.max(start.x, end.x);
            double maxY = Math.max(start.y, end.y);

            if (leftToRight) {
                return shapeBounds.getMinX() >= minX && shapeBounds.getMinY() >= minY &&
                       shapeBounds.getMaxX() <= maxX && shapeBounds.getMaxY() <= maxY;
            } else {
                return !(shapeBounds.getMaxX() < minX || shapeBounds.getMinX() > maxX ||
                        shapeBounds.getMaxY() < minY || shapeBounds.getMinY() > maxY);
            }
        }
    }

    /**
     * 从ID列表获取图形对象
     */
    protected List<Shape> getSelectedShapesFromIds(ModifyToolContext context) {
        List<Shape> result = new ArrayList<>();
        try {
            List<Shape> allShapes = com.masterplanner.core.state.AppState.getInstance().getShapes();
            for (Shape shape : allShapes) {
                if (selectedShapeIds.contains(shape.getId())) {
                    result.add(shape);
                }
            }
        } catch (Exception e) {
            LOGGER.error("获取选中图形时出错: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 重置选择状态
     */
    protected void resetSelectionState() {
        isSelecting = false;
        isPointSelecting = false;
        isLeftToRight = true;
        startPoint = null;
        currentPoint = null;
        initialClickPoint = null;
        selectedShapeIds.clear();
        tempSelectedShapeIds.clear();
        selectedShapes = null;
    }

    /**
     * 获取选中图形的数量
     */
    public int getSelectedCount() {
        return selectedShapeIds.size();
    }

    /**
     * 检查是否有选中的图形
     */
    public boolean hasSelection() {
        return !selectedShapeIds.isEmpty();
    }

    /**
     * 渲染选择预览
     */
    public void renderSelectionPreview(DrawContext context) {
        // 只有在框选模式下才显示选框
        if (startPoint == null || currentPoint == null || isPointSelecting || !isSelecting) {
            return;
        }
        Color selectionColor = getSelectionColor();

        if (isLeftToRight) {
            context.drawRect(startPoint, currentPoint, selectionColor);
        } else {
            drawDashedRect(context, startPoint, currentPoint);
        }
    }

    /**
     * 渲染选择预览（ImGui版本）
     */
    public void renderSelectionPreviewImGui(ImDrawList drawList, CanvasCamera camera) {
        // 只有在框选模式下才显示选框
        if (startPoint == null || currentPoint == null || isPointSelecting || !isSelecting) {
            return;
        }

        try {
            Vec2d screenStart = camera.worldToScreen(startPoint);
            Vec2d screenEnd = camera.worldToScreen(currentPoint);

            float minX = (float) Math.min(screenStart.x, screenEnd.x);
            float minY = (float) Math.min(screenStart.y, screenEnd.y);
            float maxX = (float) Math.max(screenStart.x, screenEnd.x);
            float maxY = (float) Math.max(screenStart.y, screenEnd.y);

            int color = ThemeManager.getInstance().getCurrentTheme().text;
            float lineWidth = 1.0f;

            if (isLeftToRight) {
                drawList.addRect(minX, minY, maxX, maxY, color, 0.0f, 0, lineWidth);
            } else {
                drawDashedRectImGui(drawList, minX, minY, maxX, maxY, color);
            }
        } catch (Exception e) {
            LOGGER.warn("渲染选择预览时出错: {}", e.getMessage());
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
        Color selectionColor = getSelectionColor();

        context.drawDashedLine(topLeft, topRight, selectionColor);
        context.drawDashedLine(topRight, bottomRight, selectionColor);
        context.drawDashedLine(bottomRight, bottomLeft, selectionColor);
        context.drawDashedLine(bottomLeft, topLeft, selectionColor);
    }

    private Color getSelectionColor() {
        return withAlpha(toColor(ThemeManager.getInstance().getCurrentTheme().text), SELECTION_ALPHA);
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
     * 绘制虚线
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
}
