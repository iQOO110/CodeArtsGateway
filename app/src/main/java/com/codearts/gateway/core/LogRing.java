package com.codearts.gateway.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/** 环形日志：内存展示 + SharedPreferences 持久化，App 重启后仍保留最近日志。 */
public final class LogRing {

    public interface Listener {
        void onLog(String line);
    }

    private static final int MAX = 400;
    private static final String PREF = "logring";
    private static final String KEY = "lines";
    private static final LinkedList<String> LINES = new LinkedList<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private static SharedPreferences sPrefs;
    private static boolean sLoaded;

    private LogRing() {
    }

    public static synchronized void attach(Context ctx) {
        if (ctx == null) {
            return;
        }
        if (sPrefs == null) {
            sPrefs = ctx.getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        }
        if (sLoaded) {
            return;
        }
        sLoaded = true;
        try {
            String raw = sPrefs.getString(KEY, "");
            if (raw != null && !raw.isEmpty()) {
                synchronized (LINES) {
                    LINES.clear();
                    for (String line : raw.split("\n")) {
                        if (!line.isEmpty()) {
                            LINES.addLast(line);
                        }
                    }
                    while (LINES.size() > MAX) {
                        LINES.removeFirst();
                    }
                }
            }
        } catch (Throwable ignore) {
            // 损坏的日志直接忽略
        }
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
        persist();
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
        persist();
    }

    private static void persist() {
        SharedPreferences p = sPrefs;
        if (p == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (LINES) {
            for (String line : LINES) {
                sb.append(line).append('\n');
            }
        }
        try {
            p.edit().putString(KEY, sb.toString()).apply();
        } catch (Throwable ignore) {
            // 忽略
        }
    }
}