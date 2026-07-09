package com.plot.core.command.commands;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;

import java.util.List;

/**
 * 复制旋转命令
 * 
 * <p>专门处理复制旋转操作，保留原图形并添加旋转后的新图形。</p>
 * 
 * @author Plot Team
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
        super(originalShapes, rotatedShapes, appState, "history.plot.op.copy_rotate");
    }
    
    @Override
    public void execute() {
        try {
            // 复制模式：只添加新的旋转图形，不删除原图形
            for (Shape shape : newShapes) {
                appState.addShape(shape);
            }
        } catch (Exception e) {
            LOGGER.error("复制旋转命令执行失败", e);
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
        return PlotI18n.tr("history.plot.copy_rotate", newShapes.size());
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.copy_rotate.detail",
                oldShapes.size(), newShapes.size(), appState.getActiveLayer().getName());
    }
} 