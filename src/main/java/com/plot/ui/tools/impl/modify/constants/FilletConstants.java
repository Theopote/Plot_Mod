package com.plot.ui.tools.impl.modify.constants;

import com.plot.utils.PlotI18n;
/**
 * 倒圆角工具常量定义
 * 
 * <p>集中管理倒圆角工具的所有常量，包括：</p>
 * <ul>
 *   <li>工具标识符</li>
 *   <li>配置键名</li>
 *   <li>默认值和范围限制</li>
 *   <li>状态消息模板</li>
 *   <li>几何计算参数</li>
 *   <li>用户交互参数</li>
 * </ul>
 * 
 * @author Plot Team
 * @version 3.0 - 扩展版本，添加几何计算和交互常量
 */
public final class FilletConstants {
    
    // 工具标识符
    public static final String TOOL_ID = "fillet";
    
    // 配置键常量
    public static final String CONFIG_KEY_RADIUS = "radius";
    public static final String CONFIG_KEY_PREVIEW = "preview";
    public static final String CONFIG_KEY_HIGHLIGHT = "highlight";

    // 默认值
    public static final double DEFAULT_RADIUS = 10.0;
    
    // 范围限制
    public static final double MIN_RADIUS = 1.0;
    public static final double MAX_RADIUS = 100.0;
    
    // 几何计算常量
    public static final double FILLET_TOLERANCE = 0.001; // 倒角计算容差
    public static final double MIN_ANGLE_DIFF = 0.1; // 最小角度差（弧度，约5.7度）
    public static final double MAX_ANGLE_DIFF = Math.PI - 0.1; // 最大角度差（弧度，约174.3度）
    public static final double PARALLEL_TOLERANCE = 0.001; // 平行判断容差
    
    // 用户交互常量
    public static final double KEYBOARD_STEP_SMALL = 1.0; // 键盘调整小步长
    public static final double KEYBOARD_STEP_LARGE = 5.0; // 键盘调整大步长
    public static final double RADIUS_THRESHOLD_LARGE_STEP = 10.0; // 大半径阈值
    
    // 选择容差
    public static final double SELECTION_TOLERANCE = 5.0; // 选择容差
    
    // 状态消息模板
    public static final String STATUS_SELECT_SECOND_LINE = "status.plot.fillet.select_second_param";
    public static final String STATUS_READY_TEMPLATE = "status.plot.fillet.ready_chamfer_template";
    public static final String STATUS_COMPLETE_TEMPLATE = "status.plot.fillet.complete_template";
    public static final String STATUS_LINES_PARALLEL = "status.plot.fillet.lines_parallel";
    public static final String STATUS_ANGLE_TOO_SMALL = "status.plot.fillet.angle_too_small";
    public static final String STATUS_ANGLE_TOO_LARGE = "status.plot.fillet.angle_too_large";
    public static final String STATUS_RADIUS_TOO_LARGE = "status.plot.fillet.radius_too_large";

    // 错误消息
    public static final String ERROR_INVALID_SHAPES = "status.plot.fillet.error_invalid_shapes";
    public static final String ERROR_INVALID_RADIUS = "status.plot.fillet.error_invalid_radius";
    public static final String ERROR_COMMAND_CREATION_FAILED = "status.plot.chamfer.command_failed";
    
    // 私有构造函数防止实例化
    private FilletConstants() {
        throw new UnsupportedOperationException(PlotI18n.error("error.plot.validation.constants_class"));
    }
}
