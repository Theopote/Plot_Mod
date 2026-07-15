package com.plot.core.material;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Gson 适配器：支持旧版纯字符串材质字段与新 {@link MaterialMix} 对象格式。
 */
public final class MaterialMixTypeAdapter extends TypeAdapter<MaterialMix> {
    @Override
    public void write(JsonWriter out, MaterialMix value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("primaryMaterial");
        out.value(value.getPrimaryMaterial());
        if (value.getAccentMaterial() != null && !value.getAccentMaterial().isBlank()) {
            out.name("accentMaterial");
            out.value(value.getAccentMaterial());
        }
        if (value.getAccentRatio() > 0f) {
            out.name("accentRatio");
            out.value(value.getAccentRatio());
        }
        out.endObject();
    }

    @Override
    public MaterialMix read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (token == JsonToken.STRING) {
            return MaterialMix.single(in.nextString());
        }

        String primary = null;
        String accent = null;
        float ratio = 0f;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "primaryMaterial" -> primary = in.nextString();
                case "accentMaterial" -> accent = in.nextString();
                case "accentRatio" -> ratio = (float) in.nextDouble();
                default -> in.skipValue();
            }
        }
        in.endObject();
        return new MaterialMix(primary, accent, ratio);
    }
}
