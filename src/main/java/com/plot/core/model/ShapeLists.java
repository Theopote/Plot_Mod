package com.plot.core.model;

import com.plot.api.model.IShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IShape 列表到 core Shape 列表的桥接，供仍依赖具体实现的调用点使用。
 */
public final class ShapeLists {
    private ShapeLists() {}

    public static List<Shape> of(List<? extends IShape> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Shape> concrete = new ArrayList<>(shapes.size());
        for (IShape shape : shapes) {
            if (shape instanceof Shape coreShape) {
                concrete.add(coreShape);
            }
        }
        return concrete;
    }
}
