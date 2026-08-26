package com.plot.core.command;

import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.command.RedoEvent;
import com.plot.infrastructure.event.command.UndoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 唯一的命令历史真相源：执行、撤销、重做与历史浏览均通过本服务。
 */
public final class CommandService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/CommandService");
    private static final int MAX_HISTORY = 100;

    private static CommandService instance;

    private final EventBus eventBus;
    private final List<Command> commands = new ArrayList<>();
    private final List<Date> commandTimestamps = new ArrayList<>();
    private int currentIndex = -1;
    private List<Command> activeTransaction = null;

    private CommandService(EventBus eventBus) {
        this.eventBus = eventBus;
        this.eventBus.subscribe(this, UndoEvent.class, event -> undo());
        this.eventBus.subscribe(this, RedoEvent.class, event -> redo());
    }

    /**
     * 组合根专用：用注入的 EventBus 初始化单例。
     */
    public static synchronized CommandService initialize(EventBus eventBus) {
        if (instance == null) {
            instance = new CommandService(eventBus);
        }
        return instance;
    }

    /**
     * @deprecated 使用 {@link com.plot.core.context.ApplicationContext#getCommandService()}。
     * 组合根构造期间仍可调用本方法。
     */
    @Deprecated
    public static synchronized CommandService getInstance() {
        if (instance == null) {
            instance = new CommandService(EventBus.getInstance());
        }
        return instance;
    }

    /**
     * 执行命令并在成功后写入历史。
     */
    public boolean execute(Command command) {
        if (command == null) {
            return false;
        }

        if (activeTransaction != null) {
            try {
                command.execute();
                activeTransaction.add(command);
                return true;
            } catch (Exception e) {
                LOGGER.error("事务中执行命令失败: {}", command.getDescription(), e);
                return false;
            }
        }

        try {
            command.execute();
        } catch (Exception e) {
            LOGGER.error("执行命令失败: {}", command.getDescription(), e);
            return false;
        }

        recordExecutedCommand(command);
        return true;
    }

    /**
     * 登记已在别处执行完毕的命令（例如异步方块落地）。
     */
    public void pushExecuted(Command command) {
        if (command == null) {
            return;
        }
        if (activeTransaction != null) {
            activeTransaction.add(command);
            return;
        }
        recordExecutedCommand(command);
    }

    public boolean undo() {
        if (!canUndo()) {
            LOGGER.debug("没有可撤销的命令");
            return false;
        }

        Command command = commands.get(currentIndex);
        try {
            LOGGER.debug("撤销命令: {}", command.getDescription());
            command.undo();
        } catch (Exception e) {
            LOGGER.error("撤销命令失败，历史指针保持不变: {}", command.getDescription(), e);
            return false;
        }

        currentIndex--;
        return true;
    }

    public boolean redo() {
        if (!canRedo()) {
            LOGGER.debug("没有可重做的命令");
            return false;
        }

        int nextIndex = currentIndex + 1;
        Command command = commands.get(nextIndex);
        try {
            LOGGER.debug("重做命令: {}", command.getDescription());
            command.redo();
        } catch (Exception e) {
            LOGGER.error("重做命令失败，历史指针保持不变: {}", command.getDescription(), e);
            return false;
        }

        currentIndex = nextIndex;
        return true;
    }

    public boolean canUndo() {
        return currentIndex >= 0;
    }

    public boolean canRedo() {
        return currentIndex < commands.size() - 1;
    }

    public List<Command> history() {
        return new ArrayList<>(commands);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Date getTimestampAt(int index) {
        if (index < 0 || index >= commandTimestamps.size()) {
            return null;
        }
        return new Date(commandTimestamps.get(index).getTime());
    }

    public int size() {
        return commands.size();
    }

    public void clear() {
        commands.clear();
        commandTimestamps.clear();
        currentIndex = -1;
        activeTransaction = null;
        LOGGER.debug("命令历史已清空");
    }

    public void beginTransaction() {
        if (activeTransaction != null) {
            throw new IllegalStateException("命令事务已在进行中");
        }
        activeTransaction = new ArrayList<>();
    }

    public void commitTransaction() {
        if (activeTransaction == null) {
            return;
        }

        List<Command> transactionCommands = activeTransaction;
        activeTransaction = null;

        if (transactionCommands.isEmpty()) {
            return;
        }

        Command recorded = transactionCommands.size() == 1
                ? transactionCommands.get(0)
                : new CompositeCommand(transactionCommands);
        recordExecutedCommand(recorded);
    }

    public void rollbackTransaction() {
        if (activeTransaction == null) {
            return;
        }

        List<Command> transactionCommands = new ArrayList<>(activeTransaction);
        activeTransaction = null;

        for (int i = transactionCommands.size() - 1; i >= 0; i--) {
            Command command = transactionCommands.get(i);
            try {
                command.undo();
            } catch (Exception e) {
                LOGGER.error("回滚事务时撤销命令失败: {}", command.getDescription(), e);
            }
        }
    }

    public boolean isInTransaction() {
        return activeTransaction != null;
    }

    private void recordExecutedCommand(Command command) {
        discardRedoBranch();
        commands.add(command);
        commandTimestamps.add(new Date());
        currentIndex++;
        trimHistorySize();
        LOGGER.debug("命令已记入历史: {}", command.getDescription());
    }

    private void discardRedoBranch() {
        if (currentIndex < commands.size() - 1) {
            commands.subList(currentIndex + 1, commands.size()).clear();
            commandTimestamps.subList(currentIndex + 1, commandTimestamps.size()).clear();
        }
    }

    private void trimHistorySize() {
        while (commands.size() > MAX_HISTORY) {
            commands.removeFirst();
            commandTimestamps.removeFirst();
            currentIndex--;
        }
        if (currentIndex < -1) {
            currentIndex = -1;
        }
    }
}
