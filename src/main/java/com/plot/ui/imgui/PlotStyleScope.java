package com.plot.ui.imgui;

import com.plot.ui.theme.ThemeManager;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plot ImGui 样式隔离作用域（仿 Treefactory/ChronoBlocks 实现）。
 * <p>
 * 确保 Plot 的 ImGui 界面与其他模组相互隔离：
 * - 其他模组的样式不会影响 Plot 界面
 * - Plot 的样式不会影响其他模组的界面（如 Treefactory、ChronoBlocks）
 * <p>
 * 通过 pushStyleVar / pushStyleColor 在渲染时临时应用样式，渲染结束后 pop，不永久修改共享上下文。
 * <p>
 * 用法：在 ImGui.newFrame() 之后、所有 Plot UI 渲染之前创建此 scope，
 * 在 ImGui.render() 之前关闭（使用 try-with-resources）。
 */
public final class PlotStyleScope implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlotStyleScope.class);

    private final int styleVarCount;
    private final int styleColorCount;

    private PlotStyleScope(int styleVarCount, int styleColorCount) {
        this.styleVarCount = styleVarCount;
        this.styleColorCount = styleColorCount;
    }

    /**
     * 创建样式隔离作用域。在 Plot 的 ImGui 渲染开始时调用。
     */
    public static PlotStyleScope enter() {
        int[] counts = ThemeManager.getInstance().pushThemeToStack();
        return new PlotStyleScope(counts[0], counts[1]);
    }

    @Override
    public void close() {
        // 防御性关闭：某些工具渲染器在异常路径可能造成样式栈失衡。
        // 逐个 pop 并在失败时停止，避免一次性 pop 导致断言直接崩溃。
        for (int i = 0; i < styleColorCount; i++) {
            try {
                ImGui.popStyleColor();
            } catch (Throwable t) {
                LOGGER.error("ImGui style color stack mismatch while closing scope at index {}", i, t);
                break;
            }
        }
        for (int i = 0; i < styleVarCount; i++) {
            try {
                ImGui.popStyleVar();
            } catch (Throwable t) {
                LOGGER.error("ImGui style var stack mismatch while closing scope at index {}", i, t);
                break;
            }
        }
    }
}
