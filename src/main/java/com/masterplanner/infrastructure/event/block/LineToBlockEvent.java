package com.masterplanner.infrastructure.event.block;

import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.api.event.EventType;
import com.masterplanner.core.model.Shape;
import com.masterplanner.ui.dialog.LineToBlockSettingsDialog.ConversionMode;

import java.util.List;

/**
 * 线转方块事件
 * 当用户点击"线转方块"按钮时触发，将选中的图形转换为方块
 */
public class LineToBlockEvent extends Event {
    private static final String SOURCE = "LineToBlockManager";
    private final List<Shape> shapes;
    private final ConversionMode conversionMode;
    private final float simplificationRatio;
    private final double canvasHeight;  // 绘制面板高度
    private final boolean isPreview;    // 是否为预览模式
    
    /**
     * 创建一个线转方块事件
     */
    public LineToBlockEvent(List<Shape> shapes) {
        super(EventType.BLOCK_CONVERSION);
        this.shapes = null;
        this.conversionMode = ConversionMode.FULL;
        this.simplificationRatio = 0.5f;
        this.canvasHeight = 0;
        this.isPreview = false;
    }
    
    /**
     * 创建一个线转方块事件
     * @param shapes 要转换的图形列表
     * @param canvasHeight 绘制面板高度
     * @param isPreview 是否为预览模式
     */
    public LineToBlockEvent(List<Shape> shapes, double canvasHeight, boolean isPreview) {
        this(shapes, ConversionMode.FULL, 0.5f, canvasHeight, isPreview);
    }
    
    /**
     * 创建一个线转方块事件
     * @param shapes 要转换的图形列表
     * @param conversionMode 转换模式
     * @param simplificationRatio 简化比率
     * @param canvasHeight 绘制面板高度
     * @param isPreview 是否为预览模式
     */
    public LineToBlockEvent(List<Shape> shapes, ConversionMode conversionMode, float simplificationRatio, double canvasHeight, boolean isPreview) {
        super(EventType.BLOCK_CONVERSION);
        this.shapes = shapes;
        this.conversionMode = conversionMode;
        this.simplificationRatio = simplificationRatio;
        this.canvasHeight = canvasHeight;
        this.isPreview = isPreview;
    }
    
    @Override
    public String getSource() {
        return SOURCE;
    }

    /**
     * 获取要转换的图形列表
     * @return 图形列表，如果为null则表示使用当前选中的图形
     */
    public List<Shape> getShapes() {
        return shapes;
    }

    public ConversionMode getConversionMode() {
        return conversionMode;
    }

    public float getSimplificationRatio() {
        return simplificationRatio;
    }

    public double getCanvasHeight() {
        return canvasHeight;
    }

    public boolean isPreview() {
        return isPreview;
    }

    @Override
    public String toString() {
        return String.format("LineToBlockEvent[shapes=%d, mode=%s, ratio=%.2f, height=%.2f, preview=%b]", 
            shapes != null ? shapes.size() : 0, conversionMode, simplificationRatio, canvasHeight, isPreview);
    }
} 