package com.masterplanner.ui.panel.tool.renderer;

import com.masterplanner.MasterPlannerMod;
import com.masterplanner.core.tool.BaseTool;
import com.masterplanner.ui.tools.impl.modify.TextTool;
import com.masterplanner.ui.tools.impl.modify.TransformTool;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具选项渲染器工厂
 * 负责创建和管理所有工具的选项渲染器
 */
public class ToolOptionRendererFactory {
    private static final Map<String, ToolOptionRenderer> renderers = new HashMap<>();
    
    /**
     * 获取指定工具的选项渲染器
     * @param tool 工具实例
     * @return 对应的选项渲染器
     */
    public static ToolOptionRenderer getRenderer(BaseTool tool) {
        if (tool == null) {
            return null;
        }
        
        String toolId = tool.getId();
        if (!renderers.containsKey(toolId)) {
            ToolOptionRenderer renderer = createRenderer(toolId, tool);
            if (renderer != null) {
                renderers.put(toolId, renderer);
            }
        }
        
        return renderers.get(toolId);
    }
    
    /**
     * 创建指定工具ID的选项渲染器
     * @param toolId 工具ID
     * @param tool 工具实例
     * @return 新创建的选项渲染器
     */
    private static ToolOptionRenderer createRenderer(String toolId, BaseTool tool) {
        try {
            return switch (toolId) {
                case "select" -> new SelectionToolOptionRenderer();
                case "line" -> new LineToolOptionRenderer();
                case "circle" -> new CircleToolOptionRenderer();
                case "rectangle" -> new RectangleToolOptionRenderer();
                case "ellipse" -> new EllipseToolOptionRenderer();
                case "arc" -> new ArcToolOptionRenderer();
                case "polygon" -> new PolygonToolOptionRenderer();
                case "polyline" -> new PolylineToolOptionRenderer();
                case "spline" -> new SplineToolOptionRenderer();
                case "catenary" -> new CatenaryLineToolOptionRenderer();
                case "freedraw" -> new FreeDrawToolOptionRenderer();
                case "eraser" -> new EraserToolOptionRenderer();
                case "semicircle" -> new SemicircleToolOptionRenderer();
                case "star" -> new StarToolOptionRenderer();
                case "spiral" -> new SpiralToolOptionRenderer();
                case "sine" -> new SineToolOptionRenderer();
                case "move" -> new MoveToolOptionRenderer();
                case "rotate" -> new RotateToolOptionRenderer();
                case "mirror" -> new MirrorToolOptionRenderer();
                case "scale" -> new ScaleToolOptionRenderer();
                case "align" -> new AlignToolOptionRenderer();
                case "array" -> new ArrayToolOptionRenderer();
                case "offset" -> new OffsetToolOptionRenderer();
                case "fillet" -> new FilletToolOptionRenderer();
                case "chamfer" -> new ChamferToolOptionRenderer();
                case "fill" -> new FillToolOptionRenderer();
                case "transform" -> createTransformToolOptionRenderer(tool);
    
                case "trim" -> new TrimToolOptionRenderer();
                case "extend" -> new ExtendToolOptionRenderer();
                case "break" -> new BreakToolOptionRenderer();
                case "text" -> createTextToolOptionRenderer(tool);
    
                default -> null;
            };
        } catch (Exception e) {
            MasterPlannerMod.LOGGER.error("创建工具选项渲染器失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 创建变换工具选项渲染器
     * @param tool 工具实例
     * @return 变换工具选项渲染器
     */
    private static ToolOptionRenderer createTransformToolOptionRenderer(BaseTool tool) {
        if (tool instanceof TransformTool) {
            return new TransformToolOptionRenderer((TransformTool) tool);
        } else {
            // 如果不是TransformTool实例，创建无参数的渲染器（向后兼容）
            MasterPlannerMod.LOGGER.warn("工具不是TransformTool实例，使用默认配置");
            return new TransformToolOptionRenderer(null);
        }
    }
    
    /**
     * 创建文字工具选项渲染器
     * @param tool 工具实例
     * @return 文字工具选项渲染器
     */
    private static ToolOptionRenderer createTextToolOptionRenderer(BaseTool tool) {
        if (tool instanceof TextTool) {
            return new TextToolOptionRenderer((TextTool) tool);
        } else {
            // 如果不是TextTool实例，创建无参数的渲染器（向后兼容）
            MasterPlannerMod.LOGGER.warn("工具不是TextTool实例，使用默认配置");
            return new TextToolOptionRenderer(null);
        }
    }
    
    /**
     * 清理所有渲染器资源
     */
    public static void cleanup() {
        for (ToolOptionRenderer renderer : renderers.values()) {
            try {
                renderer.cleanup();
            } catch (Exception e) {
                MasterPlannerMod.LOGGER.error("清理渲染器资源失败", e);
            }
        }
        renderers.clear();
    }
} 