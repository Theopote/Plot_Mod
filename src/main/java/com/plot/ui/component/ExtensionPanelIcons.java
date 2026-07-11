package com.plot.ui.component;

import net.minecraft.util.Identifier;

/**
 * 扩展面板插件图标资源
 */
public class ExtensionPanelIcons {
    private static final String NAMESPACE = "plot";
    private static final String BASE_PATH = "textures/gui/extensionpanel";

    public static final Identifier DEFAULT = create("plugin.svg");
    public static final Identifier EARTHWORK = create("earthwork.svg");
    public static final Identifier IMAGE_TOOLS = create("image_tools.svg");
    public static final Identifier ROAD_SYSTEM = create("road_system.svg");
    public static final Identifier BUILDING = create("building.svg");

    private static Identifier create(String name) {
        return Identifier.of(NAMESPACE, BASE_PATH + "/" + name);
    }

    private ExtensionPanelIcons() {
    }
}
