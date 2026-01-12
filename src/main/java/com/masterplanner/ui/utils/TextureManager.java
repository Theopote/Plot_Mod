package com.masterplanner.ui.utils;

import net.minecraft.util.Identifier;
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
import com.masterplanner.ui.imgui.gl.GlTextureUploadGuard;

/**
 * 纹理管理器
 * 负责加载和管理UI使用的纹理资源
 */
public class TextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MasterPlanner/TextureManager");
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
        String texturePath = String.format("assets/%s/%s", identifier.getNamespace(), identifier.getPath());
        return loadTexture(texturePath, identifier.toString());
    }

    /**
     * 加载纹理并返回纹理ID
     * @param path 纹理文件路径
     * @param key 缓存键
     * @return 纹理ID
     */
    public int loadTexture(String path, String key) {
        // 检查缓存
        if (textureCache.containsKey(key)) {
            return textureCache.get(key);
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }

            // 读取图片
            BufferedImage image = ImageIO.read(is);
            int width = image.getWidth();
            int height = image.getHeight();

            // 获取图片数据
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);

            // 创建字节缓冲区
            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                    buffer.put((byte) (pixel & 0xFF));         // Blue
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
                }
            }
            buffer.flip();

            // 生成纹理
            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            // 设置纹理参数
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            // 上传纹理数据
            try (GlTextureUploadGuard ignored = GlTextureUploadGuard.enter()) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            }

            // 缓存纹理ID
            textureCache.put(key, textureId);
            return textureId;

        } catch (IOException e) {
            LOGGER.error("Failed to load texture: {}", path, e);
            return 0;
        }
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