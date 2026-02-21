package com.masterplanner.core.command.commands;

import com.masterplanner.core.command.Command;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.core.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 修改图形的命令
 * 优化版：不再依赖已被移除的shapes字段，使用AppState的addShape/removeShape方法
 */
public class ModifyCommand implements Command {
    protected List<Shape> oldShapes;
    protected List<Shape> newShapes;
    protected AppState appState;
    
    public ModifyCommand(List<Shape> oldShapes, List<Shape> newShapes, AppState appState) {
        this.oldShapes = new ArrayList<>(oldShapes);
        this.newShapes = new ArrayList<>(newShapes);
        this.appState = appState;
    }
    
    @Override
    public void execute() {
        try {
            System.out.println("ModifyCommand.execute() - 开始执行修改命令");
            System.out.println("ModifyCommand.execute() - 旧图形数量: " + oldShapes.size());
            System.out.println("ModifyCommand.execute() - 新图形数量: " + newShapes.size());
            
            // 移除旧图形
            for (Shape shape : oldShapes) {
                System.out.println("ModifyCommand.execute() - 移除图形: " + shape.getId());
                appState.removeShape(shape);
            }
            
            // 添加新图形
            for (Shape shape : newShapes) {
                System.out.println("ModifyCommand.execute() - 添加图形: " + shape.getId());
                appState.addShape(shape);
            }
            
            System.out.println("ModifyCommand.execute() - 修改命令执行完成");
        } catch (Exception e) {
            // 记录错误但继续执行
            System.err.println("ModifyCommand.execute() - 执行失败: " + e.getMessage());
            e.printStackTrace();
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

        if (oldCount == 0 && newCount > 0) {
            return String.format("绘制 %d 个%s", newCount, summarizeShapeTypes(newShapes));
        }

        if (oldCount > 0 && newCount == 0) {
            return String.format("删除 %d 个%s", oldCount, summarizeShapeTypes(oldShapes));
        }

        if (oldCount == newCount && oldCount > 0) {
            return String.format("修改 %d 个%s", oldCount, summarizeShapeTypes(newShapes));
        }

        if (oldCount > 0 && newCount > 0) {
            return String.format("变更图形 %d→%d", oldCount, newCount);
        }

        return "修改图形";
    }
    
    @Override
    public String getDetailedDescription() {
        StringBuilder details = new StringBuilder();
        int oldCount = oldShapes.size();
        int newCount = newShapes.size();

        details.append(String.format(
                """
                        修改操作
                操作类型: %s
                对象数量: %d → %d
                图形类型: %s
                        所在图层: %s""",
            resolveOperationType(oldCount, newCount),
            oldCount,
            newCount,
            oldCount == 0 ? summarizeShapeTypes(newShapes) : summarizeShapeTypes(oldShapes),
            appState.getActiveLayer().getName()
        ));

        // 添加修改前后的对象类型统计
        if (!oldShapes.isEmpty()) {
            details.append("\n原始对象类型:");
            oldShapes.stream()
                .map(shape -> shape.getClass().getSimpleName())
                .distinct()
                .forEach(type -> details.append("\n- ").append(type));
        }

        if (!newShapes.isEmpty()) {
            details.append("\n修改后对象类型:");
            newShapes.stream()
                .map(shape -> shape.getClass().getSimpleName())
                .distinct()
                .forEach(type -> details.append("\n- ").append(type));
        }

        // 添加修改前后的边界框变化
        if (!oldShapes.isEmpty() && !newShapes.isEmpty()) {
            BoundingBox oldBounds = calculateCombinedBounds(oldShapes);
            BoundingBox newBounds = calculateCombinedBounds(newShapes);
            
            if (oldBounds != null && newBounds != null) {
                details.append(String.format(
                        """
                                
                                边界框变化:\
                                
                                - 原始: (%.2f, %.2f) → (%.2f, %.2f)\
                                
                                - 修改后: (%.2f, %.2f) → (%.2f, %.2f)""",
                    oldBounds.getMinX(), oldBounds.getMinY(),
                    oldBounds.getMaxX(), oldBounds.getMaxY(),
                    newBounds.getMinX(), newBounds.getMinY(),
                    newBounds.getMaxX(), newBounds.getMaxY()
                ));
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
    
    protected List<Shape> getTargetShapes() {
        return newShapes;
    }

    private String resolveOperationType(int oldCount, int newCount) {
        if (oldCount == 0 && newCount > 0) {
            return "绘制";
        }
        if (oldCount > 0 && newCount == 0) {
            return "删除";
        }
        if (oldCount == newCount && oldCount > 0) {
            return "修改";
        }
        if (oldCount > 0 && newCount > 0) {
            return "变更";
        }
        return "未知";
    }

    private String summarizeShapeTypes(List<Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return "图形";
        }

        Set<String> uniqueTypes = new LinkedHashSet<>();
        for (Shape shape : shapes) {
            if (shape == null) {
                continue;
            }
            uniqueTypes.add(toDisplayShapeType(shape.getClass().getSimpleName()));
        }

        if (uniqueTypes.isEmpty()) {
            return "图形";
        }

        if (uniqueTypes.size() == 1) {
            return uniqueTypes.iterator().next();
        }

        List<String> typeList = new ArrayList<>(uniqueTypes);
        if (typeList.size() <= 3) {
            return String.join("、", typeList);
        }
        return String.format("%s、%s、%s等%d类图形", typeList.get(0), typeList.get(1), typeList.get(2), typeList.size());
    }

    private String toDisplayShapeType(String className) {
        return switch (className) {
            case "LineShape" -> "直线";
            case "CircleShape" -> "圆";
            case "ArcShape" -> "圆弧";
            case "RectangleShape" -> "矩形";
            case "EllipseShape" -> "椭圆";
            case "EllipticalArcShape" -> "椭圆弧";
            case "PolylineShape" -> "多段线";
            case "BezierCurveShape" -> "贝塞尔曲线";
            case "SineCurveShape" -> "正弦曲线";
            case "SpiralShape" -> "螺旋线";
            case "TextShape" -> "文字";
            case "AnnotationShape" -> "标注";
            case "CableShape" -> "电缆";
            case "Polygon" -> "多边形";
            case "FreeDrawPath" -> "自由绘制路径";
            default -> className.endsWith("Shape")
                    ? className.substring(0, className.length() - "Shape".length())
                    : className;
        };
    }
}
