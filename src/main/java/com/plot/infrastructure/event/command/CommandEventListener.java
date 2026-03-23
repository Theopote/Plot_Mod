package com.plot.infrastructure.event.command;

import com.plot.core.state.AppState;
import com.plot.infrastructure.event.EventBus;
import com.plot.infrastructure.event.EventListener;
import com.plot.infrastructure.event.base.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令事件监听器
 * 处理撤销和重做事件
 */
public class CommandEventListener implements EventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/CommandEventListener");
    private static volatile CommandEventListener INSTANCE;
    private final AppState appState;

    private CommandEventListener() {
        this.appState = AppState.getInstance();
        // 订阅撤销和重做事件
        EventBus.getInstance().subscribe(UndoEvent.class, this);
        EventBus.getInstance().subscribe(RedoEvent.class, this);
        LOGGER.info("命令事件监听器已初始化");
    }

    public static CommandEventListener getInstance() {
        if (INSTANCE == null) {
            synchronized (CommandEventListener.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CommandEventListener();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof UndoEvent) {
            LOGGER.debug("收到撤销事件: {}", event);
            appState.undo();
        } else if (event instanceof RedoEvent) {
            LOGGER.debug("收到重做事件: {}", event);
            appState.redo();
        }
    }
} 