package com.plot.core.command.commands;

import com.plot.core.command.Command;
import com.plot.core.geometry.shapes.TextShape;
import com.plot.utils.PlotI18n;

public class TextEditCommand implements Command {
    private final TextShape target;
    private final String oldText;
    private final String newText;

    public TextEditCommand(TextShape target, String oldText, String newText) {
        this.target = target;
        this.oldText = oldText == null ? "" : oldText;
        this.newText = newText == null ? "" : newText;
    }

    @Override
    public void execute() {
        if (target != null) {
            target.setText(newText);
        }
    }

    @Override
    public void undo() {
        if (target != null) {
            target.setText(oldText);
        }
    }

    @Override
    public void redo() {
        execute();
    }

    @Override
    public String getDescription() {
        return PlotI18n.tr("history.plot.text_edit");
    }

    @Override
    public String getDetailedDescription() {
        return PlotI18n.tr("history.plot.text_edit.detail", oldText, newText);
    }
}
