package com.codearts.gateway.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/** 内存环形日志，供主界面 / 悬浮窗展示最近请求。 */
public final class LogRing {

    public interface Listener {
        void onLog(String line);
    }

    private static final int MAX = 400;
    private static final LinkedList<String> LINES = new LinkedList<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private LogRing() {
    }

    public static void addListener(Listener l) {
        if (l == null) {
            return;
        }
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(l)) {
                LISTENERS.add(l);
            }
        }
    }

    public static void removeListener(Listener l) {
        synchronized (LISTENERS) {
            LISTENERS.remove(l);
        }
    }

    public static void log(String tag, String msg) {
        String line = FMT.format(new Date()) + " [" + tag + "] " + msg;
        synchronized (LINES) {
            LINES.addLast(line);
            while (LINES.size() > MAX) {
                LINES.removeFirst();
            }
        }
        ArrayList<Listener> copy;
        synchronized (LISTENERS) {
            copy = new ArrayList<>(LISTENERS);
        }
        for (Listener l : copy) {
            try {
                l.onLog(line);
            } catch (Throwable ignored) {
                // 监听者异常不得影响网关主流程
            }
        }
    }

    public static List<String> snapshot() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static String dump() {
        StringBuilder sb = new StringBuilder();
        for (String s : snapshot()) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }
}
