package com.masterplanner.ui.dialog;

import com.masterplanner.api.graphics.ITextStyle;
import com.masterplanner.core.graphics.style.TextStyle;
import com.masterplanner.core.graphics.style.TextAlignment;
import javax.swing.*;
import java.awt.*;

/**
 * 增强文字对话框 - 支持多行文字和对齐选项
 */
public class TextDialog extends JDialog {
    // 常量定义
    private static final String DEFAULT_FONT_FAMILY = "Arial";
    private static final float DEFAULT_FONT_SIZE = 16.0f;
    private static final Color DEFAULT_COLOR = Color.BLACK;
    private static final int DEFAULT_ROWS = 3;
    private static final int DEFAULT_COLUMNS = 20;
    
    // UI尺寸常量
    private static final Dimension BUTTON_SIZE_SMALL = new Dimension(30, 25);
    private static final Dimension BUTTON_SIZE_MEDIUM = new Dimension(60, 25);
    private static final Dimension COMBO_BOX_SIZE = new Dimension(80, 25);
    private static final Dimension SPINNER_SIZE = new Dimension(60, 25);
    
    // UI组件
    private JTextArea textArea;
    private JSpinner fontSizeSpinner;
    private JToggleButton boldButton;
    private JToggleButton italicButton;
    private JButton colorButton;
    private JComboBox<TextAlignment.Horizontal> hAlignCombo;
    private JComboBox<TextAlignment.Vertical> vAlignCombo;
    private Color selectedColor = DEFAULT_COLOR;
    private boolean confirmed = false;
    private String inputText = "";
    private TextStyle textStyle;
    private JButton okButton;
    private JButton cancelButton;

    public TextDialog(Frame owner) {
        super(owner, "添加文字", true);
        
        createComponents();
        layoutComponents();
        registerListeners();
        pack();
        // 如果owner为null，使用屏幕中心位置，避免相对不可见父窗口定位到屏幕外
        if (owner != null && owner.isShowing()) {
            setLocationRelativeTo(owner);
        } else {
            setLocationRelativeTo(null);
        }
        // 确保对话框在最前，避免被游戏窗口或其他层级遮挡
        setAlwaysOnTop(true);
        setResizable(false);
        
        // 设置默认焦点到文本输入框
        textArea.requestFocusInWindow();
    }

