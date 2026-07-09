package com.plot.core.command.commands;

import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除多个图形的命令
 */
public class DeleteShapesCommand extends ModifyCommand {
    private final List<Shape> shapesToDelete;
    
    /**
     * 创建删除图形命令
     * @param shapesToDelete 要删除的图形列表
     */
    public DeleteShapesCommand(List<Shape> shapesToDelete) {
        super(shapesToDelete, new ArrayList<>(), AppState.getInstance());
        this.shapesToDelete = new ArrayList<>(shapesToDelete);
    }
    
    @Override
    public void execute() {
        // 从应用状态中删除图形
        for (Shape shape : shapesToDelete) {
            appState.removeShape(shape);
        }
        
        // 修复：命令自己负责清理相关状态，确保操作的原子性和完整性
        // 删除操作完成后，清空选择是"删除选中图形"这个业务操作的一部分
        appState.clearSelection();
    }
    
    @Override
    public void undo() {
        // 恢复被删除的图形
        for (Shape shape : shapesToDelete) {
            appState.addShape(shape);
        }
        
        // 修复：撤销时恢复选择状态，保持命令操作的完整对称性
        // 既然execute()时清空了选择，undo()时就应该恢复选择
        appState.setSelectedShapes(new ArrayList<>(shapesToDelete));
    }
    
    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.delete_shapes");
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.delete_shapes.detail", shapesToDelete.size());
    }
} 