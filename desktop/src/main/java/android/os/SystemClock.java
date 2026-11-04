package android.os;

/** 桌面桩：androidasync 用于超时/线程判定。 */
public final class SystemClock {
    private SystemClock() {
    }

    public static long uptimeMillis() {
        return System.nanoTime() / 1000000L;
    }

    public static long elapsedRealtime() {
        return System.nanoTime() / 1000000L;
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}