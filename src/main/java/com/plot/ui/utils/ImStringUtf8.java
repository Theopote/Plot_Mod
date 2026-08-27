package com.plot.ui.utils;

import imgui.type.ImString;

import java.nio.charset.StandardCharsets;

/**
 * 从 ImGui {@link ImString} 读取完整 UTF-8 文本。
 *
 * <p>imgui-java 的 {@link ImString#get()} 依赖内部 size 字段解码；
 * 中文输入后 size 可能不会随字节长度更新，导致只保留前几个汉字、后续变成问号。</p>
 */
public final class ImStringUtf8 {
    private ImStringUtf8() {
    }

    public static String read(ImString imString) {
        if (imString == null) {
            return "";
        }

        byte[] data = imString.getData();
        if (data == null || data.length == 0) {
            return "";
        }

        int length = 0;
        while (length < data.length && data[length] != 0) {
            length++;
        }
        if (length == 0) {
            return "";
        }

        return new String(data, 0, length, StandardCharsets.UTF_8);
    }
}
