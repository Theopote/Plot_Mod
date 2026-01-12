package com.masterplanner.ui.tools.impl.modify.helper;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.command.commands.ChamferCommand;
import com.masterplanner.core.command.commands.ModifyCommand;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import com.masterplanner.ui.tools.impl.modify.ChamferTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 倒角操作处理器
 * 
 * <p>专门处理图形倒角操作的逻辑，包括：</p>
 * <ul>
 *   <li>倒角参数计算和验证</li>
 *   <li>直线交点计算</li>
 *   <li>倒角直线生成</li>
 *   <li>直线修剪处理</li>
 *   <li>预览图形生成</li>
 *   <li>倒角命令创建</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 优化几何计算和验证逻辑
 */
public class ChamferHandler implements IModifyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamferHandler.class);
    
    // 倒角容差常量
    private static final double CHAMFER_TOLERANCE = 0.001;
    
    // 优化：使用ChamferTool的常量，确保一致性
    private static final double MIN_DISTANCE = ChamferTool.MIN_DISTANCE;
    private static final double MAX_DISTANCE = ChamferTool.MAX_DISTANCE;
    
    private final AppState appState;
    
    /**
     * 构造函数
     * @param appState 应用状态管理器
     */
    public ChamferHandler(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public ModifyType getModifyType() {
        return ModifyType.CHAMFER;
    }
    
    @Override
    public ValidationResult validateModification(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        // 检查图形列表
        if (shapes == null || shapes.size() != 2) {
            return ValidationResult.invalid("倒角操作需要选择两条直线");
        }
        
        // 检查是否为直线
        for (Shape shape : shapes) {
            if (!(shape instanceof LineShape)) {
                return ValidationResult.invalid("倒角操作只能应用于直线");
            }
        }
        
        // 检查距离参数
        double distance = getDistanceFromParameters(parameters);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            return ValidationResult.invalid(String.format("倒角距离必须在 %.1f 到 %.1f 之间", MIN_DISTANCE, MAX_DISTANCE));
        }
        
        // 检查两条直线是否相交
        LineShape line1 = (LineShape) shapes.get(0);
        LineShape line2 = (LineShape) shapes.get(1);
        
        if (!linesIntersect(line1, line2)) {
            return ValidationResult.invalid("两条直线必须相交才能进行倒角操作");
        }
        
        // 优化：加强验证 - 检查倒角距离是否过大
        Vec2d intersection = calculateIntersection(line1, line2);
        if (intersection != null) {
            double distToLine1Start = distance(intersection, line1.getStart());
            double distToLine1End = distance(intersection, line1.getEnd());
            double distToLine2Start = distance(intersection, line2.getStart());
            double distToLine2End = distance(intersection, line2.getEnd());
            
            double minDistToLine1 = Math.min(distToLine1Start, distToLine1End);
            double minDistToLine2 = Math.min(distToLine2Start, distToLine2End);
            
            if (distance > minDistToLine1 || distance > minDistToLine2) {
                return ValidationResult.invalid(String.format("倒角距离 %.1f 过大，超出线段范围", distance));
            }
        }
        
        return ValidationResult.valid();
    }
    
    @Override
    public List<Shape> calculateModifiedShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        if (shapes.size() != 2) {
            LOGGER.warn("倒角操作需要两条直线，但提供了 {} 个图形", shapes.size());
            return new ArrayList<>(shapes);
        }
        
        LineShape line1 = (LineShape) shapes.get(0);
        LineShape line2 = (LineShape) shapes.get(1);
        double distance = getDistanceFromParameters(parameters);
        
        List<Shape> result = new ArrayList<>();
        
        try {
            // 计算倒角参数
            ChamferParameters chamferParams = calculateChamferParameters(line1, line2, distance);
            
            if (chamferParams == null) {
                LOGGER.warn("无法计算倒角参数");
                result.addAll(shapes);
                return result;
            }
            
            // 优化：修复直线修剪逻辑 - 使用正确的修剪方法
            LineShape trimmedLine1 = createTrimmedLine(line1, chamferParams.trimPoint1);
            trimmedLine1.setStyle(line1.getStyle());
            result.add(trimmedLine1);
            
            LineShape trimmedLine2 = createTrimmedLine(line2, chamferParams.trimPoint2);
            trimmedLine2.setStyle(line2.getStyle());
            result.add(trimmedLine2);
            
            // 创建倒角直线
            LineShape chamferLine = new LineShape(chamferParams.trimPoint1, chamferParams.trimPoint2);
            chamferLine.setStyle(line1.getStyle());
            result.add(chamferLine);
            
            LOGGER.debug("倒角操作完成: 创建了 {} 个新图形", result.size());
            
        } catch (Exception e) {
            LOGGER.error("倒角操作失败", e);
            // 如果倒角失败，返回原始图形
            result.addAll(shapes);
        }
        
        return result;
    }
    
    @Override
    public List<Shape> createPreviewShapes(List<Shape> shapes, IModifyHandler.ModifyParameters parameters) {
        List<Shape> modifiedShapes = calculateModifiedShapes(shapes, parameters);
        
        // 预览样式应与原图形一致
        for (int i = 0; i < modifiedShapes.size(); i++) {
            Shape preview = modifiedShapes.get(i);
            Shape original = i < shapes.size() ? shapes.get(i) : null;
            if (original != null && original.getStyle() != null) {
                try { preview.setStyle(original.getStyle().clone()); } catch (Exception ignore) {}
            }
            
            // 修复：清除预览图形的选中状态，确保使用图层颜色而不是选中颜色
            preview.setSelected(false);
            preview.setHighlighted(false);
        }
        
        return modifiedShapes;
    }
    
    @Override
    public ModifyCommand createModifyCommand(List<Shape> originalShapes, 
                                           List<Shape> modifiedShapes, 
                                           IModifyHandler.ModifyParameters parameters) {
        if (originalShapes.size() != 2) {
            LOGGER.warn("倒角命令需要两条直线，但提供了 {} 个图形", originalShapes.size());
            return null;
        }
        
        LineShape line1 = (LineShape) originalShapes.get(0);
        LineShape line2 = (LineShape) originalShapes.get(1);
        double distance = getDistanceFromParameters(parameters);
        
        try {
            // 优化：计算最终生成的图形列表，避免在命令中重复计算
            List<Shape> finalShapes = calculateModifiedShapes(originalShapes, parameters);
            
            if (finalShapes.isEmpty() || finalShapes.size() == originalShapes.size()) {
                LOGGER.warn("无法生成有效的倒角图形");
                return null;
            }
            
            // 创建倒角命令 - 使用新的构造函数
            return new ChamferCommand(line1, line2, distance, finalShapes, appState);
            
        } catch (Exception e) {
            LOGGER.error("创建倒角命令失败", e);
            return null;
        }
    }
    
    /**
     * 从参数中获取距离值
     * 优化：统一使用ChamferTool.CONFIG_KEY_DISTANCE，确保参数来源唯一
     */
    private double getDistanceFromParameters(IModifyHandler.ModifyParameters parameters) {
        // 统一使用ChamferTool的配置键
        if (parameters.hasParameter(ChamferTool.CONFIG_KEY_DISTANCE)) {
            Object distanceObj = parameters.getParameter(ChamferTool.CONFIG_KEY_DISTANCE);
            if (distanceObj instanceof Number) {
                return ((Number) distanceObj).doubleValue();
            }
        }
        
        return ChamferTool.DEFAULT_DISTANCE; // 使用ChamferTool的默认值
    }
    
    /**
     * 倒角参数数据类
     */
    private static class ChamferParameters {
        Vec2d trimPoint1;
        Vec2d trimPoint2;
        
        ChamferParameters(Vec2d trimPoint1, Vec2d trimPoint2) {
            this.trimPoint1 = trimPoint1;
            this.trimPoint2 = trimPoint2;
        }
    }
    
    /**
     * 检查两条直线是否相交
     */
    private boolean linesIntersect(LineShape line1, LineShape line2) {
        try {
            Vec2d intersection = calculateIntersection(line1, line2);
            return intersection != null;
        } catch (Exception e) {
            LOGGER.warn("计算直线交点失败", e);
            return false;
        }
    }
    
    /**
     * 计算两条直线的交点
     */
    private Vec2d calculateIntersection(LineShape line1, LineShape line2) {
        Vec2d p1 = line1.getStart();
        Vec2d p2 = line1.getEnd();
        Vec2d p3 = line2.getStart();
        Vec2d p4 = line2.getEnd();
        
        // 计算方向向量
        double dx1 = p2.x - p1.x;
        double dy1 = p2.y - p1.y;
        double dx2 = p4.x - p3.x;
        double dy2 = p4.y - p3.y;
        
        // 计算行列式
        double det = dx1 * dy2 - dy1 * dx2;
        
        if (Math.abs(det) < CHAMFER_TOLERANCE) {
            return null; // 直线平行或重合
        }
        
        // 计算参数
        double t1 = ((p3.x - p1.x) * dy2 - (p3.y - p1.y) * dx2) / det;
        
        // 计算无限直线的交点
        return new Vec2d(p1.x + t1 * dx1, p1.y + t1 * dy1);
    }
    
    /**
     * 计算倒角参数
     * 优化：修复倒角点计算逻辑，正确计算从交点沿直线方向的修剪点
     */
    private ChamferParameters calculateChamferParameters(LineShape line1, LineShape line2, double distance) {
        try {
            // 计算交点
            Vec2d intersection = calculateIntersection(line1, line2);
            if (intersection == null) {
                LOGGER.warn("两条直线不相交，无法进行倒角");
                return null;
            }
            
            // 优化：正确计算倒角点 - 从交点沿直线方向回退指定距离
            Vec2d trimPoint1 = calculateTrimPoint(line1, intersection, distance);
            Vec2d trimPoint2 = calculateTrimPoint(line2, intersection, distance);

            return new ChamferParameters(trimPoint1, trimPoint2);
            
        } catch (Exception e) {
            LOGGER.error("计算倒角参数失败", e);
            return null;
        }
    }
    
    /**
     * 计算修剪点
     * 优化：正确计算从交点沿直线方向的修剪点
     */
    private Vec2d calculateTrimPoint(LineShape line, Vec2d intersection, double distance) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        // 计算直线方向向量
        Vec2d direction = normalize(new Vec2d(end.x - start.x, end.y - start.y));
        
        // 计算从交点到两个端点的距离
        double distToStart = distance(intersection, start);
        double distToEnd = distance(intersection, end);
        
        // 确定哪个端点更靠近交点
        Vec2d nearEndpoint = distToStart < distToEnd ? start : end;
        
        // 计算从交点指向近端点的向量
        Vec2d toNearEndpoint = new Vec2d(nearEndpoint.x - intersection.x, nearEndpoint.y - intersection.y);
        
        // 归一化并乘以距离，得到修剪点
        Vec2d normalizedDirection = normalize(toNearEndpoint);
        return new Vec2d(intersection.x + normalizedDirection.x * distance, 
                        intersection.y + normalizedDirection.y * distance);
    }
    
    /**
     * 向量归一化
     */
    private Vec2d normalize(Vec2d vector) {
        double length = Math.sqrt(vector.x * vector.x + vector.y * vector.y);
        if (length < CHAMFER_TOLERANCE) {
            return new Vec2d(0, 0);
        }
        return new Vec2d(vector.x / length, vector.y / length);
    }
    
    /**
     * 计算两点间距离
     */
    private double distance(Vec2d p1, Vec2d p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 创建修剪后的直线
     * 优化：修复修剪逻辑，保留距离较远的端点，将距离较近的端点替换为修剪点
     */
    private LineShape createTrimmedLine(LineShape line, Vec2d trimPoint) {
        Vec2d start = line.getStart();
        Vec2d end = line.getEnd();
        
        // 计算两个端点到修剪点的距离
        double distStartToTrim = distance(start, trimPoint);
        double distEndToTrim = distance(end, trimPoint);
        
        // 保留距离较远的端点，将距离较近的端点替换为修剪点
        Vec2d newStart, newEnd;
        
        if (distStartToTrim < distEndToTrim) {
            // 起点更靠近修剪点，保留终点
            newStart = trimPoint;
            newEnd = end;
        } else {
            // 终点更靠近修剪点，保留起点
            newStart = start;
            newEnd = trimPoint;
        }
        
        return new LineShape(newStart, newEnd);
    }
} 