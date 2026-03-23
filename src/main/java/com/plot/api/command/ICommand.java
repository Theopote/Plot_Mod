package com.plot.api.command;

/**
 * 命令接口，定义了命令的基本操作
 * 所有具体的命令都需要实现这个接口
 */
public interface ICommand {
    /**
     * 执行命令
     * @return 命令是否执行成功
     */
    boolean execute();

    /**
     * 撤销命令
     * @return 命令是否撤销成功
     */
    boolean undo();

    /**
     * 重做命令
     * @return 命令是否重做成功
     */
    boolean redo();

    /**
     * 获取命令名称
     * @return 命令名称
     */
    String getName();

    /**
     * 获取命令描述
     * @return 命令描述
     */
    String getDescription();

    /**
     * 命令是否可以合并
     * 某些连续的命令可以合并成一个命令，比如连续的移动操作
     * @param other 另一个命令
     * @return 是否可以合并
     */
    boolean canMerge(ICommand other);

    /**
     * 合并命令
     * @param other 要合并的命令
     * @return 合并后的命令
     */
    ICommand merge(ICommand other);

    /**
     * 获取命令状态
     * @return 命令状态
     */
    CommandState getState();

    /**
     * 命令是否可撤销
     * @return 是否可撤销
     */
    boolean isUndoable();
}
