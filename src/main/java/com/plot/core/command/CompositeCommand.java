package com.plot.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将多个已执行的命令合并为一条可撤销的历史记录。
 */
public final class CompositeCommand implements Command {
    private final List<Command> commands;
    private final String description;

    public CompositeCommand(List<Command> commands) {
        this.commands = new ArrayList<>(commands);
        if (this.commands.isEmpty()) {
            this.description = "";
        } else if (this.commands.size() == 1) {
            this.description = this.commands.get(0).getDescription();
        } else {
            this.description = "history.plot.op.composite";
        }
    }

    @Override
    public void execute() {
        // 子命令在加入事务前已执行，此处无需重复执行。
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    @Override
    public void redo() {
        for (Command command : commands) {
            command.redo();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getDetailedDescription() {
        if (commands.isEmpty()) {
            return "";
        }
        if (commands.size() == 1) {
            return commands.get(0).getDetailedDescription();
        }
        return commands.stream()
                .map(Command::getDetailedDescription)
                .collect(Collectors.joining("\n"));
    }

    List<Command> getCommands() {
        return commands;
    }
}
