package com.plot.plugin.powerline.design;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plot.core.material.MaterialMix;
import com.plot.core.material.MaterialMixTypeAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 杆塔分层造型。
 */
public class PoleDesign {
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(MaterialMix.class, new MaterialMixTypeAdapter())
        .create();

    private final String id;
    private String name;
    private List<PoleLayer> layers = new ArrayList<>();

    public PoleDesign(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public PoleDesign(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null && !name.isBlank() ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PoleLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<PoleLayer> layers) {
        this.layers = new ArrayList<>();
        if (layers == null) {
            return;
        }
        for (PoleLayer layer : layers) {
            if (layer != null) {
                this.layers.add(layer.copy());
            }
        }
    }

    public int totalHeight() {
        return layers.stream().mapToInt(PoleLayer::getHeight).sum();
    }

    /**
     * 自下而上遍历层栈时，最后一个 {@link PoleLayer.Shape#CROSSARM} 为导线悬挂层
     * （即物理位置最高的横担）。
     */
    public int conductorCrossarmLayerIndex() {
        int index = -1;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).getShape() == PoleLayer.Shape.CROSSARM) {
                index = i;
            }
        }
        return index;
    }

    public int wireHangHeightFromGround(int groundY) {
        int currentY = groundY + 1;
        int wireHangY = groundY + totalHeight();
        for (PoleLayer layer : layers) {
            if (layer.getShape() == PoleLayer.Shape.CROSSARM) {
                wireHangY = currentY + layer.getHeight() - 1;
            }
            currentY += layer.getHeight();
        }
        return wireHangY;
    }

    public PoleDesign copy() {
        PoleDesign copy = new PoleDesign(id, name);
        copy.setLayers(layers);
        return copy;
    }

    public String toJson() {
        return GSON.toJson(DesignData.from(this));
    }

    public static PoleDesign fromJson(String json) {
        DesignData data = GSON.fromJson(json, DesignData.class);
        return data != null ? data.toDesign() : null;
    }

    static class LayerData {
        String shape;
        int height;
        int crossarmLength;
        MaterialMix material;
    }

    static class DesignData {
        String id;
        String name;
        List<LayerData> layers = new ArrayList<>();

        static DesignData from(PoleDesign design) {
            DesignData data = new DesignData();
            data.id = design.id;
            data.name = design.name;
            for (PoleLayer layer : design.layers) {
                LayerData layerData = new LayerData();
                layerData.shape = layer.getShape().name();
                layerData.height = layer.getHeight();
                layerData.crossarmLength = layer.getCrossarmLength();
                layerData.material = layer.getMaterial();
                data.layers.add(layerData);
            }
            return data;
        }

        PoleDesign toDesign() {
            PoleDesign design = new PoleDesign(id, name);
            List<PoleLayer> restored = new ArrayList<>();
            if (layers != null) {
                for (LayerData layerData : layers) {
                    if (layerData == null || layerData.shape == null) {
                        continue;
                    }
                    PoleLayer layer = new PoleLayer();
                    layer.setShape(PoleLayer.Shape.valueOf(layerData.shape));
                    layer.setHeight(layerData.height);
                    layer.setCrossarmLength(layerData.crossarmLength);
                    if (layerData.material != null) {
                        layer.setMaterial(layerData.material);
                    }
                    restored.add(layer);
                }
            }
            design.setLayers(restored);
            return design;
        }
    }
}
