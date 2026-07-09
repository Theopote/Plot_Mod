package com.plot.core.model;

import com.plot.core.layer.Layer;
import com.plot.api.model.ILayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CanvasModel {
    private final UUID id;
    private String name;
    private final List<ILayer> layers;
    private int width;
    private int height;
    private ILayer activeLayer;

    public CanvasModel(String name, int width, int height) {
        this(UUID.randomUUID(), name, width, height, true);
    }

    public CanvasModel(UUID id, String name, int width, int height) {
        this(id, name, width, height, false);
    }

    private CanvasModel(UUID id, String name, int width, int height, boolean createDefaultLayer) {
        this.id = id;
        this.name = name;
        this.width = width;
        this.height = height;
        this.layers = new ArrayList<>();

        if (createDefaultLayer) {
            ILayer defaultLayer = new Layer("Default Layer");
            this.layers.add(defaultLayer);
            this.activeLayer = defaultLayer;
        }
    }

    public ILayer getActiveLayer() {
        return activeLayer;
    }

    public void setActiveLayer(ILayer layer) {
        if (layers.contains(layer)) {
            this.activeLayer = layer;
        }
    }

    public List<ILayer> getLayers() {
        return new ArrayList<>(layers);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }
}
