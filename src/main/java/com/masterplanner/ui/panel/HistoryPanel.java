package com.masterplanner.ui.panel;

import com.masterplanner.core.command.Command;
import com.masterplanner.core.command.CommandHistory;
import com.masterplanner.ui.component.UIComponent;
import com.masterplanner.ui.theme.ThemeManager;
import com.masterplanner.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.*;

import java.util.List;

public class HistoryPanel implements UIComponent {
    private static final int MAX_HISTORY_ITEMS = 30;
    private final CommandHistory commandHistory;

    public HistoryPanel(CommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }

    @Override
    public void render() {
        UITheme.ThemeColors currentTheme = ThemeManager.getInstance().getCurrentTheme();
        
        // 设置历史记录区域的样式
        // 注意：beginChild 的边框应紧贴窗口边缘，内容边距由窗口级 WindowPadding 控制
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);  // 边框无内边距，与标题边距一致
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4, 4);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, currentTheme.panelControlRounding);
        
        // 设置颜色
        ImGui.pushStyleColor(ImGuiCol.ChildBg, currentTheme.panelBackground);
        ImGui.pushStyleColor(ImGuiCol.Border, currentTheme.border);
        ImGui.pushStyleColor(ImGuiCol.Button, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, currentTheme.buttonActive);
        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.text);
        ImGui.pushStyleColor(ImGuiCol.Header, currentTheme.buttonNormal);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, currentTheme.buttonHovered);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, currentTheme.buttonActive);
        
        try {
            // 创建一个固定高度的子窗口来显示历史记录
            // 在 beginChild 内部设置内容边距，保持与标题边距一致
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4, 4);
            if (ImGui.beginChild("##history_list", 0, 200, true)) {
                List<Command> history = commandHistory.getHistory();
                int currentIndex = commandHistory.getCurrentIndex();
                
                // 从最新的命令开始显示
                for (int i = history.size() - 1; i >= 0 && i >= history.size() - MAX_HISTORY_ITEMS; i--) {
                    Command cmd = history.get(i);
                    boolean isCurrent = i == currentIndex;
                    
                    // 设置当前命令的高亮颜色
                    if (isCurrent) {
                        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.activeText);
                    }
                    
                    // 显示命令名称和时间戳
                    String timestamp = new java.text.SimpleDateFormat("HH:mm:ss")
                        .format(cmd.getTimestamp());
                    String label = String.format("%s (%s)##%d", cmd.getDescription(), timestamp, i);
                    
                    if (ImGui.selectable(label, isCurrent)) {
                        // 点击历史记录项时跳转到该状态
                        int steps = i - currentIndex;
                        if (steps > 0) {
                            for (int j = 0; j < steps; j++) {
                                commandHistory.redo();
                            }
                        } else if (steps < 0) {
                            for (int j = 0; j < -steps; j++) {
                                commandHistory.undo();
                            }
                        }
                    }
                    
                    if (isCurrent) {
                        ImGui.popStyleColor();
                    }
                    
                    // 显示命令的详细信息（如果有）
                    if (ImGui.isItemHovered()) {
                        ImGui.pushStyleColor(ImGuiCol.PopupBg, currentTheme.tooltipBackground);
                        ImGui.pushStyleColor(ImGuiCol.Text, currentTheme.tooltipText);
                        ImGui.beginTooltip();
                        ImGui.text(cmd.getDetailedDescription());
                        ImGui.endTooltip();
                        ImGui.popStyleColor(2);
                    }
                }
            }
            ImGui.endChild();
            ImGui.popStyleVar(1);  // 弹出 beginChild 内部的 WindowPadding
            
        } finally {
            ImGui.popStyleColor(9); // 恢复所有颜色设置
            ImGui.popStyleVar(5);   // 恢复所有样式设置
        }
    }

    @Override
    public void close() throws Exception {
        // 无需特殊清理
    }

    @Override
    public void init() {
        // 目前无需特殊初始化
    }
} 