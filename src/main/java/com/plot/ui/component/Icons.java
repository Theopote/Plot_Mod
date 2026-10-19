package com.plot.ui.component;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * 定义所有UI图标的常量
 */
public class Icons {
    // 通用图标
    public static final String SEARCH = "\uf002";
    public static final String FOLDER = "\uf07b";
    public static final String PLUGIN = "\uf1e6";
    public static final String CHECK_SQUARE = "\uf14a";
    public static final String BRUSH = "\uf1fc";
    
    // 标注工具图标
    public static final String TEXT = "\uf031";          // 文字

    // 道路系统图标
    public static final String ROAD = "\uf018";         // 道路

    // 工具图标标识符
    public static final Identifier SELECT_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/select.png");
    public static final Identifier LASSO_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/lasso.png");
    public static final Identifier LINE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/line.png");
    public static final Identifier TEXT_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/text.png");
    public static final Identifier FILLET_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/fillet.png");
    public static final Identifier CHAMFER_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/chamfer.png");
    public static final Identifier STRETCH_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/stretch.png");
    public static final Identifier SEMICIRCLE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/semicircle.png");
    public static final Identifier RECTANGLE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/rectangle.png");
    public static final Identifier POLYLINE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/polyline.png");
    public static final Identifier POLYGON_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/polygon.png");
    public static final Identifier FREEDRAW_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/freedraw.png");
    public static final Identifier ELLIPSE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/ellipse.png");
    public static final Identifier CIRCLE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/circle.png");
    public static final Identifier ARC_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/arc.png");
    public static final Identifier ERASER_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/eraser.png");
    public static final Identifier STAR_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/star.png");
    public static final Identifier SPLINE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/spline.png");
    public static final Identifier SPIRAL_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/spiral.png");
    public static final Identifier SINE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/sine.png");
    public static final Identifier ALIGN_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/align.png");
    public static final Identifier ARRAY_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/array.png");
    public static final Identifier MIRROR_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/mirror.png");
    public static final Identifier COPY_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/copy.png");
    public static final Identifier MOVE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/move.png");
    public static final Identifier OFFSET_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/offset.png");
    public static final Identifier ROTATE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/rotate.png");
    public static final Identifier SCALE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/scale.png");
    public static final Identifier TRIM_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/trim.png");
    public static final Identifier BREAK_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/break.png");
    public static final Identifier EXTEND_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/extend.png");
    public static final Identifier EXPLODE_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/explode.png");
    public static final Identifier JOIN_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/join.png");
    public static final Identifier CATENARY_IDENTIFIER = Identifier.of("plot", "textures/gui/tools/catenary.png");

    private static final Map<String, IconData> iconCache = new HashMap<>();
    private static final IconRenderer renderer = new IconRenderer();

    private Icons() {
        // 私有构造函数防止实例化
    }

    /**
     * 绘制图标
     * @param iconName 图标名称
     * @param x X坐标
     * @param y Y坐标
     * @param width 宽度
     * @param height 高度
     */
    public static void draw(String iconName, int x, int y, int width, int height) {
        IconData iconData = getIconData(iconName);
        if (iconData != null) {
            renderer.drawIcon(iconData, x, y, width, height);
        }
    }

    private static IconData getIconData(String iconName) {
        if (!iconCache.containsKey(iconName)) {
            iconCache.put(iconName, loadIconData(iconName));
        }
        return iconCache.get(iconName);
    }

    private static IconData loadIconData(String iconName) {
        // 这里实现图标资源的加载
        // 可以从文件系统、资源包或其他来源加载图标
        return new IconData(iconName);
    }

    /**
     * 图标数据类
     */
    private static class IconData {
        private final String name;
        // 添加其他需要的图标数据属性

        public IconData(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 图标渲染器
     */
    private static class IconRenderer {
        public void drawIcon(IconData iconData, int x, int y, int width, int height) {
            // 这里实现实际的图标渲染逻辑
            // 可以使用 OpenGL、LWJGL 或其他渲染系统
        }
    }
}
