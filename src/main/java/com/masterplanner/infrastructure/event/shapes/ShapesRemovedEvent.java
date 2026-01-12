package com.masterplanner.infrastructure.event.shapes;

import com.masterplanner.api.event.EventType;
import com.masterplanner.infrastructure.event.base.Event;
import com.masterplanner.core.model.Shape;
import java.util.List;
import java.util.ArrayList;

/**
 * 形状移除事件
 */
public class ShapesRemovedEvent extends Event {
    private final List<Shape> shapes;
    private final String source;
    
    public ShapesRemovedEvent(List<Shape> shapes) {
        this("ShapeManager", shapes);
    }
    
    public ShapesRemovedEvent(String source, List<Shape> shapes) {
        super(EventType.SHAPE_REMOVED);
        this.source = source;
        this.shapes = new ArrayList<>(shapes);
    }
    
    public List<Shape> getShapes() {
        return new ArrayList<>(shapes);
    }
    
    @Override
    public String getSource() {
        return source;
    }
    
    @Override
    public String toString() {
        return String.format("ShapesRemovedEvent[source=%s, count=%d]", source, shapes.size());
    }
} 