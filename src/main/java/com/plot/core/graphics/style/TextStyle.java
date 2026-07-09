package com.plot.core.graphics.style;

import com.plot.utils.PlotI18n;
import com.plot.api.graphics.ITextStyle;
import java.awt.Color;
import java.util.Objects;

import com.plot.core.graphics.style.TextAlignment.Horizontal;
import com.plot.core.graphics.style.TextAlignment.Vertical;

/**
 * 文本样式实现类
 * 支持Builder模式创建，提供不可变对象和流式配置
 */
public class TextStyle implements ITextStyle, Cloneable {
    // 常量定义
    public static final String DEFAULT_FONT_FAMILY = "Arial";
    public static final float DEFAULT_FONT_SIZE = 12.0f;
    public static final Color DEFAULT_COLOR = Color.BLACK;
    public static final float DEFAULT_LINE_HEIGHT = 1.2f;
    public static final float MIN_FONT_SIZE = 6.0f;
    public static final float MAX_FONT_SIZE = 72.0f;
    
    // 不可变字段
    private final String fontFamily;
    private final float fontSize;
    private final Color color;
    private final boolean bold;
    private final boolean italic;
    private final float lineHeight;
    private final Horizontal horizontalAlignment;
    private final Vertical verticalAlignment;

    /**
     * 私有构造函数，通过Builder创建实例
     */
    private TextStyle(Builder builder) {
        this.fontFamily = builder.fontFamily;
        this.fontSize = builder.fontSize;
        this.color = builder.color;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.lineHeight = builder.lineHeight;
        this.horizontalAlignment = builder.horizontalAlignment;
        this.verticalAlignment = builder.verticalAlignment;
    }

    /**
     * 默认构造函数（向后兼容）
     */
    public TextStyle() {
        this.fontFamily = DEFAULT_FONT_FAMILY;
        this.fontSize = DEFAULT_FONT_SIZE;
        this.color = DEFAULT_COLOR;
        this.bold = false;
        this.italic = false;
        this.lineHeight = DEFAULT_LINE_HEIGHT;
        this.horizontalAlignment = Horizontal.LEFT;
        this.verticalAlignment = Vertical.TOP;
    }

    /**
     * Builder类，提供流式配置
     */
    public static class Builder {
        private String fontFamily = DEFAULT_FONT_FAMILY;
        private float fontSize = DEFAULT_FONT_SIZE;
        private Color color = DEFAULT_COLOR;
        private boolean bold = false;
        private boolean italic = false;
        private float lineHeight = DEFAULT_LINE_HEIGHT;
        private Horizontal horizontalAlignment = Horizontal.LEFT;
        private Vertical verticalAlignment = Vertical.TOP;

        public Builder fontFamily(String fontFamily) {
            this.fontFamily = fontFamily != null ? fontFamily : DEFAULT_FONT_FAMILY;
            return this;
        }

        public Builder fontSize(float fontSize) {
            this.fontSize = Math.max(MIN_FONT_SIZE, Math.min(fontSize, MAX_FONT_SIZE));
            return this;
        }

        public Builder color(Color color) {
            this.color = color != null ? color : DEFAULT_COLOR;
            return this;
        }

        public Builder bold(boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder italic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Builder lineHeight(float lineHeight) {
            this.lineHeight = Math.max(0.5f, Math.min(lineHeight, 3.0f));
            return this;
        }

        public Builder horizontalAlignment(Horizontal alignment) {
            this.horizontalAlignment = alignment != null ? alignment : Horizontal.LEFT;
            return this;
        }

        public Builder verticalAlignment(Vertical alignment) {
            this.verticalAlignment = alignment != null ? alignment : Vertical.TOP;
            return this;
        }

        public TextStyle build() {
            return new TextStyle(this);
        }
    }

    // 移除重复的getFontName/setFontName方法，统一使用getFontFamily/setFontFamily

    @Override
    public String getFontName() {
        return fontFamily; // 向后兼容，返回fontFamily
    }

