package com.plot.utils;

import imgui.flag.ImGuiWindowFlags;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import com.plot.ui.utils.TextureManager;
import com.plot.ui.imgui.gl.GlTextureUploadGuard;

/**
 * ImGui工具类
 */
public class ImGuiUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiUtils.class);
    
    private static final int DEFAULT_TEXTURE_SIZE = 32;
    private static int defaultTextureId = 0;
    
    // 工具栏窗口标志
    public static final int TOOLBAR_FLAGS = 
        ImGuiWindowFlags.NoTitleBar |
        ImGuiWindowFlags.NoResize |
        ImGuiWindowFlags.NoMove |
        ImGuiWindowFlags.NoScrollbar |
        ImGuiWindowFlags.NoScrollWithMouse |
        ImGuiWindowFlags.NoCollapse |
        ImGuiWindowFlags.NoBackground |
        ImGuiWindowFlags.NoBringToFrontOnFocus;
    
    // 纹理ID缓存
    private static final Map<Identifier, Integer> textureCache = new HashMap<>();
    
    /**
     * 获取资源的纹理ID
     */
    public static int getTextureId(Identifier texture) {
        try {
            return textureCache.computeIfAbsent(texture, id -> {
                try {
                    return loadTexture(id);
                } catch (Exception e) {
                    LOGGER.error("Failed to load texture {}: {}", texture, e.getMessage());
                    // 返回默认纹理ID
                    return getDefaultTextureId();
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error getting texture ID for {}: {}", texture, e.getMessage());
            return getDefaultTextureId();
        }
    }
    
    private static int getDefaultTextureId() {
        if (defaultTextureId == 0) {
            try {
                defaultTextureId = GlStateManager._genTexture();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultTextureId);
                
                // 设置纹理参数
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
                
                // 生成 32x32 灰色 RGBA 纹理
                ByteBuffer buffer = ByteBuffer.allocateDirect(DEFAULT_TEXTURE_SIZE * DEFAULT_TEXTURE_SIZE * 4);
                for (int i = 0; i < DEFAULT_TEXTURE_SIZE * DEFAULT_TEXTURE_SIZE; i++) {
                    buffer.put((byte) 0x80); // R
                    buffer.put((byte) 0x80); // G
                    buffer.put((byte) 0x80); // B
                    buffer.put((byte) 0xFF); // A
                }
                buffer.flip();

                try (GlTextureUploadGuard ignored = GlTextureUploadGuard.enter()) {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, DEFAULT_TEXTURE_SIZE, DEFAULT_TEXTURE_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to create default texture: {}", e.getMessage());
            }
        }
        return defaultTextureId;
    }
    
    /**
     * 加载纹理并返回纹理ID
     */
    private static int loadTexture(Identifier location) {
        // 1.21.11：NativeImage/RenderSystem 上传路径变更，这里直接复用我们项目里的 TextureManager（GL + ImageIO）。
        try {
            int id = TextureManager.getInstance().loadTexture(location);
            return id != 0 ? id : getDefaultTextureId();
        } catch (Exception e) {
            LOGGER.error("加载纹理失败 {}: {}", location, e.getMessage());
            return getDefaultTextureId();
        }
    }
    
    /**
     * 删除纹理资源
     * @param textureId 要删除的纹理ID
     */
    public static void deleteTexture(int textureId) {
        if (textureId <= 0) return;
        
        try {
            // 从缓存中移除
            textureCache.values().removeIf(id -> id == textureId);
            
            // 1.21.11：RenderSystem#recordRenderCall 变更，这里不再强制切线程，调用方应在渲染线程删除
            GlStateManager._deleteTexture(textureId);
            
            LOGGER.debug("已删除纹理ID: {}", textureId);
        } catch (Exception e) {
            LOGGER.error("删除纹理时出错: {}", e.getMessage(), e);
        }
    }
}
