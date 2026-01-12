package com.masterplanner.ui.component;

import net.minecraft.util.Identifier;

/**
 * ????????????
 */
public class ToolPanelIcons {
    private static final String
            NAMESPACE = "masterplanner";
    private static final String BASE_PATH = "textures/gui/toolpanel";
    
    // ??????
    public static final Identifier SELECT = createIdentifier("select.png");
    public static final Identifier ERASER = createIdentifier("eraser.png");
    
    // ??????
    public static final Identifier LINE = createIdentifier("line.png");
    public static final Identifier RECTANGLE = createIdentifier("rectangle.png");
    public static final Identifier CIRCLE = createIdentifier("circle.png");
    public static final Identifier ELLIPSE = createIdentifier("ellipse.png");
    public static final Identifier POLYLINE = createIdentifier("polyline.png");
    public static final Identifier POLYGON = createIdentifier("polygon.png");
    public static final Identifier SPLINE = createIdentifier("spline.png");
    public static final Identifier FREEHAND = createIdentifier("freehand.png");
    public static final Identifier SEMICIRCLE = createIdentifier("semicircle.png");
    public static final Identifier ARC = createIdentifier("arc.png");
    public static final Identifier STAR = createIdentifier("star.png");
    public static final Identifier SPIRAL = createIdentifier("spiral.png");
    public static final Identifier CATENARY = createIdentifier("catenary.png");
    public static final Identifier SINE_WAVE = createIdentifier("sine.png");
    public static final Identifier TEXT = createIdentifier("text.png");
    public static final Identifier GROUP = createIdentifier("group.png");
    public static final Identifier UNGROUP = createIdentifier("ungroup.png");
    public static final Identifier BREAK = createIdentifier("break.png");
    public static final Identifier FILL = createIdentifier("fill.png");
    public static final Identifier ALIGN = createIdentifier("align.png");
    
    // ??????
    public static final Identifier MOVE = createIdentifier("move.png");
    public static final Identifier ROTATE = createIdentifier("rotate.png");
    public static final Identifier SCALE = createIdentifier("scale.png");
    public static final Identifier MIRROR = createIdentifier("mirror.png");
    public static final Identifier ARRAY = createIdentifier("array.png");
    
    // ??????
    public static final Identifier TRIM = createIdentifier("trim.png");
    public static final Identifier EXTEND = createIdentifier("extend.png");
    public static final Identifier FILLET = createIdentifier("fillet.png");
    public static final Identifier CHAMFER = createIdentifier("chamfer.png");
    public static final Identifier TRANSFORM = createIdentifier("transform.png");
    public static final Identifier OFFSET = createIdentifier("offset.png");
    
    private static Identifier createIdentifier(String name) {
        return Identifier.of(NAMESPACE, BASE_PATH + "/" + name);
    }
}
