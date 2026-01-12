package com.masterplanner.core.command.commands;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.geometry.shapes.LineShape;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 倒角命令
 * 在两条直线之间创建斜面倒角
 * 
 * @author MasterPlanner Team
 * @version 1.1 - 优化命令逻辑，简化几何计算
 */
public class ChamferCommand extends ModifyCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChamferCommand.class);
    
    private final LineShape line1;
    private final LineShape line2;
    private final double distance;
    
    // 优化：保存原始状态用于撤销
    private LineShape originalLine1;
    private LineShape originalLine2;
    
    // 优化：保存新生成的图形列表
    private List<Shape> newGeneratedShapes;
    
    /**
     * 优化：构造函数接收最终生成的图形列表，避免在命令中重复计算
     */
    public ChamferCommand(LineShape line1, LineShape line2, double distance, 
                         List<Shape> newGeneratedShapes, AppState appState) {
        super(List.of(line1, line2), newGeneratedShapes, appState);
        this.line1 = line1;
        this.line2 = line2;
        this.distance = distance;
        this.newGeneratedShapes = new ArrayList<>(newGeneratedShapes);
    }
    
    /**
     * 兼容性构造函数 - 保留原有接口
     * @deprecated 请使用新的构造函数，避免在命令中重复几何计算
     */
    @Deprecated
    public ChamferCommand(LineShape line1, LineShape line2, double distance, 
                         Vec2d trimPoint1, Vec2d trimPoint2, AppState appState) {
        super(List.of(line1, line2), new ArrayList<>(), appState);
        this.line1 = line1;
        this.line2 = line2;
        this.distance = distance;
        this.newGeneratedShapes = new ArrayList<>();
        
        LOGGER.warn("使用已弃用的ChamferCommand构造函数，将在下一版本移除");
    }
    
    @Override
    public void execute() {
        try {
            LOGGER.debug("执行倒角命令: 距离={}", distance);
            
            // 优化：简化execute逻辑，只需删除旧图形和添加新图形
            if (newGeneratedShapes.isEmpty()) {
                LOGGER.warn("没有新生成的图形，跳过执行");
                return;
            }
            
            // 保存原始状态用于撤销
            originalLine1 = (LineShape) line1.clone();
            originalLine2 = (LineShape) line2.clone();
            
            // 删除原始直线
            line1.delete();
            line2.delete();
            
            // 添加新生成的图形
            newShapes.addAll(newGeneratedShapes);
            
            LOGGER.debug("倒角命令执行完成: 创建了 {} 个新图形", newShapes.size());
            
        } catch (Exception e) {
            LOGGER.error("执行倒角命令失败", e);
            throw new RuntimeException("倒角操作失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void undo() {
        try {
            LOGGER.debug("撤销倒角命令");
            
            // 优化：修复undo逻辑，避免直接访问AppState
            // 删除新创建的图形
            for (Shape shape : newShapes) {
                shape.delete();
            }
            newShapes.clear();
            
            // 恢复原始直线 - 使用基类提供的方法
            if (originalLine1 != null && originalLine2 != null) {
                // 通过基类的addShape方法恢复原始图形
                addShape(originalLine1);
                addShape(originalLine2);
            }
            
        } catch (Exception e) {
            LOGGER.error("撤销倒角命令失败", e);
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("倒角 (距离: %.2f)", distance);
    }
    
    /**
     * 添加图形到模型 - 受保护方法，供undo使用
     */
    protected void addShape(Shape shape) {
        try {
            // 通过AppState添加图形，确保正确的生命周期管理
            AppState appState = AppState.getInstance();
            appState.getActiveLayer().addShape(shape);
        } catch (Exception e) {
            LOGGER.error("恢复图形失败", e);
        }
    }
} 