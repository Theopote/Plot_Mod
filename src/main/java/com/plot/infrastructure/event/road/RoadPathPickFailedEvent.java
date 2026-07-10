package com.plot.infrastructure.event.road;

import com.plot.api.event.EventType;
import com.plot.infrastructure.event.base.Event;

/**
 * 道路路径拾取失败事件（无有效路径或尚未选择）
 */
public class RoadPathPickFailedEvent extends Event {
    private final String messageKey;
    private final String source;

    public RoadPathPickFailedEvent(String messageKey) {
        this("SelectionStrategy", messageKey);
    }

    public RoadPathPickFailedEvent(String source, String messageKey) {
        super(EventType.ROAD_PATH_PICKED);
        this.source = source;
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }

    @Override
    public String getSource() {
        return source;
    }
}
