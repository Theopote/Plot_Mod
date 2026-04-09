package com.plot.ui.dialog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.util.function.Consumer;

/**
 * 系统文本输入对话框工具。
 *
 * <p>用于在 ImGui 输入框无法稳定处理 Windows IME/中文输入时，
 * 回退到系统原生 Swing 文本输入组件。</p>
 */
public final class TextDialogUtil {
    private static final Logger LOGGER = LogManager.getLogger("TextDialogUtil");

    private TextDialogUtil() {
    }

    public static void showSingleLineTextInputAsync(String title, String initialValue, int maxLength,
                                                    Consumer<String> callback) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.warn("当前为 headless 环境，无法打开系统单行文本输入框: {}", title);
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String result = null;
            try {
                JTextField textField = new JTextField(initialValue != null ? initialValue : "", Math.max(20, maxLength));
                int option = JOptionPane.showConfirmDialog(
                        null,
                        textField,
                        title,
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );
                if (option == JOptionPane.OK_OPTION) {
                    result = textField.getText();
                    if (result != null && result.length() > maxLength) {
                        result = result.substring(0, maxLength);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("显示系统单行文本输入框失败: {}", title, e);
            }

            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    public static void showMultilineTextInputAsync(String title, String initialValue,
                                                   Consumer<String> callback) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.warn("当前为 headless 环境，无法打开系统多行文本输入框: {}", title);
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String result = null;
            try {
                JTextArea textArea = new JTextArea(initialValue != null ? initialValue : "", 10, 36);
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                textArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(520, 260));

                int option = JOptionPane.showConfirmDialog(
                        null,
                        scrollPane,
                        title,
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );
                if (option == JOptionPane.OK_OPTION) {
                    result = textArea.getText();
                }
            } catch (Exception e) {
                LOGGER.error("显示系统多行文本输入框失败: {}", title, e);
            }

            if (callback != null) {
                callback.accept(result);
            }
        });
    }
}
