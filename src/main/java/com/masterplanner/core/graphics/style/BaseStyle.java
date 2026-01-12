package com.masterplanner.core.graphics.style;

import java.awt.Color;

public class BaseStyle {
    protected Color color;
    protected boolean visible = true;

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
} 