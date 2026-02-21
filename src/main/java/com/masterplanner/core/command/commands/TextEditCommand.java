package com.masterplanner.core.command.commands;

import com.masterplanner.core.command.Command;
import com.masterplanner.core.geometry.shapes.TextShape;

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
        return "编辑文字";
    }

    @Override
    public String getDetailedDescription() {
        return String.format("编辑文字: \"%s\" -> \"%s\"", oldText, newText);
    }
}
