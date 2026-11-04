package android.os;

/**
 * 桌面桩：androidasync 的 ByteBufferList 会用
 * {@code Looper.myLooper().getThread()} 判断线程亲和性。
 */
public final class Looper {
    private static final Looper MAIN = new Looper(Thread.currentThread());

    private final Thread thread;

    private Looper(Thread thread) {
        this.thread = thread;
    }

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return new Looper(Thread.currentThread());
    }

    public static void prepare() {
    }

    public static void loop() {
    }

    public Thread getThread() {
        return thread;
    }
}