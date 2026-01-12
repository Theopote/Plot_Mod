package com.masterplanner.core.command.commands;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;

/**
 * 复制旋转命令
 * 
 * <p>专门处理复制旋转操作，保留原图形并添加旋转后的新图形。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 复制旋转命令
 */
public class CopyRotateCommand extends ModifyCommand {
    
    /**
     * 构造函数
     * @param originalShapes 原始图形列表
     * @param rotatedShapes 旋转后的图形列表
     * @param appState 应用状态
     */
    public CopyRotateCommand(List<Shape> originalShapes, List<Shape> rotatedShapes, AppState appState) {
        super(originalShapes, rotatedShapes, appState);
    }
    
    @Override
    public void execute() {
        try {
            // 复制模式：只添加新的旋转图形，不删除原图形
            for (Shape shape : newShapes) {
                appState.addShape(shape);
            }
        } catch (Exception e) {
            // 记录错误但继续执行
            e.printStackTrace();
        }
    }
    
    @Override
    public void undo() {
        // 撤销：移除旋转后的图形
        for (Shape shape : newShapes) {
            appState.removeShape(shape);
        }
    }
    
    @Override
    public void redo() {
        // 重做：重新添加旋转后的图形
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("复制旋转 %d 个图形", oldShapes.size());
    }
    
    @Override
    public String getDetailedDescription() {
        StringBuilder details = new StringBuilder();
        details.append(String.format(
                """
                        复制旋转操作
                        原始图形数量: %d
                        旋转后图形数量: %d
                        所在图层: %s""",
            oldShapes.size(),
            newShapes.size(),
            appState.getActiveLayer().getName()
        ));

        // 添加图形类型统计
        if (!oldShapes.isEmpty()) {
            details.append("\n原始图形类型:");
            oldShapes.stream()
                .map(shape -> shape.getClass().getSimpleName())
                .distinct()
                .forEach(type -> details.append("\n- ").append(type));
        }

        if (!newShapes.isEmpty()) {
            details.append("\n旋转后图形类型:");
            newShapes.stream()
                .map(shape -> shape.getClass().getSimpleName())
                .distinct()
                .forEach(type -> details.append("\n- ").append(type));
        }

        return details.toString();
    }
} 