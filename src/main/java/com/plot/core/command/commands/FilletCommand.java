package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.ArcShape;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 圆角命令
 * 在两条直线之间创建圆角
 */
public class FilletCommand extends ModifyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilletCommand.class);
    
    private final LineShape line1;
    private final LineShape line2;
    private final double radius;
    private final Vec2d center;
    private final double startAngle;
    private final double endAngle;
    private final Vec2d trimPoint1;
    private final Vec2d trimPoint2;
    private final Vec2d preservedEndPoint1;  // 第一条线要保留的端点
    private final Vec2d preservedEndPoint2;  // 第二条线要保留的端点
    
    // 保存原始状态用于撤销
    private LineShape originalLine1;
    private LineShape originalLine2;
    private List<LineShape> trimmedLines;
    
    public FilletCommand(LineShape line1, LineShape line2, double radius, 
                        Vec2d center, double startAngle, double endAngle,
                        Vec2d trimPoint1, Vec2d trimPoint2,
                        Vec2d preservedEndPoint1, Vec2d preservedEndPoint2, AppState appState) {
        super(List.of(line1, line2), new ArrayList<>(), appState);
        this.line1 = line1;
        this.line2 = line2;
        this.radius = radius;
        this.center = center;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
        this.trimPoint1 = trimPoint1;
        this.trimPoint2 = trimPoint2;
        this.preservedEndPoint1 = preservedEndPoint1;
        this.preservedEndPoint2 = preservedEndPoint2;
        this.trimmedLines = new ArrayList<>();
    }
    
    @Override
    public void execute() {
        try {
            LOGGER.debug("执行圆角命令: 半径={}, 中心={}, 角度范围=[{}, {}]", 
                radius, center, Math.toDegrees(startAngle), Math.toDegrees(endAngle));
            
            // 保存原始状态
            originalLine1 = (LineShape) line1.clone();
            originalLine2 = (LineShape) line2.clone();
            
            // 创建圆角圆弧
            ArcShape filletArc = new ArcShape(center, radius, startAngle, endAngle);
            filletArc.setStyle(line1.getStyle()); // 使用第一条线的样式
            
            // 使用传入的修剪点创建修剪后的直线
            trimmedLines = createTrimmedLines();
            
            // 删除原始直线
            line1.delete();
            line2.delete();
            
            // 添加修剪后的直线和圆角圆弧
            newShapes.addAll(trimmedLines);
            newShapes.add(filletArc);
            
            LOGGER.debug("圆角命令执行完成: 创建了 {} 个新图形", newShapes.size());
            
        } catch (Exception e) {
            LOGGER.error("执行圆角命令失败", e);
            throw new RuntimeException(PlotI18n.error("error.plot.command.fillet_failed", e.getMessage()), e);
        }
    }
    
    @Override
    public void undo() {
        try {
            LOGGER.debug("撤销圆角命令");
            
            // 删除新创建的图形
            for (Shape shape : newShapes) {
                if (shape != null && !shape.isDeleted()) {
                    shape.delete();
                }
            }
            newShapes.clear();
            
            // 恢复原始直线
            if (originalLine1 != null && originalLine2 != null) {
                // 使用基类中的appState字段，确保在正确的图层中恢复
                if (appState.getActiveLayer() != null) {
                    appState.getActiveLayer().addShape(originalLine1);
                    appState.getActiveLayer().addShape(originalLine2);
                    LOGGER.debug("原始直线已恢复到活动图层");
                } else {
                    LOGGER.warn("无法恢复原始直线：活动图层为空");
                }
            } else {
                LOGGER.warn("无法恢复原始直线：原始直线为空");
            }
            
        } catch (Exception e) {
            LOGGER.error("撤销圆角命令失败", e);
            // 即使撤销失败，也要清理新创建的图形
            try {
                for (Shape shape : newShapes) {
                    if (shape != null && !shape.isDeleted()) {
                        shape.delete();
                    }
                }
                newShapes.clear();
            } catch (Exception cleanupException) {
                LOGGER.error("清理新创建图形时发生错误", cleanupException);
            }
        }
    }
    
    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.fillet", radius);
    }
    
    /**
     * 使用传入的修剪点和保留端点创建修剪后的直线
     */
    private List<LineShape> createTrimmedLines() {
        List<LineShape> result = new ArrayList<>();
        
        try {
            // 创建修剪后的第一条直线（使用保留端点和修剪点）
            LineShape trimmedLine1 = new LineShape(preservedEndPoint1, trimPoint1);
            trimmedLine1.setStyle(line1.getStyle());
            result.add(trimmedLine1);
            
            // 创建修剪后的第二条直线（使用保留端点和修剪点）
            LineShape trimmedLine2 = new LineShape(preservedEndPoint2, trimPoint2);
            trimmedLine2.setStyle(line2.getStyle());
            result.add(trimmedLine2);
            
            LOGGER.debug("创建修剪后的直线: 第一条从 {} 到 {}, 第二条从 {} 到 {}", 
                        preservedEndPoint1, trimPoint1, preservedEndPoint2, trimPoint2);
            
        } catch (Exception e) {
            LOGGER.error("创建修剪后的直线失败", e);
            // 如果创建失败，返回原始直线
            result.add((LineShape) line1.clone());
            result.add((LineShape) line2.clone());
        }
        
        return result;
    }
} 