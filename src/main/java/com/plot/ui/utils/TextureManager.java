package com.plot.ui.utils;

import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import com.plot.ui.imgui.gl.GlTextureUploadGuard;

/**
 * 纹理管理器
 * 负责加载和管理UI使用的纹理资源
 */
public class TextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Plot/TextureManager");
    private static TextureManager instance;
    private final Map<String, Integer> textureCache = new HashMap<>();



    public static TextureManager getInstance() {
        if (instance == null) {
            instance = new TextureManager();
        }
        return instance;
    }

    /**
     * 加载纹理并返回纹理ID
     * @param identifier Minecraft资源标识符
     * @return 纹理ID
     */
    public int loadTexture(Identifier identifier) {
        String key = identifier.toString();
        if (textureCache.containsKey(key)) {
            return textureCache.get(key);
        }

        try {
            Resource resource = MinecraftClient.getInstance()
                    .getResourceManager()
                    .getResource(identifier)
                    .orElseThrow(() -> new IOException("Resource not found: " + identifier));

            try (InputStream is = resource.getInputStream()) {
                int textureId = uploadTexture(is);
                textureCache.put(key, textureId);
                return textureId;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load texture: {}", identifier, e);
            return 0;
        }
    }

    private int uploadTexture(InputStream is) throws IOException {
        BufferedImage image = ImageIO.read(is);
        if (image == null) {
            throw new IOException("Failed to decode image stream");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buffer.flip();

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        try (GlTextureUploadGuard ignored = GlTextureUploadGuard.enter()) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }

        return textureId;
    }

    /**
     * 清理所有纹理
     */
    public void dispose() {
        for (int textureId : textureCache.values()) {
            if (textureId > 0) {
                GL11.glDeleteTextures(textureId);
            }
        }
        textureCache.clear();
    }
} 