package com.masterplanner.ui.tools.impl.modify.constants;

/**
 * 对齐工具常量定义
 * 
 * <p>集中管理对齐工具的所有常量，包括：</p>
 * <ul>
 *   <li>配置键常量</li>
 *   <li>阈值常量</li>
 *   <li>默认值常量</li>
 *   <li>键盘按键常量</li>
 * </ul>
 * 
 * @author MasterPlanner Team
 * @version 1.0 - 对齐工具常量
 */
public interface AlignConstants {
    
    // ====== 配置键常量 ======
    String ALIGN_MODE = "alignMode";
    String REFERENCE_MODE = "referenceMode";
    String DISTRIBUTE_SPACING = "distributeSpacing";

    // ====== 阈值常量 ======
    double MIN_RADIUS = 0.1;

    
    // ====== 默认值常量 ======
    double DEFAULT_DISTRIBUTE_SPACING = 10.0;

    // ====== 键盘按键常量 ======
    int ESC_KEY = 27;
    
    // ====== 鼠标按键常量 ======
    int MOUSE_LEFT = 0;
    int MOUSE_RIGHT = 1;
    
    // ====== 最小选择数量常量 ======
    int MIN_SELECTION_FOR_ALIGN = 2;
    int MIN_SELECTION_FOR_DISTRIBUTE = 3;
    

} 