    /**
     * 创建UI组件
     */
    private void createComponents() {
        // 多行文本输入框
        textArea = new JTextArea(DEFAULT_ROWS, DEFAULT_COLUMNS);
        textArea.setFont(new Font(DEFAULT_FONT_FAMILY, Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(BorderFactory.createCompoundBorder(
            textArea.getBorder(),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        // 字体大小
        SpinnerNumberModel sizeModel = new SpinnerNumberModel(
            DEFAULT_FONT_SIZE, 
            TextStyle.MIN_FONT_SIZE, 
            TextStyle.MAX_FONT_SIZE, 
            2
        );
        fontSizeSpinner = new JSpinner(sizeModel);
        fontSizeSpinner.setPreferredSize(SPINNER_SIZE);
        
        // 粗体和斜体按钮
        boldButton = new JToggleButton("B");
        boldButton.setFont(new Font(DEFAULT_FONT_FAMILY, Font.BOLD, 12));
        boldButton.setPreferredSize(BUTTON_SIZE_SMALL);
        boldButton.setToolTipText("粗体");
        
        italicButton = new JToggleButton("I");
        italicButton.setFont(new Font(DEFAULT_FONT_FAMILY, Font.ITALIC, 12));
        italicButton.setPreferredSize(BUTTON_SIZE_SMALL);
        italicButton.setToolTipText("斜体");
        
        // 颜色选择按钮
        colorButton = new JButton("颜色");
        colorButton.setPreferredSize(BUTTON_SIZE_MEDIUM);
        colorButton.setToolTipText("选择文字颜色");
        
        // 对齐选项
        hAlignCombo = new JComboBox<>(TextAlignment.Horizontal.values());
        hAlignCombo.setSelectedItem(TextAlignment.Horizontal.LEFT);
        hAlignCombo.setPreferredSize(COMBO_BOX_SIZE);
        hAlignCombo.setToolTipText("水平对齐");
        
        vAlignCombo = new JComboBox<>(TextAlignment.Vertical.values());
        vAlignCombo.setSelectedItem(TextAlignment.Vertical.TOP);
        vAlignCombo.setPreferredSize(COMBO_BOX_SIZE);
        vAlignCombo.setToolTipText("垂直对齐");
        
        // 确定和取消按钮
        okButton = new JButton("确定");
        cancelButton = new JButton("取消");
        
        // 设置颜色按钮的初始颜色
        colorButton.setBackground(selectedColor);
        colorButton.setForeground(getContrastColor(selectedColor));
    }

    /**
     * 布局UI组件
     */
    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 北部面板：文本输入
        JPanel northPanel = new JPanel(new BorderLayout(5, 0));
        northPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        northPanel.add(new JLabel("文字内容:"), BorderLayout.WEST);

        // 直接使用文本区域，让对话框高度按内容自适应，避免固定滚动区域导致高度不匹配
        northPanel.add(textArea, BorderLayout.CENTER);
        
        // 中部面板：样式设置
        JPanel stylePanel = new JPanel(new GridBagLayout());
        stylePanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // 第一行：字体大小和样式
        gbc.gridx = 0; gbc.gridy = 0;
        stylePanel.add(new JLabel("大小:"), gbc);
        
        gbc.gridx = 1;
        stylePanel.add(fontSizeSpinner, gbc);
        
        gbc.gridx = 2;
        stylePanel.add(boldButton, gbc);
        
        gbc.gridx = 3;
        stylePanel.add(italicButton, gbc);
        
        gbc.gridx = 4;
        stylePanel.add(colorButton, gbc);
        
        // 第二行：对齐选项
        gbc.gridx = 0; gbc.gridy = 1;
        stylePanel.add(new JLabel("水平对齐:"), gbc);
        
        gbc.gridx = 1;
        stylePanel.add(hAlignCombo, gbc);
        
        gbc.gridx = 2;
        stylePanel.add(new JLabel("垂直对齐:"), gbc);
        
        gbc.gridx = 3;
        stylePanel.add(vAlignCombo, gbc);
        
        // 南部面板：按钮
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        southPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        southPanel.add(cancelButton);
        southPanel.add(okButton);
        
        // 添加到主面板
        add(northPanel, BorderLayout.NORTH);
        add(stylePanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    /**
     * 注册事件监听器
     */
    private void registerListeners() {
        // 确定和取消按钮
        okButton.addActionListener(e -> onConfirm());
        cancelButton.addActionListener(e -> onCancel());
        
        // 颜色选择按钮
        colorButton.addActionListener(e -> showColorChooser());
        
        // ESC键取消
        getRootPane().registerKeyboardAction(
            e -> onCancel(),
            "Cancel",
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        
        // 添加Ctrl+Enter快捷键确认
        getRootPane().registerKeyboardAction(
            e -> onConfirm(),
            "Confirm",
            KeyStroke.getKeyStroke("control ENTER"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    /**
     * 确认按钮处理
     */
    private void onConfirm() {
        confirmed = true;
        inputText = textArea.getText();
        textStyle = buildTextStyleFromUI();
        dispose();
    }

    /**
     * 取消按钮处理
     */
    private void onCancel() {
        confirmed = false;
        dispose();
    }

    /**
     * 显示颜色选择器
     */
    private void showColorChooser() {
        try {
            Color newColor = JColorChooser.showDialog(this, "选择文字颜色", selectedColor);
            if (newColor != null) {
                selectedColor = newColor;
                colorButton.setBackground(selectedColor);
                colorButton.setForeground(getContrastColor(selectedColor));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "颜色选择器初始化失败: " + e.getMessage(), 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 计算对比色，确保文字在背景色上可见
     */
    private Color getContrastColor(Color color) {
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    /**
     * 根据UI状态构建最终的TextStyle对象
     */
    private TextStyle buildTextStyleFromUI() {
        try {
            float fontSize = ((Number) fontSizeSpinner.getValue()).floatValue();
            boolean bold = boldButton.isSelected();
            boolean italic = italicButton.isSelected();
            TextAlignment.Horizontal hAlign = (TextAlignment.Horizontal) hAlignCombo.getSelectedItem();
            TextAlignment.Vertical vAlign = (TextAlignment.Vertical) vAlignCombo.getSelectedItem();
            
            return new TextStyle.Builder()
                .fontFamily(DEFAULT_FONT_FAMILY)
                .fontSize(fontSize)
                .color(selectedColor)
                .bold(bold)
                .italic(italic)
                .horizontalAlignment(hAlign)
                .verticalAlignment(vAlign)
                .build();
        } catch (Exception e) {
            // 如果构建失败，返回一个安全的默认样式
            return new TextStyle.Builder()
                .fontFamily(DEFAULT_FONT_FAMILY)
                .fontSize(DEFAULT_FONT_SIZE)
                .color(DEFAULT_COLOR)
                .build();
        }
    }

    /**
     * 检查是否确认
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * 获取输入的文本
     */
    public String getInputText() {
        return inputText;
    }

    /**
     * 获取文本样式
     * 只有在确认后才返回有效的样式
     */
    public ITextStyle getTextStyle() {
        if (!confirmed) {
            return null;
        }
        return textStyle;
    }

    /**
     * 设置初始字体大小
     * @param fontSize 初始字体大小
     */
    public void setInitialFontSize(float fontSize) {
        if (fontSizeSpinner != null) {
            fontSizeSpinner.setValue(Math.max(TextStyle.MIN_FONT_SIZE, 
                Math.min(TextStyle.MAX_FONT_SIZE, fontSize)));
        }
    }

    /**
     * 设置初始粗体状态
     * @param bold 是否粗体
     */
    public void setInitialBold(boolean bold) {
        if (boldButton != null) {
            boldButton.setSelected(bold);
        }
    }

    /**
     * 设置初始斜体状态
     * @param italic 是否斜体
     */
    public void setInitialItalic(boolean italic) {
        if (italicButton != null) {
            italicButton.setSelected(italic);
        }
    }

    /**
     * 设置初始水平对齐
     * @param hAlign 水平对齐方式
     */
    public void setInitialHorizontalAlignment(TextAlignment.Horizontal hAlign) {
        if (hAlignCombo != null && hAlign != null) {
            hAlignCombo.setSelectedItem(hAlign);
        }
    }

    /**
     * 设置初始垂直对齐
     * @param vAlign 垂直对齐方式
     */
    public void setInitialVerticalAlignment(TextAlignment.Vertical vAlign) {
        if (vAlignCombo != null && vAlign != null) {
            vAlignCombo.setSelectedItem(vAlign);
        }
    }
} 