package com.masterplanner.ui.imgui.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/**
 * 纹理上传（glTexImage2D/glTexSubImage2D）前的 PixelStore/PBO 状态保护。
 *
 * 典型症状：
 * - 图标/字体贴图“压缩/错位/碎片化”
 * - 原因往往是 Minecraft 渲染流程留下了 GL_UNPACK_ROW_LENGTH / PBO 绑定等状态
 */
public final class GlTextureUploadGuard implements AutoCloseable {
    private final int prevUnpackAlignment;
    private final int prevUnpackRowLength;
    private final int prevUnpackSkipPixels;
    private final int prevUnpackSkipRows;
    private final int prevPixelUnpackBuffer;
    private final int prevPixelPackBuffer;

    private GlTextureUploadGuard() {
        RenderSystem.assertOnRenderThread();

        prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        prevUnpackRowLength = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        prevUnpackSkipPixels = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
        prevUnpackSkipRows = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS);
        prevPixelUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        prevPixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        // 关键：确保是“CPU 内存指针上传”，而不是把指针当 PBO offset
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

        // 关键：防止行跨度/跳行/跳像素污染导致“贴图碎片化”
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
    }

    public static GlTextureUploadGuard enter() {
        return new GlTextureUploadGuard();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();

        // 恢复 PixelStore
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, prevUnpackRowLength);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, prevUnpackSkipPixels);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, prevUnpackSkipRows);

        // 恢复 PBO 绑定
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevPixelUnpackBuffer);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, prevPixelPackBuffer);
    }
}

