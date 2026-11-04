package com.codearts.gateway.desktop;

import android.content.Context;

import com.codearts.gateway.core.GatewayServer;
import com.codearts.gateway.core.LogRing;

import java.io.File;

/**
 * Windows 桌面版入口。
 *
 * <p>用法：
 * <pre>
 *   codearts-gateway.exe                 启动图形窗口（默认）
 *   codearts-gateway.exe --headless      只读配置文件直接启动（无界面）
 *   codearts-gateway.exe --base=D:\dir   指定数据目录（配置/产物/日志）
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // JDK 16+ 在 Windows 上 PipeImpl 默认走 Unix Domain Socket，
        // 当 TEMP 路径含空格或特殊字符时 androidasync 的 Selector 初始化会失败。
        // 设置临时目录为不含空格的短路径可规避该问题。
        fixTempForSelector();

        File base = defaultBase();
        boolean headless = false;
        for (String a : args) {
            if (a == null) {
                continue;
            }
            if ("--headless".equalsIgnoreCase(a)) {
                headless = true;
            } else if (a.startsWith("--base=")) {
                base = new File(a.substring("--base=".length()));
            }
        }
        Context ctx = new Context(base);
        System.out.println("CodeArts Gateway (Windows) 数据目录: " + base.getAbsolutePath());
        LogRing.log("desktop", "数据目录: " + base.getAbsolutePath());
        if (headless) {
            GatewayServer server = new GatewayServer(ctx);
            server.start();
            LogRing.log("desktop", "已以无界面模式启动，监听端口 " + server.getPort());
            // 常驻
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        Gui.show(ctx);
    }

    private static File defaultBase() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            home = ".";
        }
        return new File(home, "CodeArtsGateway");
    }

    /** 确保 java.io.tmpdir 指向一个不含空格的目录，避免 JDK 21 Unix Domain Socket 路径异常。 */
    private static void fixTempForSelector() {
        String tmp = System.getProperty("java.io.tmpdir", "");
        if (!tmp.contains(" ")) {
            return;
        }
        File fallback = new File("C:\\Temp");
        if (!fallback.exists()) {
            fallback.mkdirs();
        }
        if (fallback.isDirectory() && fallback.canWrite()) {
            System.setProperty("java.io.tmpdir", fallback.getAbsolutePath());
            LogRing.log("desktop", "java.io.tmpdir 重定向至 " + fallback.getAbsolutePath()
                    + "（原路径含空格，JDK 21 NIO 兼容性修复）");
        }
    }
}
