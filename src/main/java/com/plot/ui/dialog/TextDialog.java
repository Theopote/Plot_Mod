package com.plot.ui.dialog;

import com.plot.api.graphics.ITextStyle;
import com.plot.core.graphics.style.TextStyle;
import com.plot.core.graphics.style.TextAlignment;
import javax.swing.*;
import java.awt.*;

/**
 * 增强文字对话框 - 支持多行文字和对齐选项
 */
public class TextDialog extends JDialog {
    // 常量定义
    private static final String DEFAULT_FONT_FAMILY = "Arial";
    private static final float DEFAULT_FONT_SIZE = 16.0f;
    private static final float DEFAULT_LINE_HEIGHT = TextStyle.DEFAULT_LINE_HEIGHT;
    private static final Color DEFAULT_COLOR = Color.BLACK;
    private static final int DEFAULT_ROWS = 5;
    private static final int DEFAULT_COLUMNS = 20;
    
    // UI尺寸常量
    private static final Dimension BUTTON_SIZE_SMALL = new Dimension(30, 25);
    private static final Dimension BUTTON_SIZE_MEDIUM = new Dimension(60, 25);
    private static final Dimension COMBO_BOX_SIZE = new Dimension(120, 25);
    private static final Dimension SPINNER_SIZE = new Dimension(90, 25);
    private static final Dimension TEXT_AREA_SIZE = new Dimension(380, 180);
    private static final int DIALOG_MIN_WIDTH = 500;
    private static final int DIALOG_EXTRA_VERTICAL_PADDING = 40;
    private static final int BUTTON_AREA_EXTRA_HEIGHT = BUTTON_SIZE_MEDIUM.height + 20;
    
    // UI组件
    private JTextArea textArea;
    private JSpinner fontSizeSpinner;
    private JSpinner lineHeightSpinner;
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
        applyAdaptiveMinimumSize();
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
        textArea.setPreferredSize(TEXT_AREA_SIZE);
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

        // 行高
        SpinnerNumberModel lineHeightModel = new SpinnerNumberModel(
            DEFAULT_LINE_HEIGHT,
            0.8f,
            3.0f,
            0.1f
        );
        lineHeightSpinner = new JSpinner(lineHeightModel);
        lineHeightSpinner.setPreferredSize(SPINNER_SIZE);
        
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
        setLayout(new BorderLayout(0, 8));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JLabel textTitle = new JLabel("请输入文字内容（可多行）");
        textTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(textTitle);
        contentPanel.add(Box.createVerticalStrut(6));

        JScrollPane textScrollPane = new JScrollPane(
            textArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        textScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        textScrollPane.setPreferredSize(TEXT_AREA_SIZE);
        textScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, TEXT_AREA_SIZE.height));
        contentPanel.add(textScrollPane);
        contentPanel.add(Box.createVerticalStrut(10));

        JPanel stylePanel = new JPanel(new GridBagLayout());
        stylePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stylePanel.setBorder(BorderFactory.createTitledBorder("文字样式"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        stylePanel.add(new JLabel("文字大小"), gbc);
        gbc.gridx = 1;
        stylePanel.add(fontSizeSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        stylePanel.add(new JLabel("行高"), gbc);
        gbc.gridx = 1;
        stylePanel.add(lineHeightSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        stylePanel.add(new JLabel("字形"), gbc);
        JPanel glyphPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        glyphPanel.add(boldButton);
        glyphPanel.add(italicButton);
        glyphPanel.add(colorButton);
        gbc.gridx = 1;
        stylePanel.add(glyphPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        stylePanel.add(new JLabel("对齐"), gbc);
        JPanel alignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        alignPanel.add(hAlignCombo);
        alignPanel.add(vAlignCombo);
        gbc.gridx = 1;
        stylePanel.add(alignPanel, gbc);

        contentPanel.add(stylePanel);

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        southPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        southPanel.add(cancelButton);
        southPanel.add(okButton);

        add(contentPanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void applyAdaptiveMinimumSize() {
        Dimension contentPreferred = getContentPane().getPreferredSize();
        Insets insets = getInsets();

        int minWidth = Math.max(
            DIALOG_MIN_WIDTH,
            contentPreferred.width + insets.left + insets.right
        );
        int minHeight = contentPreferred.height
            + insets.top
            + insets.bottom
            + DIALOG_EXTRA_VERTICAL_PADDING
            + BUTTON_AREA_EXTRA_HEIGHT;

        Dimension adaptiveMinSize = new Dimension(minWidth, minHeight);
        setMinimumSize(adaptiveMinSize);

        if (getWidth() < adaptiveMinSize.width || getHeight() < adaptiveMinSize.height) {
            setSize(
                Math.max(getWidth(), adaptiveMinSize.width),
                Math.max(getHeight(), adaptiveMinSize.height)
            );
        }
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
            float lineHeight = ((Number) lineHeightSpinner.getValue()).floatValue();
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
                .lineHeight(lineHeight)
                .build();
        } catch (Exception e) {
            // 如果构建失败，返回一个安全的默认样式
            return new TextStyle.Builder()
                .fontFamily(DEFAULT_FONT_FAMILY)
                .fontSize(DEFAULT_FONT_SIZE)
                .color(DEFAULT_COLOR)
                .lineHeight(DEFAULT_LINE_HEIGHT)
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

    public void setInitialLineHeight(float lineHeight) {
        if (lineHeightSpinner != null) {
            float clamped = Math.max(0.8f, Math.min(3.0f, lineHeight));
            lineHeightSpinner.setValue(clamped);
        }
    }
} 