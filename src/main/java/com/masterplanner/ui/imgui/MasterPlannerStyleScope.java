package com.masterplanner.ui.imgui;

import com.masterplanner.ui.theme.ThemeManager;
import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

/**
 * MasterPlanner ImGui 样式隔离作用域（仿 Treefactory/ChronoBlocks 实现）。
 * <p>
 * 确保 MasterPlanner 的 ImGui 界面与其他模组相互隔离：
 * - 其他模组的样式不会影响 MasterPlanner 界面
 * - MasterPlanner 的样式不会影响其他模组的界面（如 Treefactory、ChronoBlocks）
 * <p>
 * 通过 pushStyleVar / pushStyleColor 在渲染时临时应用样式，渲染结束后 pop，不永久修改共享上下文。
 * <p>
 * 用法：在 ImGui.newFrame() 之后、所有 MasterPlanner UI 渲染之前创建此 scope，
 * 在 ImGui.render() 之前关闭（使用 try-with-resources）。
 */
public final class MasterPlannerStyleScope implements AutoCloseable {

    private final int styleVarCount;
    private final int styleColorCount;

    private MasterPlannerStyleScope(int styleVarCount, int styleColorCount) {
        this.styleVarCount = styleVarCount;
        this.styleColorCount = styleColorCount;
    }

    /**
     * 创建样式隔离作用域。在 MasterPlanner 的 ImGui 渲染开始时调用。
     */
    public static MasterPlannerStyleScope enter() {
        int[] counts = ThemeManager.getInstance().pushThemeToStack();
        return new MasterPlannerStyleScope(counts[0], counts[1]);
    }

    @Override
    public void close() {
        ImGui.popStyleColor(styleColorCount);
        ImGui.popStyleVar(styleVarCount);
    }
}
