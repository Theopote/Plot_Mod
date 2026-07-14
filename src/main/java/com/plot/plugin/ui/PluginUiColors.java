package com.plot.plugin.ui;

import imgui.ImColor;

/**
 * 插件 ImGui 文字/控件颜色（ABGR，经 {@link ImColor#rgba} 打包）。
 * <p>
 * 勿使用超过 32 位的十六进制字面量（如 {@code 0xFF808080FFL}），强转 int 会截断最高字节导致颜色错误。
 */
public final class PluginUiColors {
    /** 次要说明、空状态提示 */
    public static final int HINT_GRAY = ImColor.rgba(128, 128, 128, 255);
    /** 操作成功 / 状态栏正常消息 */
    public static final int STATUS_OK = ImColor.rgba(128, 255, 128, 255);
    /** 信息强调（进度、自动模式说明等） */
    public static final int STATUS_INFO = ImColor.rgba(128, 192, 255, 255);
    /** 路径类型、链接类信息 */
    public static final int INFO_BLUE = ImColor.rgba(64, 128, 255, 255);
    /** 主题强调色（选中预设、边框高亮） */
    public static final int ACCENT_BLUE = ImColor.rgba(77, 166, 255, 255);
    /** 一般警告 */
    public static final int WARNING = ImColor.rgba(255, 160, 96, 255);
    /** 较轻警告（空结果等） */
    public static final int WARNING_LIGHT = ImColor.rgba(255, 176, 96, 255);
    /** 范围重叠等警告 */
    public static final int WARNING_OVERLAP = ImColor.rgba(255, 128, 64, 255);
    /** 强警告（手动标高冲突等） */
    public static final int WARNING_STRONG = ImColor.rgba(255, 136, 0, 255);
    /** 软性错误 / 就绪检查未通过 */
    public static final int ERROR_SOFT = ImColor.rgba(255, 128, 128, 255);
    /** 硬性错误 */
    public static final int ERROR = ImColor.rgba(255, 96, 96, 255);
    /** 校验失败（范围无效等） */
    public static final int INVALID = ImColor.rgba(64, 64, 255, 255);
    /** 图例标签 */
    public static final int LEGEND = ImColor.rgba(170, 170, 170, 255);
    /** 图例深色文字 */
    public static final int LEGEND_DARK = ImColor.rgba(112, 112, 112, 255);
    /** 描边 / 深色环 */
    public static final int RING_DARK = ImColor.rgba(32, 32, 32, 255);
    /** 删除按钮 */
    public static final int DELETE = ImColor.rgba(255, 0, 0, 255);
    public static final int DELETE_HOVER = ImColor.rgba(255, 32, 32, 255);
    public static final int DELETE_ACTIVE = ImColor.rgba(204, 0, 0, 255);

    private PluginUiColors() {
    }
}
