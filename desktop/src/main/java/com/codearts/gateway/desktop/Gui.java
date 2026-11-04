package com.codearts.gateway.desktop;

import android.content.Context;

import com.codearts.gateway.core.GatewayServer;
import com.codearts.gateway.core.LogRing;
import com.codearts.gateway.core.Store;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Windows 桌面版配置窗口。 */
final class Gui implements LogRing.Listener {

    private final Context ctx;
    private final Store store;

    private final JFrame frame = new JFrame("码道网关 CodeArts Gateway (Windows)");
    private final JTextField portField = new JTextField(8);
    private final JTextField apiKeyField = new JTextField(24);
    private final JTextArea cookieArea = new JTextArea(5, 46);
    private final JCheckBox lanBox = new JCheckBox("允许局域网访问（0.0.0.0）");
    private final JCheckBox thinkingBox = new JCheckBox("流式透出思考过程");
    private final JCheckBox lastUserBox = new JCheckBox("只发送最后一条用户输入");
    private final JCheckBox deliverablesBox = new JCheckBox("透传码道产物到 agent（列表 + 文本内容 + 本地落盘）");
    private final JCheckBox trimBox = new JCheckBox("裁剪上游裸 system 提示");
    private final JLabel statusLabel = new JLabel("未启动");
    private final JButton startBtn = new JButton("保存并启动");
    private final JButton stopBtn = new JButton("停止");
    private final JTextArea logArea = new JTextArea(14, 60);

    private GatewayServer server;

    private Gui(Context ctx) {
        this.ctx = ctx;
        this.store = Store.get(ctx);
    }

    static void show(Context ctx) {
        Gui gui = new Gui(ctx);
        SwingUtilities.invokeLater(gui::build);
    }

    private void build() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        cookieArea.setLineWrap(true);
        cookieArea.setWrapStyleWord(true);
        portField.setText(String.valueOf(store.getPort()));
        apiKeyField.setText(store.getApiKeys().isEmpty() ? "" : store.getApiKeys().get(0));
        cookieArea.setText(store.getCookie());
        lanBox.setSelected(store.isLanEnabled());
        thinkingBox.setSelected(store.isStreamThinking());
        lastUserBox.setSelected(store.isOnlyLastUser());
        deliverablesBox.setSelected(store.isDeliverables());
        trimBox.setSelected(store.isTrimSystem());

        startBtn.addActionListener(e -> saveAndStart());
        stopBtn.addActionListener(e -> stopServer());
        JButton openDirBtn = new JButton("打开产物目录");
        openDirBtn.addActionListener(e -> openArtifactDir());
        JButton clearLogBtn = new JButton("清空日志");
        clearLogBtn.addActionListener(e -> logArea.setText(""));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("配置"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 6, 3, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        form.add(new JLabel("监听端口"), gc);
        gc.gridx = 1;
        gc.weightx = 0.2;
        form.add(portField, gc);
        gc.gridx = 2;
        gc.weightx = 0.8;
        form.add(lanBox, gc);
        row++;

        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        form.add(new JLabel("网关密钥(可空)"), gc);
        gc.gridx = 1;
        gc.gridwidth = 2;
        gc.weightx = 1;
        form.add(apiKeyField, gc);
        gc.gridwidth = 1;
        row++;

        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("码道 Cookie"), gc);
        JScrollPane cookieScroll = new JScrollPane(cookieArea);
        cookieScroll.setBorder(BorderFactory.createEmptyBorder());
        gc.gridx = 1;
        gc.gridwidth = 2;
        gc.weightx = 1;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.BOTH;
        form.add(cookieScroll, gc);
        gc.gridwidth = 1;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;
        row++;

        JPanel boxes = new JPanel();
        boxes.setLayout(new BoxLayout(boxes, BoxLayout.Y_AXIS));
        boxes.add(thinkingBox);
        boxes.add(lastUserBox);
        boxes.add(deliverablesBox);
        boxes.add(trimBox);
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        form.add(boxes, gc);
        gc.gridwidth = 1;
        row++;

        JPanel buttons = new JPanel();
        buttons.add(startBtn);
        buttons.add(stopBtn);
        buttons.add(openDirBtn);
        buttons.add(clearLogBtn);
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        form.add(buttons, gc);
        gc.gridwidth = 1;
        row++;

        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        form.add(statusLabel, gc);

        frame.setLayout(new BorderLayout(6, 6));
        frame.add(form, BorderLayout.NORTH);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("运行日志"));
        frame.add(logScroll, BorderLayout.CENTER);
        frame.add(new JLabel(" 产物/配置目录: " + ctx.getBaseDir().getAbsolutePath() + "    "
                + "Windows 端把该目录加入 agent 工作区即可直接读取产物  "),
                BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopServer();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.pack();
        frame.setMinimumSize(new Dimension(760, 620));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        LogRing.addListener(this);
        append("启动前请先填入码道 Cookie（含 cftk），再点“保存并启动”。");
    }

    private synchronized void saveAndStart() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("端口越界");
            }
            store.setPort(port);
        } catch (Throwable t) {
            statusLabel.setText("端口无效: " + portField.getText());
            return;
        }
        store.setCookie(cookieArea.getText());
        store.setLanEnabled(lanBox.isSelected());
        store.setStreamThinking(thinkingBox.isSelected());
        store.setOnlyLastUser(lastUserBox.isSelected());
        store.setDeliverables(deliverablesBox.isSelected());
        store.setTrimSystem(trimBox.isSelected());
        List<String> keys = new ArrayList<>();
        String key = apiKeyField.getText().trim();
        if (!key.isEmpty()) {
            keys.add(key);
        }
        store.saveApiKeys(keys);

        stopServer();
        try {
            server = new GatewayServer(ctx);
            server.start();
            statusLabel.setText("运行中: http://127.0.0.1:" + server.getPort()
                    + "  (会话 " + server.getSessionCount() + ")");
        } catch (Throwable t) {
            statusLabel.setText("启动失败: " + t);
            LogRing.log("desktop", "启动失败: " + t);
        }
    }

    private synchronized void stopServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Throwable ignored) {
                // 忽略
            }
            server = null;
            statusLabel.setText("已停止");
        }
    }

    private void openArtifactDir() {
        try {
            File dir = new File(ctx.getBaseDir(), "Download");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Desktop.getDesktop().open(dir);
        } catch (Throwable t) {
            LogRing.log("desktop", "打开目录失败: " + t);
        }
    }

    @Override
    public void onLog(String line) {
        SwingUtilities.invokeLater(() -> append(line));
    }

    private void append(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}