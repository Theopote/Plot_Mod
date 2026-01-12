package com.masterplanner.api.command;

/**
 * 命令监听器接口，用于监听命令的执行状态
 */
public interface ICommandListener {
    /**
     * 命令执行前调用
     * @param command 要执行的命令
     */
    void beforeCommandExecute(ICommand command);

    /**
     * 命令执行后调用
     * @param command 执行的命令
     * @param success 是否执行成功
     */
    void afterCommandExecute(ICommand command, boolean success);

    /**
     * 命令撤销前调用
     * @param command 要撤销的命令
     */
    void beforeCommandUndo(ICommand command);

    /**
     * 命令撤销后调用
     * @param command 撤销的命令
     * @param success 是否撤销成功
     */
    void afterCommandUndo(ICommand command, boolean success);

    /**
     * 命令重做前调用
     * @param command 要重做的命令
     */
    void beforeCommandRedo(ICommand command);

    /**
     * 命令重做后调用
     * @param command 重做的命令
     * @param success 是否重做成功
     */
    void afterCommandRedo(ICommand command, boolean success);

    /**
     * 命令组开始时调用
     */
    void onCommandGroupBegin();

    /**
     * 命令组结束时调用
     */
    void onCommandGroupEnd();

    /**
     * 命令组取消时调用
     */
    void onCommandGroupCancel();
}
