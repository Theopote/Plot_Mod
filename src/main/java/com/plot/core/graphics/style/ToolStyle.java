package com.plot.core.graphics.style;

import java.awt.Color;

public class ToolStyle {
    private final PointStyle pointStyle;
    private final LineStyle lineStyle;
    private final BaseStyle previewStyle;

    public ToolStyle() {
        pointStyle = new PointStyle();
        lineStyle = new LineStyle();
        previewStyle = new BaseStyle();
        
        // 设置默认样式
        pointStyle.setColor(new Color(255, 0, 0, 200));  // 红色，半透明
        lineStyle.setColor(new Color(255, 255, 255, 255));  // 白色
        previewStyle.setColor(new Color(200, 200, 200, 180));  // 灰色，半透明
    }

    public PointStyle getPointStyle() {
        return pointStyle;
    }

    public LineStyle getLineStyle() {
        return lineStyle;
    }

    public BaseStyle getPreviewStyle() {
        return previewStyle;
    }
} 