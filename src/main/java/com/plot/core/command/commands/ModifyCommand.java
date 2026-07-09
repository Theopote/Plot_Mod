package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.core.geometry.BoundingBox;
import com.plot.utils.PlotI18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 修改图形的命令
 * 优化版：不再依赖已被移除的shapes字段，使用AppState的addShape/removeShape方法
 */
public class ModifyCommand implements Command {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ModifyCommand.class);

    protected List<Shape> oldShapes;
    protected List<Shape> newShapes;
    protected AppState appState;
    protected String operationName;
    
    public ModifyCommand(List<Shape> oldShapes, List<Shape> newShapes, AppState appState) {
        this(oldShapes, newShapes, appState, null);
    }

    public ModifyCommand(List<Shape> oldShapes, List<Shape> newShapes, AppState appState, String operationName) {
        this.oldShapes = new ArrayList<>(oldShapes);
        this.newShapes = new ArrayList<>(newShapes);
        this.appState = appState;
        this.operationName = operationName;
    }
    
    @Override
    public void execute() {
        try {
            LOGGER.debug("开始执行修改命令，旧图形数量: {}，新图形数量: {}", oldShapes.size(), newShapes.size());

            // 移除旧图形
            for (Shape shape : oldShapes) {
                LOGGER.debug("移除图形: {}", shape.getId());
                appState.removeShape(shape);
            }

            // 添加新图形
            for (Shape shape : newShapes) {
                LOGGER.debug("添加图形: {}", shape.getId());
                appState.addShape(shape);
            }

            LOGGER.debug("修改命令执行完成");
        } catch (Exception e) {
            LOGGER.error("修改命令执行失败", e);
        }
    }
    
    @Override
    public void undo() {
        // 移除新图形
        for (Shape shape : newShapes) {
            appState.removeShape(shape);
        }
        
        // 恢复旧图形
        for (Shape shape : oldShapes) {
            appState.addShape(shape);
        }
    }
    
    @Override
    public void redo() {
        // 移除旧图形
        for (Shape shape : oldShapes) {
            appState.removeShape(shape);
        }
        
        // 添加新图形
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }
    
    @Override
    public String getDescription() {
        int oldCount = oldShapes.size();
        int newCount = newShapes.size();
        String operationType = resolveOperationType(oldCount, newCount);

        if (oldCount == 0 && newCount > 0) {
            return PlotI18n.tr("history.plot.modify.draw", newCount, summarizeShapeTypes(newShapes));
        }

        if (oldCount > 0 && newCount == 0) {
            return PlotI18n.tr("history.plot.modify.delete", oldCount, summarizeShapeTypes(oldShapes));
        }

        if (oldCount == newCount && oldCount > 0) {
            return PlotI18n.tr("history.plot.modify.modify", oldCount, summarizeShapeTypes(newShapes));
        }

        if (oldCount > 0 && newCount > 0) {
            return PlotI18n.tr("history.plot.modify.change", operationType, oldCount, newCount);
        }

        return PlotI18n.tr("history.plot.modify.generic", operationType);
    }

    @Override
    public String getDetailedDescription() {
        int oldCount = oldShapes.size();
        int newCount = newShapes.size();
        String operationType = resolveOperationType(oldCount, newCount);
        String shapeSummary = oldCount == 0 ? summarizeShapeTypes(newShapes) : summarizeShapeTypes(oldShapes);

        StringBuilder details = new StringBuilder(PlotI18n.tr(
                "history.plot.modify.detail_header",
                operationType,
                oldCount,
                newCount,
                shapeSummary,
                appState.getActiveLayer().getName()));

        if (!oldShapes.isEmpty()) {
            details.append('\n').append(PlotI18n.tr("history.plot.modify.original_types"));
            oldShapes.stream()
                .map(shape -> shape.getClass().getSimpleName())
                .distinct()
                .forEach(type -> details.append('\n').append("- ").append(PlotI18n.shapeTypeLabel(type)));
        }

        if (!newShapes.isEmpty()) {
            details.append('\n').append(PlotI18n.tr("history.plot.modify.result_types"));
            newShapes.stream()
                .map(shape -> shape.getClass().getSimpleName())
                .distinct()
                .forEach(type -> details.append('\n').append("- ").append(PlotI18n.shapeTypeLabel(type)));
        }

        // 添加修改前后的边界框变化
        if (!oldShapes.isEmpty() && !newShapes.isEmpty()) {
            BoundingBox oldBounds = calculateCombinedBounds(oldShapes);
            BoundingBox newBounds = calculateCombinedBounds(newShapes);
            
            if (oldBounds != null && newBounds != null) {
                details.append('\n').append(PlotI18n.tr(
                        "history.plot.modify.bounds_change",
                        oldBounds.getMinX(), oldBounds.getMinY(),
                        oldBounds.getMaxX(), oldBounds.getMaxY(),
                        newBounds.getMinX(), newBounds.getMinY(),
                        newBounds.getMaxX(), newBounds.getMaxY()));
            }
        }

        return details.toString();
    }

    /**
     * 获取修改前的图形列表（副本）
     */
    public List<Shape> getOldShapes() {
        return new ArrayList<>(oldShapes);
    }

    /**
     * 获取修改后的图形列表（副本）
     */
    public List<Shape> getNewShapes() {
        return new ArrayList<>(newShapes);
    }

    /**
     * 计算多个图形的组合边界框
     */
    private BoundingBox calculateCombinedBounds(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return null;
        }

        // 从第一个图形开始
        BoundingBox firstBounds = shapes.getFirst().getBoundingBox();
        if (firstBounds == null) {
            return null;
        }

        // 如果只有一个图形，直接返回其边界框
        if (shapes.size() == 1) {
            return firstBounds;
        }

        // 合并所有图形的边界框
        double minX = firstBounds.getMinX();
        double minY = firstBounds.getMinY();
        double maxX = firstBounds.getMaxX();
        double maxY = firstBounds.getMaxY();

        for (int i = 1; i < shapes.size(); i++) {
            BoundingBox bounds = shapes.get(i).getBoundingBox();
            if (bounds == null) continue;

            minX = Math.min(minX, bounds.getMinX());
            minY = Math.min(minY, bounds.getMinY());
            maxX = Math.max(maxX, bounds.getMaxX());
            maxY = Math.max(maxY, bounds.getMaxY());
        }

        return new BoundingBox(minX, minY, maxX, maxY);
    }
    
    protected void setTargetShapes(List<Shape> shapes) {
        this.newShapes = new ArrayList<>(shapes);
    }

    private String resolveOperationType(int oldCount, int newCount) {
        if (operationName != null && !operationName.isBlank()) {
            return PlotI18n.operationName(operationName);
        }

        if (oldCount == 0 && newCount > 0) {
            return PlotI18n.tr("history.plot.op.draw");
        }
        if (oldCount > 0 && newCount == 0) {
            return PlotI18n.tr("history.plot.op.delete");
        }
        if (oldCount == newCount && oldCount > 0) {
            return PlotI18n.tr("history.plot.op.modify");
        }
        if (oldCount > 0 && newCount > 0) {
            return PlotI18n.tr("history.plot.op.change");
        }
        return PlotI18n.tr("history.plot.op.unknown");
    }

    private String summarizeShapeTypes(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return PlotI18n.tr("history.plot.shape.generic");
        }

        Set<String> uniqueTypes = new LinkedHashSet<>();
        for (Shape shape : shapes) {
            if (shape == null) {
                continue;
            }
            uniqueTypes.add(PlotI18n.shapeTypeLabel(shape.getClass().getSimpleName()));
        }

        if (uniqueTypes.isEmpty()) {
            return PlotI18n.tr("history.plot.shape.generic");
        }

        if (uniqueTypes.size() == 1) {
            return uniqueTypes.iterator().next();
        }

        List<String> typeList = new ArrayList<>(uniqueTypes);
        if (typeList.size() <= 3) {
            return String.join(PlotI18n.tr("history.plot.list_separator"), typeList);
        }
        return PlotI18n.tr("history.plot.shape.mixed", typeList.get(0), typeList.get(1), typeList.get(2), typeList.size());
    }
}
