package com.plot.core.command.commands;

import com.plot.api.geometry.Vec2d;
import com.plot.core.geometry.shapes.LineShape;
import com.plot.core.model.Shape;
import com.plot.core.state.AppState;
import com.plot.utils.PlotI18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 倒角命令
 * 在两条直线之间创建斜面倒角
 * 
 * @author Plot Team
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

            if (newGeneratedShapes.isEmpty()) {
                LOGGER.warn("没有新生成的图形，跳过执行");
                return;
            }

            // 保存原始状态用于撤销（保留旧行为）
            originalLine1 = (LineShape) line1.clone();
            originalLine2 = (LineShape) line2.clone();

            // 使用基类执行：统一从AppState移除旧图形并添加新图形
            super.execute();

            LOGGER.debug("倒角命令执行完成: 创建了 {} 个新图形", newShapes.size());
        } catch (Exception e) {
            LOGGER.error("执行倒角命令失败", e);
            throw new RuntimeException(PlotI18n.error("error.plot.command.chamfer_failed", e.getMessage()), e);
        }
    }
    
    @Override
    public void undo() {
        try {
            LOGGER.debug("撤销倒角命令");
            super.undo();
        } catch (Exception e) {
            LOGGER.error("撤销倒角命令失败", e);
        }
    }
    
    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.chamfer", distance);
    }
    
} 