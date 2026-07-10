package com.plot.infrastructure.event.road;

import com.plot.api.event.EventType;
import com.plot.core.model.Shape;
import com.plot.infrastructure.event.base.Event;

/**
 * 道路系统路径拾取完成事件（由选择工具右键确认触发）
 */
public class RoadPathPickedEvent extends Event {
    private final Shape path;
    private final String source;

    public RoadPathPickedEvent(Shape path) {
        this("SelectionStrategy", path);
    }

    public RoadPathPickedEvent(String source, Shape path) {
        super(EventType.ROAD_PATH_PICKED);
        this.source = source;
        this.path = path;
    }

    public Shape getPath() {
        return path;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("RoadPathPickedEvent[source=%s, path=%s]", source,
            path != null ? path.getId() : "null");
    }
}
