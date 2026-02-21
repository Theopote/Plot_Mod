package com.masterplanner.core.command.commands;

import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;

import java.util.List;

/**
 * 阵列命令
 * 
 * <p>专门处理阵列复制操作，保留原图形并添加阵列后的新图形。</p>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 阵列命令
 */
public class ArrayCommand extends ModifyCommand {
    
    /**
     * 构造函数
     * @param originalShapes 原始图形列表
     * @param arrayedShapes 阵列后的图形列表
     * @param appState 应用状态
     */
    public ArrayCommand(List<Shape> originalShapes, List<Shape> arrayedShapes, AppState appState) {
        super(originalShapes, arrayedShapes, appState, "阵列");
    }
    
    @Override
    public void execute() {
        try {
            // 阵列模式：只添加新的阵列图形，不删除原图形
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
        // 撤销：移除阵列后的图形
        for (Shape shape : newShapes) {
            appState.removeShape(shape);
        }
    }
    
    @Override
    public void redo() {
        // 重做：重新添加阵列后的图形
        for (Shape shape : newShapes) {
            appState.addShape(shape);
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("阵列 %d 个图形", newShapes.size());
    }
    
    @Override
    public String getDetailedDescription() {
        return String.format("阵列操作：选中 %d 个图形，生成 %d 个新图形", 
            oldShapes.size(), newShapes.size());
    }
} 