    @Override
    public void setFontName(String name) {
        // 由于字段不可变，此方法仅用于接口兼容性
        // 实际使用时应该通过Builder创建新实例
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public String getFontFamily() {
        return fontFamily;
    }

    @Override
    public void setFontFamily(String family) {
        // 由于字段不可变，此方法仅用于接口兼容性
        // 实际使用时应该通过Builder创建新实例
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public float getFontSize() {
        return fontSize;
    }

    @Override
    public void setFontSize(float size) {
        // 由于字段不可变，此方法仅用于接口兼容性
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public void setColor(Color color) {
        // 由于字段不可变，此方法仅用于接口兼容性
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public boolean isBold() {
        return bold;
    }

    @Override
    public void setBold(boolean bold) {
        // 由于字段不可变，此方法仅用于接口兼容性
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public boolean isItalic() {
        return italic;
    }

    @Override
    public void setItalic(boolean italic) {
        // 由于字段不可变，此方法仅用于接口兼容性
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public float getLineHeight() {
        return lineHeight;
    }

    @Override
    public void setLineHeight(float height) {
        // 由于字段不可变，此方法仅用于接口兼容性
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    @Override
    public int getFontColor() {
        return color != null ? color.getRGB() : 0;
    }

    @Override
    public void setFontColor(int color) {
        // 由于字段不可变，此方法仅用于接口兼容性
        throw new UnsupportedOperationException("TextStyle是不可变对象，请使用Builder创建新实例");
    }

    public TextStyle withFontSize(float fontSize) {
        return new Builder()
            .fontFamily(fontFamily)
            .fontSize(fontSize)
            .color(color)
            .bold(bold)
            .italic(italic)
            .lineHeight(lineHeight)
            .horizontalAlignment(horizontalAlignment)
            .verticalAlignment(verticalAlignment)
            .build();
    }

    @Override
    public ITextStyle clone() {
        try {
            return (TextStyle) super.clone();
        } catch (CloneNotSupportedException e) {
            return new TextStyle();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TextStyle other)) return false;
        return Float.compare(fontSize, other.fontSize) == 0 &&
               bold == other.bold &&
               italic == other.italic &&
               Float.compare(lineHeight, other.lineHeight) == 0 &&
               horizontalAlignment == other.horizontalAlignment &&
               verticalAlignment == other.verticalAlignment &&
               (Objects.equals(fontFamily, other.fontFamily)) &&
               (Objects.equals(color, other.color));
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (fontFamily != null ? fontFamily.hashCode() : 0);
        result = 31 * result + Float.floatToIntBits(fontSize);
        result = 31 * result + (color != null ? color.hashCode() : 0);
        result = 31 * result + (bold ? 1 : 0);
        result = 31 * result + (italic ? 1 : 0);
        result = 31 * result + Float.floatToIntBits(lineHeight);
        result = 31 * result + (horizontalAlignment != null ? horizontalAlignment.hashCode() : 0);
        result = 31 * result + (verticalAlignment != null ? verticalAlignment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format(
            "TextStyle[family=%s, size=%.1f, color=%s, bold=%b, italic=%b, hAlign=%s, vAlign=%s]",
            fontFamily, fontSize, color, bold, italic, horizontalAlignment, verticalAlignment
        );
    }

    /**
     * 序列化样式为JSON格式
     */
    public String serialize() {
        return String.format(
            "{\"fontSize\":%.1f,\"fontFamily\":\"%s\",\"color\":%d,\"bold\":%b,\"italic\":%b,\"hAlign\":\"%s\",\"vAlign\":\"%s\",\"lineHeight\":%.1f}",
            fontSize, fontFamily, getFontColor(), bold, italic,
            horizontalAlignment.name(), verticalAlignment.name(), lineHeight
        );
    }

    /**
     * 反序列化样式，增强错误处理
     */
    public void deserialize(String data) {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.empty_data"));
        }
        
        try {
            // 简单的JSON解析（实际项目中建议使用Jackson等库）
            if (data.startsWith("{")) {
                deserializeJson(data);
            } else {
                deserializeLegacy(data);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.shape.validation.deserialize_failed") + ": " + e.getMessage(), e);
        }
    }

    private void deserializeJson(String json) {
        // 简单的JSON解析实现
        String cleanJson = json.replaceAll("[{}\"]", "");
        String[] pairs = cleanJson.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim();

                switch (key) {
                    case "fontSize", "fontFamily", "color", "bold", "italic", "hAlign", "lineHeight", "vAlign":
                        // 由于字段不可变，这里仅用于兼容性
                        break;
                }
            }
        }
    }

    private void deserializeLegacy(String data) {
        String[] parts = data.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException(PlotI18n.error("error.plot.style.text.serialize_min_parts", parts.length));
        }
        
        // 由于字段不可变，这里仅用于兼容性
        // 实际使用时应该通过Builder创建新实例
    }
} 