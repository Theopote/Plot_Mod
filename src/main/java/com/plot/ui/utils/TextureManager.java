package com.plot.ui.utils;

import com.plot.utils.SvgUtils;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
            ResolvedResource resolvedResource = resolveResource(identifier);

            try (InputStream is = resolvedResource.resource().getInputStream()) {
                int textureId = uploadTexture(is, resolvedResource.identifier().getPath());
                textureCache.put(key, textureId);
                return textureId;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load texture: {}", identifier, e);
            return 0;
        }
    }

    private ResolvedResource resolveResource(Identifier requested) throws IOException {
        Optional<Resource> direct = MinecraftClient.getInstance().getResourceManager().getResource(requested);
        if (direct.isPresent()) {
            return new ResolvedResource(requested, direct.get());
        }

        String path = requested.getPath();
        if (path.endsWith(".png")) {
            Identifier svgIdentifier = Identifier.of(
                    requested.getNamespace(),
                    path.substring(0, path.length() - 4) + ".svg"
            );
            Optional<Resource> svg = MinecraftClient.getInstance().getResourceManager().getResource(svgIdentifier);
            if (svg.isPresent()) {
                LOGGER.debug("Texture fallback hit: {} -> {}", requested, svgIdentifier);
                return new ResolvedResource(svgIdentifier, svg.get());
            }
        }

        throw new IOException("Resource not found: " + requested);
    }

    private int uploadTexture(InputStream is, String path) throws IOException {
        BufferedImage image = path.endsWith(".svg") ? SvgUtils.readSvg(is) : ImageIO.read(is);
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
     * 从缓存中移除指定纹理 ID（不删除 GL 对象）。
     */
    public void evict(int textureId) {
        if (textureId <= 0) {
            return;
        }
        textureCache.entrySet().removeIf(entry -> entry.getValue() == textureId);
    }

    /**
     * 清理所有纹理
     */
    public void dispose() {
        if (textureCache.isEmpty()) {
            return;
        }

        int released = 0;
        for (int textureId : textureCache.values()) {
            if (textureId > 0) {
                GL11.glDeleteTextures(textureId);
                released++;
            }
        }
        textureCache.clear();
        LOGGER.debug("Released {} cached UI texture(s)", released);
    }

    private record ResolvedResource(Identifier identifier, Resource resource) {
    }
} 