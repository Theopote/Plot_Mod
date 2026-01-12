package com.masterplanner.core.selection;

import com.masterplanner.api.geometry.Vec2d;
import com.masterplanner.core.model.Shape;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示当前选中的图形集合
 */
public class Selection {
    private final List<Shape> selectedShapes;
    private Vec2d center;
    
    public Selection(List<Shape> shapes) {
        this.selectedShapes = new ArrayList<>(shapes);
        updateCenter();
    }
    
    public List<Shape> getShapes() {
        return selectedShapes;
    }
    
    public boolean isEmpty() {
        return selectedShapes.isEmpty();
    }
    
    public Vec2d getCenter() {
        return center;
    }
    
    public void add(Shape shape) {
        if (!selectedShapes.contains(shape)) {
            selectedShapes.add(shape);
            updateCenter();
        }
    }
    
    public void remove(Shape shape) {
        if (selectedShapes.remove(shape)) {
            updateCenter();
        }
    }
    
    public void clear() {
        selectedShapes.clear();
        center = null;
    }
    
    private void updateCenter() {
        if (selectedShapes.isEmpty()) {
            center = null;
            return;
        }
        
        double sumX = 0;
        double sumY = 0;
        for (Shape shape : selectedShapes) {
            Vec2d shapeCenter = shape.getBoundingBox().getCenter();
            sumX += shapeCenter.x;
            sumY += shapeCenter.y;
        }
        
        center = new Vec2d(
            sumX / selectedShapes.size(),
            sumY / selectedShapes.size()
        );
    }
}
