package com.masterplanner.ui.imgui.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;

/**
 * ImGui（imgui-java GL3 backend）在 Minecraft 1.21+ 下的“GL 状态安全壳”。
 * <p>
 * 目的：
 * - 修复 RenderGraph/Sampler 管理引入的 sampler 绑定污染（典型症状：所有采样全黑）
 * - 修复 ColorMask/Depth/Scissor/Blend 等状态污染
 * - 渲染结束后尽量恢复原状态，避免影响 MC 后续流程
 */
public final class ImGuiGLStateGuard implements AutoCloseable {
    private final int prevActiveTexture;
    private final int prevSampler0;
    private final int prevTex2d0;
    private final boolean[] prevColorMask = new boolean[4];
    private final boolean prevDepthTest;
    private final boolean prevBlend;
    private final boolean prevScissor;

    private ImGuiGLStateGuard() {
        RenderSystem.assertOnRenderThread();

        // ---- 保存状态 ----
        prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        // sampler/texture binding 与 active texture unit 相关：先切到 unit0 再读取
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        prevSampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        prevTex2d0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        ByteBuffer cm = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, cm);
        prevColorMask[0] = cm.get(0) != 0;
        prevColorMask[1] = cm.get(1) != 0;
        prevColorMask[2] = cm.get(2) != 0;
        prevColorMask[3] = cm.get(3) != 0;
        prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        // ---- 设置 ImGui 安全状态 ----
        applyImGuiSafeState();
    }

    public static ImGuiGLStateGuard enter() {
        return new ImGuiGLStateGuard();
    }

    private static void applyImGuiSafeState() {
        // 🔥 Minecraft 1.21+ 核心：确保采样走 ImGui 预期的 unit0 + default sampler
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // 颜色写入必须开启（否则“画了但全黑/透明”）
        GL11.glColorMask(true, true, true, true);

        // ImGui overlay：通常不需要深度/裁剪
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // 标准 alpha blend
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();

        // 恢复到 unit0，再恢复 unit0 的 sampler/texture 绑定
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, prevSampler0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2d0);

        // 恢复颜色写入
        GL11.glColorMask(prevColorMask[0], prevColorMask[1], prevColorMask[2], prevColorMask[3]);

        setEnabled(GL11.GL_DEPTH_TEST, prevDepthTest);
        setEnabled(GL11.GL_BLEND, prevBlend);
        setEnabled(GL11.GL_SCISSOR_TEST, prevScissor);

        // 恢复 active texture
        GL13.glActiveTexture(prevActiveTexture);
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) GL11.glEnable(cap);
        else GL11.glDisable(cap);
    }
}

