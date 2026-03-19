package com.masterplanner.ui.imgui;

import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import com.mojang.blaze3d.systems.RenderSystem;
import com.masterplanner.ui.imgui.gl.ImGuiGLStateGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ImGuiRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiRenderer.class);
    private static volatile ImGuiRenderer INSTANCE;
    private static final Object LOCK = new Object();

    private ImGuiImplGlfw imGuiGlfw;
    private ImGuiImplGl3 imGuiGl3;
    private long windowHandle;
    private boolean initialized;
    private boolean frameInProgress;
    private boolean drawDataReady;
    private long lastDrawDataLogMs;
    /** 本模组是否创建了 ImGui 上下文（共享模式下仅创建者负责销毁） */
    private boolean weCreatedContext;

    private ImGuiRenderer() {}

    public static ImGuiRenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new ImGuiRenderer();
                }
            }
        }
        return INSTANCE;
    }

    public void init() {
        if (initialized) {
            return;
        }

        try {
            LOGGER.info("Starting ImGui initialization...");
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                throw new RuntimeException("Minecraft client not initialized");
            }

            windowHandle = client.getWindow().getHandle();
            
            if (!client.isOnThread()) {
                throw new RuntimeException("Not on main thread");
            }

            // 关键：在 Minecraft 环境中，必须显式使 GL 上下文为当前，否则 imgui-java 原生的 GImGui 断言失败
            // 参考：fabric imgui 示例在 init 前调用 window.makeContextCurrent()
            GLFW.glfwMakeContextCurrent(windowHandle);

            // 本模组始终创建自己的 ImGui 上下文，避免 getCurrentContext() 返回无效值导致 getIO() 断言失败
            weCreatedContext = true;
            var ctx = ImGui.createContext();
            ImGui.setCurrentContext(ctx);
            LOGGER.info("已创建并设置 ImGui 上下文");

            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null);
            io.setConfigFlags(ImGuiConfigFlags.NavEnableKeyboard | ImGuiConfigFlags.DockingEnable);
            
            // 本模组始终创建自己的上下文，在此初始化字体
            initializeFonts(io);
            // 不在此处设置样式；MasterPlannerStyleScope 在每帧渲染时临时 push 样式，渲染后 pop，避免影响 Treefactory 等模组
            
            imGuiGlfw = new ImGuiImplGlfw();
            if (!imGuiGlfw.init(windowHandle, true)) {
                throw new RuntimeException("ImGui GLFW implementation init failed");
            }

            imGuiGl3 = new ImGuiImplGl3();
            // 使用与 ChronoBlocks 相同的 GLSL 版本选择逻辑
            String glslVersion = pickGlslVersionString();
            LOGGER.info("Initializing ImGui GL3 backend with {}", glslVersion);
            
            // 关键修复（1.21.x 常见坑）：
            // Minecraft 渲染/截图/纹理上传流程可能遗留 GL_UNPACK_* 状态（尤其 UNPACK_ROW_LENGTH），
            // 会直接把 ImGui 的 font atlas 上传"写花"，表现为文字/图标像二维码碎片。
            // 在创建 ImGui device objects（包含 fonts texture）前强制重置像素存储参数。
            resetPixelStoreStateForImGuiTextureUpload();
            imGuiGl3.init(glslVersion);
            
            initialized = true;
            LOGGER.info("ImGui initialization completed successfully");
        } catch (Exception e) {
            LOGGER.error("ImGui initialization failed", e);
            dispose();
            throw e;
        }
    }

    /**
     * 根据当前 OpenGL/GLSL 能力选择 ImGui GL3 backend 的 shader version 字符串。
     * 这是解决"升级后 UI 不显示"的高频原因：shader 版本写死过高/过低导致编译失败。
     * 参考 ChronoBlocks 的实现
     */
    private static String pickGlslVersionString() {
        try {
            String glVersion = GL11.glGetString(GL11.GL_VERSION);
            String slVersion = GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
            LOGGER.info("OpenGL={}, GLSL={}", glVersion, slVersion);

            // 粗略提取 GLSL 主版本（如 "4.60" / "3.30" / "1.50"）
            int major = 0;
            int minor = 0;
            if (slVersion != null) {
                String[] parts = slVersion.trim().split("[^0-9.]+")[0].split("\\.");
                if (parts.length >= 1) major = Integer.parseInt(parts[0]);
                if (parts.length >= 2) minor = Integer.parseInt(parts[1]);
            }

            // GLSL 1.50 ~ OpenGL 3.2 core（Minecraft 常见下限）
            if (major <= 1) {
                return "#version 150";
            }
            // 3.30+ 更常见（现代驱动）
            if (major == 3) {
                return "#version 330 core";
            }
            // 4.x
            if (major >= 4) {
                // 4.10+ 用 410，否则用 330 core
                if (major > 4 || minor >= 10) return "#version 410 core";
                return "#version 330 core";
            }
        } catch (Throwable t) {
            // ignore
        }
        // 最保守 fallback
        return "#version 150";
    }

    private static void resetPixelStoreStateForImGuiTextureUpload() {
        try {
            RenderSystem.assertOnRenderThread();
            // 关键兜底：如果 Minecraft 留下了 PBO 绑定，会导致 glTexImage2D 把指针当 offset → 上传全黑/乱码
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
        } catch (Throwable t) {
            // ignore
        }
    }

    private void initializeFonts(ImGuiIO io) {
        try {
            ImFontConfig fontConfig = new ImFontConfig();
            fontConfig.setGlyphRanges(io.getFonts().getGlyphRangesChineseFull());
            
            String[] fontPaths = {
                "C:/Windows/Fonts/msyh.ttc",
                "/System/Library/Fonts/PingFang.ttc",
                "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
            };
            
            boolean fontLoaded = false;
            for (String fontPath : fontPaths) {
                if (new File(fontPath).exists()) {
                    io.getFonts().addFontFromFileTTF(fontPath, 16.0f, fontConfig);
                    fontLoaded = true;
                    break;
                }
            }
            
            if (!fontLoaded) {
                io.getFonts().addFontDefault();
            }
            
            io.getFonts().build();
            fontConfig.destroy();
        } catch (Exception e) {
            LOGGER.error("Font init failed", e);
            io.getFonts().addFontDefault();
            io.getFonts().build();
        }
    }

    public void beginFrame() {
        synchronized (LOCK) {
            if (!initialized || frameInProgress) {
                return;
            }

            try {
                drawDataReady = false;
                ImGui.setCurrentContext(ImGui.getCurrentContext());
                updateDisplaySize();
                imGuiGlfw.newFrame();
                ImGui.newFrame();
                frameInProgress = true;
            } catch (Exception e) {
                LOGGER.error("Failed to begin frame", e);
                frameInProgress = false;
                reinitialize();
            }
        }
    }

    public void endFrame() {
        synchronized (LOCK) {
            if (!initialized || !frameInProgress) {
                LOGGER.warn("endFrame: skipped - initialized={}, frameInProgress={}", initialized, frameInProgress);
                return;
            }

            try {
                ImGui.render();
                drawDataReady = true;
                LOGGER.debug("endFrame: ImGui.render() completed, drawDataReady=true");
            } catch (Exception e) {
                LOGGER.error("ImGui endFrame failed", e);
            } finally {
                frameInProgress = false;
            }
        }
    }

    public boolean hasPendingDrawData() {
        return drawDataReady;
    }

    /**
     * 在"帧末 swap 前"绘制 ImGui（1.21.x 新渲染管线下更稳定）。
     * 典型调用点：注入到 RenderSystem.flipFrame(HEAD)。
     * 参考 ChronoBlocks 的实现方式：只禁用 SCISSOR_TEST，让 ImGuiImplGl3 内部处理其他状态
     */
    public void renderPendingDrawData() {
        synchronized (LOCK) {
            if (!initialized || !drawDataReady || imGuiGl3 == null) {
                LOGGER.warn("renderPendingDrawData: skipped - initialized={}, drawDataReady={}, imGuiGl3={}", 
                    initialized, drawDataReady, imGuiGl3 != null);
                return;
            }
            try {
                RenderSystem.assertOnRenderThread();

                // 1.21.x 常见坑：某些 pass 会关掉颜色写入，导致“画了但全黑”
                // 1.21.x 另一个高频坑：当前绑定的不是默认 framebuffer（0），导致 ImGui 画到了离屏目标上
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                // 同步 viewport，避免尺寸不一致导致裁剪/画面异常
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getWindow() != null) {
                    int fbW = Math.max(1, mc.getWindow().getFramebufferWidth());
                    int fbH = Math.max(1, mc.getWindow().getFramebufferHeight());
                    GL11.glViewport(0, 0, fbW, fbH);
                }
                
                // 渲染 ImGui
                imgui.ImDrawData dd = ImGui.getDrawData();
                // 节流日志：用于确认 draw data 真的存在
                long now = System.currentTimeMillis();
                if (dd != null && now - lastDrawDataLogMs > 3000L) {
                    lastDrawDataLogMs = now;
                    LOGGER.warn("ImGui dd lists={}, vtx={}, idx={}",
                        dd.getCmdListsCount(), dd.getTotalVtxCount(), dd.getTotalIdxCount());
                }
                if (dd != null) {
                    try (ImGuiGLStateGuard ignored = ImGuiGLStateGuard.enter()) {
                        imGuiGl3.renderDrawData(dd);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to render pending ImGui draw data", e);
                e.printStackTrace();
            } finally {
                drawDataReady = false;
            }
        }
    }

    public void dispose() {
        synchronized (LOCK) {
            if (!initialized) return;

            try {
                if (frameInProgress) ImGui.render();
                
                if (imGuiGl3 != null) imGuiGl3.dispose();
                if (imGuiGlfw != null) imGuiGlfw.dispose();
                // 仅在本模组创建了上下文时销毁，避免影响共享同一上下文的 Treefactory/ChronoBlocks
                if (weCreatedContext) {
                    ImGui.destroyContext();
                }
                
                initialized = false;
                LOGGER.info("ImGui resources cleaned up");
            } catch (Exception e) {
                LOGGER.error("Error cleaning up", e);
            }
        }
    }

    private void reinitialize() {
        dispose();
        init();
    }

    public void updateDisplaySize() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) return;
            
            Window window = client.getWindow();
            long currentWindowHandle = window.getHandle();
            if (currentWindowHandle != windowHandle) {
                windowHandle = currentWindowHandle;
                if (imGuiGlfw != null) {
                    imGuiGlfw.dispose();
                    imGuiGlfw = new ImGuiImplGlfw();
                    imGuiGlfw.init(windowHandle, true);
                }
            }
            
            int winW = Math.max(1, window.getWidth());
            int winH = Math.max(1, window.getHeight());
            int fbW = Math.max(1, window.getFramebufferWidth());
            int fbH = Math.max(1, window.getFramebufferHeight());
            double sfD = Math.max(1.0, window.getScaleFactor());
            float displayW = (float) (winW / sfD);
            float displayH = (float) (winH / sfD);
             
            ImGuiIO io = ImGui.getIO();
            io.setDisplaySize(displayW, displayH);
            io.setDisplayFramebufferScale(fbW / displayW, fbH / displayH);
        } catch (Exception e) {
            LOGGER.error("Error updating display size", e);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
