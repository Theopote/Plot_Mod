package com.masterplanner.core.command.commands;

import com.masterplanner.core.command.Command;
import com.masterplanner.core.model.Shape;
import com.masterplanner.core.state.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 清除画布命令
 * 清除画布上的所有图形，支持撤销和重做
 */
public class ClearCanvasCommand implements Command {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/ClearCanvasCommand");
    
    private final AppState appState;
    private List<Shape> savedShapes; // 保存的图形列表，用于撤销
    
    /**
     * 创建清除画布命令
     * @param appState 应用状态
     */
    public ClearCanvasCommand(AppState appState) {
        this.appState = appState;
    }
    
    @Override
    public void execute() {
        try {
            // 保存当前所有图形（拷贝列表引用，不深拷贝图形对象）
            savedShapes = new ArrayList<>(appState.getShapes());
            LOGGER.debug("清除画布：保存了 {} 个图形", savedShapes.size());
            
            // 通过AppState删除所有图形（这会自动清理所有图层中的图形）
            for (Shape shape : new ArrayList<>(savedShapes)) {
                appState.removeShape(shape);
            }
            
            // 清空选择
            appState.clearSelection();
            
            LOGGER.info("清除画布完成：删除了 {} 个图形", savedShapes.size());
        } catch (Exception e) {
            LOGGER.error("清除画布时发生错误", e);
            throw new RuntimeException("清除画布失败", e);
        }
    }
    
    @Override
    public void undo() {
        try {
            if (savedShapes == null || savedShapes.isEmpty()) {
                LOGGER.debug("撤销清除画布：没有保存的图形");
                return;
            }
            
            // 恢复所有图形
            for (Shape shape : savedShapes) {
                appState.addShape(shape);
            }
            
            LOGGER.info("撤销清除画布：恢复了 {} 个图形", savedShapes.size());
        } catch (Exception e) {
            LOGGER.error("撤销清除画布时发生错误", e);
            throw new RuntimeException("撤销清除画布失败", e);
        }
    }
    
    @Override
    public void redo() {
        // 重做就是再次执行清除（清空画布）
        // 注意：CommandHistory.redo() 实际上会调用 execute()，但为了接口完整性，这里也实现
        try {
            // 保存当前所有图形（可能是从撤销恢复的图形）
            List<Shape> currentShapes = new ArrayList<>(appState.getShapes());
            savedShapes = new ArrayList<>(currentShapes);
            LOGGER.debug("重做清除画布：保存了 {} 个图形", savedShapes.size());
            
            // 清除所有图形
            for (Shape shape : currentShapes) {
                appState.removeShape(shape);
            }
            
            // 清空选择
            appState.clearSelection();
            
            LOGGER.info("重做清除画布：删除了 {} 个图形", currentShapes.size());
        } catch (Exception e) {
            LOGGER.error("重做清除画布时发生错误", e);
            throw new RuntimeException("重做清除画布失败", e);
        }
    }
    
    @Override
    public String getDescription() {
        return "清除画布";
    }
    
    @Override
    public String getDetailedDescription() {
        int shapeCount = savedShapes != null ? savedShapes.size() : 0;
        return String.format("清除了 %d 个图形", shapeCount);
    }
}
