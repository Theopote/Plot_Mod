package com.plot.ui.tools.impl.drawing.helper;

import com.plot.core.command.commands.DrawingGeometryEditCommand;
import com.plot.core.state.AppState;

/**
 * 绘制中几何编辑的撤销/重做辅助类。
 */
public final class DrawingEditHistory {
    private DrawingEditHistory() {
    }

    public static void commitGeometryEdit(DrawingGeometrySnapshot before, DrawingGeometrySnapshot after) {
        if (before == null || after == null || before.sameGeometryAs(after)) {
            return;
        }
        AppState.getInstance().getCommandHistory().execute(new DrawingGeometryEditCommand(before, after));
    }
}
