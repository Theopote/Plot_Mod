package com.plot.api.graphics;

import java.awt.Color;

/**
 * 线条样式接口
 */
public interface ILineStyle {
    Color getColor();

    void setColor(Color color);

    float getWidth();

    void setWidth(float width);

    boolean isVisible();

    void setVisible(boolean visible);

    LineType getType();

    ILineStyle clone();
}
