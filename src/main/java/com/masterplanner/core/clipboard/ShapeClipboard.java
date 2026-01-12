package com.masterplanner.core.clipboard;

import com.masterplanner.core.model.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * 形状剪贴板管理类
 */
public class ShapeClipboard {
    private List<Shape> copiedShapes = new ArrayList<>();
    
    public void copyShapes(List<Shape> shapes) {
        copiedShapes.clear();
        for (Shape shape : shapes) {
            copiedShapes.add(shape.clone());
        }
    }
    
    public List<Shape> pasteShapes() {
        List<Shape> pastedShapes = new ArrayList<>();
        for (Shape shape : copiedShapes) {
            pastedShapes.add(shape.clone());
        }
        return pastedShapes;
    }
    
    public void clear() {
        copiedShapes.clear();
    }
    
    public boolean isEmpty() {
        return copiedShapes.isEmpty();
    }
} 