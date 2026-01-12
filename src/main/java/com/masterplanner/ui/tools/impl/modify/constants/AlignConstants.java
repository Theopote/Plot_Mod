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
    String ENHANCED_SNAP = "enhancedSnap";
    
    // ====== 阈值常量 ======
    double MIN_ALIGN_DISTANCE = 0.001;
    double ZERO_TOLERANCE = 0.001;
    double MIN_RADIUS = 0.1;
    double MAX_DISTRIBUTE_SPACING = 1000.0;
    double MIN_DISTRIBUTE_SPACING = 0.1;
    
    // ====== 默认值常量 ======
    double DEFAULT_DISTRIBUTE_SPACING = 10.0;
    boolean DEFAULT_ENHANCED_SNAP = true;
    
    // ====== 键盘按键常量 ======
    int ESC_KEY = 27;
    int L_KEY = 76; // L键 - 左对齐
    int R_KEY = 82; // R键 - 右对齐
    int C_KEY = 67; // C键 - 中心对齐
    int T_KEY = 84; // T键 - 顶部对齐
    int B_KEY = 66; // B键 - 底部对齐
    int M_KEY = 77; // M键 - 中间对齐
    
    // ====== 鼠标按键常量 ======
    int MOUSE_LEFT = 0;
    int MOUSE_RIGHT = 1;
    
    // ====== 最小选择数量常量 ======
    int MIN_SELECTION_FOR_ALIGN = 2;
    int MIN_SELECTION_FOR_DISTRIBUTE = 3;
    

} 