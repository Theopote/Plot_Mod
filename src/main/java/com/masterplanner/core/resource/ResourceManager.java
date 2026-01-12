package com.masterplanner.core.resource;

import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import com.masterplanner.ui.imgui.gl.GlTextureUploadGuard;

public class ResourceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);
    private static ResourceManager INSTANCE;

    private ResourceManager() {
        // 私有构造函数
    }

    public static ResourceManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ResourceManager();
        }
        return INSTANCE;
    }

    /**
     * 加载纹理
     * @param path 纹理路径
     * @return OpenGL纹理ID
     */
    public int loadTexture(String path) {
        try {
            LOGGER.debug("Loading texture: {}", path);
            
            // 创建正确的Identifier
            Identifier identifier = Identifier.of("masterplanner", path);
            
            // 使用Minecraft的资源管理器获取资源
            Resource resource = MinecraftClient.getInstance().getResourceManager()
                .getResource(identifier)
                .orElseThrow(() -> new RuntimeException("Resource not found: " + identifier));

            // 1.21.11：NativeImage/RenderSystem 的纹理上传 API 发生变化。
            // 这里改用 ImageIO -> RGBA ByteBuffer -> glTexImage2D 的纯 OpenGL 路径，避免版本耦合。
            try (InputStream in = resource.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    throw new RuntimeException("Failed to decode image: " + identifier);
                }
                int width = image.getWidth();
                int height = image.getHeight();

                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);

                ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = pixels[y * width + x];
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                    }
                }
                buffer.flip();

                int textureId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

                try (GlTextureUploadGuard ignored = GlTextureUploadGuard.enter()) {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                }

                LOGGER.debug("Loaded texture: {} (ID: {}, {}x{})", path, textureId, width, height);
                return textureId;
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to load texture: {}", path, e);
            return 0;
        }
    }
    
    /**
     * 判断一个数是否是2的幂次方
     */
    // 预留：如果后续需要对纹理尺寸做校验，可恢复 isPowerOfTwo 检查逻辑
}