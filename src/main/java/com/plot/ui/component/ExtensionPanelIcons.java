package com.plot.ui.component;

import net.minecraft.util.Identifier;

/**
 * 扩展面板插件图标资源
 */
public class ExtensionPanelIcons {
    private static final String NAMESPACE = "plot";
    private static final String BASE_PATH = "textures/gui/extensionpanel";

    // 预烘焙 PNG：打开扩展面板时避免运行时 Batik SVG 栅格化拖慢首帧
    public static final Identifier DEFAULT = create("plugin.png");
    public static final Identifier EARTHWORK = create("earthwork.png");
    public static final Identifier ROAD_SYSTEM = create("road_system.png");
    public static final Identifier BUILDING = create("building.png");
    public static final Identifier POWER_LINE = create("power_line.png");
    public static final Identifier PATTERN = create("pattern.png");

    private static Identifier create(String name) {
        return Identifier.of(NAMESPACE, BASE_PATH + "/" + name);
    }

    private ExtensionPanelIcons() {
    }
}
