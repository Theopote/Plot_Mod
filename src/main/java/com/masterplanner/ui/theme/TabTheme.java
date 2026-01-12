package com.masterplanner.ui.theme;

import com.masterplanner.ui.theme.ThemeManager;

public class TabTheme {
    private final ThemeManager themeManager;
    
    public TabTheme(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public int getTabNormalColor() {
        return themeManager.getCurrentTheme().tabNormal;
    }

    public int getTabHoveredColor() {
        return themeManager.getCurrentTheme().tabHovered;
    }

    public int getTabActiveColor() {
        return themeManager.getCurrentTheme().tabActive;
    }

    public int getTextColor() {
        return themeManager.getCurrentTheme().tabText;
    }

    public int getBorderColor() {
        return themeManager.getCurrentTheme().tabBorder;
    }
} 