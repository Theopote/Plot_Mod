package com.plot.ui.imgui;

import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.internal.ImGuiContext;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import imgui.gl3.ImGuiImplGl3;
import com.mojang.blaze3d.systems.RenderSystem;
import com.plot.ui.imgui.gl.ImGuiGLStateGuard;
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
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import org.lwjgl.system.MemoryStack;

public class ImGuiRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiRenderer.class);
    private static volatile ImGuiRenderer INSTANCE;
    private static final Object LOCK = new Object();

    private ImGuiImplGl3 imGuiGl3;
    private long windowHandle;
    private boolean initialized;
    private boolean frameInProgress;
    private boolean drawDataReady;
    private long lastDrawDataLogMs;
    /** 本模组创建的 ImGui 上下文；渲染时设为 current */
    private ImGuiContext ourContext;
    /** 初始化前已有的 ImGui 上下文（可能来自 ChronoBlocks/Treefactory 或其他 ImGui 模组）；关闭界面时恢复 */
    private ImGuiContext savedPreviousContext;
    /** 上一帧时间戳（纳秒），用于计算 DeltaTime */
    private long lastFrameTimeNanos;
    /** 滚轮增量缓存：由 Screen 事件线程写入，在 beginFrame 时统一注入 ImGui IO */
    private float pendingMouseWheel;
    /** 作为背景层补绘 Plot UI 时，禁止 ImGui 读取真实鼠标/修饰键输入，避免点击穿透到底层面板。 */
    private boolean inputSuppressed;

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
            GLFW.glfwMakeContextCurrent(windowHandle);

            // 系统化方案：不依赖任何模组检测。保存当前上下文（可能来自 ChronoBlocks、Treefactory 或任何 ImGui 模组），
            // 关闭 Plot 界面时恢复，避免破坏其他模组的字体/图标。
            // 关键：imgui-java 1.86.11 存在 bug（#253）—— createContext() 会覆盖先前上下文对象的 ptr，
            // 导致 savedPreviousContext 被破坏，进而使 restorePreviousContext 恢复错误，其它模组的 ImGuiImplGlfw 等状态错乱。
            // 必须在 createContext 之前保存指针值，并用 new ImGuiContext(ptr) 重建，确保不受 createContext 覆盖影响。
            ImGuiContext previous = ImGui.getCurrentContext();
            long previousPtr = (previous != null && previous.ptr != 0) ? previous.ptr : 0;

            // 始终创建本模组独立的 ImGui 上下文
            ourContext = ImGui.createContext();
            // 同上 bug 变通：用新实例保存 ptr，防止后续 createContext（如 dispose 中 fallback）覆盖
            ourContext = new ImGuiContext(ourContext.ptr);
            savedPreviousContext = (previousPtr != 0) ? new ImGuiContext(previousPtr) : null;
            ImGui.setCurrentContext(ourContext);
            LOGGER.info("已创建并设置 ImGui 上下文");

            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null);
            io.setConfigFlags(ImGuiConfigFlags.NavEnableKeyboard | ImGuiConfigFlags.DockingEnable);
            initializeFonts(io);
            // 设置 KeyMap，NavEnableKeyboard 要求映射 ImGuiKey_Space 等，否则 NewFrame 断言失败
            setupKeyMap(io);
            // 不在此处设置样式；PlotStyleScope 在每帧渲染时临时 push 样式，渲染后 pop，避免影响 Treefactory 等模组
            
            // 完全不使用 ImGuiImplGlfw，改用手动 IO 更新，彻底避免对 ChronoBlocks 等模组 GLFW 回调/窗口状态的任何影响。

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

    /**
     * 设置 ImGui KeyMap，将 GLFW 键码映射到 ImGuiKey。
     * NavEnableKeyboard 要求至少映射 ImGuiKey_Space，否则 NewFrame 会断言失败。
     * 参考 imgui_impl_glfw 的键盘映射。
     */
    private static void setupKeyMap(ImGuiIO io) {
        io.setKeyMap(ImGuiKey.Tab, GLFW.GLFW_KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, GLFW.GLFW_KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, GLFW.GLFW_KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, GLFW.GLFW_KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, GLFW.GLFW_KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, GLFW.GLFW_KEY_PAGE_UP);
        io.setKeyMap(ImGuiKey.PageDown, GLFW.GLFW_KEY_PAGE_DOWN);
        io.setKeyMap(ImGuiKey.Home, GLFW.GLFW_KEY_HOME);
        io.setKeyMap(ImGuiKey.End, GLFW.GLFW_KEY_END);
        io.setKeyMap(ImGuiKey.Insert, GLFW.GLFW_KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, GLFW.GLFW_KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, GLFW.GLFW_KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, GLFW.GLFW_KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, GLFW.GLFW_KEY_ENTER);
        io.setKeyMap(ImGuiKey.Escape, GLFW.GLFW_KEY_ESCAPE);
    }

    /**
     * 手动更新 ImGui IO（DeltaTime、鼠标位置、鼠标按键）。
     * 使用 GLFW 只读 API（glfwGetCursorPos、glfwGetMouseButton），不安装任何回调，
     * 彻底避免与 ChronoBlocks 等模组的 GLFW 回调冲突。
     */
    private void updateFrameInputs() {
        try {
            ImGuiIO io = ImGui.getIO();

            // DeltaTime（imgui-java 1.86 使用 setDeltaTime）
            long now = System.nanoTime();
            if (lastFrameTimeNanos > 0) {
                float dt = (float) ((now - lastFrameTimeNanos) / 1_000_000_000.0);
                io.setDeltaTime(dt > 0.0f ? dt : 1.0f / 60.0f);
            } else {
                io.setDeltaTime(1.0f / 60.0f);
            }
            lastFrameTimeNanos = now;

            if (inputSuppressed) {
                io.setMousePos(-10_000.0f, -10_000.0f);
                for (int i = 0; i < 3; i++) {
                    io.setMouseDown(i, false);
                }
                io.setKeyCtrl(false);
                io.setKeyShift(false);
                io.setKeyAlt(false);
                io.setKeySuper(false);
                io.setMouseWheel(0.0f);
                pendingMouseWheel = 0.0f;
                return;
            }

            // 鼠标位置与按键（只读 GLFW API，不修改任何回调；imgui-java 1.86 使用 setMousePos/setMouseDown）
            try (MemoryStack stack = MemoryStack.stackPush()) {
                DoubleBuffer x = stack.mallocDouble(1);
                DoubleBuffer y = stack.mallocDouble(1);
                GLFW.glfwGetCursorPos(windowHandle, x, y);
                float mouseX = (float) x.get(0);
                float mouseY = (float) y.get(0);
                io.setMousePos(mouseX, mouseY);
            }

            for (int i = 0; i < 3; i++) {
                boolean down = GLFW.glfwGetMouseButton(windowHandle, i) == GLFW.GLFW_PRESS;
                io.setMouseDown(i, down);
            }

            // 修饰键必须在 NewFrame 之前同步，否则 io.KeyCtrl/io.KeyShift 与 io.KeyMods 会断言不一致
            boolean ctrlDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shiftDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean altDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            boolean superDown = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS;
            io.setKeyCtrl(ctrlDown);
            io.setKeyShift(shiftDown);
            io.setKeyAlt(altDown);
            io.setKeySuper(superDown);

            // 手动桥接滚轮输入：本项目未使用 ImGuiImplGlfw，需要自行把滚轮事件喂给 ImGui
            io.setMouseWheel(pendingMouseWheel);
            pendingMouseWheel = 0.0f;
        } catch (Exception e) {
            LOGGER.debug("updateFrameInputs failed", e);
        }
    }

    /**
     * 记录鼠标滚轮增量（每帧在 updateFrameInputs 中消费）。
     * @param verticalDelta 纵向滚轮增量（向上通常为正）
     */
    public void onMouseScrolled(double verticalDelta) {
        synchronized (LOCK) {
            pendingMouseWheel += (float) verticalDelta;
        }
    }

    public void setInputSuppressed(boolean suppressed) {
        synchronized (LOCK) {
            inputSuppressed = suppressed;
            if (suppressed) {
                pendingMouseWheel = 0.0f;
            }
        }
    }

    public void beginFrame() {
        synchronized (LOCK) {
            if (!initialized || frameInProgress) {
                return;
            }

            try {
                drawDataReady = false;
                // 确保使用本模组上下文（可能因关闭界面已 restore，需重新激活）
                if (ourContext != null) {
                    ImGui.setCurrentContext(ourContext);
                }
                updateDisplaySize();
                updateFrameInputs();
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
                    LOGGER.debug("ImGui dd lists={}, vtx={}, idx={}",
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
                // 关键：渲染完成后立即恢复其他模组上下文，确保 ChronoBlocks 等下次打开时不受影响
                restorePreviousContext();
            }
        }
    }

    /**
     * 释放 ImGui 资源。在游戏退出 (CLIENT_STOPPING) 时调用。
     * 关键：必须确保在销毁本模组上下文后，GImGui 永远不为 null，否则 ChronoBlocks 等模组
     * 在关闭其界面时调用 ImGui.getIO() 会触发断言崩溃。
     * 若 savedPreviousContext 为 null 或不可靠（其他模组可能已先清理），则创建临时
     * fallback 上下文作为 current，避免任何模组后续调用 getIO() 时崩溃。
     */
    public void dispose() {
        synchronized (LOCK) {
            if (!initialized) return;

            try {
                if (frameInProgress) ImGui.render();

                if (imGuiGl3 != null) {
                    imGuiGl3.dispose();
                    imGuiGl3 = null;
                }
                if (ourContext != null) {
                    // 必须在销毁前切换 current，否则 destroyContext 可能导致 GImGui 变为 null。
                    // 游戏退出时其他模组（如 ChronoBlocks）可能已先清理其上下文，savedPreviousContext
                    // 可能已失效。为绝对安全，始终创建 fallback，确保后续任何 ImGui.getIO() 调用不崩溃。
                    ImGuiContext fallback = ImGui.createContext();
                    ImGui.setCurrentContext(fallback);
                    ImGui.destroyContext(ourContext);
                    ourContext = null;
                    // 保留 fallback 为 current，不销毁（游戏退出时的小泄漏可接受）
                }

                initialized = false;
                LOGGER.info("ImGui resources cleaned up");
            } catch (Exception e) {
                LOGGER.error("Error cleaning up", e);
                // 兜底：若异常导致未设置 current，尝试恢复或创建 fallback
                try {
                    if (ImGui.getCurrentContext() == null) {
                        ImGuiContext fallback = ImGui.createContext();
                        ImGui.setCurrentContext(fallback);
                        LOGGER.debug("dispose: 异常后兜底，已设置 fallback 上下文");
                    }
                } catch (Throwable t) {
                    LOGGER.error("dispose fallback failed", t);
                }
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
            windowHandle = window.getHandle();
            // 与 imgui_impl_glfw 一致：DisplaySize=窗口内容区，Scale=fb/窗口。用 GLFW 直接获取（IntBuffer 为 LWJGL 要求）
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer fbW = stack.mallocInt(1);
                IntBuffer fbH = stack.mallocInt(1);
                GLFW.glfwGetWindowSize(windowHandle, w, h);
                GLFW.glfwGetFramebufferSize(windowHandle, fbW, fbH);
                int winW = Math.max(1, w.get(0));
                int winH = Math.max(1, h.get(0));
                int fw = Math.max(1, fbW.get(0));
                int fh = Math.max(1, fbH.get(0));
                // 若窗口尺寸异常偏小（如 < 100），可能是驱动/环境问题，回退为 framebuffer 尺寸 + scale=1
                if (winW < 100 || winH < 100) {
                    winW = fw;
                    winH = fh;
                }
                float displayW = (float) winW;
                float displayH = (float) winH;
                ImGuiIO io = ImGui.getIO();
                io.setDisplaySize(displayW, displayH);
                io.setDisplayFramebufferScale((float) fw / winW, (float) fh / winH);
            }
        } catch (Exception e) {
            LOGGER.error("Error updating display size", e);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 关闭 Plot 界面时恢复此前已有的 ImGui 上下文。
     * 使 ChronoBlocks、Treefactory 或任何其他 ImGui 模组能继续正确渲染其字体与图标。
     */
    public void restorePreviousContext() {
        if (savedPreviousContext != null) {
            ImGui.setCurrentContext(savedPreviousContext);
            LOGGER.debug("已恢复此前 ImGui 上下文");
        }
    }
}
