package android.util;

/** 桌面桩：把 android.util.Log 输出到控制台。 */
public final class Log {
    private Log() {
    }

    public static int v(String tag, String msg) {
        return 0;
    }

    public static int d(String tag, String msg) {
        System.out.println("[d] " + tag + " " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("[i] " + tag + " " + msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.println("[w] " + tag + " " + msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.println("[e] " + tag + " " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        System.out.println("[e] " + tag + " " + msg);
        if (tr != null) {
            tr.printStackTrace();
        }
        return 0;
    }

    public static boolean isLoggable(String tag, int level) {
        return true;
    }
}