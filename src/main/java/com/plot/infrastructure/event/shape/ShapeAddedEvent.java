package com.plot.infrastructure.event.shape;

import com.plot.api.event.EventType;
import com.plot.infrastructure.event.base.Event;
import com.plot.core.model.Shape;

/**
 * 图形添加事件
 */
public class ShapeAddedEvent extends Event {
    private final Shape shape;
    private final String layerId;
    private final String source;

    public ShapeAddedEvent(String source, Shape shape, String layerId) {
        super(EventType.SHAPE_ADDED);
        this.source = source;
        this.shape = shape;
        this.layerId = layerId;
    }

    public Shape getShape() {
        return shape;
    }

    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ShapeAddedEvent[source=%s, shape=%s, layerId=%s]", 
            source, shape, layerId);
    }
} 